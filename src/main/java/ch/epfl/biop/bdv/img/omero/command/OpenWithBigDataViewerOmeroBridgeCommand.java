/*-
 * #%L
 * A nice project implementing an OMERO connection with ImageJ
 * %%
 * Copyright (C) 2021 EPFL
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

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.img.omero.OmeroBdvOpener;
import ch.epfl.biop.bdv.img.omero.OmeroToSpimData;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
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
			List<OmeroBdvOpener> openers = new ArrayList<>();
			String[] omeroIDstrings = omeroIDs.split(",");
			Gateway gateway = OmeroTools.omeroConnect(host, port, username, password);
			System.out.println("Session active : " + gateway.isConnected());
			SecurityContext ctx = OmeroTools.getSecurityContext(gateway);

			for (String s : omeroIDstrings) {
				int ID = Integer.valueOf(s.trim());
				logger.debug("Getting opener for omero ID " + ID);

				// create a new opener and modify it
				OmeroBdvOpener opener = new OmeroBdvOpener().imageID(ID).host(host)
					.gateway(gateway).securityContext(ctx).unit(unitsLength)
					.ignoreMetadata().create();

				openers.add(opener);
			}
			StopWatch watch = new StopWatch();
			logger.debug("All openers obtained, converting to spimdata object ");
			watch.start();
			spimdata = OmeroToSpimData.getSpimData(openers);
			watch.stop();
			logger.debug("Converted to SpimData in " + (int) (watch.getTime() /
				1000) + " s");

		}
		catch (Exception e) {
			e.printStackTrace();
		}
		catch (Throwable throwable) {
			throwable.printStackTrace();
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
