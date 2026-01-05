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

package ch.epfl.biop.bdv.img.omero;

import ch.epfl.biop.bdv.img.omero.command.OmeroConnectCommand;
import net.imagej.omero.OMEROCredentials;
import net.imagej.omero.OMEROServer;
import net.imagej.omero.OMEROService;
import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.model.enums.UnitsLength;
import org.apache.commons.lang.StringUtils;
import org.scijava.Context;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

public class OmeroHelper {

	protected static final Logger logger = LoggerFactory.getLogger(OmeroHelper.class);

	/**
	 * Look into Fields of BioFormats UNITS class that matches the input string
	 * Return the corresponding Unit Field Case insensitive
	 *
	 * @param unit_string a string representation of a length unit
	 * @return corresponding BF Unit object
	 */
	public static UnitsLength getUnitsLengthFromString(String unit_string) {
		Field[] bfUnits = UnitsLength.class.getFields();
		for (Field f : bfUnits) {
			if (f.getType().equals(UnitsLength.class)) {
                try {
                    if (f.getName().equalsIgnoreCase(unit_string.trim())) {// (f.getName().toUpperCase().equals(unit_string.trim().toUpperCase()))
// {
// Field found
                        return (UnitsLength) f.get(null); // Field is assumed to be static
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
		}
		// Field not found
		return null;
	}


	public static IOMEROSession getGatewayAndSecurityContext(Context context, String host, long groupID) throws Exception {
		OMEROHost oh = getOMEROInfos(host);
		if (hasCachedSession(host)) {
			if (hasCachedSession(host, groupID)) {
				return getCachedOMEROSession(host, groupID);
			} if (groupID==-1) {
				// Request a session, whatever the groupID is
				OMEROHost infos = memoOmero.get(host);
				return cachedSession.get(infos.webHost).values().iterator().next(); // Just take one
			} else {
				// There's a cached session but of the wrong groupID
				OMEROHost infos = memoOmero.get(host);
				IOMEROSession session = cachedSession.get(infos.webHost).values().iterator().next(); // Just take one
				IOMEROSession modifiedSession = new DefaultOMEROSession(session.getGateway(), new SecurityContext(groupID));
				registerOMEROSession(modifiedSession, host);
				return getCachedOMEROSession(host, groupID);
			}
		}

		logger.info("The OMERO session for "+ oh.webHost+" needs to be initialized");
		CommandService command = context.getService(CommandService.class);
		boolean success = false;
		int iAttempt = 0;
		int nAttempts = 3;
		String lastErrorMessage = "";

		while ((iAttempt<nAttempts) && (!success)) {
			iAttempt++;
			Exception error;
			try {
				if (lastErrorMessage == null || lastErrorMessage.isEmpty()) {
					OmeroConnectCommand.message_in = "<html>Please enter your " + oh.webHost + " credentials:</html>";
				} else {
					OmeroConnectCommand.message_in = "<html>Error:"+lastErrorMessage+"<br> Please re-enter your " + oh.webHost + " credentials ("+iAttempt+"/"+nAttempts+"):</html>";
				}
				CommandModule module = command.run(OmeroConnectCommand.class, true,
						"host", oh.iceHost,
						"port", oh.port).get();
				success = (Boolean) module.getOutput("success");
				IOMEROSession omeroSession = (IOMEROSession) module.getOutput("omeroSession"); // Caching already happened
				if (success) return omeroSession;
				error = (Exception) module.getOutput("error");
				if (error!=null) {
					lastErrorMessage = error.getMessage();
				}
			} catch (Exception commandException) {
				error = commandException;
			}
			if ((!success) && (iAttempt == nAttempts) && (error!=null)) throw error;
		}

		throw  new RuntimeException("Could not create OMERO Session for host "+host);
	}

	// Host memoization
	static final Map<String, OMEROHost> memoOmero = new HashMap<>();

	static synchronized OMEROHost getOMEROInfos(String host) throws IOException { // host could be for ICE or not
		if (memoOmero.containsKey(host)) return memoOmero.get(host);

		OMEROHost oh = new OMEROHost();
		if (host.contains("-server")) {
			oh.webHost = host.replace("-server", "");
		} else {
			oh.webHost = host;
		}
		oh.iceHost = queryIceHost(oh.webHost);
		oh.port = queryIcePort(oh.webHost);
		memoOmero.put(host, oh);
		memoOmero.put(oh.webHost, oh);
		memoOmero.put(oh.iceHost, oh);
		return oh;
	}

	public synchronized static IOMEROSession getOMEROSession(String host,
															 int port,
															 String username,
															 char[] password,
															 Context ctx) throws Exception {
		IOMEROSession session;

		if (hasCachedSession(host)) {
			OMEROHost infos = memoOmero.get(host);
			session = cachedSession.get(infos.webHost).values().iterator().next(); // Just take one

			if (session.getGateway().isConnected()) {
				return session;
			} // Otherwise we need to recreate the session
		}

		assert ctx != null;
		OMEROService omeroService = ctx.getService(OMEROService.class);
		OMEROSession omeroSession = omeroService.session(new OMEROServer(host,port), new OMEROCredentials(username, new String(password)));
		session = new DefaultOMEROSession(omeroSession.getGateway(), omeroSession.getSecurityContext());

		logger.info("Session active : " + session.getGateway().isConnected());
		session.getSecurityContext().setServerInformation(new ServerInformation(host));
		registerOMEROSession(session, host);
		return session;
	}

	static Map<String, Map<Long,IOMEROSession>> cachedSession = new HashMap<>();

	public static synchronized void registerOMEROSession(IOMEROSession session, String host) {
		OMEROHost infos = memoOmero.get(host);
		if (!cachedSession.containsKey(infos.webHost)) {
			cachedSession.put(infos.webHost, new HashMap<>());
		}
		long groupID = session.getSecurityContext().getGroupID();
		cachedSession.get(infos.webHost).put(groupID, session);
	}

	public static void removeCachedSessions(String host) {
		OMEROHost infos = memoOmero.get(host);
		cachedSession.remove(infos.webHost);
	}

	public synchronized static boolean hasCachedSession(String host) {
		if (memoOmero.containsKey(host)) {
			OMEROHost infos = memoOmero.get(host);
			return cachedSession.containsKey(infos.webHost) && !cachedSession.get(infos.webHost).isEmpty();
		} else {
			return false;
		}
	}

	public synchronized static boolean hasCachedSession(String host, long groupID) {
		if (memoOmero.containsKey(host)) {
			OMEROHost infos = memoOmero.get(host);
			return cachedSession.containsKey(infos.webHost) && cachedSession.get(infos.webHost).containsKey(groupID);
		} else {
			return false;
		}
	}

	public synchronized static IOMEROSession getCachedOMEROSession(String host, long groupID) {
		OMEROHost infos = memoOmero.get(host);
		IOMEROSession session = cachedSession.get(infos.webHost).get(groupID);

		if (!session.getGateway().isConnected()) {
			System.out.println("A cached session is retrieved but has been disconnected!");
		}
		return session;
	}

	public synchronized static Collection<IOMEROSession> getCachedOMEROSessions(String host) {
		OMEROHost infos = memoOmero.get(host);
		return cachedSession.get(infos.webHost).values();
	}

	private static String queryIceHost(String host) throws IOException {
		String urlString = (host.startsWith("http") ? host : "https://" + host) + "/api/v0/servers/";
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(3000); // 3 seconds connection timeout
		conn.setReadTimeout(3000);    // 3 seconds read timeout

		if (conn.getResponseCode() != 200) {
			throw new IOException("HTTP " + conn.getResponseCode());
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
		JsonObject server = root.getAsJsonArray("data").get(0).getAsJsonObject();
		String retrievedHost = server.get("host").getAsString();
		if (retrievedHost.equals("localhost")) {
			return host;
		} else {
			return retrievedHost;
		}
	}

	private static int queryIcePort(String host) throws IOException {
		String urlString = (host.startsWith("http") ? host : "https://" + host) + "/api/v0/servers/";
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(1000); // 1 second connection timeout
		conn.setReadTimeout(1000);    // 1 second read timeout

		if (conn.getResponseCode() != 200) {
			throw new IOException("HTTP " + conn.getResponseCode());
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
		JsonObject server = root.getAsJsonArray("data").get(0).getAsJsonObject();
		return server.get("port").getAsInt();
	}

	public static Long getImageID(String omeroURL) throws MalformedURLException {
		URL url = new URL(omeroURL);
		String query = url.getQuery();

		// case single or multiple image(s) link, generated with the CREATE LINK BUTTON in OMERO.web
		// Single image example: https://hostname/webclient/?show=image-4738
		// Multiple images example: https://hostname/webclient/?show=image-4736|image-4737|image-4738
		if (query.contains("show=image-")) {
			List<Long> imageIDs = new ArrayList<>();
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

			if (imageIDs.size()>1) {
				throw new RuntimeException("Can't parse URL with multiple IDs "+omeroURL);
			} else {
				return imageIDs.get(0);
			}

			// case single or multiple dataset link, generated with the CREATE LINK BUTTON  in OMERO.web
			// Single dataset example: https://hostname/webclient/?show=dataset-604
			// Multiple datasets example: https://hostname/webclient/?show=dataset-604|dataset-603
		} else {
			throw new RuntimeException("Can't parse ImageID from URL "+omeroURL);
		}
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
	 *              - Single image opened with the "open with... iviewer" button, e.g:  {@code "https://hostname/iviewer/?images=4737&dataset=604"}
	 *              - Multiple images opened with the "open with... iviewer" button, e.g: {@code "https://hostname/iviewer/?images=4736,4737,4738,4739"}
	 *
	 * @param omeroURL OMERO dataset- or image- URL
	 * @param gateway OMERO gateway
	 * @param ctx OMERO security context
	 * @return list of image IDs extracted from the URL
	 * @throws ExecutionException if an error occurs during execution
	 * @throws DSOutOfServiceException if connection to OMERO fails
	 * @throws DSAccessException if access to data is denied
	 * @throws MalformedURLException if the URL is invalid
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
					Collection<ImageData> images = browse.getImagesForDatasets(ctx, Collections.singletonList(datasetID));
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
			Collection<ImageData> images = browse.getImagesForDatasets(ctx, Collections.singletonList(datasetID));
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

	static class OMEROHost {
		String webHost;
		String iceHost;
		int port;
	}

}
