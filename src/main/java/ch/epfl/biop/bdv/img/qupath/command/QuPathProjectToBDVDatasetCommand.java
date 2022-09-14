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
import ch.epfl.biop.bdv.img.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
//import ch.epfl.biop.bdv.img.qupath.QuPathToSpimData;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.AbstractSpimData;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Warning : a QuPath project may have its source reordered and or removed : -
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

	Map<String, OmeroTools.GatewaySecurityContext> hostToGatewayCtx =
			new HashMap<>();

	@Parameter
	File quPathProject;

	@Parameter(
		label = "Dataset name (leave empty to name it like the QuPath project)",
		persist = false)
	public String datasetname = ""; // Cheat to allow dataset renaming

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimData;

	@Override
	public void run() {

		try {
			BioformatsBigdataviewerBridgeDatasetCommand settings =
					new BioformatsBigdataviewerBridgeDatasetCommand();

			// Deserialize the QuPath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject.toURI()));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			List<OpenerSettings> openerSettingsList = new ArrayList<>();

			project.images.forEach(image -> {

				image.indexInQuPathProject = project.images.indexOf(image);

				OpenerSettings openerSettings = settings.getSettings(image.serverBuilder.uri.toString())
						.setQpImage(image)
						.setQpProject(project.uri)
						.quPathBuilder();

				try {
					if (image.serverBuilder.providerClassName.equals(
							"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
						if (!hostToGatewayCtx.containsKey(image.serverBuilder.providerClassName)) {
							// ask for user credentials
							Boolean onlyCredentials = false;
							String[] credentials = OmeroTools.getOmeroConnectionInputParameters(
									onlyCredentials);
							String host = credentials[0];
							int port = Integer.parseInt(credentials[1]);
							String username = credentials[2];
							String password = credentials[3];
							credentials = new String[]{};

							// connect to omero
							Gateway gateway = OmeroTools.omeroConnect(host, port, username, password);
							SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
							ctx.setServerInformation(new ServerInformation(host));

							// add it in the channel hashmap
							OmeroTools.GatewaySecurityContext gtCtx =
									new OmeroTools.GatewaySecurityContext(host, port, gateway, ctx);
							hostToGatewayCtx.put(image.serverBuilder.providerClassName, gtCtx);
						}

						OmeroTools.GatewaySecurityContext gtCtx = hostToGatewayCtx.get(image.serverBuilder.providerClassName);
						// get omero image ID
						String[] imageString = image.serverBuilder.uri.toString().split("%3D");
						String[] omeroId = imageString[1].split("-");

						// populate the openerSettings
						openerSettings
								.setGateway(gtCtx.gateway)
								.setContext(gtCtx.ctx)
								.setImageID(Long.parseLong(omeroId[1]));
					}
				}catch (Exception e) {
					throw new RuntimeException(e);
				}

				int iSerie = image.serverBuilder.args.indexOf("--series");
				int serie = 0;
				if (iSerie > -1) {
					serie = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
				}

				openerSettings
						.setSerie(serie)
						//.cornerPositionConvention()
						;
				openerSettingsList.add(openerSettings);
			});

			spimData = ImageToSpimData.getSpimData(openerSettingsList);

			if (datasetname.equals("")) {
				datasetname = quPathProject.getParentFile().getName();// FilenameUtils.removeExtension(FilenameUtils.getName(quPathProject.getAbsolutePath()))
				// + ".xml";
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
