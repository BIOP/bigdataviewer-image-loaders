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
 *//*


package ch.epfl.biop.bdv.img.qupath;

import ch.epfl.biop.bdv.img.Opener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
import ch.epfl.biop.bdv.img.qupath.entity.QuPathEntryEntity;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;

*/
/**
 * See documentation in {@link QuPathImageLoader}
 * 
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 * @author Rémy Dornier, EPFL, BIOP, 2022
 *//*


public class QuPathToSpimData {

	protected static Logger logger = LoggerFactory.getLogger(
		QuPathToSpimData.class);

	Map<URI, AbstractSpimData> spimDataMap = new HashMap<>();
	Map<URI, OpenerSettings> uriToOpenerSettings = new HashMap<>();

	Map<URI, QuPathImageOpener> uriToFileOpener = new HashMap<>();
	Map<URI, String> uriToImageName = new HashMap<>();
	List<URI> rawURI = new ArrayList<>();
	Map<String, OmeroTools.GatewaySecurityContext> hostToGatewayCtx =
		new HashMap<>();

	public AbstractSpimData getSpimDataInstance(URI quPathProject,
		OpenerSettings projectSettings)
	{

		try {
			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			// IJ.log("projectJson : "+projectJson);

			project.images.forEach(image -> {
				logger.debug("Opening qupath image " + image);

				OpenerSettings imageSettings = new OpenerSettings();
				QuPathSourceIdentifier identifier = new QuPathSourceIdentifier();
				// get the rotation angle if the image has been loaded in qupath with the
				// rotation command
				double angleRotationZAxis = 0;
				if (image.serverBuilder.builderType.equals("rotated")) {
					angleRotationZAxis = getAngleRotationZAxis(image);
				}


				// fill the identifier
				identifier.indexInQuPathProject = project.images.indexOf(image);
				identifier.entryID = image.entryID;
				identifier.angleRotationZAxis = angleRotationZAxis;

				try {
					// check for omero opener and ask credentials if necessary
					if (image.serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
						if (!hostToGatewayCtx.containsKey(image.serverBuilder.providerClassName)) {
							// ask credentials
							logger.debug("Ask credentials to user");
							Boolean onlyCredentials = false;
							String[] credentials = OmeroTools
								.getOmeroConnectionInputParameters(onlyCredentials);
							String host = credentials[0];
							int port = Integer.parseInt(credentials[1]);
							String username = credentials[2];
							String password = credentials[3];
							credentials = new String[] {};

							// connect to omero
							Gateway gateway = OmeroTools.omeroConnect(host, port, username,
								password);
							SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
							ctx.setServerInformation(new ServerInformation(host));

							// add the connection to the hash map
							OmeroTools.GatewaySecurityContext gtCtx =
								new OmeroTools.GatewaySecurityContext(host, port, gateway, ctx);
							hostToGatewayCtx.put(image.serverBuilder.providerClassName,
								gtCtx);
						}

						// initialize omero opener
						OmeroTools.GatewaySecurityContext gtCtx = hostToGatewayCtx.get(image.serverBuilder.providerClassName);
						identifier.sourceFile = image.serverBuilder.uri.toString();
						identifier.serieNumber = 0;
						imageSettings.setGateway(gtCtx.gateway).setContext(gtCtx.ctx).omeroBuilder();
					}
					else {
						if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")){
							int iSerie = image.serverBuilder.args.indexOf("--series");

							if (iSerie == -1) {
								logger.error("Series not found in qupath project server builder!");
								identifier.serieNumber = 0;
							}
							else {
								identifier.serieNumber = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
							}

							identifier.sourceFile = Paths.get(image.serverBuilder.uri).toString();
							imageSettings.setSerie(identifier.serieNumber).bioFormatsBuilder();
						}else{
							logger.error("Unsupported " + image.serverBuilder.providerClassName + " provider Class Name");
						}
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}


				// create a unique URI for each image based on the name of the image
				// file AND on the name of the serie
				URI enhancedURI;
				try {

					enhancedURI = new URI(image.serverBuilder.uri.toString() + "-s"+identifier.serieNumber);
					identifier.uri = enhancedURI;

					*/
/*//*
/ get the name of the serie (real image name)
					String imageName = qpOpener.getOmeMetaIdxOmeXml().getImageName(
						qpOpener.getIdentifier().bioformatsIndex);

					// add the name of the serie at the end of the current URI
					if (imageName != null && !imageName.isEmpty()) {
						enhancedURI = new URI(qpOpener.getURI().toString() + "/" +
							imageName);
						uriToImageName.put(enhancedURI, imageName);
					}
					else {
						// if the name of the serie is not valid, just remove the extension
						enhancedURI = qpOpener.getURI();
						String[] nameWithoutExtension = image.imageName.split("\\.");
						uriToImageName.put(enhancedURI, nameWithoutExtension[0]);
					}*//*

				}
				catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}

				// build spimdata depending on the opener class
				*/
