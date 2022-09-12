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
import bdv.cache.CacheControl;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsSetupLoader;
import ch.epfl.biop.bdv.img.bioformats.FileChannel;
import ch.epfl.biop.bdv.img.omero.OmeroSetupLoader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Generic class implementing how to load an image on BDV.
 * Only setup loaders depend on the opener type (BioFormats, OMERO, OpenSlide, and other)
 */
public class ImageLoader implements ViewerImgLoader, MultiResolutionImgLoader, Closeable
{

	final protected static Logger logger = LoggerFactory.getLogger(ImageLoader.class);


	// -------- ViewSetups core infos (pixel type, channels)
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	final Map<Integer, FileChannel> viewSetupToFileChannel = new HashMap<>();
	int viewSetupCounter = 0;
	final Map<Integer, NumericType> tTypeGetter = new HashMap<>();
	final Map<Integer, Volatile> vTypeGetter = new HashMap<>();


	// -------- setupLoader registrtation
	final HashMap<Integer, BiopSetupLoader> setupLoaders = new HashMap<>();


	// -------- How to open image (threds, cache)
	protected final VolatileGlobalCellCache cache;
	protected final SharedQueue sq;
	public final int numFetcherThreads = 2;
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
	 * @param openers
	 * @param openerSettings
	 * @param sequenceDescription
	 */
	public ImageLoader(List<Opener<?>> openers,
                       List<OpenerSettings> openerSettings,
                       final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.openerSettings = openerSettings;
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
						FileChannel fsc = new FileChannel(iFile, iCh);
						viewSetupToFileChannel.put(viewSetupCounter, fsc);
						viewSetupCounter++;
					});

					// get pixel types
					Type t = opener.getPixelType();
					tTypeGetter.put(iF, (NumericType) t);
					Volatile v = getVolatileOf((NumericType) t);
					vTypeGetter.put(iF, v);

				}
				catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		cache = new VolatileGlobalCellCache(sq);
	}

	/**
	 *
	 * @param setupId : viewsetup id
	 * @return the setupLoader corresponding to the current viewsetup id
	 */
	public BiopSetupLoader getSetupImgLoader(int setupId) {
		try {
			// if already registered setup loader
			if (setupLoaders.containsKey(setupId)) {
				return setupLoaders.get(setupId);
			}
			else {
				int iF = viewSetupToFileChannel.get(setupId).iFile;
				int iC = viewSetupToFileChannel.get(setupId).iChannel;
				logger.debug("loading file number = " + iF + " setupId = " + setupId);

				// select the correct setup loader according to opener type
				try {
					if (openers.get(iF) instanceof BioFormatsBdvOpener) {
						BioFormatsSetupLoader imgL = new BioFormatsSetupLoader((BioFormatsBdvOpener) openers.get(iF),
								iC, setupId, tTypeGetter.get(iF), vTypeGetter.get(iF), this::getCacheControl);

						setupLoaders.put(setupId, imgL);
						return imgL;
					}
					if (openers.get(iF) instanceof OmeroBdvOpener) {
						OmeroSetupLoader imgL = new OmeroSetupLoader((OmeroBdvOpener) openers.get(iF),
								iC, setupId, tTypeGetter.get(iF), vTypeGetter.get(iF), this::getCacheControl);

						setupLoaders.put(setupId, imgL);
						return imgL;
					}
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
	public CacheControl getCacheControl() {
		return cache;
	}


	@Override
	public void close() {
		synchronized (this) {
			openers.forEach(opener -> close());
			cache.clearCache();
			sq.shutdown();
		}
	}


	/**
	 *
	 * @param t
	 * @return volatile pixel type from t
	 */
	public static Volatile getVolatileOf(NumericType t) {
		if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

		if (t instanceof IntType) return new VolatileIntType();

		if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

		if (t instanceof FloatType) return new VolatileFloatType();

		if (t instanceof ARGBType) return new VolatileARGBType();
		return null;
	}

}
