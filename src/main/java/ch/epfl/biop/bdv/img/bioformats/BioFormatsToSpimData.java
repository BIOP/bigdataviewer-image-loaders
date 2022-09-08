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

import ch.epfl.biop.bdv.img.ChannelProperties;
import ch.epfl.biop.bdv.img.Opener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.entity.FileIndex;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesNumber;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Converting BioFormats structure into an Xml Dataset, compatible for
 * BigDataViewer and FIJI BIG Plugins Limitation Series are considered as Tiles,
 * no Illumination or Angle is considered
 *
 * @author nicolas.chiaruttini@epfl.ch, BIOP, EPFL 2020
 */

public class BioFormatsToSpimData {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsToSpimData.class);

	private int getChannelId(int iChannel, ChannelProperties channelProperties)
	{
		if (!channelToId.containsKey(channelProperties)) {
			// No : add it in the channel hashmap
			channelToId.put(channelProperties, channelCounter);
			logger.debug("New Channel " + iChannel + ", set as number " + channelCounter);
			channelIdToChannel.put(channelCounter, new Channel(channelCounter));
			channelCounter++;
		}
		else {
			logger.debug("Channel " + iChannel + ", already known.");
		}

		return channelIdToChannel.get(channelToId.get(channelProperties)).getId();
	}

	int viewSetupCounter = 0;
	int nTileCounter = 0;
	int maxTimepoints = -1;
	int channelCounter = 0;

	final Map<Integer, Channel> channelIdToChannel = new HashMap<>();
	final ArrayList<Opener<?>> openers = new ArrayList<>();
	final Map<ChannelProperties, Integer> channelToId = new HashMap<>();
	//final Map<Integer, Integer> fileIdxToNumberOfSeries = new HashMap<>();
	//final Map<Integer, SeriesTps> fileIdxToNumberOfSeriesAndTimepoints =
	//	new HashMap<>();
	final Map<Integer, FileChannel> viewSetupToBFFileSerieChannel = new HashMap<>();

	protected AbstractSpimData getSpimDataInstance(List<OpenerSettings> openerSettings) {
		//openers.forEach(o -> o.ignoreMetadata()); // necessary for spimdata
		/*viewSetupCounter = 0;
		nTileCounter = 0;
		maxTimepoints = -1;
		channelCounter = 0;*/

		// No Illumination
		Illumination dummy_ill = new Illumination(0);
		// No Angle
		Angle dummy_ang = new Angle(0);
		// Many View Setups
		List<ViewSetup> viewSetups = new ArrayList<>();

		try {
			for (int iF = 0; iF < openerSettings.size(); iF++) {
				final int iFile = iF;

				// get the opener
				Opener<?> opener = openerSettings.get(iF).create();
				this.openers.add(opener);

				// get image location
				String dataLocation = openerSettings.get(iF).getDataLocation(); // other entity
				logger.debug("Data located at " + dataLocation);

				FileIndex fi = new FileIndex(iF); // first entity
				fi.setName(dataLocation);


				//IFormatReader memo = openers.get(iF).getNewReader();



				//final int seriesCount = opener.getSerieCount();
				//logger.debug("Number of Series " + seriesCount);
				//final IMetadata omeMeta = opener.getMetadata();//(IMetadata) memo.getMetadataStore();

				//fileIdxToNumberOfSeries.put(iF, seriesCount);

				// -------------------------- SETUPS For each Series : one per timepoint and one per channel
				//IntStream series = IntStream.range(0, seriesCount);
				//int iSerie = opener.getSerie();
			//	series.forEach(iSerie -> {
					//memo.setSeries(iSerie);
					//SeriesNumber sn = new SeriesNumber(iSerie, opener.getImageName()); // other entity
					//fileIdxToNumberOfSeriesAndTimepoints.put(iFile, new SeriesTps(seriesCount, omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue()));

					// One serie = one Tile
					Tile tile = new Tile(nTileCounter);

					nTileCounter++;
					// ---------- Serie >
					// ---------- Serie > Timepoints
					//logger.debug("\t Serie " + iSerie + " Number of timesteps = " + opener.getNTimePoints());
					// ---------- Serie > Channels
					//logger.debug("\t Serie " + iSerie + " Number of channels = " + opener.getNChannels());

					// final int iS = iSerie;
					// Properties of the serie
					IntStream channels = IntStream.range(0, opener.getNChannels());
				//	if (opener.getNTimePoints() > maxTimepoints)
				//	{
						maxTimepoints = opener.getNTimePoints();
				//	}

					//String imageName = getImageName(dataLocation, seriesCount, omeMeta, iSerie);
					//sn.setName(imageName);

					// get image dimensions (x, y and z)
					Dimensions dims = opener.getDimensions()[0];
					//BioFormatsTools.getSeriesDimensions(omeMeta, iSerie); // number of pixels .. no calibration
					logger.debug("X:" + dims.dimension(0) + " Y:" + dims.dimension(1) + " Z:" + dims.dimension(2));

					// get voxel dimension (voxel size in x,y and z in ?? unit)
					VoxelDimensions voxDims = opener.getVoxelDimensions();

					// Register Setups (one per channel and one per timepoint)
					channels.forEach(iCh -> {
						// get channel properties
						ChannelProperties ch = opener.getChannel(iCh);
						int ch_id = getChannelId(iCh, ch);

						// build the viewsetup
						String setupName = opener.getImageName() + "-" + ch.getChannelName();
						logger.debug("setup name : "+setupName);
						ViewSetup vs = new ViewSetup(viewSetupCounter, setupName, dims, voxDims, tile, // Tile is index of Serie
							channelIdToChannel.get(ch_id), dummy_ang, dummy_ill);

						// Attempt to set color
						Displaysettings ds = new Displaysettings(viewSetupCounter);
						ds.min = 0;
						ds.max = 255;
						ds.isSet = false;

						// ----------- Color
						ARGBType color = ch.getColor();
						if (color != null) {
							ds.isSet = true;
							ds.color = new int[] { ARGBType.red(color.get()), ARGBType.green(
								color.get()), ARGBType.blue(color.get()), ARGBType.alpha(color
									.get()) };
						}

						// set viewsetup attributes
						opener.getEntities(iCh).forEach(vs::setAttribute);
						vs.setAttribute(fi);
						vs.setAttribute(ds);

						// add viewsetup to the list
						viewSetups.add(vs);
						viewSetupToBFFileSerieChannel.put(viewSetupCounter, new FileChannel(iFile, iCh));
						viewSetupCounter++;

					});
				//});
				//memo.close();
			}

			// ------------------- BUILDING SPIM DATA
			/*ArrayList<String> inputFilesArray = new ArrayList<>();
			for (BioFormatsBdvOpener opener : this.openers) {
				inputFilesArray.add(opener.getDataLocation());
			}*/

			List<TimePoint> timePoints = new ArrayList<>();
			IntStream.range(0, maxTimepoints).forEach(tp -> timePoints.add(
				new TimePoint(tp)));

			final ArrayList<ViewRegistration> registrations = new ArrayList<>();

			List<ViewId> missingViews = new ArrayList<>();
			// for all viewsetupts
			//    for iTp = 0 to max timepoints
			// 			either:
			//				missingViews.add(new ViewId(iTp.getId(), viewSetupId));
			//          or:
			//				rootTransform = f(viewsetupts)
			//  			registrations.add(new ViewRegistration(iTp.getId(),
			//													viewSetupId, rootTransform));


			for (int iF = 0; iF < this.openers.size(); iF++) {
				int iFile = iF;

				//IFormatReader memo = this.openers.get(iF).getNewReader();
				Opener opener = this.openers.get(iF);
				//logger.debug("Number of Series : " + opener.getSerieCount());
				//final IMetadata omeMeta = opener.getMetadata();//(IMetadata) memo.getMetadataStore();

				//int nSeries = fileIdxToNumberOfSeries.get(iF);
				// Need to set view registrations : identity ? how does that work with
				// the one given by the image loader ?
				//IntStream series = IntStream.range(0, nSeries);

				//series.forEach(iSerie -> {
					final int nTimepoints = opener.getNTimePoints();
					AffineTransform3D rootTransform = /*openers.get(iFile)*/opener.getTransform();
					/*BioFormatsTools
						.getSeriesRootTransform(omeMeta, iSerie, openers.get(iFile).u,
							openers.get(iFile).positionPreTransformMatrixArray, // AffineTransform3D
																																	// positionPreTransform,
							openers.get(iFile).positionPostTransformMatrixArray, // AffineTransform3D
																																		// positionPostTransform,
							openers.get(iFile).positionReferenceFrameLength, openers.get(
								iFile).positionIsImageCenter, // boolean positionIsImageCenter,
							openers.get(iFile).voxSizePreTransformMatrixArray, // voxSizePreTransform,
							openers.get(iFile).voxSizePostTransformMatrixArray, // AffineTransform3D
																																	// voxSizePostTransform,
							openers.get(iFile).voxSizeReferenceFrameLength, // null, //Length
																															// voxSizeReferenceFrameLength,
							openers.get(iFile).axesOfImageFlip // axesOfImageFlip
					);*/
					timePoints.forEach(iTp -> {
						viewSetupToBFFileSerieChannel.keySet().stream()
								.filter(viewSetupId -> (viewSetupToBFFileSerieChannel.get(viewSetupId).iFile == iFile))
								//.filter(viewSetupId -> (viewSetupToBFFileSerieChannel.get(viewSetupId).iSerie == opener.getSerie()))
								.forEach(viewSetupId -> {
									if (iTp.getId() < nTimepoints) {
										registrations.add(new ViewRegistration(iTp.getId(), viewSetupId, rootTransform)); // do not need to keep the root transform per setupID
										// because the transform is set for one opener (XYZCT) and one opener = one serie
									}
									else {
										missingViews.add(new ViewId(iTp.getId(), viewSetupId));
									}
								});
					});

				//});
				//memo.close();
			}

			SequenceDescription sd = new SequenceDescription(new TimePoints(
				timePoints), viewSetups, null, new MissingViews(missingViews));
			sd.setImgLoader(new BioFormatsImageLoader(openers, openerSettings, sd));

			return new SpimData(null, sd, new ViewRegistrations(registrations));
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/*private String getChannelName(IMetadata omeMeta, int iSerie, int iCh) {
		String channelName = omeMeta.getChannelName(iSerie, iCh);
		channelName = (channelName == null || channelName.equals("")) ? "ch" + iCh
			: channelName;
		return channelName;
	}*/



	public static AbstractSpimData getSpimData(List<OpenerSettings> openersSettings) {
		return new BioFormatsToSpimData().getSpimDataInstance(openersSettings);
	}

	public static AbstractSpimData getSpimData(OpenerSettings openerSetting) {
		ArrayList<OpenerSettings> singleOpenerList = new ArrayList<>();
		singleOpenerList.add(openerSetting);
		return BioFormatsToSpimData.getSpimData(singleOpenerList);
	}

	public static AbstractSpimData getSpimData(File f) {
		OpenerSettings opener = getDefaultSettings(f.getAbsolutePath());//(getDefaultOpener(f.getAbsolutePath()));
		return getSpimData(opener);
	}

	public static AbstractSpimData getSpimData(File[] files) {
		ArrayList<OpenerSettings> openers = new ArrayList<>();
		for (File f : files) {
			openers.add(getDefaultSettings(f.getAbsolutePath()));//(getDefaultOpener(f.getAbsolutePath()));
		}
		return BioFormatsToSpimData.getSpimData(openers);
	}

	public static OpenerSettings getDefaultSettings(String dataLocation) {
		return OpenerSettings.getDefaultSettings(OpenerSettings.OpenerType.BIOFORMATS, dataLocation);//.auto();
	}

}
