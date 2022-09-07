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

package ch.epfl.biop.bdv.img.omero;

import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.SimpleLogger;
import omero.model.enums.UnitsLength;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class OmeroTools {

	/**
	 * OMERO connection with credentials and simpleLogger
	 * 
	 * @param hostname OMERO Host name
	 * @param port Port (Usually 4064)
	 * @param userName OMERO User
	 * @param password Password for OMERO User
	 * @return OMERO gateway (Gateway for simplifying access to an OMERO server)
	 * @throws Exception
	 */
	public static Gateway omeroConnect(String hostname, int port, String userName,
		String password) throws Exception
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
		datasetIds.add(new Long(DatasetID));
		return browse.getImagesForDatasets(ctx, datasetIds);
	}

	/**
	 * @param gateway OMERO gateway
	 * @return Security context hosting information required to access correct
	 *         connector
	 * @throws Exception
	 */
	public static SecurityContext getSecurityContext(Gateway gateway)
		throws Exception
	{
		ExperimenterData exp = gateway.getLoggedInUser();
		long groupID = exp.getGroupId();
		return new SecurityContext(groupID);
	}

	/**
	 * @param imageID ID of the OMERO image to access
	 * @param gateway OMERO gateway
	 * @param ctx OMERO Security context
	 * @return OMERO raw pixel data
	 * @throws Exception
	 */
	public static PixelsData getPixelsDataFromOmeroID(long imageID,
		Gateway gateway, SecurityContext ctx) throws Exception
	{

		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		ImageData image = browse.getImage(ctx, imageID);
		PixelsData pixels = image.getDefaultPixels();
		return pixels;

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
						if (f.getName().toUpperCase().equals(unit_string.trim()
								.toUpperCase()))
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

	public static String[] getOmeroConnectionInputParameters(
		boolean onlyCredentials)
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
		if (!onlyCredentials) {
			myPanel.add(new JLabel("host"));
			myPanel.add(host);
			myPanel.add(Box.createVerticalStrut(15)); // a spacer
			myPanel.add(new JLabel("port"));
			myPanel.add(port);
			myPanel.add(Box.createVerticalStrut(15)); // a spacer
		}

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
			if (!onlyCredentials) {
				omeroParameters.add(host.getText());
				omeroParameters.add(port.getValue().toString());
			}
			omeroParameters.add(username.getText());
			char[] chArray = jpf.getPassword();
			omeroParameters.add(new String(chArray));
			Arrays.fill(chArray, (char) 0);

			String[] omeroParametersArray = new String[omeroParameters.size()];
			return omeroParameters.toArray(omeroParametersArray);
		}
		return null;
	}

	public static class GatewaySecurityContext {

		public Gateway gateway;
		public SecurityContext ctx;
		public String host;
		public int port;

		public GatewaySecurityContext(String host, int port, Gateway gateway,
			SecurityContext ctx)
		{
			this.gateway = gateway;
			this.ctx = ctx;
			this.host = host;
			this.port = port;
		}
	}
}
