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

package ch.epfl.biop.bdv.img.bioformats.command;

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Bio-Formats>Open File with Bio-Formats",
	description = "Support Bio-Formats multiresolution API. Set colors based " +
		"on bioformats metadata. Do not attempt auto contrast.")
public class BdvShowFileBioFormatsCommand
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
	@Parameter(required = false,
			label = "Image metadata location = ", choices = {"CENTER", "TOP LEFT"})
	String position_convention = "CENTER";

	public void run() {

		List<OpenerSettings> openerSettings = new ArrayList<>();
		int nSeries = BioFormatsHelper.getNSeries(file);
		for (int i = 0; i < nSeries; i++) {
			openerSettings.add(OpenerSettings.BioFormats()
							.location(file)
							.setSerie(i)
							.unit(unit)
							.splitRGBChannels(splitrgbchannels)
							.positionConvention(position_convention));
		}

		final AbstractSpimData spimData = OpenersToSpimData.getSpimData(openerSettings);
		BdvFunctions.show(spimData);
	}

}
