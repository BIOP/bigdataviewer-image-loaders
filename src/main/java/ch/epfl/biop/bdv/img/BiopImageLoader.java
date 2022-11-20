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

package ch.epfl.biop.bdv.img;

import bdv.ViewerImgLoader;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Generic class implementing how to load an image on BDV.
 * Only setup loaders depend on the opener type (BioFormats, OMERO, OpenSlide, and other)
 */
public class BiopImageLoader implements ViewerImgLoader, MultiResolutionImgLoader, Closeable
{

	final protected static Logger logger = LoggerFactory.getLogger(BiopImageLoader.class);


	// -------- ViewSetups core infos (pixel type, channels)
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	final Map<Integer, OpenerAndChannelIndex> viewSetupToOpenerChannel = new HashMap<>();
	int viewSetupCounter = 0;


	// -------- setupLoader registration
	final Map<Integer, BiopSetupLoader<?,?,?>> setupLoaders = new HashMap<>();

	// -------- setupLoader optimisation
	Map<String, Opener> rawPixelDataChannelToOpener = new HashMap<>();
	Map<String, BiopSetupLoader<?,?,?>> rawPixelDataChannelToSetupLoader = new HashMap<>();

	// -------- How to open image (threads, cache)
	protected final VolatileGlobalCellCache cache;
	protected final SharedQueue sq;
	public final int numFetcherThreads = 10;
	public final int numPriorities = 4;


	// -------- Openers core infos
	final  List<OpenerSettings> openerSettings;
	final public List<Opener<?>> openers;


	// GETTER
	public  List<OpenerSettings> getOpenerSettings() {
		return openerSettings;
	}

	/**
	 * Constructor
	 * @param openerSettings
	 * @param sequenceDescription
	 */
	public BiopImageLoader(List<OpenerSettings> openerSettings,
						   final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this(openerSettings, createOpeners(openerSettings), sequenceDescription);
	}

	/**
	 * Constructor
	 * @param openerSettings
	 * @param sequenceDescription
	 */
	public BiopImageLoader(List<OpenerSettings> openerSettings,
						   List<Opener<?>> openers,
						   final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.openerSettings = openerSettings; // Need to keep a ref for serialization
		this.openers = openers;
		this.sequenceDescription = sequenceDescription;
		this.sq = new SharedQueue(numFetcherThreads, numPriorities);

		// for each opener
		IntStream openersIdxStream = IntStream.range(0, openers.size());
		if ((sequenceDescription != null)) {
			openersIdxStream.forEach(iF -> {
				try {
					// get the opener
					Opener<?> opener = openers.get(iF);
					final int iFile = iF;

					logger.debug("\t Number of timesteps = " + opener.getNTimePoints());
					logger.debug("\t Number of channels = " +opener.getNChannels());

					// Register Setups (one per channel and one per timepoint)
					IntStream channels = IntStream.range(0, opener.getNChannels());
					channels.forEach(iCh -> {
						rawPixelDataChannelToOpener.put(iCh+"."+opener.getRawPixelDataKey(), opener);
						OpenerAndChannelIndex oci = new OpenerAndChannelIndex(iFile, iCh);
						viewSetupToOpenerChannel.put(viewSetupCounter, oci);
						viewSetupCounter++;
					});
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		cache = new VolatileGlobalCellCache(sq);
	}

	public static List<Opener<?>> createOpeners(List<OpenerSettings> openerSettings) {
		List<Opener<?>> openers = new ArrayList<>();
		Map<String, Object> cachedObjects = new HashMap<>();
		openerSettings.forEach(settings -> {
			try {
				Opener<?> opener = settings.create(cachedObjects);
				openers.add(opener);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return openers;
	}

	/**
	 * @param setupId : viewsetup id
	 * @return the setupLoader corresponding to the current viewsetup id
	 */
	public BiopSetupLoader getSetupImgLoader(int setupId) {
		try {
			// if already registered setup loader
			if (setupLoaders.containsKey(setupId)) {
				return setupLoaders.get(setupId);
			}

			int iOpener = viewSetupToOpenerChannel.get(setupId).openerIndex;
			int iC = viewSetupToOpenerChannel.get(setupId).channelIndex;

			String keySetup = iC+"."+openers.get(viewSetupToOpenerChannel.get(setupId).openerIndex).getRawPixelDataKey();

			if (rawPixelDataChannelToSetupLoader.containsKey(keySetup)) {
				System.out.println("Reuse "+keySetup);
				BiopSetupLoader<?,?,?> loader = rawPixelDataChannelToSetupLoader.get(keySetup);
				setupLoaders.put(setupId, loader);
				return loader;
				//rawPixelDataChannelToSetupLoader.get(keySetup);.get(openers.get(viewSetupToOpenerChannel.get(setupId).openerIndex).getRawPixelDataKey());
				//problem : couleur et a mettre dans setupLoader
				//		faire la fonction synchronized peut etre
			}
			else {

				logger.debug("loading file number = " + iOpener + " setupId = " + setupId);

				// select the correct setup loader according to opener type
				try {
					BiopSetupLoader<?,?,?> imgL = openers.get(iOpener).getSetupLoader(iC, setupId, this::getCacheControl);
					setupLoaders.put(setupId, imgL);
					rawPixelDataChannelToSetupLoader.put(keySetup, imgL);
					return imgL;
				}catch(Exception e){
					e.printStackTrace();
				}

				return null;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Error in setup loader creation: " + e
				.getMessage());
		}
	}

	@Override
	public VolatileGlobalCellCache getCacheControl() {
		return cache;
	}


	@Override
	public void close() {
		openers.forEach(opener -> {
			try {
				opener.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		cache.clearCache();
		sq.shutdown();
	}


	/**
	 * Small class to save opener and channel index together
	 */
	public class OpenerAndChannelIndex {
		final int openerIndex;
		final int channelIndex;

		public OpenerAndChannelIndex(int openerIndex, int channelIndex) {
			this.openerIndex = openerIndex;
			this.channelIndex = channelIndex;
		}
	}

}