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

package ch.epfl.biop.bdv.img.omero;

import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.ChannelProperties;
import ch.epfl.biop.bdv.img.OmeroBdvOpener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.omero.entity.OmeroUri;
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
import omero.gateway.model.ChannelData;
import omero.model.ChannelBinding;
import omero.model.enums.UnitsLength;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Converting BioFormats structure into an Xml Dataset, compatible for
 * BigDataViewer and FIJI BIG Plugins Limitation Omero openers are considered as
 * Tiles, no Illumination or Angle is considered
 *
 * @author nicolas.chiaruttini@epfl.ch, BIOP, EPFL 2020
 */

public class OmeroToSpimData {

	int viewSetupCounter = 0;
	int openerIdxCounter = 0;
	int maxTimepoints = -1;
	int channelCounter = 0;
	Map<ChannelDataComparator, Integer> channelToId = new HashMap<>();

	final ArrayList<OmeroBdvOpener> openers = new ArrayList<>();
	// Map<Integer,Channel> channelIdToChannel = new HashMap<>();
	// Map<BioFormatsMetaDataHelper.BioformatsChannel,Integer> channelToId = new
	// HashMap<>();
	// Map<Integer,Integer> fileIdxToNumberOfSeries = new HashMap<>();
	// Map<Integer, SeriesTps> fileIdxToNumberOfSeriesAndTimepoints = new
	// HashMap<>();
	Map<Integer, OpenerIdxChannel> viewSetupToOpenerIdxChannel = new HashMap<>();

