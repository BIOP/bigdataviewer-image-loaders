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

package ch.epfl.biop.bdv.img.omero.io;

import ch.epfl.biop.bdv.img.ImageLoader;
import ch.epfl.biop.bdv.img.OmeroBdvOpener;
import ch.epfl.biop.bdv.img.Opener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import org.jdom2.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo(format = "spimreconstruction.biop_OmeroImageLoader-v1",
	type = ImageLoader.class)
public class XmlIoOmeroImgLoader implements
	XmlIoBasicImgLoader<ImageLoader>
{

	public static final String OPENER_CLASS_TAG = "opener_class";
	public static final String OPENER_TAG = "opener";
	public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
	public static final String CACHE_NUM_PRIORITIES = "num_priorities";
	public static final String DATASET_NUMBER_TAG = "dataset_number";

	Map<String, OmeroTools.GatewaySecurityContext> hostToGatewayCtx = new HashMap<>();

	@Override
	public Element toXml(ImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
			.getAnnotation(ImgLoaderIo.class).format());
		// For potential extensibility
		elem.addContent(XmlHelpers.textElement(OPENER_CLASS_TAG,
			OpenerSettings.class.getName()));
		/*elem.addContent(XmlHelpers.intElement(CACHE_NUM_FETCHER,
			imgLoader.numFetcherThreads));
		elem.addContent(XmlHelpers.intElement(CACHE_NUM_PRIORITIES,
			imgLoader.numPriorities));*/
		elem.addContent(XmlHelpers.intElement(DATASET_NUMBER_TAG, imgLoader.openers
			.size()));

		Gson gson = new Gson();
		for (int i = 0; i < imgLoader.openers.size(); i++) {
			// OpenerSettings serialization
			elem.addContent(XmlHelpers.textElement(OPENER_TAG + "_" + i, gson.toJson(
					imgLoader.getOpenerSettings().get(i))));
		}
		return elem;
	}

	@Override
	public ImageLoader fromXml(Element elem, File basePath,
		AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			final int number_of_datasets = XmlHelpers.getInt(elem,
				DATASET_NUMBER_TAG);
		/*	final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
			final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);*/

			List<Opener<?>> openers = new ArrayList<>();
			List<OpenerSettings> openersSettings = new ArrayList<>();
			String openerClassName = XmlHelpers.getText(elem, OPENER_CLASS_TAG);

			if (!openerClassName.equals(OpenerSettings.class.getName())) {
				throw new UnsupportedOperationException("Error class " +
					openerClassName + " not recognized.");
			}



			Gson gson = new Gson();
			for (int i = 0; i < number_of_datasets; i++) {
				// Opener de-serialization
				String jsonInString = XmlHelpers.getText(elem, OPENER_TAG + "_" + i);
				OpenerSettings settings = gson.fromJson(jsonInString,
						OpenerSettings.class);

				if (!hostToGatewayCtx.containsKey(settings.getHost())) {
					// TODO handle login to OMERO
					// Get credentials
					Boolean onlyCredentials = false;
					String[] credentials = OmeroTools.getOmeroConnectionInputParameters(
						onlyCredentials);
					int port = Integer.parseInt(credentials[0]);
					String host = credentials[1];
					String username = credentials[2];
					String password = credentials[3];
					credentials = new String[] {};

					// connect to OMERO
					Gateway gateway = OmeroTools.omeroConnect(host, port,
						username, password);
					SecurityContext ctx = OmeroTools.getSecurityContext(gateway);

					// add it in the channel hashmap
					OmeroTools.GatewaySecurityContext gtCtx =
						new OmeroTools.GatewaySecurityContext(host, port,
							gateway, ctx);
					hostToGatewayCtx.put(host, gtCtx);
					settings.setGateway(hostToGatewayCtx.get(host).gateway)
							.setContext(hostToGatewayCtx.get(host).ctx);
				}

				openersSettings.add(settings);
				openers.add(settings.omeroBuilder().create());
			}

			return new ImageLoader(openers, openersSettings, sequenceDescription);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
