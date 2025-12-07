/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.qupath;

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.opener.ChannelProperties;
import ch.epfl.biop.bdv.img.opener.EmptyOpener;
import ch.epfl.biop.bdv.img.opener.Opener;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsOpener;
import ch.epfl.biop.bdv.img.omero.OmeroOpener;
import ch.epfl.biop.bdv.img.qupath.entity.QuPathEntryIdEntity;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static ch.epfl.biop.bdv.img.omero.OmeroChecker.PromptUserIfOmeroDependenciesMissing;

/**
 * QuPath Image Opener. This class builds a specific opener depending on the
 * image provider class that is used to convert QuPath data into BDV compatible
 * data There are some limitations: only bioformats image server, rotated image
 * server and omero-raw image server are supported ( among probably other
 * limitations ). Also, editing files in the QuPath project after it has been
 * converted to a xml bdv dataset is not guaranteed to work.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 */
public class QuPathOpener<T> implements Opener<T> {

	protected static final Logger logger = LoggerFactory.getLogger(
			QuPathOpener.class);
	final Opener<T> opener;
	final MinimalQuPathProject.ImageEntry image;
	final String unit;
	final int entryId;

	final OpenerMeta meta;


	/**
	 * Create an OMERO or BioFormats opener depending on the providerClassName of QuPath
	 * @param dataLocation
	 * @param poolSize
	 * @param useDefaultXYBlockSize
	 * @param cacheBlockSize
	 * @param splitRGBChannels
	 */
	public QuPathOpener(// opener core option
						Context context,
						String dataLocation,
						int entryId,
						String unit,
						boolean positionIsImageCenter,
						// How to stream it
						int poolSize,
						boolean useDefaultXYBlockSize,
						int[] cacheBlockSize,
						// channel options
						boolean splitRGBChannels,
						// Optimisation : reuse existing openers
						Map<String, Object> cachedObjects,
						int defaultNumberOfChannels,
						boolean skipMeta) throws UnsupportedOperationException {

		MinimalQuPathProject project = OpenerHelper.memoize("opener.qupath.project."+dataLocation,
				cachedObjects,
				() -> getQuPathProject(context, dataLocation));


		this.unit = unit;
		this.entryId = entryId;
		Map<Integer, MinimalQuPathProject.ImageEntry> idToImage = new HashMap<>();
		project.images.forEach(server -> idToImage.put(server.entryID, server));
		if (!idToImage.containsKey(entryId)) {
			logger.error("Entry "+entryId+" not found! You've probably deleted an entry in the QuPath. Let's try to deal with it the best we can...");
			this.image = new MinimalQuPathProject.EmptyImageEntry(entryId, defaultNumberOfChannels);
		} else {
			this.image = idToImage.get(entryId); //project.images.get(entryId);
		}

		MinimalQuPathProject.ServerBuilderEntry mostInnerBuilder = image.serverBuilder;
		// get the rotation angle if the image has been loaded in qupath with the
		// rotation command
		while ((mostInnerBuilder.builderType.equals("rotated")) || (mostInnerBuilder.builderType.equals("pyramidize"))) {
			if (mostInnerBuilder.builderType.equals("pyramidize")) {
				// Notify the opener builder that pyramidisation should take place
				setPyramidize(true);
			}

			// We keep the most outer pixel calibration to apply it to the most inner retrieved builder
			// STORE PIX CAL, with null check
			MinimalQuPathProject.PixelCalibrations pixelCalibration = null;
			if (mostInnerBuilder.metadata!=null) pixelCalibration = mostInnerBuilder.metadata.pixelCalibration;

			mostInnerBuilder = mostInnerBuilder.builder;

			// RESTORE PIX CAL, with null check
			if (pixelCalibration!=null) {
				if (mostInnerBuilder.metadata!=null) {
					mostInnerBuilder.metadata.pixelCalibration = pixelCalibration;
				}
			}
		}

		if (mostInnerBuilder.builderType.equals("Empty")) {
			logger.error("Empty image server!");
			this.opener = (Opener<T>) new EmptyOpener("Entry "+entryId, defaultNumberOfChannels, "Error, entry "+entryId+" missing!", false);
		} else if (mostInnerBuilder.builderType.equals("uri")) {
			logger.debug("URI image server");
			try {
				logger.debug("provided class name : " + mostInnerBuilder.providerClassName);
				// create openers
				if (mostInnerBuilder.providerClassName.equals(
						"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {

					URI uri = new URI(mostInnerBuilder.uri.getScheme(),
							mostInnerBuilder.uri.getHost(), mostInnerBuilder.uri
							.getPath(), null);

					// This appears to work more reliably than converting to a File
					String filePath = Paths.get(uri).toString();

					if (!new File(filePath).exists()) {
						String multiLinePath = filePath.replace(File.separator, "\n\t\\");
						throw new UnsupportedOperationException("Please fix URIs in QuPath.\nFile not found:\n " + multiLinePath);

					} else {

						int idxSeriesK = mostInnerBuilder.args.indexOf("--series");
						int idxSeriesV = idxSeriesK + 1;

						// Removes --series i from the options because otherwise this creates a reader pool per
						// image file series instead of per image file only

						List<String> filteredArgs = new ArrayList<>(mostInnerBuilder.args);
						filteredArgs.remove(idxSeriesK);
						filteredArgs.remove(idxSeriesK);

						String joinedArgs = String.join(" ", filteredArgs);

						this.opener = (Opener<T>) new BioFormatsOpener(
								context,
								filePath,
								Integer.parseInt(mostInnerBuilder.args.get(idxSeriesV)),
								// Location of the image
								new AffineTransform3D().getRowPackedCopy(),
								new AffineTransform3D().getRowPackedCopy(),
								positionIsImageCenter,
								new Length(1, UNITS.MICROMETER),
								new Length(1, UNITS.MICROMETER),
								unit,
								// How to stream it
								poolSize,
								useDefaultXYBlockSize,
								cacheBlockSize,
								// Channel options
								splitRGBChannels,
								cachedObjects,
								defaultNumberOfChannels,
								skipMeta,
								false,
								joinedArgs);
					}

					logger.debug("BioFormats Opener for image " + this.image.imageName);
				} else {
					if (mostInnerBuilder.providerClassName.equals(
							"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {

						PromptUserIfOmeroDependenciesMissing(context);
						this.opener = (Opener<T>) new OmeroOpener(
								context,
								URLDecoder.decode(mostInnerBuilder.uri.toString(), "UTF-8"),
								//mostInnerBuilder.uri.toString(),
								poolSize,
								unit,
								positionIsImageCenter,
								cachedObjects,
								defaultNumberOfChannels, skipMeta);

						logger.debug("OMERO-RAW Opener (BIOP) for image " + this.image.imageName);
					} else if (mostInnerBuilder.providerClassName.equals(
							"qupath.ext.omero.core.imageserver.OmeroImageServerBuilder")) {

						if (!mostInnerBuilder.args.contains("Ice")) {
							throw new UnsupportedOperationException("Unsupported " +
									mostInnerBuilder.providerClassName +
									" because it is not using the Ice API");
						}
						PromptUserIfOmeroDependenciesMissing(context);
						this.opener = (Opener<T>) new OmeroOpener(
								context,
								URLDecoder.decode(mostInnerBuilder.uri.toString(), "UTF-8"),
								//mostInnerBuilder.uri.toString(),
								poolSize,
								unit,
								positionIsImageCenter,
								cachedObjects,
								defaultNumberOfChannels, skipMeta);

						logger.debug("OMERO-ICE Opener for image " + this.image.imageName);
					} else {
						throw new UnsupportedOperationException("Unsupported " +
								mostInnerBuilder.providerClassName +
								" provider Class Name");
					}
				}
			} catch (Exception e) {
				logger.error("URI Syntax error " + e.getMessage());
				e.printStackTrace();
				throw new UnsupportedOperationException(e.getMessage());
			}
		} else {
			throw new UnsupportedOperationException("Unsupported " + image.serverBuilder.builderType +
					" server builder");
		}
		if (!skipMeta) {
			meta = new OpenerMeta() {

				@Override
				public String getImageName() {
					return image.imageName;
				}

				@Override
				public ChannelProperties getChannel(int iChannel) {
					if (image.serverBuilder != null &&
							image.serverBuilder.metadata != null &&
							image.serverBuilder.metadata.channels != null){

						if (image.serverBuilder.metadata.isRGB) {
							if (opener.getPixelType() instanceof ARGBType) {
								// No split RGB
								return opener.getMeta().getChannel(iChannel).setChannelName("RGB");
							} else {
								MinimalQuPathProject.ChannelInfo channel = image.serverBuilder.metadata.channels.get(iChannel);
								return opener.getMeta().getChannel(iChannel).setChannelName(channel.name).setChannelColor(channel.color);
							}
						}

						MinimalQuPathProject.ChannelInfo channel = image.serverBuilder.metadata.channels.get(iChannel);
						return opener.getMeta().getChannel(iChannel).setChannelName(channel.name).setChannelColor(channel.color);
					} else return opener.getMeta().getChannel(iChannel);
				}


				@Override
				public List<Entity> getEntities(int iChannel) {
					List<Entity> entities = new ArrayList<>();
					QuPathEntryIdEntity entry = new QuPathEntryIdEntity(entryId);
					entities.add(entry);
					return entities;
				}

				@Override
				public AffineTransform3D getTransform() {
					if (image.serverBuilder != null &&
							image.serverBuilder.metadata != null &&
							image.serverBuilder.metadata.pixelCalibration != null){
						MinimalQuPathProject.PixelCalibrations pixelCalibration = image.serverBuilder.metadata.pixelCalibration;
						// QuPath modified pixel size
						return QuPathOpener.getTransform(pixelCalibration, unit, opener.getMeta().getTransform(), opener.getVoxelDimensions());
					} else return opener.getMeta().getTransform(); // Bio-Formats pixel size
				}
			};
		} else meta = null;
	}

	boolean pyramidize = false;
	private void setPyramidize(boolean pyramidize) {
		this.pyramidize = pyramidize;
	}

	/**
	 * Uses to retrieve the fact that a QuPath entry should be auto-pyramidized
	 * @return whether the underlying ImageServer is wrapped in a pyramidized one
	 */
	public boolean getPyramidize() {
		return this.pyramidize;
	}

	public static MinimalQuPathProject getQuPathProject(Context ctx, String dataLocation) {
		try {
			File quPathProject = new File(dataLocation);
			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject.toURI()));
			return new Gson().fromJson(projectJson, MinimalQuPathProject.class);
		} catch (IOException e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	/**
	 * get the rotation angle of the image if the image was imported in qupath
	 * with a rotation
	 *
	 * @param image
	 * @return
	 */
	private static double getAngleRotationZAxis(MinimalQuPathProject.ImageEntry image) {
		double angleRotationZAxis;

		// "ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
		String angleDegreesStr = image.serverBuilder.rotation.substring(7);

		logger.debug("Rotated image server (" + angleDegreesStr + ")");
		if (angleDegreesStr.equals("NONE")) {
			angleRotationZAxis = 0;
		}
		else {
			angleRotationZAxis = (Double.parseDouble(angleDegreesStr) / 180.0) *
					Math.PI;
		}
		MinimalQuPathProject.ServerBuilderMetadata metadata =
				image.serverBuilder.metadata; // To keep the metadata (pixel size for
		// instance)
		image.serverBuilder = image.serverBuilder.builder; // Skips the rotation
		image.serverBuilder.metadata = metadata;

		return angleRotationZAxis;
	}

	/**
	 * Scale the rootTransform of the basic opener (OMERO, BioFormats) with QuPath unit and pixel dimensions
	 * @param pixelCalibrations
	 * @param outputUnit
	 * @param rootTransform
	 * @param voxSizes
	 * @return
	 */
	private static AffineTransform3D getTransform(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String outputUnit,
												 AffineTransform3D rootTransform, VoxelDimensions voxSizes) {

		AffineTransform3D quPathRescaling = new AffineTransform3D();

		if (pixelCalibrations != null) {
			double scaleX = 1.0;
			double scaleY = 1.0;
			double scaleZ = 1.0;

			double voxSizeX = voxSizes.dimension(0);
			double voxSizeY = voxSizes.dimension(1);
			double voxSizeZ = voxSizes.dimension(2);

			// compute scaling factor
			if (pixelCalibrations.pixelWidth != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
				Length voxLengthX = new Length(voxSizeX, BioFormatsHelper.getUnitFromString(voxSizes.unit()));

				if (voxLengthX.value(UNITS.MICROMETER) != null) {
					logger.debug("xVox size = " + pc.value + " micrometer");
					scaleX = pc.value / voxLengthX.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsHelper.getUnitFromString(outputUnit));
					scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling x");
				}
			}
			if (pixelCalibrations.pixelHeight != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
				Length voxLengthY = new Length(voxSizeY, BioFormatsHelper.getUnitFromString(voxSizes.unit()));
				// if (pc.unit.equals("um")) {
				if (voxLengthY.value(UNITS.MICROMETER) != null) {
					logger.debug("yVox size = " + pc.value + " micrometer");
					scaleY = pc.value / voxLengthY.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsHelper.getUnitFromString(outputUnit));
					scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling y");
				}
			}
			if (pixelCalibrations.zSpacing != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.zSpacing;
				Length voxLengthZ = new Length(voxSizeZ, BioFormatsHelper.getUnitFromString(voxSizes.unit()));
				// if (pc.unit.equals("um")) { problem with micrometer character
				if (voxLengthZ.value(UNITS.MICROMETER) != null) {
					logger.debug("zVox size = " + pc.value + " micrometer");
					scaleZ = pc.value / voxLengthZ.value(UNITS.MICROMETER).doubleValue();
				} else {
                   /* if ((voxLengthZ != null)) {
                    }
                    else {*/
					logger.warn("Null Z voxel size");
				}
			}

			logger.debug("ScaleX: " + scaleX + " scaleY:" + scaleY + " scaleZ:" + scaleZ);

			final double finalScalex = scaleX;
			final double finalScaley = scaleY;
			final double finalScalez = scaleZ;

			if ((Math.abs(finalScalex - 1.0) > 0.0001) || (Math.abs(
					finalScaley - 1.0) > 0.0001) || (Math.abs(finalScalez -
					1.0) > 0.0001)) {
				logger.debug("Perform QuPath rescaling");
				// create a new AffineTransform3D based on pixelCalibration
				quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
				// scale the root transform
				double oX = rootTransform.get(0, 3);
				double oY = rootTransform.get(1, 3);
				double oZ = rootTransform.get(2, 3);
				rootTransform.preConcatenate(quPathRescaling);
				rootTransform.set(oX, 0, 3);
				rootTransform.set(oY, 1, 3);
				rootTransform.set(oZ, 2, 3);
			}

		}

		return rootTransform;
	}

