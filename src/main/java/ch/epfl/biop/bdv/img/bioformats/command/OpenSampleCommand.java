/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	//menuPath = "Plugins>BigDataViewer-Playground>Import>Dataset - Samples",
		menu = {
				@Menu(label = "Plugins"),
				@Menu(label = "BigDataViewer-Playground"),
				@Menu(label = "Import", weight = -8),
				@Menu(label = "Dataset - Samples", weight = 19)
		},
	//label = "Open sample datasets",
	description = "Opens a sample dataset from a selection of test images (downloads and caches on first use).")
public class OpenSampleCommand implements Command {

	@Parameter(persist = false)
	OpenSampleCommand.DemoDataset dataset_name;

	@Parameter
	CommandService cs;

	/*@Parameter(type = ItemIO.OUTPUT,
			label = "BDV Dataset",
			description = "The resulting BDV dataset.")
	AbstractSpimData<?> spimData;*/

	public void run() {
		try {
			switch (dataset_name) {
				case EGG_CHAMBER:
					File eggChamber = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/1472859/files/DrosophilaEggChamber.tif");
					// Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
					cs.run(DatasetFromBioFormatsCreateCommand.class,
							true,
							"datasetname", "Egg_Chamber",
							"unit", "MICROMETER",
							"files", new File[]{eggChamber},
							"split_rgb_channels", false,
							"plane_origin_convention", "CENTER",
							"auto_pyramidize", true,
							"disable_memo", false
					).get();
					break;

				case BRAIN_SLICES:
					String path = ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3);
					File wsiBrainSlices = new File(path,"Slide_03.vsi");
					System.out.println(wsiBrainSlices.getAbsolutePath());
					// Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
					cs.run(DatasetFromBioFormatsCreateCommand.class,
							true,
							"datasetname", "Slide_03",
							"unit", "MICROMETER",
							"files", new File[]{wsiBrainSlices},
							"split_rgb_channels", false,
							"plane_origin_convention", "TOP LEFT",
							"auto_pyramidize", true,
							"disable_memo", false
					).get();
					break;

				case LATTICE_HELA_SKEWED:
					File f = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi");
					cs.run(DatasetFromBioFormatsCreateCommand.class,
							true,
							"datasetname", "Hela Cells - LLS7",
							"unit", "MICROMETER",
							"files", new File[]{f},
							"split_rgb_channels", false,
							"plane_origin_convention", "CENTER",
							"auto_pyramidize", true,
							"disable_memo", false
					).get();
					break;
				case LATTICE_PSF:
					File psf = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-200nm.tif");
					cs.run(DatasetFromBioFormatsCreateCommand.class,
							true,
							"datasetname", "PSF - LLS7",
							"unit", "MICROMETER",
							"files", new File[]{psf},
							"split_rgb_channels", false,
							"plane_origin_convention", "CENTER",
							"auto_pyramidize", true,
							"disable_memo", false
					).get();
					break;
				case EUROPE:
					File europePyramidize = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/12738352/files/easterness_edtm_m_240m_s_20000101_20221231_eu_epsg.3035_v20240528.tif");
					// Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
					cs.run(DatasetFromBioFormatsCreateCommand.class,
							true,
							"datasetname", "Egg_Chamber",
							"unit", "MICROMETER",
							"files", new File[]{europePyramidize},
							"split_rgb_channels", false,
							"plane_origin_convention", "CENTER",
							"auto_pyramidize", true,
							"disable_memo", false
					).get();
					break;

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public enum DemoDataset {
		BRAIN_SLICES("Mouse Brain Sections (SXYC)"),
		EGG_CHAMBER("Fly Egg Chamber (XYZC)"),
		LATTICE_HELA_SKEWED("LLS7 Hela Cells (XYz'C) (Skewed)"),
		LATTICE_PSF("LLS7 Point Spread Function (XYz') (Skewed)"),
		EUROPE("Europe Height Map (XY)");

		final String name;

		DemoDataset(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
