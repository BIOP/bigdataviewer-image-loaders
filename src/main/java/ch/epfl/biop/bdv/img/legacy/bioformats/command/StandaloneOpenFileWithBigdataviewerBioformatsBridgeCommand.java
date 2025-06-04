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

package ch.epfl.biop.bdv.img.legacy.bioformats.command;

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Deprecated
@SuppressWarnings({ "unused", "CanBeFinal" })
/*
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Bio-Formats>Open File with Bio-Formats",
	description = "Support bioformats multiresolution api. Attempts to set colors based " +
		"on bioformats metadata. Do not attempt auto contrast.")
*/
public class StandaloneOpenFileWithBigdataviewerBioformatsBridgeCommand
	implements Command
{

	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(label = "File to open", style = "open")
	File file;

	@Parameter(required = false,
		label = "Split RGB channels if you have 16 bits RGB images")
	boolean splitrgbchannels = true; // Split rgb channels to allow for best
																		// compatibility (RGB 16 bits)

	public void run() {

		BioformatsBigdataviewerBridgeDatasetCommand settings =
			new BioformatsBigdataviewerBridgeDatasetCommand();
		settings.splitrgbchannels = splitrgbchannels;
		settings.unit = unit;

		List<BioFormatsBdvOpener> openers = new ArrayList<>();
		openers.add(settings.getOpener(file));
		final AbstractSpimData<?> spimData = BioFormatsToSpimData.getSpimData(openers);
		BdvFunctions.show(spimData);
	}

}
