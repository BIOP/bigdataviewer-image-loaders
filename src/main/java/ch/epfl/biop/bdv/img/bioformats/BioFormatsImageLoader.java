/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.bdv.img.bioformats;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
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
import ome.xml.model.enums.PixelType;
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

	final public List<BioFormatsBdvOpener> openers;

	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

	final Map<Integer, FileSerieChannel> viewSetupToBFFileSerieChannel =
		new HashMap<>();

	int viewSetupCounter = 0;

	final Map<Integer, Map<Integer, NumericType>> tTypeGetter = new HashMap<>();

	final Map<Integer, Map<Integer, Volatile>> vTypeGetter = new HashMap<>();

	final HashMap<Integer, BioFormatsSetupLoader> imgLoaders = new HashMap<>();

	@SuppressWarnings("CanBeFinal")
	protected VolatileGlobalCellCache cache;

	@SuppressWarnings("CanBeFinal")
	protected SharedQueue sq;

	public final int numFetcherThreads;
	public final int numPriorities;

	public BioFormatsImageLoader(List<BioFormatsBdvOpener> openers,
		final AbstractSequenceDescription<?, ?, ?> sequenceDescription,
		int numFetcherThreads, int numPriorities)
	{
		this.openers = openers;
		this.sequenceDescription = sequenceDescription;
		this.numFetcherThreads = numFetcherThreads;
		this.numPriorities = numPriorities;
		sq = new SharedQueue(numFetcherThreads, numPriorities);

		openers.forEach(opener -> opener.setCache(sq));

		IntStream openersIdxStream = IntStream.range(0, openers.size());
		if ((sequenceDescription != null)) {
			openersIdxStream.forEach(iF -> {
				try {
					BioFormatsBdvOpener opener = openers.get(iF);

					logger.debug("Data location = " + opener.getDataLocation());

					IFormatReader memo = opener.getNewReader();

					tTypeGetter.put(iF, new HashMap<>());
					vTypeGetter.put(iF, new HashMap<>());

					logger.debug("Number of Series : " + memo.getSeriesCount());
					IMetadata omeMeta = (IMetadata) memo.getMetadataStore();
					memo.setMetadataStore(omeMeta);
					// -------------------------- SETUPS For each Series : one per
					// timepoint and one per channel

					IntStream series = IntStream.range(0, memo.getSeriesCount());

					final int iFile = iF;

					series.forEach(iSerie -> {
						memo.setSeries(iSerie);
						// One serie = one Tile
						// ---------- Serie >
						// ---------- Serie > Timepoints
						logger.debug("\t Serie " + iSerie + " Number of timesteps = " +
							omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue());
						// ---------- Serie > Channels
						logger.debug("\t Serie " + iSerie + " Number of channels = " +
							omeMeta.getChannelCount(iSerie));
						// Properties of the serie
						IntStream channels = IntStream.range(0, omeMeta.getChannelCount(
							iSerie));
						// Register Setups (one per channel and one per timepoint)
						channels.forEach(iCh -> {
							FileSerieChannel fsc = new FileSerieChannel(iFile, iSerie, iCh);
							viewSetupToBFFileSerieChannel.put(viewSetupCounter, fsc);
							viewSetupCounter++;
						});
						Type t = getBioformatsBdvSourceType(memo, iSerie);
						tTypeGetter.get(iF).put(iSerie, (NumericType) t);
						Volatile v = getVolatileOf((NumericType) t);
						vTypeGetter.get(iF).put(iSerie, v);
					});
					memo.close();
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
			if (imgLoaders.containsKey(setupId)) {
				return imgLoaders.get(setupId);
			}
			else {
				int iF = viewSetupToBFFileSerieChannel.get(setupId).iFile;
				int iS = viewSetupToBFFileSerieChannel.get(setupId).iSerie;
				int iC = viewSetupToBFFileSerieChannel.get(setupId).iChannel;
				logger.debug("loading file number = " + iF + " setupId = " + setupId);

				BioFormatsSetupLoader imgL = new BioFormatsSetupLoader(openers.get(iF),
					iS, iC, setupId, tTypeGetter.get(iF).get(iS), vTypeGetter.get(iF).get(
						iS), this::getCacheControl);

				imgLoaders.put(setupId, imgL);
				return imgL;
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
			openers.forEach(opener -> {
				opener.getReaderPool().shutDown(reader -> {
					try {
						reader.close();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				});
			});
			cache.clearCache();
			sq.shutdown();
		}
	}

	public static Type getBioformatsBdvSourceType(IFormatReader reader,
		int image_index) throws UnsupportedOperationException
	{
		final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();
		reader.setSeries(image_index);
		if (reader.isRGB()) {
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
	}

	public static Volatile getVolatileOf(NumericType t) {
		if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

		if (t instanceof IntType) return new VolatileIntType();

		if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

		if (t instanceof FloatType) return new VolatileFloatType();

		if (t instanceof ARGBType) return new VolatileARGBType();
		return null;
	}

}
