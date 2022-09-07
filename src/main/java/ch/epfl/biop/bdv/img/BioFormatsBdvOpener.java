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

import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import ome.model.units.Unit;
import ome.units.quantity.Length;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BioFormatsBdvOpener implements Opener<IFormatReader> {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsBdvOpener.class);

	transient protected Consumer<IFormatReader> readerModifier = (e) -> {};

	public OpenerSettings settings;
	public boolean[] axesOfImageFlip = new boolean[] { false, false, false };
	transient ReaderPool pool = new ReaderPool(10, true, this::getNewReader);
	private int nTimePoints;
	private IMetadata omeMeta;
	private boolean isLittleEndian;
	private boolean isRGB;
	private int[] cellDimensions;
	private Dimensions[] dimensions;
	private String format;
	private int nMipMapLevels;
	private VoxelDimensions voxelDimensions;
	private int serieCount;

	private int iSerie;

	public OpenerSettings getSettings(){return this.settings;}

	// For copying the object
	/*public BioFormatsBdvOpener copy() {
		return new BioFormatsBdvOpener(this);
	}*/

	public BioFormatsBdvOpener(OpenerSettings settings) {
		this.settings = settings;
		this.iSerie = settings.iSerie;
		this.pool = new ReaderPool(this.settings.poolSize, true, this::getNewReader);

		try (IFormatReader reader = getNewReader()) {
			this.serieCount = reader.getSeriesCount();
			this.omeMeta = (IMetadata) reader.getMetadataStore();
			/*this.nMipMapLevels = new int[this.serieCount];
			this.nTimePoints = new int[this.serieCount];
			this.voxelDimensions = new VoxelDimensions[this.serieCount];
			this.isLittleEndian = new boolean[this.serieCount];
			this.cellDimensions = new int[this.serieCount][3];
			this.dimensions = new Dimensions[this.serieCount][];
			this.format = new String[this.serieCount];
			this.isRGB = new boolean[this.serieCount];*/
			this.iSerie = reader.getSeries();
			System.out.println("Try to catch the serie "+this.iSerie+" from the reader but I dont know if it is possible");
			reader.setSeries(this.iSerie);
			this.nMipMapLevels = reader.getResolutionCount();
			this.nTimePoints = reader.getSizeT();
			this.voxelDimensions = BioFormatsTools.getSeriesVoxelDimensions(this.omeMeta,
					this.iSerie, BioFormatsTools.getUnitFromString(this.settings.unit), this.settings.voxSizeReferenceFrameLength);
			this.isLittleEndian = reader.isLittleEndian();
			this.isRGB = reader.isRGB();
			this.format = reader.getFormat();

			boolean is3D = this.omeMeta.getPixelsSizeZ(this.iSerie).getNumberValue().intValue() > 1;

			this.cellDimensions = new int[] { this.settings.useDefaultXYBlockSize ? reader
					.getOptimalTileWidth() : (int) this.settings.cacheBlockSize.dimension(0),
					this.settings.useDefaultXYBlockSize ? reader.getOptimalTileHeight()
							: (int) this.settings.cacheBlockSize.dimension(1), (!is3D) ? 1
					: (int) this.settings.cacheBlockSize.dimension(2) };

			this.dimensions = new Dimensions[this.nMipMapLevels];
			for (int level = 0; level < this.nMipMapLevels; level++) {
				reader.setResolution(level);
				this.dimensions[level] = getDimension(reader.getSizeX(), reader.getSizeY(),
						(!is3D) ? 1 : reader.getSizeZ());
			}


		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	// All serializable fields


	//public String dataLocation = null; // URL or File

	//public boolean useBioFormatsXYBlockSize = true; // Block size : use the one
																									// defined by BioFormats or

	//public FinalInterval cacheBlockSize = new FinalInterval(new long[] { 0, 0,
	//	0 }, new long[] { 512, 512, 1 }); // needs a default size for z

	// Channels options
	//public boolean swZC; // Switch Z and Channels
	//public boolean splitRGBChannels = false;

	// Unit used for display
	//public Unit<Length> u;

	// Bioformats location fix
	//public double[] positionPreTransformMatrixArray;
	//public double[] positionPostTransformMatrixArray;
	//public Length positionReferenceFrameLength;
	//public boolean positionIgnoreBioFormatsMetaData = false;
	//public boolean positionIsImageCenter = false; // Top left corner otherwise

	// Bioformats voxsize fix
	//public double[] voxSizePreTransformMatrixArray;
	//public double[] voxSizePostTransformMatrixArray;
	//public Length voxSizeReferenceFrameLength;
	//public boolean voxSizeIgnoreBioFormatsMetaData = false;


	public String getDataLocation() {
		return this.settings.dataLocation;
	}

	public BioFormatsBdvOpener with(
		Consumer<BioFormatsBdvOpener> builderFunction)
	{
		builderFunction.accept(this);
		return this;
	}

	/*public BioFormatsBdvOpener file(File f) {
		this.dataLocation = f.getAbsolutePath();
		return this;
	}*/

	// BioFormats builder

	/*public BioFormatsBdvOpener positionReferenceFrameLength(Length l) {
		this.positionReferenceFrameLength = l;
		return this;
	}

	public BioFormatsBdvOpener setPositionPreTransformMatrixArray(double[] transform) {
		this.positionPreTransformMatrixArray = transform;
		return this;
	}

	public BioFormatsBdvOpener setPositionPostTransformMatrixArray(double[] transform) {
		this.positionPostTransformMatrixArray = transform;
		return this;
	}

	public BioFormatsBdvOpener voxSizeReferenceFrameLength(Length l) {
		this.voxSizeReferenceFrameLength = l;
		return this;
	}

	public BioFormatsBdvOpener location(String location) {
		this.dataLocation = location;
		return this;
	}

	public BioFormatsBdvOpener unit(String u) {
		this.u = BioFormatsTools.getUnitFromString(u);
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

	public BioFormatsBdvOpener centerPositionConvention(boolean flag) {
		this.positionIsImageCenter = flag;
		return this;
	}

	public BioFormatsBdvOpener splitRGBChannels(boolean flag) {
		this.splitRGBChannels = flag;
		return this;
	}

	*/

	/*public ReaderPool getReaderPool() {
		return pool;
	}*/

	/*public BioFormatsBdvOpener file(String filePath) {
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
	}*/

	/*public BioFormatsBdvOpener setPositionPreTransform(AffineTransform3D at3d) {
		positionPreTransformMatrixArray = at3d.getRowPackedCopy();
		return this;
	}*/

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

	/*public BioFormatsBdvOpener setPositionPostTransform(AffineTransform3D at3d) {
		positionPostTransformMatrixArray = at3d.getRowPackedCopy();
		return this;
	}*/

	public BioFormatsBdvOpener auto() {
		// Special cases based on File formats are handled here
		if (this.settings.dataLocation == null) {
			// dataLocation not set -> we can't do anything
			return this;
		}
		IFormatReader readerIdx = new ImageReader();
		if (this.settings.splitRGBChannels) readerIdx = new ChannelSeparator(readerIdx);

		readerIdx.setFlattenedResolutions(false);
		Memoizer memo = new Memoizer(readerIdx);

		final IMetadata omeMetaOmeXml = MetadataTools.createOMEXMLMetadata();
		memo.setMetadataStore(omeMetaOmeXml);

		// TODO : fix CZI
		// if (dataLocation.endsWith("czi"))
		// BioFormatsBdvOpenerFix.fixCziReader(memo);

		try {
			memo.setId(this.settings.dataLocation);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		logger.info("Attempts to set opener settings for file format " + memo
			.getFormat() + "; data location = " + this.settings.dataLocation);

		// Adjustements here!

		/*if (memo.getFormat().equals("Nikon ND2")) {
			return BioFormatsBdvOpenerFix.fixNikonND2(this);
		}
		else if (memo.getFormat().equals("Leica Image File Format")) {
			return BioFormatsBdvOpenerFix.fixLif(this);
		}*/
		/*else if (dataLocation.endsWith("czi")) {
		  return BioFormatsBdvOpenerFix.fixCzi(this);
		} */ //else {
			return this;
		//}

	}

	/*public BioFormatsBdvOpener url(URL url) {
		this.dataLocation = url.toString();
		return this;
	}*/

	/*public BioFormatsBdvOpener location(File f) {
		this.dataLocation = f.getAbsolutePath();
		return this;
	}

	public BioFormatsBdvOpener unit(Unit<Length> u) {
		this.u = u;
		return this;
	}*/

	/*public BioFormatsBdvOpener millimeter() {
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
	}*/

	/*public BioFormatsBdvOpener ignoreMetadata() {
		this.positionIgnoreBioFormatsMetaData = true;
		this.voxSizeIgnoreBioFormatsMetaData = true;
		return this;
	}*/


	public IFormatReader getNewReader() {
		logger.debug("Getting new reader for " + this.settings.dataLocation);
		IFormatReader reader = new ImageReader();
		reader.setFlattenedResolutions(false);
		if (this.settings.splitRGBChannels) {
			reader = new ChannelSeparator(reader);
		}
		Memoizer memo = new Memoizer(reader);

		final IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
		memo.setMetadataStore(omeMetaIdxOmeXml);
		readerModifier.accept(memo); // Specific modifications of the genrated
																	// readers

		try {
			logger.debug("setId for reader " + this.settings.dataLocation);
			StopWatch watch = new StopWatch();
			watch.start();
			memo.setId(this.settings.dataLocation);
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

	// TODO see if there is something necessary to add
	/*public static BioFormatsBdvOpener getOpener() {
		return new BioFormatsBdvOpener(new OpenerSettings()
				.bioFormatsBuilder()
				.positionReferenceFrameLength(new Length(1, UNITS.MICROMETER)) // Compulsory
				.voxSizeReferenceFrameLength(new Length(1, UNITS.MICROMETER))
				.millimeter()
				.useDefaultCacheBlockSizeFrom(true)
		);
	}*/

	static Dimensions getDimension(long sizeX, long sizeY, long sizeZ) {
		return new Dimensions() {

			@Override
			public long dimension(int d) {
				if (d == 0) return sizeX;
				if (d == 1) return sizeY;
				return sizeZ;
			}

			@Override
			public int numDimensions() {
				return 3;
			}
		};
	}



	@Override
	public int getNumMipmapLevels() {
		return this.nMipMapLevels;
	}

	@Override
	public int getNTimePoints() {
		return this.nTimePoints;
	}

	@Override
	public AffineTransform3D getTransform() {
		return BioFormatsTools
				.getSeriesRootTransform(this.omeMeta, this.iSerie, BioFormatsTools.getUnitFromString(this.settings.unit),
						this.settings.positionPreTransformMatrixArray, // AffineTransform3D
						// positionPreTransform,
						this.settings.positionPostTransformMatrixArray, // AffineTransform3D
						// positionPostTransform,
						this.settings.positionReferenceFrameLength,
						this.settings.positionIsImageCenter, // boolean positionIsImageCenter,
						this.settings.voxSizePreTransformMatrixArray, // voxSizePreTransform,
						this.settings.voxSizePostTransformMatrixArray, // AffineTransform3D
						// voxSizePostTransform,
						this.settings.voxSizeReferenceFrameLength, // null, //Length
						// voxSizeReferenceFrameLength,
						this.axesOfImageFlip // axesOfImageFlip
				);
	}

	@Override
	public ResourcePool<IFormatReader> getPixelReader() {
		return this.pool;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		return this.voxelDimensions;
	}


	public boolean getSwitchZAndT() {
		return this.settings.swZC;
	}
	public int getNumFetcherThread() {
		return this.settings.numFetcherThreads;
	}
	public int getNumPriorities() {
		return this.settings.numPriorities;
	}

	public String getUnit() {
		return this.settings.unit;
	}

	@Override
	public int[] getCellDimensions(int level) {
		return this.cellDimensions;
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.dimensions;
	}


	public String getReaderFormat() {
		return this.format;
	}


	public Boolean getEndianness() {
		return this.isLittleEndian;
	}


	public Boolean getRGB() {
		return this.isRGB;
	}

	@Override
	public IMetadata getMetadata() {
			return this.omeMeta;
	}
	@Override
	public int getSerieCount(){
		return this.serieCount;
	}

	public int getSerie(){
		return this.iSerie;
	}




	static class ReaderPool extends ResourcePool<IFormatReader> {

		final Supplier<IFormatReader> readerSupplier;

		public ReaderPool(int size, Boolean dynamicCreation,
						  Supplier<IFormatReader> readerSupplier)
		{
			super(size, dynamicCreation);
			createPool();
			this.readerSupplier = readerSupplier;
		}

		@Override
		public IFormatReader createObject() {
			return readerSupplier.get();
		}

	}

}
