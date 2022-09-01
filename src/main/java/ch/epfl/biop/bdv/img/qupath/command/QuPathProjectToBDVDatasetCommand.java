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

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.img.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.img.qupath.QuPathToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Warning : a qupath project may have its source reordered and or removed : -
 * not all entries will be present in the qupath project Limitations : only
 * images
 */

@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Open [QuPath Project]")
public class QuPathProjectToBDVDatasetCommand extends
	BioformatsBigdataviewerBridgeDatasetCommand
{

	private static final Logger logger = LoggerFactory.getLogger(
		QuPathProjectToBDVDatasetCommand.class);

	@Parameter
	File quPathProject;

	@Parameter(
		label = "Dataset name (leave empty to name it like the QuPath project)",
		persist = false)
	public String datasetname = ""; // Cheat to allow dataset renaming

	@Parameter
	public boolean show = false;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimData;

	@Override
	public void run() {

		try {
			spimData = (new QuPathToSpimData()).getSpimDataInstance(quPathProject
				.toURI(), getGuiParams());
			if (datasetname.equals("")) {
				datasetname = quPathProject.getParentFile().getName();// FilenameUtils.removeExtension(FilenameUtils.getName(quPathProject.getAbsolutePath()))
																															// + ".xml";
			}

			if (show) BdvFunctions.show(spimData);

			// BdvFunctions.show(spimData);

			// Directly registers it to prevent memory leak...
			/*SourceAndConverterServices
			        .getSourceAndConverterService()
			        .register(spimData);
			SourceAndConverterServices
			        .getSourceAndConverterService()
			        .setSpimDataName(spimData, datasetname);*/

			// End of session
			// Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// fail
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Get from the interface all parameters given by the user
	 * 
	 * @return
	 */
	public GuiParams getGuiParams() {
		return (new GuiParams()).setUnit(this.unit).setCachesizex(this.cachesizex)
			.setCachesizey(this.cachesizey).setCachesizez(this.cachesizez)
			.setFlippositionx(this.flippositionx).setFlippositiony(this.flippositiony)
			.setFlippositionz(this.flippositionz).setNumberofblockskeptinmemory(
				this.numberofblockskeptinmemory).setPositioniscenter(
					this.positioniscenter).setPositionReferenceFrameLength(
						this.refframesizeinunitvoxsize).setSplitChannels(
							this.splitrgbchannels).setSwitchzandc(this.switchzandc)
			.setUsebioformatscacheblocksize(this.usebioformatscacheblocksize)
			.setVoxSizeReferenceFrameLength(this.refframesizeinunitvoxsize);
	}
}
