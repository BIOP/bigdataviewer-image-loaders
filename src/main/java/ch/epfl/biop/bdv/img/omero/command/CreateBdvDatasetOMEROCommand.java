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

package ch.epfl.biop.bdv.img.omero.command;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.lang.time.StopWatch;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("CanBeFinal")
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [OMERO]",
	description = "Bridge between OMERO and BigDataViewer. You can create a BDV dataset" +
			" from a set of OMERO URLs.")

public class CreateBdvDatasetOMEROCommand implements Command {

	final private static Logger logger = LoggerFactory.getLogger(
		CreateBdvDatasetOMEROCommand.class);

	@Parameter
	Context context;

	@Parameter(label = "Name of this dataset")
	public String datasetname = "dataset";

	@Parameter(label = "OMERO URLs", style = "text area")
	public String omero_urls;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData<?> spimdata;

	// Parameter for dataset creation
	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(required = false,
			label = "Plane Origin Convention", choices = {"CENTER", "TOP LEFT"})
	String plane_origin_convention = "CENTER";


	public void run() {
		try {
			List<OpenerSettings> openersSettings = new ArrayList<>();
			String[] omeroIDstrings = omero_urls.split(",");

			for (String s : omeroIDstrings) {
				logger.debug("Getting settings for omero url " + s);

				// create a new settings and modify it
				OpenerSettings settings =
						OpenerSettings.OMERO()
						.context(context)
						.location(s)
						.unit(unit)
						.positionConvention(plane_origin_convention);

				openersSettings.add(settings);
			}
			StopWatch watch = new StopWatch();
			logger.debug("All openers obtained, converting to spimdata object ");
			watch.start();
			spimdata = OpenersToSpimData.getSpimData(openersSettings);
			watch.stop();
			logger.debug("Converted to SpimData in " + (int) (watch.getTime() /
				1000) + " s");

		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
