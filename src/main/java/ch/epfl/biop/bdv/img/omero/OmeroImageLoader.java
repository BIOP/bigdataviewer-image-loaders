/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package ch.epfl.biop.bdv.img.omero;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.cache.SharedQueue;
import ch.epfl.biop.bdv.img.OmeroBdvOpener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import omero.gateway.Gateway;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ch.epfl.biop.bdv.img.bioformats.BioFormatsImageLoader.getVolatileOf;

public class OmeroImageLoader implements ViewerImgLoader,
	MultiResolutionImgLoader, Closeable
{

	public List<OmeroBdvOpener> openers;

	Map<Integer, OpenerIdxChannel> viewSetupToOpenerIdxChannel = new HashMap<>();

	Map<Integer, NumericType> tTypeGetter = new HashMap<>();

	Map<Integer, Volatile> vTypeGetter = new HashMap<>();

	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

	HashMap<Integer, OmeroSetupLoader> imgLoaders = new HashMap<>();

	public Consumer<String> log = s -> {};// System.out.println(s);

	protected VolatileGlobalCellCache cache;

	protected SharedQueue cc;

	public final int numFetcherThreads;
	public final int numPriorities;

	/**
	 * OMERO image loader constructor
	 * 
	 * @param openers
	 * @param sequenceDescription
	 * @param numFetcherThreads
	 * @param numPriorities
	 * @throws Exception
	 */
	public OmeroImageLoader(List<OmeroBdvOpener> openers,
		final AbstractSequenceDescription<?, ?, ?> sequenceDescription,
		int numFetcherThreads, int numPriorities) throws Exception
	{
		this.openers = openers;
		this.sequenceDescription = sequenceDescription;
		this.numFetcherThreads = numFetcherThreads;
		this.numPriorities = numPriorities;
		cc = new SharedQueue(numFetcherThreads, numPriorities);

		//openers.forEach(opener -> opener.setCache(cc));

		int viewSetupCounter = 0;
		if ((sequenceDescription != null)) {
			// openersIdxStream.forEach(openerIdx -> {
			for (int openerIdx = 0; openerIdx < openers.size(); openerIdx++) {
				OmeroBdvOpener opener = openers.get(openerIdx);
				this.openers.add(opener);
				// Register Setups (one per channel and one per timepoint)
				for (int channelIdx = 0; channelIdx < opener.getSizeC(); channelIdx++) {
					OpenerIdxChannel openerIdxChannel = new OpenerIdxChannel(openerIdx,
						channelIdx);
					viewSetupToOpenerIdxChannel.put(viewSetupCounter, openerIdxChannel);
					Type t = opener.getNumericType(0);
					tTypeGetter.put(viewSetupCounter, (NumericType) t);
					Volatile v = getVolatileOf((NumericType) t);
					vTypeGetter.put(viewSetupCounter, v);
					viewSetupCounter++;
				}
			}
		}
		cache = new VolatileGlobalCellCache(cc);
	}

	@Override
	public OmeroSetupLoader getSetupImgLoader(int setupId) {
		if (imgLoaders.containsKey(setupId)) {
			return imgLoaders.get(setupId);
		}
		else {
			int openerIdx = viewSetupToOpenerIdxChannel.get(setupId).openerIdx;
			int channel = viewSetupToOpenerIdxChannel.get(setupId).iChannel;

			OmeroSetupLoader imgL = null;
			try {
				imgL = new OmeroSetupLoader(openers.get(openerIdx), channel, setupId,
					tTypeGetter.get(setupId), vTypeGetter.get(setupId), () -> this.cache);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			imgLoaders.put(setupId, imgL);
			return imgL;
		}
	}

	@Override
	public CacheControl getCacheControl() {
		return cache;
	}

	@Override
	public void close() throws IOException {
		Set<Gateway> all_gateways = openers.stream().map(opener -> opener
			.getGateway()).collect(Collectors.toSet());
		all_gateways.forEach(gateway -> {
			System.out.println("Session active : " + gateway.isConnected());
			gateway.disconnect();
			System.out.println("Gateway disconnected");
		});

	}
}
