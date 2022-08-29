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

import bdv.cache.SharedQueue;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

public class BioFormatsBdvOpener {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsBdvOpener.class);

	transient protected Consumer<IFormatReader> readerModifier = (e) -> {};

	// For copying the object
	public BioFormatsBdvOpener copy() {
		return new BioFormatsBdvOpener(this);
	}

	public BioFormatsBdvOpener(BioFormatsBdvOpener opener) {
		dataLocation = opener.dataLocation;
		useBioFormatsXYBlockSize = opener.useBioFormatsXYBlockSize;
		cacheBlockSize = new FinalInterval(opener.cacheBlockSize);
		isSoftRef = opener.isSoftRef;
		maxCacheSize = opener.maxCacheSize;
		swZC = opener.swZC;
		splitRGBChannels = opener.splitRGBChannels;
		u = opener.u; // No deep copy
		if (opener.positionPreTransformMatrixArray != null)
			positionPreTransformMatrixArray = opener.positionPreTransformMatrixArray
				.clone();
		if (opener.positionPostTransformMatrixArray != null)
			positionPostTransformMatrixArray = opener.positionPostTransformMatrixArray
				.clone();
		positionReferenceFrameLength = opener.positionReferenceFrameLength; // no
																																				// deep
																																				// copy
		positionIgnoreBioFormatsMetaData = opener.positionIgnoreBioFormatsMetaData;
		positionIsImageCenter = opener.positionIsImageCenter;
		if (opener.voxSizePreTransformMatrixArray != null)
			voxSizePreTransformMatrixArray = opener.voxSizePreTransformMatrixArray
				.clone();
		if (opener.voxSizePostTransformMatrixArray != null)
			voxSizePostTransformMatrixArray = opener.voxSizePostTransformMatrixArray
				.clone();
		voxSizeReferenceFrameLength = opener.voxSizeReferenceFrameLength;
		voxSizeIgnoreBioFormatsMetaData = opener.voxSizeIgnoreBioFormatsMetaData;
		axesOfImageFlip = opener.axesOfImageFlip.clone();
		nFetcherThread = opener.nFetcherThread;
		numPriorities = opener.numPriorities;
		readerModifier = opener.readerModifier;
	}

	public BioFormatsBdvOpener() {}

	public SharedQueue getCacheControl() {
		if (cc == null) {
			cc = new SharedQueue(nFetcherThread, numPriorities);
		}
		return cc;
	}

	// All serializable fields
	public String dataLocation = null; // URL or File

	public boolean useBioFormatsXYBlockSize = true; // Block size : use the one
																									// defined by BioFormats or
	public boolean isSoftRef = true;
	public FinalInterval cacheBlockSize = new FinalInterval(new long[] { 0, 0,
		0 }, new long[] { 512, 512, 1 }); // needs a default size for z

	// Channels options
	public boolean swZC; // Switch Z and Channels
	public boolean splitRGBChannels = false;

	// Unit used for display
	public Unit<Length> u;

	// Bioformats location fix
	public double[] positionPreTransformMatrixArray;
	public double[] positionPostTransformMatrixArray;
	public Length positionReferenceFrameLength;
	public boolean positionIgnoreBioFormatsMetaData = false;
	public boolean positionIsImageCenter = false; // Top left corner otherwise

	// Bioformats voxsize fix
	public double[] voxSizePreTransformMatrixArray;
	public double[] voxSizePostTransformMatrixArray;
	public Length voxSizeReferenceFrameLength;
	public boolean voxSizeIgnoreBioFormatsMetaData = false;
	public boolean[] axesOfImageFlip = new boolean[] { false, false, false };

	public int nFetcherThread = 2;

	public int numPriorities = 4;

	public int maxCacheSize = 1;

	transient SharedQueue cc = new SharedQueue(2, 4);

	transient ReaderPool pool = new ReaderPool(10, true, this::getNewReader);

	public String getDataLocation() {
		return dataLocation;
	}

	public BioFormatsBdvOpener with(
		Consumer<BioFormatsBdvOpener> builderFunction)
	{
		builderFunction.accept(this);
		return this;
	}

	public BioFormatsBdvOpener file(File f) {
		this.dataLocation = f.getAbsolutePath();
		return this;
	}

	public BioFormatsBdvOpener positionReferenceFrameLength(Length l) {
		this.positionReferenceFrameLength = l;
		return this;
	}

	public BioFormatsBdvOpener cacheSoftRef() {
		isSoftRef = true;
		return this;
	}

	public BioFormatsBdvOpener cacheBounded(int maxCacheSize) {
		isSoftRef = false;
		this.maxCacheSize = maxCacheSize;
		return this;
	}

	public BioFormatsBdvOpener voxSizeReferenceFrameLength(Length l) {
		this.voxSizeReferenceFrameLength = l;
		return this;
	}

	public BioFormatsBdvOpener setReaderPool(ReaderPool pool) {
		this.pool = pool;
		return this;
	}

	public ReaderPool getReaderPool() {
		return pool;
	}

	public BioFormatsBdvOpener queueOptions(int numFetcherThreads,
		int numPriorities)
	{
		this.nFetcherThread = numFetcherThreads;
		this.numPriorities = numPriorities;
		this.cc = new SharedQueue(this.nFetcherThread, this.numPriorities);
		return this;
	}

	public BioFormatsBdvOpener setCache(SharedQueue sq) {
		this.cc = sq;
		return this;
	}

	public BioFormatsBdvOpener file(String filePath) {
		this.dataLocation = filePath;
		return this;
	}

	public BioFormatsBdvOpener splitRGBChannels() {
		splitRGBChannels = true;
		return this;
	}

	public BioFormatsBdvOpener flipPositionXYZ() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(-1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener flipPositionX() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(-1, 1, 1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener flipPositionY() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(1, -1, 1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener flipPositionZ() {
		if (this.positionPreTransformMatrixArray == null) {
			positionPreTransformMatrixArray = new AffineTransform3D()
				.getRowPackedCopy();
		}
		AffineTransform3D at3D = new AffineTransform3D();
		at3D.set(positionPreTransformMatrixArray);
		at3D.scale(1, 1, -1);
		positionPreTransformMatrixArray = at3D.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener setPositionPreTransform(AffineTransform3D at3d) {
		positionPreTransformMatrixArray = at3d.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener addReaderModifier(
		Consumer<IFormatReader> modifier)
	{
		Consumer<IFormatReader> originModifier = this.readerModifier;
		// Concatenate modifiers
		readerModifier = (r) -> {
			originModifier.accept(r);
			modifier.accept(r);
		};
		return this;
	}

	public BioFormatsBdvOpener setPositionPostTransform(AffineTransform3D at3d) {
		positionPostTransformMatrixArray = at3d.getRowPackedCopy();
		return this;
	}

	public BioFormatsBdvOpener auto() {
		// Special cases based on File formats are handled here
		if (this.dataLocation == null) {
			// dataLocation not set -> we can't do anything
			return this;
		}
		IFormatReader readerIdx = new ImageReader();
		if (splitRGBChannels) readerIdx = new ChannelSeparator(readerIdx);

		readerIdx.setFlattenedResolutions(false);
		Memoizer memo = new Memoizer(readerIdx);

		final IMetadata omeMetaOmeXml = MetadataTools.createOMEXMLMetadata();
		memo.setMetadataStore(omeMetaOmeXml);

		// TODO : fix CZI
		// if (dataLocation.endsWith("czi"))
		// BioFormatsBdvOpenerFix.fixCziReader(memo);

		try {
			memo.setId(dataLocation);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		logger.info("Attempts to set opener settings for file format " + memo
			.getFormat() + "; data location = " + dataLocation);

		// Adjustements here!

		if (memo.getFormat().equals("Nikon ND2")) {
			return BioFormatsBdvOpenerFix.fixNikonND2(this);
		}
		else if (memo.getFormat().equals("Leica Image File Format")) {
			return BioFormatsBdvOpenerFix.fixLif(this);
		}
		/*else if (dataLocation.endsWith("czi")) {
		  return BioFormatsBdvOpenerFix.fixCzi(this);
		} */ else {
			return this;
		}

	}

	public BioFormatsBdvOpener url(URL url) {
		this.dataLocation = url.toString();
		return this;
	}

	public BioFormatsBdvOpener location(String location) {
		this.dataLocation = location;
		return this;
	}

	public BioFormatsBdvOpener location(File f) {
		this.dataLocation = f.getAbsolutePath();
		return this;
	}

	public BioFormatsBdvOpener unit(Unit<Length> u) {
		this.u = u;
		return this;
	}

	public BioFormatsBdvOpener unit(String u) {
		this.u = BioFormatsMetaDataHelper.getUnitFromString(u);
		return this;
	}

	public BioFormatsBdvOpener millimeter() {
		this.u = UNITS.MILLIMETER;
		return this;
	}

	public BioFormatsBdvOpener micrometer() {
		this.u = UNITS.MICROMETER;
		return this;
	}

	public BioFormatsBdvOpener nanometer() {
		this.u = UNITS.NANOMETER;
		return this;
	}

	public BioFormatsBdvOpener centerPositionConvention() {
		this.positionIsImageCenter = true;
		return this;
	}

	public BioFormatsBdvOpener cornerPositionConvention() {
		this.positionIsImageCenter = false;
		return this;
	}

	public BioFormatsBdvOpener ignoreMetadata() {
		this.positionIgnoreBioFormatsMetaData = true;
		this.voxSizeIgnoreBioFormatsMetaData = true;
		return this;
	}

	public BioFormatsBdvOpener useCacheBlockSizeFromBioFormats(boolean flag) {
		useBioFormatsXYBlockSize = flag;
		return this;
	}

	public BioFormatsBdvOpener switchZandC(boolean flag) {
		this.swZC = flag;
		return this;
	}

	public BioFormatsBdvOpener cacheBlockSize(int sx, int sy, int sz) {
		useBioFormatsXYBlockSize = false;
		cacheBlockSize = new FinalInterval(sx, sy, sz);
		return this;
	}

	public IFormatReader getNewReader() {
		logger.debug("Getting new reader for " + dataLocation);
		IFormatReader reader = new ImageReader();
		reader.setFlattenedResolutions(false);
		if (splitRGBChannels) {
			reader = new ChannelSeparator(reader);
		}
		Memoizer memo = new Memoizer(reader);

		final IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
		memo.setMetadataStore(omeMetaIdxOmeXml);
		readerModifier.accept(memo); // Specific modifications of the genrated
																	// readers

		try {
			logger.debug("setId for reader " + dataLocation);
			StopWatch watch = new StopWatch();
			watch.start();
			memo.setId(dataLocation);
			watch.stop();
			logger.debug("id set in " + (int) (watch.getTime() / 1000) + " s");

		}
		catch (FormatException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return memo;
	}

	public static BioFormatsBdvOpener getOpener() {
		return new BioFormatsBdvOpener().positionReferenceFrameLength(new Length(1,
			UNITS.MICROMETER)) // Compulsory
			.voxSizeReferenceFrameLength(new Length(1, UNITS.MICROMETER)).millimeter()
			.useCacheBlockSizeFromBioFormats(true);
	}

}
