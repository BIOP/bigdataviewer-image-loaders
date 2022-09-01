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

package ch.epfl.biop.bdv.img.qupath.io;

import ch.epfl.biop.bdv.img.omero.OmeroTools;
import ch.epfl.biop.bdv.img.qupath.QuPathImageLoader;
import ch.epfl.biop.bdv.img.qupath.QuPathImageOpener;
import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import org.jdom2.Element;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo(format = "spimreconstruction.biop_qupathimageloader_v2",
	type = QuPathImageLoader.class)
public class XmlIoQuPathImgLoader implements
	XmlIoBasicImgLoader<QuPathImageLoader>
{

	public static final String OPENER_CLASS_TAG = "opener_class";
	public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
	public static final String CACHE_NUM_PRIORITIES = "num_priorities";
	public static final String QUPATH_PROJECT_TAG = "qupath_project";
	public static final String OPENER_MODEL_TAG = "opener_model";
	public static final String DATASET_NUMBER_TAG = "dataset_number";
	Map<String, OmeroTools.GatewaySecurityContext> hostToGatewayCtx =
		new HashMap<>();

	/**
	 * Write QuPathImageOpener class in a xml file
	 * 
	 * @param imgLoader
	 * @param basePath
	 * @return
	 */
	@Override
	public Element toXml(QuPathImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
			.getAnnotation(ImgLoaderIo.class).format());
		// For potential extensibility
		elem.addContent(XmlHelpers.intElement(CACHE_NUM_FETCHER,
			imgLoader.numFetcherThreads));
		elem.addContent(XmlHelpers.intElement(CACHE_NUM_PRIORITIES,
			imgLoader.numPriorities));
		elem.addContent(XmlHelpers.textElement(QUPATH_PROJECT_TAG, (new Gson())
			.toJson(imgLoader.getProjectURI(), URI.class)));
		elem.addContent(XmlHelpers.textElement(OPENER_CLASS_TAG,
			QuPathImageOpener.class.getName()));
		elem.addContent(XmlHelpers.intElement(DATASET_NUMBER_TAG, imgLoader
			.getModelOpener().size()));

		// write each opener separately
		Gson gson = new Gson();
		for (int i = 0; i < imgLoader.getModelOpener().size(); i++) {
			// Opener serialization
			elem.addContent(XmlHelpers.textElement(OPENER_MODEL_TAG + "_" + i, gson
				.toJson(imgLoader.getModelOpener().get(i))));
		}

		return elem;
	}

	/**
	 * Read the xml file, fill QuPathImageOpener class, create each openers and
	 * write the corresponding QuPathImageLoader
	 * 
	 * @param elem
	 * @param basePath
	 * @param sequenceDescription
	 * @return
	 */
	@Override
	public QuPathImageLoader fromXml(Element elem, File basePath,
		AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			final int number_of_datasets = XmlHelpers.getInt(elem,
				DATASET_NUMBER_TAG);
			final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
			final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);

			List<QuPathImageOpener> openers = new ArrayList<>();
			String openerClassName = XmlHelpers.getText(elem, OPENER_CLASS_TAG);

			if (!openerClassName.equals(QuPathImageOpener.class.getName())) {
				throw new UnsupportedOperationException("Error class " +
					openerClassName + " not recognized.");
			}

			Gson gson = new Gson();
			for (int i = 0; i < number_of_datasets; i++) {
				// Opener de-serialization
				String jsonInString = XmlHelpers.getText(elem, OPENER_MODEL_TAG + "_" +
					i);
				QuPathImageOpener opener = (gson.fromJson(jsonInString,
					QuPathImageOpener.class));

				// check for omero opener and ask credentials if necessary
				if (opener.getImage().serverBuilder.providerClassName.equals(
					"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder"))
				{
					if (!hostToGatewayCtx.containsKey(opener
						.getImage().serverBuilder.providerClassName))
					{
						// ask for user credentials
						Boolean onlyCredentials = true;
						String[] credentials = OmeroTools.getOmeroConnectionInputParameters(
							onlyCredentials);
						String username = credentials[0];
						String password = credentials[1];
						credentials = new String[] {};

						// connect to omero
						Gateway gateway = OmeroTools.omeroConnect(opener.getHost(), opener
							.getPort(), username, password);
						SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
						ctx.setServerInformation(new ServerInformation(opener.getHost()));

						// add it in the channel hashmap
						OmeroTools.GatewaySecurityContext gtCtx =
							new OmeroTools.GatewaySecurityContext(opener.getHost(), opener
								.getPort(), gateway, ctx);
						hostToGatewayCtx.put(opener
							.getImage().serverBuilder.providerClassName, gtCtx);
					}

					// create omero image opener
					OmeroTools.GatewaySecurityContext gtCtx = hostToGatewayCtx.get(opener
						.getImage().serverBuilder.providerClassName);
					opener.create(gtCtx.host, gtCtx.port, gtCtx.gateway, gtCtx.ctx)
						.loadMetadata();

					// create bioformats opener
				}
				else opener.create("", -1, null, null).loadMetadata();

				openers.add(opener);
			}

			String qupathProjectUri = XmlHelpers.getText(elem, QUPATH_PROJECT_TAG);
			URI qpProjURI = (new Gson()).fromJson(qupathProjectUri, URI.class);

			// disconnect all gateway
			hostToGatewayCtx.values().forEach(e -> e.gateway.disconnect());

			return new QuPathImageLoader(qpProjURI, openers, sequenceDescription,
				numFetcherThreads, numPriorities);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