	protected AbstractSpimData getSpimDataInstance(List<OpenerSettings> openersSettings) {
		//openers.forEach(OmeroBdvOpener::ignoreMetadata); // necessary for spimdata
		viewSetupCounter = 0;
		openerIdxCounter = 0;
		maxTimepoints = -1;
		channelCounter = 0;

		// No Illumination
		Illumination dummy_ill = new Illumination(0);
		// No Angle
		Angle dummy_ang = new Angle(0);
		// No Tile
		Tile dummy_tile = new Tile(0);

		// Many View Setups
		List<ViewSetup> viewSetups = new ArrayList<>();

		try {
			for (int openerIdx = 0; openerIdx < openersSettings.size(); openerIdx++) {
				OmeroBdvOpener opener = (OmeroBdvOpener) openersSettings.get(openerIdx).create();
				openers.add(opener);
				//OmeroUri ou = new OmeroUri(openerIdx, opener.getDataLocation());
				// openerIdxCounter++;
				if (opener.getNTimePoints() > maxTimepoints) {
					maxTimepoints = opener.getNTimePoints();
				}

				String imageName = opener.getImageName();

				Dimensions dims = opener.getDimension();
				// logger.debug("X:"+dims.dimension(0)+" Y:"+dims.dimension(1)+"
				// Z:"+dims.dimension(2));
				VoxelDimensions voxDims = opener.getVoxelDimensions();

				List<ChannelData> channelMetadata = opener.getChannelMetadata();
				// Register Setups (one per channel and one per timepoint)
				for (int iCh = 0; iCh < opener.getNChannels(); iCh++) {
					//ChannelData channelData = channelMetadata.get(iCh);
					ChannelProperties ch = opener.getChannel(iCh);
					String setupName = imageName + "-" + ch.getChannelName();
					// logger.debug(setupName);

					// For spimdata
					//Channel channel = new Channel(getChannelIndex(channelData),
					//	ch.getChannelName());

					// ----------- Set channel contrast (min and max values)
					Displaysettings ds = new Displaysettings(viewSetupCounter);
					//ChannelBinding cb = opener.getRenderingDef().getChannelBinding(
					//		iCh);
					ds.min = ch.getMinDynamicRange();
					ds.max = ch.getMaxDynamicRange();
					ds.isSet = false;

					// ----------- Color
					ARGBType color = ch.getColor();
					if (color != null) {
						ds.isSet = true;
						ds.color = new int[] { ARGBType.red(color.get()), ARGBType.green(
							color.get()), ARGBType.blue(color.get()), ARGBType.alpha(color
								.get()) };
					}

					ViewSetup vs = new ViewSetup(viewSetupCounter, setupName, dims,
						voxDims, dummy_tile, channel, dummy_ang, dummy_ill);
					opener.getEntities(iCh).forEach(vs::setAttribute);
					//vs.setAttribute(ou);
					vs.setAttribute(ds);

					viewSetups.add(vs);
					viewSetupToOpenerIdxChannel.put(viewSetupCounter,
						new OpenerIdxChannel(openerIdx, iCh));
					viewSetupCounter++;
				}
			}

			// ------------------- BUILDING SPIM DATA
			/*ArrayList<String> inputFilesArray = new ArrayList<>();
			for (OmeroBdvOpener opener : openers) {
				inputFilesArray.add(opener.getDataLocation());
			}*/

			List<TimePoint> timePoints = new ArrayList<>();
			IntStream.range(0, maxTimepoints).forEach(tp -> timePoints.add(
				new TimePoint(tp)));

			final ArrayList<ViewRegistration> registrations = new ArrayList<>();

			List<ViewId> missingViews = new ArrayList<>();
			for (int openerIdx = 0; openerIdx < openers.size(); openerIdx++) {

				// logger.debug("Number of Series : " + memo.getSeriesCount());

				// Need to set view registrations : identity ? how does that work with
				// the one given by the image loader ?
				// IntStream series = IntStream.range(0, nSeries);

				final int nTimepoints = openers.get(openerIdx).getNTimePoints();
				AffineTransform3D rootTransform = openers.get(openerIdx).getTransform();

				final int oIdx = openerIdx;
				timePoints.forEach(iTp -> {
					viewSetupToOpenerIdxChannel.keySet().stream()
							.filter(viewSetupId -> (viewSetupToOpenerIdxChannel.get(viewSetupId).openerIdx == oIdx)).
							forEach(viewSetupId -> {
								if (iTp.getId() < nTimepoints) {
									registrations.add(new ViewRegistration(iTp.getId(),
										viewSetupId, rootTransform));
								}
								else {
									missingViews.add(new ViewId(iTp.getId(), viewSetupId));
								}
							});
				});

			}

			SequenceDescription sd = new SequenceDescription(new TimePoints(
				timePoints), viewSetups, null, new MissingViews(missingViews));
			sd.setImgLoader(new OmeroImageLoader(openers, openersSettings, sd));

			final SpimData spimData = new SpimData(null, sd, new ViewRegistrations(
				registrations));
			return spimData;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private int getChannelIndex(ChannelData channelData) throws Exception {
		ChannelDataComparator channelDataComparator = new ChannelDataComparator(
			channelData);
		if (!channelToId.containsKey(channelDataComparator)) {
			// No : add it in the channel hashmap
			channelToId.put(channelDataComparator, channelCounter);
			// logger.debug("New Channel for series "+iSerie+", channel "+iChannel+",
			// set as number "+channelCounter);
			channelCounter++;
		}
		return channelToId.get(channelDataComparator);
	}

	public static AbstractSpimData getSpimData(List<OpenerSettings> openersSettings) {
		return new OmeroToSpimData().getSpimDataInstance(openersSettings);
	}

	public static AbstractSpimData getSpimData(OpenerSettings openerSettings) {
		ArrayList<OpenerSettings> singleOpenerList = new ArrayList<>();
		singleOpenerList.add(openerSettings);
		return OmeroToSpimData.getSpimData(singleOpenerList);
	}

	public static AbstractSpimData getSpimData(File f) {
		OpenerSettings opener = getDefaultSettings(f.getAbsolutePath());
		return getSpimData(opener);
	}

	public static AbstractSpimData getSpimData(File[] files) {
		ArrayList<OpenerSettings> openers = new ArrayList<>();
		for (File f : files) {
			openers.add(getDefaultSettings(f.getAbsolutePath()));
		}
		return OmeroToSpimData.getSpimData(openers);
	}

	public static OpenerSettings getDefaultSettings(String dataLocation) {
		return OpenerSettings.getDefaultSettings(OpenerSettings.OpenerType.OMERO,dataLocation);
	}

	public static class ChannelDataComparator {

		double globalMax;
		int iChannel;
		String chName = "";
		double emissionWl = 1;
		double excitationWl = 1;

		public ChannelDataComparator(ChannelData channelData) throws Exception {
			this.globalMax = channelData.getGlobalMax();
			this.iChannel = channelData.getIndex();
			// this.name = channelData.getChannelLabeling();

			if (channelData.getEmissionWavelength(UnitsLength.NANOMETER) != null) {
				this.emissionWl = channelData.getEmissionWavelength(
					UnitsLength.NANOMETER).getValue();
			}
			if (channelData.getExcitationWavelength(UnitsLength.NANOMETER) != null) {
				this.excitationWl = channelData.getExcitationWavelength(
					UnitsLength.NANOMETER).getValue();
			}
			if (channelData.getChannelLabeling() != null) {
				this.chName = channelData.getChannelLabeling();
			}
			else {
				this.chName = "ch_" + iChannel;
			}

		}

		@Override
		public int hashCode() {
			return (int) (this.chName.hashCode() * (this.globalMax + 1) *
				this.emissionWl * this.excitationWl * (iChannel + 1));
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ChannelDataComparator) {
				ChannelDataComparator cdc = (ChannelDataComparator) obj;
				return (globalMax == cdc.globalMax) && (iChannel == cdc.iChannel) &&
					(emissionWl == cdc.emissionWl) &&
					(excitationWl == cdc.excitationWl) && (chName.equals(cdc.chName));
			}
			else {
				return false;
			}
		}
	}

}
