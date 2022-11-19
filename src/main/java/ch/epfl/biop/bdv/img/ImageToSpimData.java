package ch.epfl.biop.bdv.img;

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


import ch.epfl.biop.bdv.img.entity.ImageName;
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
 * Converting generic image structure (BioFormats, Omero, QuPath entries, OpenSlide or other)
 * into an Xml Dataset, compatible for
 * BigDataViewer and Fiji BIG Plugins
 * Limitation: Series are considered as Tiles,
 * no Illumination or Angle is considered
 *
 * @author nicolas.chiaruttini@epfl.ch, BIOP, EPFL 2020
 * @author remy.dornier@epfl.ch, BIOP, EPFL 2022
 */
public class ImageToSpimData {

    final protected static Logger logger = LoggerFactory.getLogger(
            ImageToSpimData.class);

    // -------- ViewSetups map and counters
    int viewSetupCounter = 0;
    int nTileCounter = 0;
    final Map<Integer, OpenerChannel> viewSetupToFileChannel = new HashMap<>();


    // Channel registration to Ids
    int channelCounter = 0;
    final Map<Integer, Channel> channelIdToChannel = new HashMap<>();
    final Map<ChannelProperties, Integer> channelToId = new HashMap<>();

    // TimePoints
    int maxTimepoints = -1;

    // openers registration
    //List<Opener<?>> openers = new ArrayList<>();

