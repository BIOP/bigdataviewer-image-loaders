/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.imageplus;

import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import ij.ImagePlus;
import ij.process.LUT;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ImagePlusToSpimData {

	static final private Logger logger = LoggerFactory.getLogger(
			ImagePlusToSpimData.class);

	// Function stolen and modified from bigdataviewer_fiji
	public static AbstractSpimData<?> getSpimData(ImagePlus imp)
			throws UnsupportedOperationException
	{
		// check the image type
		switch (imp.getType()) {
			case ImagePlus.GRAY8:
			case ImagePlus.GRAY16:
			case ImagePlus.GRAY32:
			case ImagePlus.COLOR_RGB:
				break;
			default:
				String message = "Error in image " + imp.getShortTitle() +
						": Only 8, 16, 32-bit images and RGB images are supported currently!";
				logger.error(message);
				throw new UnsupportedOperationException(message);
		}

		// get calibration and image size
		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;
		String punit = imp.getCalibration().getUnit();
		if (punit == null || punit.isEmpty()) punit = "px";
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions(punit, pw,
				ph, pd);
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getNSlices();
		final FinalDimensions size = new FinalDimensions(w, h, d);

		int originTimePoint = ImagePlusHelper.getTimeOriginFromImagePlus(imp);
		final BasicImgLoader imgLoader;
		{
			switch (imp.getType()) {
				case ImagePlus.GRAY8:
					imgLoader = ImagePlusImageLoader.createUnsignedByteInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.GRAY16:
					imgLoader = ImagePlusImageLoader.createUnsignedShortInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.GRAY32:
					imgLoader = ImagePlusImageLoader.createFloatInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.COLOR_RGB:
				default:
					imgLoader = ImagePlusImageLoader.createARGBInstance(imp,
							originTimePoint);
					break;
			}
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create setups from channels
		final HashMap<Integer, BasicViewSetup> setups = new HashMap<>(numSetups);
		for (int s = 0; s < numSetups; ++s) {
			final BasicViewSetup setup = new BasicViewSetup(s, String.format(imp
					.getTitle() + " channel %d", s + 1), size, voxelSize);
			setup.setAttribute(new Channel(s + 1));
			Displaysettings ds = new Displaysettings(s + 1);
			imp.setPositionWithoutUpdate(s+1,1,1);
			ds.min = imp.getDisplayRangeMin();
			ds.max = imp.getDisplayRangeMax();
			if (imp.getType() == ImagePlus.COLOR_RGB) {
				ds.isSet = false;
			}
			else {
				ds.isSet = true;
				LUT[] luts = imp.getLuts();
				LUT lut = luts.length>s ? luts[s]:luts[0];
				ds.color = new int[] { lut.getRed(255), lut.getGreen(255), lut.getBlue(
						255), lut.getAlpha(255) };
			}
			setup.setAttribute(ds);
			setups.put(s, setup);
		}

		// create timepoints
		final ArrayList<TimePoint> timepoints = new ArrayList<>(numTimepoints);

		MissingViews mv = null;

		if (originTimePoint > 0) {

			Set<ViewId> missingViewIds = new HashSet<>();
			for (int t = 0; t < originTimePoint; t++) {
				for (int s = 0; s < numSetups; ++s) {
					ViewId vId = new ViewId(t, s);
					missingViewIds.add(vId);
				}
			}
			mv = new MissingViews(missingViewIds);
		}

		for (int t = 0; t < numTimepoints + originTimePoint; ++t)
			timepoints.add(new TimePoint(t));
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal(
				new TimePoints(timepoints), setups, imgLoader, mv);

		// create ViewRegistrations from the images calibration
		final AffineTransform3D sourceTransform = ImagePlusHelper
				.getMatrixFromImagePlus(imp);
		final ArrayList<ViewRegistration> registrations = new ArrayList<>();
		for (int t = 0; t < numTimepoints + originTimePoint; ++t)
			for (int s = 0; s < numSetups; ++s)
				registrations.add(new ViewRegistration(t, s, sourceTransform));

		final File basePath = new File(".");

		return new SpimDataMinimal(basePath, seq,
				new ViewRegistrations(registrations));
	}

}
