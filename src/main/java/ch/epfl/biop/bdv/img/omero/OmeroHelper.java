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

package ch.epfl.biop.bdv.img.omero;

import ch.epfl.biop.bdv.img.omero.command.OmeroConnectCommand;
import net.imagej.omero.OMEROServer;
import net.imagej.omero.OMEROService;
import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.log.SimpleLogger;
import omero.model.enums.UnitsLength;
import org.apache.commons.lang.StringUtils;
import org.scijava.Context;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OmeroHelper {

	/**
	 * OMERO connection with credentials and simpleLogger
	 * 
	 * @param hostname OMERO Host name
	 * @param port Port (Usually 4064)
	 * @param userName OMERO User
	 * @param password Password for OMERO User
	 * @return OMERO gateway (Gateway for simplifying access to an OMERO server)
	 * @throws DSOutOfServiceException
	 */
	public static Gateway omeroConnect(String hostname, int port, String userName,
		String password) throws DSOutOfServiceException
	{
		// Omero Connect with credentials and simpleLogger
		LoginCredentials cred = new LoginCredentials();
		cred.getServer().setHost(hostname);
		cred.getServer().setPort(port);
		cred.getUser().setUsername(userName);
		cred.getUser().setPassword(password);
		SimpleLogger simpleLogger = new SimpleLogger();
		Gateway gateway = new Gateway(simpleLogger);
		gateway.connect(cred);
		return gateway;
	}

	public static Collection<ImageData> getImagesFromDataset(Gateway gateway,
		long DatasetID) throws Exception
	{
		// List all images contained in a Dataset
		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		SecurityContext ctx = getSecurityContext(gateway);
		Collection<Long> datasetIds = new ArrayList<>();
		datasetIds.add(DatasetID);
		return browse.getImagesForDatasets(ctx, datasetIds);
	}

	/**
	 * @param gateway OMERO gateway
	 * @return Security context hosting information required to access correct
	 *         connector
	 */
	public static SecurityContext getSecurityContext(Gateway gateway)
	{
		ExperimenterData exp = gateway.getLoggedInUser();
		long groupID = exp.getGroupId();
		return new SecurityContext(groupID);
	}


	/**
	 * Look into Fields of BioFormats UNITS class that matches the input string
	 * Return the corresponding Unit Field Case insensitive
	 *
	 * @param unit_string
	 * @return corresponding BF Unit object
	 */
	public static UnitsLength getUnitsLengthFromString(String unit_string) {
		Field[] bfUnits = UnitsLength.class.getFields();
		for (Field f : bfUnits) {
			if (f.getType().equals(UnitsLength.class)) {
				if (f.getName() != null) {
					try {
						if (f.getName().equalsIgnoreCase(unit_string.trim()))
						{// (f.getName().toUpperCase().equals(unit_string.trim().toUpperCase()))
							// {
							// Field found
							return (UnitsLength) f.get(null); // Field is assumed to be static
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		// Field not found
		return null;
	}

	/**
	 * Minimal Graphical interface that catches user's name and password. Host and port are set by default
	 * but can be changed if necessary.
	 * @return connection information (host, port, username, password)
	 */
	public static String[] getOmeroConnectionInputParameters()
	{

		// build the gui
		JTextField host = new JTextField("omero-server.epfl.ch", 20);
		JSpinner port = new JSpinner();
		port.setValue(4064);
		JTextField username = new JTextField(50);
		JPasswordField jpf = new JPasswordField(24);

		// build the main window
		JPanel myPanel = new JPanel();
		myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
		myPanel.add(new JLabel("host"));
		myPanel.add(host);
		myPanel.add(Box.createVerticalStrut(15)); // a spacer
		myPanel.add(new JLabel("port"));
		myPanel.add(port);
		myPanel.add(Box.createVerticalStrut(15)); // a spacer
		myPanel.add(new JLabel("Username"));
		myPanel.add(username);
		myPanel.add(Box.createVerticalStrut(15)); // a spacer
		myPanel.add(new JLabel("Password"));
		myPanel.add(jpf);

		// get results
		int result = JOptionPane.showConfirmDialog(null, myPanel,
			"Please enter OMERO connection input parameters",
			JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {
			ArrayList<String> omeroParameters = new ArrayList<>();
			omeroParameters.add(host.getText());
			omeroParameters.add(port.getValue().toString());
			omeroParameters.add(username.getText());
			char[] chArray = jpf.getPassword();
			omeroParameters.add(new String(chArray));
			Arrays.fill(chArray, (char) 0);

			String[] omeroParametersArray = new String[omeroParameters.size()];
			return omeroParameters.toArray(omeroParametersArray);
		}
		return null;
	}

	public static OMEROSession getGatewayAndSecurityContext(Context context, String host) throws DSOutOfServiceException {
		OMEROService omeroService = context.getService(OMEROService.class);

		OMEROServer server = new OMEROServer(host, 4064);

		OMEROSession omeroSession;
		try {
			omeroSession = omeroService.session(server);
		} catch (Exception e) {
			System.err.println("The OMERO session for "+host+" needs to be initialized");
			CommandService command = context.getService(CommandService.class);
			boolean success = false;
			DSOutOfServiceException error = null;
			try {
				OmeroConnectCommand.message_in = "Please enter your "+host+" credentials:";
				CommandModule module = command.run(OmeroConnectCommand.class, true, "host", host).get();
				success = (Boolean) module.getOutput("success");
				omeroSession = (OMEROSession) module.getOutput("omeroSession");
				error = (DSOutOfServiceException) module.getOutput("error");
			} catch (Exception commandException) {
				commandException.printStackTrace();
				omeroSession = null;
			}
			if ((!success)&&(error!=null)) throw error;
		}
		return omeroSession;
	}

	/**
	 * Set all OMERO image IDs from an OMERO dataset- or image- URL
	 * Supported URLs include:
	 *      - URLs generated from the "create link" button from OMERO.web's mainpage:
	 *              - Single image, e.g:  {@code "https://hostname/webclient/?show=image-4738"}
	 *              - Multiple images, e.g:  {@code "https://hostname/webclient/?show=image-4736|image-4737|image-4738"}
	 *              - Single dataset, e.g:  {@code "https://hostname/webclient/?show=dataset-604"}
	 *              - Multiple datasets, e.g:  {@code "https://hostname/webclient/?show=dataset-604|dataset-603"}
	 *      - URLs pasted from the OMERO.iviewer
	 *              - Single image opened with a double clic on a thumbnail, e.g:  {@code "https://hostname/webclient/img_detail/4735/?dataset=604"}
	 *              - Single image opened with the "open with.. iviewer" button, e.g:  {@code "https://hostname/iviewer/?images=4737&dataset=604"}
	 *              - Multiple images opened with the "open with.. iviewer" button, e.g: {@code "https://hostname/iviewer/?images=4736,4737,4738,4739"}
	 *
	 * @param omeroURL OMERO dataset- or image- URL
	 * @param gateway OMERO gateway
	 * @param ctx OMERO security context
	 * @throws ExecutionException
	 * @throws DSOutOfServiceException
	 * @throws DSAccessException
	 * @throws MalformedURLException
	 *
	 */
	public static List<Long> getImageIDs(String omeroURL, Gateway gateway, SecurityContext ctx) throws ExecutionException, DSOutOfServiceException, DSAccessException, MalformedURLException {
		List<Long> imageIDs = new ArrayList<>();
		URL url = new URL(omeroURL);
		String query = url.getQuery();

		// case single or multiple image(s) link, generated with the CREATE LINK BUTTON in OMERO.web
		// Single image example: https://hostname/webclient/?show=image-4738
		// Multiple images example: https://hostname/webclient/?show=image-4736|image-4737|image-4738
		if (query.contains("show=image-")) {
			String[] parts = query.split("-");
			// deal with links created while multiple images are selected
			for (int i = 1; i < parts.length; i++) {
				if (parts[i].contains("image")) {
					String part = parts[i];
					String[] subParts = part.split("\\|image");
					imageIDs.add(Long.valueOf(subParts[0]));
				}
			}
			imageIDs.add(Long.valueOf(parts[parts.length - 1]));

			// case single or multiple dataset link, generated with the CREATE LINK BUTTON  in OMERO.web
			// Single dataset example: https://hostname/webclient/?show=dataset-604
			// Multiple datasets example: https://hostname/webclient/?show=dataset-604|dataset-603
		} else if(query.contains("show=dataset-")){
			String[] parts = query.split("-");
			for(int i = 1; i<parts.length; i++) {
				if (parts[i].contains("dataset")) {
					String[] subParts = parts[i].split("\\|dataset");
					long datasetID = Long.parseLong(subParts[0]);
					BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
					Collection<ImageData> images = browse.getImagesForDatasets(ctx, Arrays.asList(datasetID));
					Iterator<ImageData> j = images.iterator();
					ImageData image;
					while (j.hasNext()) {
						image = j.next();
						imageIDs.add(image.getId());
					}
				}
			}
			long datasetID = Long.parseLong(parts[parts.length-1]);
			BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
			Collection<ImageData> images = browse.getImagesForDatasets(ctx, Arrays.asList(datasetID));
			Iterator<ImageData> j = images.iterator();
			ImageData image;
			while (j.hasNext()) {
				image = j.next();
				imageIDs.add(image.getId());
			}

			// case single image link, pasted from iviewer (iviewer opened with a double clic on a thumbnail)
			// Example: https://hostname/webclient/img_detail/4735/?dataset=604
		} else if (omeroURL.contains("img_detail")) {
			String[] parts = url.getFile().split("/");
			int index = findIndexOfStringInStringArray(parts,"img_detail")+1;
			imageIDs.add(Long.valueOf(parts[index]));

			// case single or multiple image(s) link, pasted from iviewer (iviewer opened with the "open with" option)
			// Single image example: https://hostname/iviewer/?images=4737&dataset=604
			// Multiple images example: https://hostname/iviewer/?images=4736,4737,4738,4739
		} else if (query.contains("images=")) {
			if (query.contains(",")){
				//multiple images link
				String[] parts = query.split(",");
				imageIDs.add(Long.valueOf(parts[0].substring(parts[0].indexOf("=")+1)));
				for(int i = 1; i<parts.length; i++){
					imageIDs.add(Long.valueOf(parts[i]));
				}
			} else {
				//simple image link
				String[] parts = query.split("&");
				imageIDs.add(Long.valueOf(parts[0].substring(parts[0].indexOf("=")+1)));
			}
		} else {
			System.err.println("Can't parse OMERO URL "+omeroURL);
		}
		return imageIDs;
	}

	public static int findIndexOfStringInStringArray(String[] array, String pattern){
		int idx = 0;
		for(String content : array){
			if(StringUtils.contains(content, pattern)){
				return idx;
			}
			++idx;
		}
		return -1;
	}

}
