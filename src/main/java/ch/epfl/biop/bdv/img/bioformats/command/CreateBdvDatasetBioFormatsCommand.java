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

package ch.epfl.biop.bdv.img.bioformats.command;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import ch.epfl.biop.bdv.img.entity.ImageName;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ViewId;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import spimdata.SpimDataHelper;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [Bio-Formats]",
	description = "Creates a BDV dataset from one or more Bio-Formats compatible files.")
public class CreateBdvDatasetBioFormatsCommand implements
	Command
{

	@Parameter(label = "Dataset Name",
			description = "Name for the resulting BDV dataset.")
	public String datasetname = "dataset";

	@Parameter(required = false,
			label = "World coordinate units",
			description = "Unit for the coordinate system where images will be positioned.",
			choices = { "MILLIMETER", "MICROMETER", "NANOMETER", "BIGSTITCHER COMPATIBLE" })
	public String unit = "MILLIMETER";

	@Parameter(label = "Input Files",
			description = "The image files to include in the dataset.")
	File[] files;

	@Parameter(label = "Split RGB Channels",
			description = "When checked, splits RGB images into separate channels.")
	boolean split_rgb_channels = false;

	@Parameter(label = "Auto-Pyramidize",
			description = "Generates multi-resolution pyramids for large images without native multiresolution.")
	boolean auto_pyramidize = true;

	@Parameter(required = false,
			label = "Plane Origin Convention",
			description = "Defines where the image origin is located.",
			choices = {"CENTER", "TOP LEFT"})
	String plane_origin_convention = "CENTER";

	@Parameter(label = "Disable Memoization",
			description = "Disables Bio-Formats file caching (not recommended for large files).")
	boolean disable_memo = false;

	@Parameter(type = ItemIO.OUTPUT,
			label = "BDV Dataset",
			description = "The resulting BDV dataset.")
	AbstractSpimData<?> spimdata;

	@Parameter
	Context ctx;

	public void run() {

		List<OpenerSettings> openerSettings = new ArrayList<>();
		for (File f : files) {

			String bfOptions = "";

			if (FilenameUtils.getExtension(f.getAbsolutePath()).equals("czi") && unit.equals("BIGSTITCHER COMPATIBLE")) {
				bfOptions+=" --bfOptions zeissczi.autostitch=false ";
			}

			if (disable_memo) {
				bfOptions+=" --bfOptions " + OpenerSettings.BF_MEMO_KEY + "=false";
			}

			int nSeries = BioFormatsHelper.getNSeries(f, bfOptions );

			for (int i = 0; i < nSeries; i++) {
				openerSettings.add(
						OpenerSettings.BioFormats()
								.location(f)
								.setSerie(i)
								.unit(unit.equals("BIGSTITCHER COMPATIBLE")?"MICROMETER":unit)
								.splitRGBChannels(split_rgb_channels)
								.positionConvention(plane_origin_convention)
								.pyramidize(auto_pyramidize)
								.useBFMemo(!disable_memo)
								.addOptions(bfOptions)
								.context(ctx));
			}
		}
		spimdata = OpenersToSpimData.getSpimData(openerSettings);

		try {
			if (unit.equals("BIGSTITCHER COMPATIBLE")) {
				// We need to rescale the whole dataset in order to get a pixel size of 1
				double scalingForBigStitcher =
						1. / spimdata.getViewRegistrations().getViewRegistration(new ViewId(0, 0))
								.getModel().get(0, 0);

				// Scaling such as size of one pixel = 1
				SpimDataHelper.scale(spimdata, "BigStitcher Scaling", scalingForBigStitcher);

				// We also remove most of the extra tags
				SpimDataHelper.removeEntities(spimdata,
						Displaysettings.class,
						SeriesIndex.class,
						ImageName.class);
			}

		} catch (Exception e) {
			System.err.println("Could not scale dataset for Bigstitcher: "+e.getMessage());
			e.printStackTrace();
		}
	}

}