/*if (!rawURI.contains(qpOpener.getURI())) {
					if (opener instanceof BioFormatsBdvOpener) {
						spimDataMap.put(enhancedURI, (SpimData) BioFormatsToSpimData
							.getSpimData(Collections.singletonList(
								(BioFormatsBdvOpener) qpOpener.getOpener())));
					}
					else if (opener instanceof OmeroBdvOpener) {
						spimDataMap.put(enhancedURI, (SpimData) (new OmeroToSpimData())
							.getSpimDataInstance(Collections.singletonList(
								(OmeroBdvOpener) qpOpener.getOpener())));
					}
					else logger.error("Opener +" + opener.getClass().getName() +
						" is not recognized");

					rawURI.add(qpOpener.getURI());
				}
				else {
					// If the image file has more than one serie, the same spimdata is
					// linked to all series (because one spimdata contains all series at
					// once)
					spimDataMap.put(enhancedURI, spimDataMap.get(spimDataMap.keySet()
						.stream().filter(e -> e.toString().contains(qpOpener.getURI()
							.toString())).findFirst().get()));
				}*//*

				uriToOpenerSettings.put(enhancedURI, imageSettings);
			});

			// regroup all the spimdata in one big spimdata
			logger.debug("Grouping spmidata");
			uriToOpenerSettings.keySet().forEach(uri -> {
				OpenerSettings settings = uriToOpenerSettings.get(uri);
				Opener<?> opener = null;
				try {
					opener = settings.create();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				QuPathImageLoader.QuPathSourceIdentifier identifier = //qpOpener.getIdentifier();
				MinimalQuPathProject.PixelCalibrations pixelCalibrations = qpOpener
					.getPixelCalibrations();

				// create a QuPath Entry
				QuPathEntryEntity qpentry = new QuPathEntryEntity(project.images.get(
					identifier.indexInQuPathProject).entryID);
				qpentry.setName(QuPathEntryEntity.getNameFromURIAndSerie(settings.getDataLocation(), settings.getSerie()));
				qpentry.setQuPathProjectionLocation(Paths.get(quPathProject).toString());
				//SeriesNumber sn = new SeriesNumber(identifier.bioformatsIndex);

				// update spimdata by adding attributed to viewsetups
				localSpimData.getSequenceDescription().getViewSetups().values().forEach(
					vss -> {
						//vss.setAttribute(sn);
						vss.setAttribute(qpentry);
					});

				// create a new AffineTransform3D based on pixelCalibration
			*/
