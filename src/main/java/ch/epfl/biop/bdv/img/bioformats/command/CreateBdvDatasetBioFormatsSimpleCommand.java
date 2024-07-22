/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2024 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset from file",
	description = "Bridge between Bio-Formats (BioFormats) and BigDataViewer. You can create a BDV dataset" +
			" from a Bio-Formats supported file.")
public class CreateBdvDatasetBioFormatsSimpleCommand implements
	Command
{

	@Parameter(visibility = ItemVisibility.MESSAGE)
	public String datasetname = "";

	@Parameter(label = "File", callback = "setDatasetName")
	File file;

	boolean auto_pyramidize = true;
	public String unit = "MILLIMETER";
	String plane_origin_convention = "TOP LEFT";

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData<?> spimdata;

	@Parameter
	Context ctx;

	public void run() {
		List<OpenerSettings> openerSettings = new ArrayList<>();
		int nSeries = BioFormatsHelper.getNSeries(file);
		for (int i = 0; i < nSeries; i++) {
			openerSettings.add(
					OpenerSettings.BioFormats()
							.location(file)
							.setSerie(i)
							.unit(unit)
							.splitRGBChannels(FilenameUtils.getExtension(file.getAbsolutePath()).equals("czi")) // splits only for czi file
							.positionConvention(plane_origin_convention)
							.pyramidize(auto_pyramidize)
							.context(ctx));
		}

		if ((datasetname!=null)&&(datasetname.trim().isEmpty())) {
			if (file.getAbsolutePath().toUpperCase().endsWith(".OME.TIFF") || file.getAbsolutePath().toUpperCase().endsWith(".OME.TIF")) {
				datasetname = FilenameUtils.getName(file.getAbsolutePath()); // Removes tif
				datasetname = FilenameUtils.getName(datasetname); // Removes ome
			} else {
				datasetname = FilenameUtils.getName(file.getAbsolutePath());
			}
		}

		spimdata = OpenersToSpimData.getSpimData(openerSettings);
	}

	void setDatasetName() {
		if (file == null) return;
		if (file.getAbsolutePath().toUpperCase().endsWith(".OME.TIFF") || file.getAbsolutePath().toUpperCase().endsWith(".OME.TIF")) {
			datasetname = FilenameUtils.getBaseName(file.getAbsolutePath()); // Removes tif
			datasetname = FilenameUtils.getBaseName(datasetname); // Removes ome
		} else {
			datasetname = FilenameUtils.getBaseName(file.getAbsolutePath());
		}
	}

}
