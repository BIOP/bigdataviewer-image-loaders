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

package ch.epfl.biop.bdv.img.qupath;

import ch.epfl.biop.bdv.img.*;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import ch.epfl.biop.bdv.img.bioformats.entity.ChannelName;
import ch.epfl.biop.bdv.img.qupath.command.GuiParams;
import ch.epfl.biop.bdv.img.qupath.entity.QuPathEntryEntity;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import loci.formats.IFormatReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.primitives.Color;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.model.enums.UnitsLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * QuPath Image Opener. This class builds a specific opener depending on the
 * image provider class that is used to convert QuPath data into BDV compatible
 * data There are some limitations: only bioformats image server, rotated image
 * server and omero-raw image server are supported ( among probably other
 * limitations ). Also, editing files in the QuPath project after it has been
 * converted to an xml bdv dataset is not guaranteed to work.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 */
public class QuPathImageOpener<T> implements Opener<T> {

	protected static Logger logger = LoggerFactory.getLogger(
			QuPathImageOpener.class);
	private Opener<T> opener;
	private MinimalQuPathProject.ImageEntry image;
	private String unit;
	private URI qpProj;
	private int iSerie;


	// getter functions
	public URI getURI() {
		return this.image.serverBuilder.uri;
	}

	public Object getOpener() {
		return this.opener;
	}

	public MinimalQuPathProject.ImageEntry getImage() {
		return this.image;
	}


	/**
	 * Constructor building the qupath opener //TODO see what to do with guiparams
	 *
	 */
	public QuPathImageOpener(){
	}

	public Opener<?> create(// opener core option
						 	String dataLocation,
						 	int iSerie,
						 	// Location of the image
						 	double[] positionPreTransformMatrixArray,
						 	double[] positionPostTransformMatrixArray,
						 	boolean positionIsImageCenter,
						 	// units
						 	Length defaultSpaceUnit,
						 	Length defaultVoxelUnit,
						 	String unit,
						 	// How to stream it
						 	int poolSize,
						 	boolean useDefaultXYBlockSize,
						 	FinalInterval cacheBlockSize,
						 	// channel options
						 	boolean swZC,
						 	boolean splitRGBChannels,
						 	Gateway gateway,
						 	SecurityContext ctx,
						 	long imageID,
						 	MinimalQuPathProject.ImageEntry image,
						 	URI qpPathProject)
	{

		// get the rotation angle if the image has been loaded in qupath with the
		// rotation command
		if (image.serverBuilder.builderType.equals("rotated")) {
			double angleRotationZAxis = getAngleRotationZAxis(image);
		}


		this.image = image;
		this.unit = unit;
		this.qpProj = qpPathProject;
		this.iSerie = iSerie;

		if (image.serverBuilder.builderType.equals("uri")) {
			logger.debug("URI image server");

			try {
				//this.identifier = new QuPathImageLoader.QuPathSourceIdentifier();
				//this.identifier.angleRotationZAxis = angleRotationZAxis;

				/*String filePath;*/

				System.out.println("provided class name : "+image.serverBuilder.providerClassName);
				// create openers
				if (image.serverBuilder.providerClassName.equals(
					"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder"))
				{
					// This appears to work more reliably than converting to a File
					/*URI uri = new URI(image.serverBuilder.uri.getScheme(),
					image.serverBuilder.uri.getHost(), image.serverBuilder.uri
						.getPath(), null);
					String filePath = Paths.get(uri).toString();*/

					//BioFormatsBdvOpener bfOpener = getInitializedBioFormatsBDVOpener(
					//	filePath);//.ignoreMetadata();
					//this.opener = bfOpener;.
					System.out.println("datalocation bioformat : "+dataLocation);
					this.opener = (Opener<T>) new BioFormatsBdvOpener(
							dataLocation,
							iSerie,
							// Location of the image
							positionPreTransformMatrixArray,
							positionPostTransformMatrixArray,
							positionIsImageCenter,
							defaultSpaceUnit,
							defaultVoxelUnit,
							unit,
							// How to stream it
							poolSize,
							useDefaultXYBlockSize,
							cacheBlockSize,
							// Channel options
							swZC,
							splitRGBChannels);

					logger.debug("BioFormats Opener for image " + this.image.imageName);
				}
				else {
					if (this.image.serverBuilder.providerClassName.equals(
						"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder"))
					{
						//filePath = this.image.serverBuilder.uri.toString();
						System.out.println("datalocation omero : "+dataLocation);
						this.opener = (Opener<T>) new OmeroBdvOpener(
							gateway,
							ctx,
							imageID,
							poolSize,
							unit,
							dataLocation);

						logger.debug("OMERO-RAW Opener for image " + this.image.imageName);
					}
					else {
						logger.error("Unsupported " +
							this.image.serverBuilder.providerClassName +
							" provider Class Name");
						System.out.println("Unsupported " +
							this.image.serverBuilder.providerClassName +
							" provider Class Name");
						return this;
					}
				}

				// fill the identifier
				//this.identifier.uri = this.image.serverBuilder.uri;
				//this.identifier.sourceFile = filePath;
				//this.identifier.indexInQuPathProject = this.indexInQuPathProject;
				//this.identifier.entryID = this.image.entryID;

				// get bioformats serie number
				/*int iSerie = this.image.serverBuilder.args.indexOf("--series");

				if (iSerie == -1) {
					logger.error("Series not found in qupath project server builder!");
					this.identifier.bioformatsIndex = 0;// was initially -1 but put to 0
																							// because of index -1 does not
																							// exists (in QuPathToSpimData /
																							// BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths()
				}
				else {
					this.identifier.bioformatsIndex = Integer.parseInt(
						this.image.serverBuilder.args.get(iSerie + 1));
				}*/

			}
			catch (Exception e) {
				logger.error("URI Syntax error " + e.getMessage());
				System.out.println("URI Syntax error " + e.getMessage());
				e.printStackTrace();
			}
		}
		else {
			logger.error("Unsupported " + image.serverBuilder.builderType +
				" server builder");
		}
		return this;
	}


