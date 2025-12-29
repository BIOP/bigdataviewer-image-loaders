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

package ch.epfl.biop.bdv.img.qupath.command;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("CanBeFinal")
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [QuPath]",
	description = "Creates a BDV dataset from all images in a QuPath project.")
public class CreateBdvDatasetQuPathCommand implements Command
{

	private static final Logger logger = LoggerFactory.getLogger(
		CreateBdvDatasetQuPathCommand.class);

	@Parameter(label = "QuPath Project",
			description = "The QuPath project file (project.qpproj) to import.")
	File qupath_project;

	@Parameter
	Context context;

	@Parameter(label = "Dataset Name",
			description = "Name for the dataset (leave empty to use the project folder name).",
			persist = false)
	public String datasetname = "";

	@Parameter(required = false,
			label = "World coordinate units",
			description = "Unit for the coordinate system where images will be positioned.",
			choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(type = ItemIO.OUTPUT,
			label = "BDV Dataset",
			description = "The resulting BDV dataset.")
	AbstractSpimData<?> spimData;

	@Parameter(label = "Split RGB Channels",
			description = "When checked, splits RGB images into separate channels.")
	boolean split_rgb_channels = false;

	@Parameter(required = false,
			label = "Plane Origin Convention",
			description = "Defines where the image origin is located.",
			choices = {"CENTER", "TOP LEFT"})
	String plane_origin_convention = "CENTER";

	@Override
	public void run() {

		try {
			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(qupath_project.toURI()));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			List<OpenerSettings> openerSettingsList = new ArrayList<>();

			project.images.forEach(image -> {

				OpenerSettings openerSettings =
						OpenerSettings.QuPath()
								.splitRGBChannels(split_rgb_channels)
								.location(qupath_project.getAbsolutePath())
								.setEntry(image.entryID)
								.unit(unit)
								.positionConvention(plane_origin_convention)
								.context(context)
								.quPathBuilder();

				openerSettingsList.add(openerSettings);

			});

			spimData = OpenersToSpimData.getSpimData(openerSettingsList);

			if (datasetname.isEmpty()) {
				datasetname = qupath_project.getParentFile().getName();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