	/**
	 * Convert the string unit from QuPath metadata into Unit class readable by
	 * the opener
	 * 
	 * @param unitString
	 * @return
	 */
	private static Unit<Length> convertStringToUnit(String unitString) {
		switch (unitString) {
			case "µm":
				return UNITS.MICROMETER;
			case "mm":
				return UNITS.MILLIMETER;
			case "cm":
				return UNITS.CENTIMETER;
			case "px":
				return UNITS.PIXEL;
			default:
				return UNITS.REFERENCEFRAME;
		}
	}

	/**
	 * Use QuPath pixelCalibration to retrieve the size of an image voxel in the specified unit.
	 * @param pixelCalibrations
	 * @param unit
	 * @return
	 */
	public static VoxelDimensions getVoxelDimensions(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String unit)
	{
		// Always 3 to allow for big stitcher compatibility
		int numDimensions = 3;

		// get voxel size with the original unit
		Length[] voxSize = new Length[]{
				new Length(pixelCalibrations.pixelWidth.value, convertStringToUnit(pixelCalibrations.pixelWidth.unit)),
				new Length(pixelCalibrations.pixelHeight.value, convertStringToUnit(pixelCalibrations.pixelHeight.unit)),
				new Length(pixelCalibrations.zSpacing.value, convertStringToUnit(pixelCalibrations.zSpacing.unit))
		};
		double[] d = new double[3];
		Unit<Length> u = BioFormatsHelper.getUnitFromString(unit);
		Length voxSizeReferenceFrameLength = new Length(1, UNITS.MICROMETER);

		// fill the array with the dimension value converted into the specified unit u
		for (int iDimension = 0; iDimension < 3; iDimension++) { // X:0; Y:1; Z:2
			if ((voxSize[iDimension].unit() != null) && (voxSize[iDimension].unit()
					.isConvertible(u)))
			{
				d[iDimension] = voxSize[iDimension].value(u).doubleValue();
			}
			else if (voxSize[iDimension].unit().getSymbol().equals(
					"reference frame"))
			{
				Length l = new Length(voxSize[iDimension].value().doubleValue() *
						voxSizeReferenceFrameLength.value().doubleValue(),
						voxSizeReferenceFrameLength.unit());
				d[iDimension] = l.value(u).doubleValue();
			}
			else {
				d[iDimension] = 1;
			}
		}

		// create the Voxel dimension object
		VoxelDimensions voxelDimensions;

		{
			assert numDimensions == 3;
			voxelDimensions = new VoxelDimensions() {

				final Unit<Length> targetUnit = u;

				final double[] dims = { d[0], d[1], d[2] };

				@Override
				public String unit() {
					return targetUnit.getSymbol();
				}

				@Override
				public void dimensions(double[] doubles) {
					doubles[0] = dims[0];
					doubles[1] = dims[1];
					doubles[2] = dims[2];
				}

				@Override
				public double dimension(int i) {
					return dims[i];
				}

				@Override
				public int numDimensions() {
					return numDimensions;
				}
			};
		}
		return voxelDimensions;
	}

