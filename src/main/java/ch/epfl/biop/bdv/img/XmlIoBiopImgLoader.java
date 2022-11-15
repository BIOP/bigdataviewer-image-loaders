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

package ch.epfl.biop.bdv.img;

import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo(format = "spimreconstruction.biop_imageloader_v2",
	type = BiopImageLoader.class)
public class XmlIoBiopImgLoader implements
	XmlIoBasicImgLoader<BiopImageLoader>
{

	public static final String OPENERS_TAG = "openers";

	/**
	 * Write QuPathImageOpener class in a xml file
	 * 
	 * @param imgLoader
	 * @param basePath
	 * @return
	 */
	@Override
	public Element toXml(BiopImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
			.getAnnotation(ImgLoaderIo.class).format());
		String allOpeners = new Gson().toJson(imgLoader.getOpenerSettings().toArray(new OpenerSettings[0]));
		elem.addContent(XmlHelpers.textElement(OPENERS_TAG, allOpeners));
		return elem;
	}

	/**
	 * Read the xml file, fill OpenerSettings class, create each opener and
	 * write the corresponding QuPathImageLoader
	 * 
	 * @param elem
	 * @param basePath
	 * @param sequenceDescription
	 * @return
	 */
	@Override
	public BiopImageLoader fromXml(Element elem, File basePath,
                                   AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			String allOpeners = XmlHelpers.getText(elem, OPENERS_TAG);
			List<OpenerSettings> openerSettingsList = Arrays.asList(new Gson().fromJson(allOpeners, OpenerSettings[].class));
			return new BiopImageLoader(openerSettingsList, sequenceDescription);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
