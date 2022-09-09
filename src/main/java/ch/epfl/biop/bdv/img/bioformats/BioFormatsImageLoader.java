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

package ch.epfl.biop.bdv.img.bioformats;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.Opener;
import ch.epfl.biop.bdv.img.OpenerSettings;
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
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileIntType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class BioFormatsImageLoader implements ViewerImgLoader,
	MultiResolutionImgLoader, Closeable
{

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsBdvOpener.class);

	final public List<Opener<?>> openers;

	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

	final Map<Integer, FileChannel> viewSetupToBFFileSerieChannel =
		new HashMap<>();

	int viewSetupCounter = 0;

	final Map<Integer, NumericType> tTypeGetter = new HashMap<>();

	final Map<Integer, Volatile> vTypeGetter = new HashMap<>();

	final HashMap<Integer, BioFormatsSetupLoader> setupLoaders = new HashMap<>();

	@SuppressWarnings("CanBeFinal")
	protected VolatileGlobalCellCache cache;

	protected SharedQueue sq;

	public final int numFetcherThreads = 2;
	public final int numPriorities = 4;

	final  List<OpenerSettings> openerSettings;

	public  List<OpenerSettings> getOpenerSettings() {
		return openerSettings;
	}

	public BioFormatsImageLoader(List<Opener<?>> openers,
								 List<OpenerSettings> openerSettings,
		final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.openerSettings = openerSettings;
		this.openers = openers;
		this.sequenceDescription = sequenceDescription;
		sq = new SharedQueue(numFetcherThreads, numPriorities);

		//openers.forEach(opener -> opener.setCache(sq));

		IntStream openersIdxStream = IntStream.range(0, openers.size());
		if ((sequenceDescription != null)) {
			openersIdxStream.forEach(iF -> {
				try {
					Opener<?> opener = openers.get(iF);

					//logger.debug("Data location = " + opener.getDataLocation());

					//IFormatReader memo = opener.getNewReader();

					//logger.debug("Number of Series : " + opener.getSerieCount());//memo.getSeriesCount());
					//IMetadata omeMeta = opener.getMetadata();//(IMetadata) memo.getMetadataStore();
					//memo.setMetadataStore(omeMeta);
					// -------------------------- SETUPS For each Series : one per
					// timepoint and one per channel

					//IntStream series = IntStream.range(0, opener.getSerieCount());//memo.getSeriesCount());

					final int iFile = iF;
					// int iSerie = opener.getSerie();
					//series.forEach(iSerie -> {
						//memo.setSeries(iSerie);
						// One serie = one Tile
						// ---------- Serie >
						// ---------- Serie > Timepoints
						logger.debug("\t Number of timesteps = " + opener.getNTimePoints());
						// ---------- Serie > Channels
						logger.debug("\t Number of channels = " +opener.getNChannels());
						// Properties of the serie

					    // Register Setups (one per channel and one per timepoint)
						IntStream channels = IntStream.range(0, opener.getNChannels());
						channels.forEach(iCh -> {
							FileChannel fsc = new FileChannel(iFile, iCh);
							viewSetupToBFFileSerieChannel.put(viewSetupCounter, fsc);
							viewSetupCounter++;
						});
						Type t = opener.getPixelType();
						tTypeGetter.put(iF, (NumericType) t);
						Volatile v = getVolatileOf((NumericType) t);
						vTypeGetter.put(iF, v);
				//	});
					//memo.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		cache = new VolatileGlobalCellCache(sq);
	}

	public BioFormatsSetupLoader getSetupImgLoader(int setupId) {
		try {
			if (setupLoaders.containsKey(setupId)) {
				return setupLoaders.get(setupId);
			}
			else {
				int iF = viewSetupToBFFileSerieChannel.get(setupId).iFile;
				//int iS = viewSetupToBFFileSerieChannel.get(setupId).iSerie;
				int iC = viewSetupToBFFileSerieChannel.get(setupId).iChannel;
				logger.debug("loading file number = " + iF + " setupId = " + setupId);


				try {
					if (openers.get(iF) instanceof BioFormatsBdvOpener) {
						BioFormatsSetupLoader imgL = new BioFormatsSetupLoader((BioFormatsBdvOpener) openers.get(iF),
								/*iS,*/ iC, setupId, tTypeGetter.get(iF), vTypeGetter.get(iF), this::getCacheControl);

						setupLoaders.put(setupId, imgL);
						return imgL;
					}
				}catch(Exception e){
					e.printStackTrace();
				}

				// TODO find a way to return other sedtuploaders
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

	public SharedQueue getQueue() {
		return sq;
	}

	@Override
	public void close() {
		synchronized (this) {
		/*	openers.forEach(opener -> {
				if(opener instanceof BioFormatsBdvOpener) {
					((BioFormatsBdvOpener)opener).getPixelReader().shutDown(reader -> {
						try {
							reader.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			});*/
			openers.forEach(opener -> close());
			cache.clearCache();
			sq.shutdown();
		}
	}

	/*public static Type getBioformatsBdvSourceType(IMetadata omeMeta,
		int image_index, boolean isRGB) throws UnsupportedOperationException
	{
		//final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();
		//reader.setSeries(image_index);
		if (isRGB) {
			if (omeMeta.getPixelsType(image_index) == PixelType.UINT8) {
				return new ARGBType();
			}
			else {
				throw new UnsupportedOperationException("Unhandled 16 bits RGB images");
			}
		}
		else {
			PixelType pt = omeMeta.getPixelsType(image_index);
			if (pt == PixelType.UINT8) {
				return new UnsignedByteType();
			}
			if (pt == PixelType.UINT16) {
				return new UnsignedShortType();
			}
			if (pt == PixelType.INT32) {
				return new IntType();
			}
			if (pt == PixelType.FLOAT) {
				return new FloatType();
			}
		}
		throw new UnsupportedOperationException("Unhandled pixel type for serie " +
			image_index + ": " + omeMeta.getPixelsType(image_index));
	}*/

	public static Volatile getVolatileOf(NumericType t) {
		if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

		if (t instanceof IntType) return new VolatileIntType();

		if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

		if (t instanceof FloatType) return new VolatileFloatType();

		if (t instanceof ARGBType) return new VolatileARGBType();
		return null;
	}

}