	// OVERRIDDEN METHODS
	@Override
	public int[] getCellDimensions(int level) {
		return this.opener.getCellDimensions(level);
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.opener.getDimensions();
	}



	@Override
	public int getNChannels() {
		return this.opener.getNChannels();
	}

	@Override
	public int getNTimePoints() {
		return this.opener.getNTimePoints();
	}

	@Override
	public int getNumMipmapLevels() {
		return this.opener.getNumMipmapLevels();
	}

	@Override
	public ResourcePool<T> getPixelReader() {
		return opener.getPixelReader();
	}

	@Override
	public Type<? extends NumericType<?>> getPixelType() {
		return this.opener.getPixelType();
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		if(this.image.serverBuilder != null &&
		this.image.serverBuilder.metadata != null &&
		this.image.serverBuilder.metadata.pixelCalibration != null){
			return getVoxelDimensions(this.image.serverBuilder.metadata.pixelCalibration,this.unit);
		}
		else return this.opener.getVoxelDimensions();
	}

	@Override
	public boolean isLittleEndian() {
		return this.opener.isLittleEndian();
	}

	@Override
	public String getImageFormat() {
		return this.opener.getImageFormat();
	}

	@Override
	public OpenerSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
		return this.opener.getSetupLoader(channelIdx, setupIdx, cacheSupplier);
	}

	@Override
	public String getRawPixelDataKey() {
		return opener.getRawPixelDataKey();
	}

	@Override
	public OpenerMeta getMeta() {
		return meta;
	}

	@Override
	public void close() throws IOException {
		this.opener.close();
	}

}
