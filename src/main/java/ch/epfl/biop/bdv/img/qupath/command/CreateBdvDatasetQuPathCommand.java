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

/**
 */

@SuppressWarnings("CanBeFinal")
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [QuPath]")
public class CreateBdvDatasetQuPathCommand implements Command
{

	private static final Logger logger = LoggerFactory.getLogger(
		CreateBdvDatasetQuPathCommand.class);

	@Parameter
	File quPathProject;

	@Parameter
	Context context;

	@Parameter(
		label = "Dataset name (leave empty to name it like the QuPath project)",
		persist = false)
	public String datasetname = ""; // Cheat to allow dataset renaming

	@Parameter(required = false, label = "Physical units of the dataset",
			choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData<?> spimData;

	@Parameter(label = "Split RGB channels")
	boolean split_rgb_channels = false;

	@Parameter(required = false,
			label = "Plane Origin Convention", choices = {"CENTER", "TOP LEFT"})
	String plane_origin_convention = "CENTER";

	@Override
	public void run() {

		try {
			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject.toURI()));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			List<OpenerSettings> openerSettingsList = new ArrayList<>();

			project.images.forEach(image -> {

				OpenerSettings openerSettings =
						OpenerSettings.QuPath()
								.splitRGBChannels(split_rgb_channels)
								.location(quPathProject.getAbsolutePath())
								.setEntry(image.entryID)
								.unit(unit)
								.positionConvention(plane_origin_convention)
								.context(context)
								.quPathBuilder();

				openerSettingsList.add(openerSettings);

			});

			spimData = OpenersToSpimData.getSpimData(openerSettingsList);

			if (datasetname.equals("")) {
				datasetname = quPathProject.getParentFile().getName();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