/*	AffineTransform3D quPathRescaling = new AffineTransform3D();
				if (pixelCalibrations != null) {
					double scaleX = 1.0;
					double scaleY = 1.0;
					double scaleZ = 1.0;

					Length[] voxSizes = BioFormatsTools.getSeriesVoxelSizeAsLengths(
						qpOpener.getOmeMetaIdxOmeXml(), identifier.bioformatsIndex);
					if (pixelCalibrations.pixelWidth != null) {
						MinimalQuPathProject.PixelCalibration pc =
							pixelCalibrations.pixelWidth;
						// if (pc.unit.equals("um")) {
						if ((voxSizes[0] != null) && (voxSizes[0].value(
							UNITS.MICROMETER) != null))
						{
							logger.debug("xVox size = " + pc.value + " micrometer");
							scaleX = pc.value / voxSizes[0].value(UNITS.MICROMETER)
								.doubleValue();
						}
						else {
							Length defaultxPix = new Length(1, BioFormatsTools
								.getUnitFromString(guiparams.getUnit()));
							scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER)
								.doubleValue();
							logger.debug("rescaling x");
						}
						//} else {
						//    logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
						//}
					}
					if (pixelCalibrations.pixelHeight != null) {
						MinimalQuPathProject.PixelCalibration pc =
							pixelCalibrations.pixelHeight;
						// if (pc.unit.equals("um")) {
						if ((voxSizes[1] != null) && (voxSizes[1].value(
							UNITS.MICROMETER) != null))
						{
							scaleY = pc.value / voxSizes[1].value(UNITS.MICROMETER)
								.doubleValue();
						}
						else {
							Length defaultxPix = new Length(1, BioFormatsTools
								.getUnitFromString(guiparams.getUnit()));
							scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER)
								.doubleValue();
							logger.debug("rescaling y");
						}
						//} else {
						//    logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
						//}
					}
					if (pixelCalibrations.zSpacing != null) {
						MinimalQuPathProject.PixelCalibration pc =
							pixelCalibrations.zSpacing;
						// if (pc.unit.equals("um")) { problem with micrometer character
						if ((voxSizes[2] != null) && (voxSizes[2].value(
							UNITS.MICROMETER) != null))
						{
							scaleZ = pc.value / voxSizes[2].value(UNITS.MICROMETER)
								.doubleValue();
						}
						else {
							if ((voxSizes[2] != null)) {

							}
							else {
								logger.warn("Null Z voxel size");
							}
							// logger.warn("Null Z voxel size");
						}
						//} else {
						//    logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
						//}
					}
					logger.debug("ScaleX: " + scaleX + " scaleY:" + scaleY + " scaleZ:" +
						scaleZ);
					final double finalScalex = scaleX;
					final double finalScaley = scaleY;
					final double finalScalez = scaleZ;

					// update view Registrations
					localSpimData.getViewRegistrations().getViewRegistrations().values()
						.forEach(vr -> {
							if ((Math.abs(finalScalex - 1.0) > 0.0001) || (Math.abs(
								finalScaley - 1.0) > 0.0001) || (Math.abs(finalScalez -
									1.0) > 0.0001))
						{
								logger.debug("Perform QuPath rescaling");
								quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
								double oX = vr.getModel().get(0, 3);
								double oY = vr.getModel().get(1, 3);
								double oZ = vr.getModel().get(2, 3);
								vr.getModel().preConcatenate(quPathRescaling);
								quPathRescaling.preConcatenate()
								vr.getModel().set(oX, 0, 3);
								vr.getModel().set(oY, 1, 3);
								vr.getModel().set(oZ, 2, 3);
							}
						});
				}*//*

				// update spimdata
				spimDataMap.replace(uri, spimDataMap.get(uri), localSpimData);
			});

			// get the longest time serie
			List<TimePoint> newListOfTimePoint = new ArrayList<>();
			int lastSize = -1;
			for (AbstractSpimData spData : spimDataMap.values()) {
				SpimData spd = (SpimData) spData;
				if (spd.getSequenceDescription().getTimePoints().getTimePointsOrdered()
					.size() > lastSize)
				{
					lastSize = spd.getSequenceDescription().getTimePoints()
						.getTimePointsOrdered().size();
					newListOfTimePoint = spd.getSequenceDescription().getTimePoints()
						.getTimePointsOrdered();
				}
			}

			List<ViewSetup> newViewSetups = new ArrayList<>();
			List<ViewRegistration> newRegistrations = new ArrayList<>();
			List<ViewId> newMissingViews = new ArrayList<>();
			int i = 0;

			// create new viewSetups
			for (URI spURI : spimDataMap.keySet()) {
				SpimData spd = (SpimData) spimDataMap.get(spURI);
				for (ViewSetup viewSetup : spd.getSequenceDescription().getViewSetups()
					.values())
				{
					if (viewSetup.getName().contains(uriToImageName.get(spURI))) {
						// duplicate each new viewsetup with a new ID corresponding to the
						// new spimdata
						ViewSetup newViewSetup = new ViewSetup(i, viewSetup.getName(),
							viewSetup.getSize(), viewSetup.getVoxelSize(), viewSetup
								.getTile(), viewSetup.getChannel(), viewSetup.getAngle(),
							viewSetup.getIllumination());

						// set attributes to the new viewsetup
						Map<String, Entity> attributes = viewSetup.getAttributes();
						attributes.values().forEach(newViewSetup::setAttribute);
						newViewSetups.add(newViewSetup);

						// create new viewRegistrations based on the new list of timepoints
						for (TimePoint iTp : newListOfTimePoint) {
							if (iTp.getId() < spd.getSequenceDescription().getTimePoints()
								.getTimePointsOrdered().size())
							{
								newRegistrations.add(new ViewRegistration(iTp.getId(), i, spd
									.getViewRegistrations().getViewRegistration(0, 0)
									.getModel()));
							}
							else {
								newMissingViews.add(new ViewId(iTp.getId(), i));
							}
						}
						i++;
					}
				}
			}

			// create the new sequence description and set the image loader
			logger.debug("Create spimdata");
			SequenceDescription sd = new SequenceDescription(new TimePoints(
				newListOfTimePoint), newViewSetups, null, new MissingViews(
					newMissingViews));
			sd.setImgLoader(new QuPathImageLoader(quPathProject, new ArrayList<>(
				uriToOpenerSettings.values()), sd, 2, 4));

			// create the new spimdata
			final SpimData newSpimData = new SpimData(null, sd, new ViewRegistrations(
				newRegistrations));

			// disconnect the gateways
			//hostToGatewayCtx.values().forEach(e -> e.gateway.disconnect());

			return newSpimData;

		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// disconnect the gateways
		//hostToGatewayCtx.values().forEach(e -> e.gateway.disconnect());

		return null;
	}





	public static class QuPathSourceIdentifier {

		int indexInQuPathProject;
		int entryID;
		String sourceFile;
		int serieNumber;
		double angleRotationZAxis = 0;
		URI uri;

		public String toString() {
			String str = "";
			str += "sourceFile:" + sourceFile + "[bf:" + serieNumber + " - qp:" +
					indexInQuPathProject + "]";
			return str;
		}
	}

}
*/
