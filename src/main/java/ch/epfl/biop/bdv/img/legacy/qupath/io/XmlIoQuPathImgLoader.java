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

package ch.epfl.biop.bdv.img.legacy.qupath.io;

import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.qupath.QuPathImageLoader;
import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;

import java.io.File;
import java.net.URI;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@Deprecated
@ImgLoaderIo(format = "spimreconstruction.biop_qupathimageloader",
	type = QuPathImageLoader.class)
public class XmlIoQuPathImgLoader implements
	XmlIoBasicImgLoader<QuPathImageLoader>
{

	public static final String OPENER_CLASS_TAG = "opener_class";
	public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
	public static final String CACHE_NUM_PRIORITIES = "num_priorities";
	public static final String QUPATH_PROJECT_TAG = "qupath_project";
	public static final String OPENER_MODEL_TAG = "opener_model";

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
			BioFormatsBdvOpener.class.getName()));
		elem.addContent(XmlHelpers.textElement(OPENER_MODEL_TAG, (new Gson())
			.toJson(imgLoader.getModelOpener())));
		return elem;
	}

	@Override
	public QuPathImageLoader fromXml(Element elem, File basePath,
		AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
			final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);

			String openerClassName = XmlHelpers.getText(elem, OPENER_CLASS_TAG);
			if (openerClassName.equals(
				"ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener"))
			{
				openerClassName = BioFormatsBdvOpener.class.getName(); // Fix for old
																																// versions
			}

			if (!openerClassName.equals(BioFormatsBdvOpener.class.getName())) {
				throw new UnsupportedOperationException("Error class " +
					openerClassName + " not recognized.");
			}

			Gson gson = new Gson();
			String jsonInString = XmlHelpers.getText(elem, OPENER_MODEL_TAG);
			BioFormatsBdvOpener modelOpener = gson.fromJson(jsonInString,
				BioFormatsBdvOpener.class);

			String qupathProjectUri = XmlHelpers.getText(elem, QUPATH_PROJECT_TAG);// ,
																																							// Paths.get(imgLoader.getProjectURI()).toString());

			URI qpProjURI = (new Gson()).fromJson(qupathProjectUri, URI.class);

			return new QuPathImageLoader(qpProjURI, modelOpener, sequenceDescription,
				numFetcherThreads, numPriorities);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
