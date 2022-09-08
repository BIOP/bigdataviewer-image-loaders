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

package ch.epfl.biop.bdv.img.bioformats.io;

import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.Opener;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsImageLoader;
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

@ImgLoaderIo(format = "spimreconstruction.biop_bioformatsimageloader_v2",
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
		elem.addContent(XmlHelpers.intElement(DATASET_NUMBER_TAG, imgLoader
				.getOpenerSettings().size()));

		Gson gson = new Gson();
		for (int i = 0; i < imgLoader.getOpenerSettings().size(); i++) {
			// Opener serialization
			elem.addContent(XmlHelpers.textElement(OPENER_TAG+"_"+i, gson.toJson(imgLoader.getOpenerSettings().get(i))));
		}

		return elem;
	}

	@Override
	public BioFormatsImageLoader fromXml(Element elem, File basePath,
		AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			Gson gson = new Gson();
			List<Opener<?>> openers = new ArrayList<>();
			List<OpenerSettings> openersSettings = new ArrayList<>();
			final int number_of_datasets = XmlHelpers.getInt(elem, DATASET_NUMBER_TAG);

			for (int i = 0; i < number_of_datasets; i++) {
				// Opener de-serialization
				String jsonInString = XmlHelpers.getText(elem, OPENER_TAG + "_" + i);
				OpenerSettings settings = gson.fromJson(jsonInString,
						OpenerSettings.class);

				openersSettings.add(settings);
				openers.add(settings.bioFormatsBuilder().create());
			}

			return new BioFormatsImageLoader(openers, openersSettings, sequenceDescription);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
