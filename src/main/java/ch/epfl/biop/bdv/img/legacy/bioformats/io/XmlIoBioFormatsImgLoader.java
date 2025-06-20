/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.legacy.bioformats.io;

import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsImageLoader;
import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@Deprecated
@ImgLoaderIo(format = "spimreconstruction.biop_bioformatsimageloader",
	type = BioFormatsImageLoader.class)
public class XmlIoBioFormatsImgLoader implements
	XmlIoBasicImgLoader<BioFormatsImageLoader>
{

	public static final String OPENER_CLASS_TAG = "opener_class";
	public static final String OPENER_TAG = "opener";
	public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
	public static final String CACHE_NUM_PRIORITIES = "num_priorities";
	public static final String DATASET_NUMBER_TAG = "dataset_number";

	@Override
	public Element toXml(BioFormatsImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
			.getAnnotation(ImgLoaderIo.class).format());
		// For potential extensibility
		elem.addContent(XmlHelpers.textElement(OPENER_CLASS_TAG,
			BioFormatsBdvOpener.class.getName()));
		elem.addContent(XmlHelpers.intElement(CACHE_NUM_FETCHER,
			imgLoader.numFetcherThreads));
		elem.addContent(XmlHelpers.intElement(CACHE_NUM_PRIORITIES,
			imgLoader.numPriorities));
		elem.addContent(XmlHelpers.intElement(DATASET_NUMBER_TAG, imgLoader.openers
			.size()));

		Gson gson = new Gson();
		for (int i = 0; i < imgLoader.openers.size(); i++) {
			// Opener serialization
			elem.addContent(XmlHelpers.textElement(OPENER_TAG + "_" + i, gson.toJson(
				imgLoader.openers.get(i))));
		}
		return elem;
	}

	@Override
	public BioFormatsImageLoader fromXml(Element elem, File basePath,
		AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			final int number_of_datasets = XmlHelpers.getInt(elem,
				DATASET_NUMBER_TAG);
			final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
			final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);

			List<BioFormatsBdvOpener> openers = new ArrayList<>();

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
			for (int i = 0; i < number_of_datasets; i++) {
				// Opener de-serialization
				String jsonInString = XmlHelpers.getText(elem, OPENER_TAG + "_" + i);
				BioFormatsBdvOpener opener = gson.fromJson(jsonInString,
					BioFormatsBdvOpener.class);
				openers.add(opener);
			}

			return new BioFormatsImageLoader(openers, sequenceDescription,
				numFetcherThreads, numPriorities);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