	/**
	 * get the rotation angle of the image if the image was imported in qupath
	 * with a rotation
	 *
	 * @param image
	 * @return
	 */
	private double getAngleRotationZAxis(MinimalQuPathProject.ImageEntry image) {
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


	private AffineTransform3D getTransform(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String outputUnit,
												 AffineTransform3D rootTransform, VoxelDimensions voxSizes) {

		// create a new AffineTransform3D based on pixelCalibration
		AffineTransform3D quPathRescaling = new AffineTransform3D();

		if (pixelCalibrations != null) {
			double scaleX = 1.0;
			double scaleY = 1.0;
			double scaleZ = 1.0;

			double voxSizeX = voxSizes.dimension(0);
			double voxSizeY = voxSizes.dimension(1);
			double voxSizeZ = voxSizes.dimension(2);

			if (pixelCalibrations.pixelWidth != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
				Length voxLengthX = new Length(voxSizeX, BioFormatsTools.getUnitFromString(voxSizes.unit()));

				if (voxLengthX.value(UNITS.MICROMETER) != null) {
					logger.debug("xVox size = " + pc.value + " micrometer");
					scaleX = pc.value / voxLengthX.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
					scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling x");
				}
			}
			if (pixelCalibrations.pixelHeight != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
				Length voxLengthY = new Length(voxSizeY, BioFormatsTools.getUnitFromString(voxSizes.unit()));
				// if (pc.unit.equals("um")) {
				if (voxLengthY.value(UNITS.MICROMETER) != null) {
					logger.debug("yVox size = " + pc.value + " micrometer");
					scaleY = pc.value / voxLengthY.value(UNITS.MICROMETER).doubleValue();
				} else {
					Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
					scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
					logger.debug("rescaling y");
				}
			}
			if (pixelCalibrations.zSpacing != null) {
				MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.zSpacing;
				Length voxLengthZ = new Length(voxSizeZ, BioFormatsTools.getUnitFromString(voxSizes.unit()));
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
				quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
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
	 * Fill the opener metadata with QuPath metadata
	 * 
	 * @return this object
	 */
	/*public QuPathImageOpener loadMetadata() {
		if (this.image.serverBuilder != null) {
			// if metadata is null, it means that the image has been imported using
			// BioFormats
			if (this.image.serverBuilder.metadata != null) {
				MinimalQuPathProject.PixelCalibrations pixelCalibration =
					this.image.serverBuilder.metadata.pixelCalibration;
				this.pixelCalibrations = pixelCalibration;

				if (pixelCalibration != null) {
					// fill pixels size and unit
					this.omeMetaIdxOmeXml.setPixelsPhysicalSizeX(new Length(
						pixelCalibration.pixelWidth.value, convertStringToUnit(
							pixelCalibration.pixelWidth.unit)), 0);
					this.omeMetaIdxOmeXml.setPixelsPhysicalSizeY(new Length(
						pixelCalibration.pixelHeight.value, convertStringToUnit(
							pixelCalibration.pixelHeight.unit)), 0);
					this.omeMetaIdxOmeXml.setPixelsPhysicalSizeZ(new Length(
						pixelCalibration.zSpacing.value, convertStringToUnit(
							pixelCalibration.zSpacing.unit)), 0);

					// fill channels' name and color
					List<MinimalQuPathProject.ChannelInfo> channels =
						this.image.serverBuilder.metadata.channels;
					for (int i = 0; i < channels.size(); i++) {
						this.omeMetaIdxOmeXml.setChannelName(channels.get(i).name, 0, i);
						this.omeMetaIdxOmeXml.setChannelColor(new Color(channels.get(
							i).color), 0, i);
					}
				}
				else logger.warn(
					"PixelCalibration field does not exist in the image metadata");
			}
			else logger.warn("Metadata are not available in the image metadata");
		}
		else logger.warn("The image does not contain any builder");

		return this;
	}*/

	/**
	 * Sets the reader for this opener with an existing reader (to not build it twice
	 * because it takes very long time).
	 *
	 * @param reader
	 * @return this opener
	 */
	/*public QuPathImageOpener setReader(IFormatReader reader){
		if(this.image.serverBuilder.providerClassName.equals(
				"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
			this.reader = reader;
			this.seriesCount = reader.getSeriesCount();
			this.omeMetaIdxOmeXml = (IMetadata) reader.getMetadataStore();
		}else{
			this.setReader();
		}

		return this;
	}*/

	/**
	 * Build a reader for this opener
	 * @return this opener
	 */
	/*public QuPathImageOpener setReader(){
		if(this.image.serverBuilder.providerClassName.equals(
				"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
			IFormatReader Ireader = ((BioFormatsBdvOpener) (this.opener)).getNewReader();
			this.reader = Ireader;
			this.seriesCount = Ireader.getSeriesCount();
			this.omeMetaIdxOmeXml = (IMetadata) Ireader.getMetadataStore();
		}else{
			if(this.image.serverBuilder.providerClassName.equals(
					"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")){
				this.reader = null;
				this.seriesCount = 1;
				this.omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
			}else{
				logger.error("Reader cannot be set ; Unsupported " +
						this.image.serverBuilder.providerClassName +
						" provider Class Name");
			}
		}

		return this;
	}*/

	/**
	 * Convert the string unit from QuPath metadata into Unit class readable by
	 * the opener
	 * 
	 * @param unitString
	 * @return
	 */
	private Unit<Length> convertStringToUnit(String unitString) {
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

	@Override
	public int[] getCellDimensions(int level) {
		return this.opener.getCellDimensions(level);
	}

	@Override
	public ChannelProperties getChannel(int iChannel) {
		if(this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.channels != null){
			MinimalQuPathProject.ChannelInfo channel = this.image.serverBuilder.metadata.channels.get(iChannel);
			System.out.println(channel.color);
			return this.opener.getChannel(iChannel).setChannelName(channel.name).setChannelColor(channel.color);
		}
		else return this.opener.getChannel(iChannel);
	}

	@Override
	public Dimensions[] getDimensions() {
		return this.opener.getDimensions();
	}

	@Override
	public List<Entity> getEntities(int iChannel) {
		List<Entity> oldEntities = this.opener.getEntities(iChannel);
		List<Entity> newEntities = oldEntities.stream().filter(e->!(e instanceof ChannelName)).collect(Collectors.toList());
		newEntities.add(new ChannelName(0, getChannel(iChannel).getChannelName()));

		// create a QuPath Entry
		QuPathEntryEntity qpentry = new QuPathEntryEntity(this.image.entryID);
		qpentry.setName(QuPathEntryEntity.getNameFromURIAndSerie(this.image.serverBuilder.uri, this.iSerie));
		qpentry.setQuPathProjectionLocation(Paths.get(this.qpProj).toString());

		newEntities.add(qpentry);
		newEntities.forEach(e->System.out.println(e));
		return newEntities;
	}

	@Override
	public String getImageName() {
		return this.image.imageName;
	}

	@Override
	public int getNChannels() {
		if(this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.channels != null){
			System.out.println("find NChannel with qupath");
			return this.image.serverBuilder.metadata.channels.size();
		}

		else {
			System.out.println("find NChannel with Bioformats");
			return this.opener.getNChannels();
		}
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
	public Type<? extends NumericType> getPixelType() {
		return this.opener.getPixelType();
	}

	@Override
	public AffineTransform3D getTransform() {
		if(this.image.serverBuilder != null &&
				this.image.serverBuilder.metadata != null &&
				this.image.serverBuilder.metadata.pixelCalibration != null){
			return getTransform(this.image.serverBuilder.metadata.pixelCalibration,
					this.unit, opener.getTransform(), getVoxelDimensions());
		}else
			return this.opener.getTransform();
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
	public void close() throws IOException {
		this.opener.close();
	}





	public VoxelDimensions getVoxelDimensions(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String unit)
	{
		// Always 3 to allow for big stitcher compatibility
		int numDimensions = 3;
		Length[] voxSize = new Length[]{
				new Length(pixelCalibrations.pixelWidth.value, convertStringToUnit(pixelCalibrations.pixelWidth.unit)),
				new Length(pixelCalibrations.pixelHeight.value, convertStringToUnit(pixelCalibrations.pixelHeight.unit)),
				new Length(pixelCalibrations.zSpacing.value, convertStringToUnit(pixelCalibrations.zSpacing.unit))
		};
		double[] d = new double[3];
		Unit<Length> u = BioFormatsTools.getUnitFromString(unit);
		Length voxSizeReferenceFrameLength = new Length(1, UNITS.MICROMETER);

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









	/**
	 * create and initialize an OmeroSourceOpener object to read images from OMERO
	 * in BDV
	 * 
	 * @param datalocation : url of the image
	 * @param gateway : connected gateway
	 * @param ctx
	 * @return
	 * @throws Exception
	 */
	/*public OmeroBdvOpener getInitializedOmeroBDVOpener(String datalocation,
		Gateway gateway, SecurityContext ctx) throws Exception
	{
		Unit bfUnit = BioFormatsTools.getUnitFromString(this.defaultParams
			.getUnit());
		Length positionReferenceFrameLength = new Length(this.defaultParams
			.getRefframesizeinunitlocation(), bfUnit);
		Length voxSizeReferenceFrameLength = new Length(this.defaultParams
			.getVoxSizeReferenceFrameLength(), bfUnit);

		// create the Omero opener
		OmeroBdvOpener opener = new OmeroBdvOpener().location(datalocation)
			.ignoreMetadata();

		// flip x, y and z axis
		if (!this.defaultParams.getFlippositionx().equals("AUTO") &&
			this.defaultParams.getFlippositionx().equals("TRUE"))
		{
			opener = opener.flipPositionX();
			logger.debug("FlipPositionX");
		}

		if (!this.defaultParams.getFlippositiony().equals("AUTO") &&
			this.defaultParams.getFlippositiony().equals("TRUE"))
		{
			opener = opener.flipPositionY();
			logger.debug("FlipPositionY");
		}

		if (!this.defaultParams.getFlippositionz().equals("AUTO") &&
			this.defaultParams.getFlippositionz().equals("TRUE"))
		{
			opener = opener.flipPositionZ();
			logger.debug("FlipPositionZ");
		}

		// set unit length and references
		UnitsLength unit = this.defaultParams.getUnit().equals("MILLIMETER")
			? UnitsLength.MILLIMETER : this.defaultParams.getUnit().equals(
				"MICROMETER") ? UnitsLength.MICROMETER : this.defaultParams.getUnit()
					.equals("NANOMETER") ? UnitsLength.NANOMETER : null;
		logger.debug("Convert input unit to " + unit.name());
		opener = opener.unit(unit);
		opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
		opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

		// split RGB channels
		if (this.defaultParams.getSplitChannels()) {
			opener = opener.splitRGBChannels();
			logger.debug("splitRGBChannels");
		}

		// set omero connection
		String[] imageString = datalocation.split("%3D");
		String[] omeroId = imageString[1].split("-");

		logger.debug("OmeroID : " + omeroId[1]);
		opener.gateway(gateway).securityContext(ctx).imageID(Long.parseLong(
			omeroId[1])).host(ctx.getServerInformation().getHost()).create();

		return opener;
	}*/

	/**
	 * create and initialize an BioFormatsBdvOpener object to read images from
	 * Bioformats in BDV
	 * 
	 * @param datalocation : uri of the image
	 * @return
	 */
	/*public BioFormatsBdvOpener getInitializedBioFormatsBDVOpener(
		String datalocation)
	{
		Unit<Length> bfUnit = BioFormatsTools.getUnitFromString(this.defaultParams
			.getUnit());
		Length positionReferenceFrameLength = new Length(this.defaultParams
			.getRefframesizeinunitlocation(), bfUnit);
		Length voxSizeReferenceFrameLength = new Length(this.defaultParams
			.getVoxSizeReferenceFrameLength(), bfUnit);

		// create the bioformats opener
		BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener().location(
			datalocation).ignoreMetadata();

		// Switch channels and Z axis
		if (!this.defaultParams.getSwitchzandc().equals("AUTO")) {
			opener = opener.switchZandC(this.defaultParams.getSwitchzandc().equals(
				"TRUE"));
			logger.debug("Switch Z and C");
		}

		// configure cache block size
		if (!this.defaultParams.getUsebioformatscacheblocksize()) {
			opener = opener.cacheBlockSize(this.defaultParams.getCachesizex(),
				this.defaultParams.getCachesizey(), this.defaultParams.getCachesizez());
			logger.debug("cacheBlockSize : " + this.defaultParams.getCachesizex() +
				", " + this.defaultParams.getCachesizey() + ", " + this.defaultParams
					.getCachesizez());
		}

		// configure the coordinates origin convention
		if (!this.defaultParams.getPositoniscenter().equals("AUTO")) {
			if (this.defaultParams.getPositoniscenter().equals("TRUE")) {
				opener = opener.centerPositionConvention();
				logger.debug("CENTER position convention");
			}
			else {
				opener = opener.cornerPositionConvention();
				logger.debug("CORNER position convention");
			}
		}

		// flip x,y and z axis
		if (!this.defaultParams.getFlippositionx().equals("AUTO") &&
			this.defaultParams.getFlippositionx().equals("TRUE"))
		{
			opener = opener.flipPositionX();
			logger.debug("FlipPositionX");
		}

		if (!this.defaultParams.getFlippositiony().equals("AUTO") &&
			this.defaultParams.getFlippositiony().equals("TRUE"))
		{
			opener = opener.flipPositionY();
			logger.debug("FlipPositionY");
		}

		if (!this.defaultParams.getFlippositionz().equals("AUTO") &&
			this.defaultParams.getFlippositionz().equals("TRUE"))
		{
			opener = opener.flipPositionZ();
			logger.debug("FlipPositionZ");
		}

		// set unit length
		logger.debug("Convert input unit to " + this.defaultParams.getUnit());
		opener = opener.unit(bfUnit);
		opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
		opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

		// split channels
		if (this.defaultParams.getSplitChannels()) {
			opener = opener.splitRGBChannels();
			logger.debug("splitRGBChannels");
		}
		return opener;
	}*/
}
