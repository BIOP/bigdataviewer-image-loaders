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

package ch.epfl.biop.bdv.img.bioformats.command;

import ch.epfl.biop.bdv.img.ImageToSpimData;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.lang.time.StopWatch;
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

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Open [BioFormats Bdv Bridge (Basic)]",
	description = "Support bioformats multiresolution API. Attempts to set colors based " +
		"on bioformats metadata. Do not attempt auto contrast.")
public class BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand implements
	Command
{

	final private static Logger logger = LoggerFactory.getLogger(
			BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class);

	@Parameter(label = "Name of this dataset")
	public String datasetname = "dataset";

	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(label = "Dataset files")
	File[] files;

	@Parameter(label = "Split RGB channels")
	boolean splitrgbchannels;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimdata;

	@Parameter
	Context ctx;

	public void run() {
		List<OpenerSettings> openerSettings = new ArrayList<>();

		BioformatsBigdataviewerBridgeDatasetCommand settings =
			new BioformatsBigdataviewerBridgeDatasetCommand();
		settings.splitrgbchannels = splitrgbchannels;
		settings.unit = unit;

		// map file reader
		for (File f : files) {
			for (int i = 0; i< BioFormatsTools.getNSeries(f); i++) {
				openerSettings.add(settings.getSettings(f).setSerie(i).cornerPositionConvention().context(ctx));
			}
		}

		StopWatch watch = new StopWatch();
		logger.debug("All openers obtained, converting to spimdata object ");
		watch.start();
		spimdata = ImageToSpimData.getSpimData(openerSettings);
		// close readers for map
		watch.stop();
		logger.debug("Converted to SpimData in " + (int) (watch.getTime() / 1000) + " s");
	}

}
