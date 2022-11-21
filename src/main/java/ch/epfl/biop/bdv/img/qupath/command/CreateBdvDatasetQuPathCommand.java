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

package ch.epfl.biop.bdv.img.qupath.command;

import ch.epfl.biop.bdv.img.ImageToSpimData;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsBaseCommand;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.AbstractSpimData;
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
 * Warning : a QuPath project may have its source reordered and or removed : -
 * not all entries will be present in the qupath project Limitations : only
 * images
 */

@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [QuPath Bridge]")
public class CreateBdvDatasetQuPathCommand implements Command
		//CreateBdvDatasetBioFormatsBaseCommand
{

	private static final Logger logger = LoggerFactory.getLogger(
		CreateBdvDatasetQuPathCommand.class);

	@Parameter
	File quPathProject;

	@Parameter(
		label = "Dataset name (leave empty to name it like the QuPath project)",
		persist = false)
	public String datasetname = ""; // Cheat to allow dataset renaming

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimData;

	@Parameter(label = "Split RGB channels")
	boolean splitRGB = false;

	@Parameter(required = false,
			label = "Image metadata location = ", choices = {"CENTER", "TOP LEFT"})
	String position_convention = "CENTER"; // Split rgb channels to allow for best

	@Override
	public void run() {

		try {
			CreateBdvDatasetBioFormatsBaseCommand settings =
					new CreateBdvDatasetBioFormatsBaseCommand();

			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject.toURI()));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			List<OpenerSettings> openerSettingsList = new ArrayList<>();

			project.images.forEach(image -> {

				//image.indexInQuPathProject = project.images.indexOf(image); // TODO : put a normal index

				OpenerSettings openerSettings =
						settings.getSettings()
								.splitRGBChannels(splitRGB)
								.location(quPathProject.getAbsolutePath())
								.setSerie(image.entryID)//.indexInQuPathProject)
								.positionConvention(position_convention)
								.quPathBuilder();

				openerSettingsList.add(openerSettings);

			});

			spimData = ImageToSpimData.getSpimData(openerSettingsList);

			if (datasetname.equals("")) {
				datasetname = quPathProject.getParentFile().getName();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
