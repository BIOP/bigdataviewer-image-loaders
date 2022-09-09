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

package ch.epfl.biop.bdv.img.omero.command;

import ch.epfl.biop.bdv.img.ImageToSpimData;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import omero.model.enums.UnitsLength;
import org.apache.commons.lang.time.StopWatch;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class,
	menuPath = "Plugins>BIOP>OMERO>Open [Omero Bdv Bridge]",
	description = "description")

public class OpenWithBigDataViewerOmeroBridgeCommand implements Command {

	final private static Logger logger = LoggerFactory.getLogger(
		OpenWithBigDataViewerOmeroBridgeCommand.class);

	@Parameter(label = "Name of this dataset")
	public String datasetname = "dataset";

	@Parameter(label = "OMERO IDs")
	public String omeroIDs;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimdata;

	@Parameter(label = "OMERO host")
	String host;

	@Parameter(label = "Enter your gaspar username")
	String username;

	@Parameter(label = "Enter your gaspar password", style = "password",
		persist = false)
	String password;

	static int port = 4064;

	// Parameter for dataset creation
	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	public UnitsLength unitsLength;

	public void run() {
		try {
			if (unit.equals("MILLIMETER")) {
				unitsLength = UnitsLength.MILLIMETER;
			}
			if (unit.equals("MICROMETER")) {
				unitsLength = UnitsLength.MICROMETER;
			}
			if (unit.equals("NANOMETER")) {
				unitsLength = UnitsLength.NANOMETER;
			}
			List<OpenerSettings> openersSettings = new ArrayList<>();
			String[] omeroIDstrings = omeroIDs.split(",");

			Gateway gateway = OmeroTools.omeroConnect(host, port, username, password);
			System.out.println("Session active : " + gateway.isConnected());

			SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
			ctx.setServerInformation(new ServerInformation(host));

			for (String s : omeroIDstrings) {
				int ID = Integer.parseInt(s.trim());
				logger.debug("Getting settings for omero ID " + ID);

				// create a new settings and modify it
				OpenerSettings settings = new OpenerSettings()
						.setImageID(ID)
						.unit(unit)
						.setGateway(gateway)
						.setContext(ctx)
						.omeroBuilder();

				openersSettings.add(settings);
			}
			StopWatch watch = new StopWatch();
			logger.debug("All openers obtained, converting to spimdata object ");
			watch.start();
			spimdata = ImageToSpimData.getSpimData(openersSettings);
			watch.stop();
			logger.debug("Converted to SpimData in " + (int) (watch.getTime() /
				1000) + " s");

		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * This main function serves for development purposes. It allows you to run
	 * the plugin immediately out of your integrated development environment
	 * (IDE).
	 *
	 * @param args whatever, it's ignored
	 * @throws Exception
	 */
	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		ij.command().run(OpenWithBigDataViewerOmeroBridgeCommand.class, true).get();

	}

}