    /**
     * Build a SpimData object from a list of OpenerSettings
     * A SpimData is made of many ViewSetups, and
     * there is one ViewSetup per channel/serie/timepoint/slice
     * @param openerSettings
     * @return
     */
    protected AbstractSpimData getSpimDataInstance(List<OpenerSettings> openerSettings) {

        // No Illumination
        Illumination dummy_ill = new Illumination(0);
        // No Angle
        Angle dummy_ang = new Angle(0);

        // Many View Setups
        List<ViewSetup> viewSetups = new ArrayList<>();

        List<Opener<?>> openers = BiopImageLoader.createOpeners(openerSettings);

        try {
            for (int iOpener = 0; iOpener < openerSettings.size(); iOpener++) {
                final int iOpener_final = iOpener;

                // get the opener
                Opener<?> opener = openers.get(iOpener);

                // TODO see if it is necessary to keep the tile with nTileCounter
                Tile tile = new Tile(nTileCounter);
                nTileCounter++;

                // get maxTimePoints
                if (opener.getNTimePoints() > maxTimepoints) {
                    maxTimepoints = opener.getNTimePoints();
                }

                // get image dimensions (x, y and z)
                Dimensions dims = opener.getDimensions()[0];
                logger.debug("X:" + dims.dimension(0) + " Y:" + dims.dimension(1) + " Z:" + dims.dimension(2));

                // get voxel dimension (voxel size in x,y and z in ?? unit)
                VoxelDimensions voxDims = opener.getVoxelDimensions();

                // create fileIndex entity

                IntStream channels = IntStream.range(0, opener.getNChannels());
                logger.debug("There are "+opener.getNChannels()+" channels.");

                ImageName imageName = new ImageName(iOpener, opener.getImageName());
                System.out.println("ImageName ["+imageName.getId()+"] = "+imageName.getName());

                channels.forEach(iCh -> {
                    // get channel properties
                    ChannelProperties channelProperties = opener.getChannel(iCh);

                    // build the viewsetup
                    String setupName = opener.getImageName() + "-" + channelProperties.getChannelName();
                    logger.debug("setup name : "+setupName);
                    ViewSetup vs = new ViewSetup(viewSetupCounter, setupName, dims, voxDims, tile, // Tile is index of Serie
                            getChannelEntity(iCh, channelProperties),
                            dummy_ang, dummy_ill);

                    // Attempt to set color
                    Displaysettings ds = new Displaysettings(viewSetupCounter);
                    ds.min = channelProperties.getDisplayRangeMin();
                    ds.max = channelProperties.getDisplayRangeMax();
                    ds.isSet = false;

                    // ----------- Color
                    ARGBType color = channelProperties.getColor();
                    if (color != null) {
                        ds.isSet = true;
                        ds.color = new int[] { ARGBType.red(color.get()), ARGBType.green(
                                color.get()), ARGBType.blue(color.get()), ARGBType.alpha(color
                                .get()) };
                    }

                    // set viewsetup attributes
                    opener.getEntities(iCh).forEach(vs::setAttribute);
                    vs.setAttribute(ds);
                    vs.setAttribute(imageName);

                    // add viewsetup to the list
                    viewSetups.add(vs);
                    viewSetupToFileChannel.put(viewSetupCounter, new OpenerChannel(iOpener_final, iCh));
                    viewSetupCounter++;

                });
            }

            // ------------------- BUILDING SPIM DATA

            // Create time points
            List<TimePoint> timePoints = new ArrayList<>();
            IntStream.range(0, maxTimepoints).forEach(tp -> timePoints.add(new TimePoint(tp)));

            final ArrayList<ViewRegistration> registrations = new ArrayList<>();
            List<ViewId> missingViews = new ArrayList<>();

            for (int iF = 0; iF < openers.size(); iF++) {
                int iFile = iF;

                Opener<?> opener = openers.get(iF);
                final int nTimePoints = opener.getNTimePoints();
                AffineTransform3D rootTransform = opener.getTransform();

                // create views
                timePoints.forEach(iTp -> {
                    viewSetupToFileChannel.keySet().stream()
                            .filter(viewSetupId -> (viewSetupToFileChannel.get(viewSetupId).iOpener == iFile))
                            .forEach(viewSetupId -> {
                                if (iTp.getId() < nTimePoints) {
                                    registrations.add(new ViewRegistration(iTp.getId(), viewSetupId, rootTransform)); // do not need to keep the root transform per setupID
                                    // because the transform is set for one opener (XYZCT) and one opener = one serie
                                }
                                else {
                                    missingViews.add(new ViewId(iTp.getId(), viewSetupId));
                                }
                            });
                });
            }

            // create spimdata
            SequenceDescription sd = new SequenceDescription(new TimePoints(timePoints), viewSetups, null, new MissingViews(missingViews));
            sd.setImgLoader(new BiopImageLoader(openerSettings, openers, sd));
            return new SpimData(null, sd, new ViewRegistrations(registrations));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * Get or create a unique ID for each identical channel among all images.
     * If two channels of two different images have the same color, pixel type, name, position and so on
     * (see {@link ChannelProperties} equals method), they will have the same ID.
     * @param iChannel
     * @param channelProperties
     * @return
     */
    private Channel getChannelEntity(int iChannel, ChannelProperties channelProperties)
    {
        if (!channelToId.containsKey(channelProperties)) {
            // No : add it in the channel hashmap
            channelToId.put(channelProperties, channelCounter);
            logger.debug("New Channel " + iChannel + ", set as number " + channelCounter);
            channelIdToChannel.put(channelCounter, new Channel(channelCounter, channelProperties.getChannelName()));
            channelCounter++;
        }
        else {
            logger.debug("Channel " + iChannel + ", already known.");
        }

        return channelIdToChannel.get(channelToId.get(channelProperties));
    }

    // CLASS BUILDERS

    /**
     * Create {@link SpimData} from a list of {@link OpenerSettings}
     * @param openersSettings
     * @return
     */
    public static AbstractSpimData getSpimData(List<OpenerSettings> openersSettings) {
        return new ImageToSpimData().getSpimDataInstance(openersSettings);
    }

    /**
     * Create {@link SpimData} from one {@link OpenerSettings}
     * @param openerSetting
     * @return
     */
    public static AbstractSpimData getSpimData(OpenerSettings openerSetting) {
        ArrayList<OpenerSettings> singleOpenerList = new ArrayList<>();
        singleOpenerList.add(openerSetting);
        return ImageToSpimData.getSpimData(singleOpenerList);
    }

    /**
     * Create default {@link SpimData} for BioFormats opener from a list of Files
     * @param files
     * @return
     */
    public static AbstractSpimData getBioFormatsSpimData(List<File> files) {
        ArrayList<OpenerSettings> openerSettingsList = new ArrayList<>();
        for (File f : files) {
            openerSettingsList.add(getBioFormatsDefaultSettings(f.getAbsolutePath()));
        }
        return ImageToSpimData.getSpimData(openerSettingsList);
    }

    /**
     * Create default {@link SpimData} for OMERO opener from a list of Files
     * @param files
     * @return
     */
    public static AbstractSpimData getOmeroSpimData(List<File> files) {
        ArrayList<OpenerSettings> openerSettingsList = new ArrayList<>();
        for (File f : files) {
            openerSettingsList.add(getOmeroDefaultSettings(f.getAbsolutePath()));
        }
        return ImageToSpimData.getSpimData(openerSettingsList);
    }

    /**
     * Create default {@link OpenerSettings} for BioFormats opener from a list of Files
     * @param dataLocation
     * @return
     */
    public static OpenerSettings getBioFormatsDefaultSettings(String dataLocation) {
        return OpenerSettings.getDefaultSettings(OpenerSettings.OpenerType.BIOFORMATS, dataLocation);
    }

    /**
     * Create default {@link OpenerSettings} for OMERO opener from a list of Files
     * @param dataLocation
     * @return
     */
    public static OpenerSettings getOmeroDefaultSettings(String dataLocation) {
        return OpenerSettings.getDefaultSettings(OpenerSettings.OpenerType.OMERO, dataLocation);
    }

    static class OpenerChannel {

        public final int iOpener;
        public final int iChannel;

        public OpenerChannel(int iF, int iC) {
            iOpener = iF;
            iChannel = iC;
        }
    }

}
