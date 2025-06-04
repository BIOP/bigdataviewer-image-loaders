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

package ch.epfl.biop.bdv.img.imageplus.io;

import ch.epfl.biop.bdv.img.imageplus.ImagePlusImageLoader;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.type.NativeType;
import org.jdom2.Element;

import java.io.File;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo(
		format = "spimreconstruction.biop_virtualstackimageplusimageloader",
		type = ImagePlusImageLoader.class)
public class ImagePlusImgLoader<T extends NativeType<T>, V extends Volatile<T> & NativeType<V>, A extends VolatileAccess & DataAccess>
		implements XmlIoBasicImgLoader<ImagePlusImageLoader<T, V, A>>
{

	final public static String IMAGEPLUS_FILEPATH_TAG = "imageplus_filepath";
	final public static String IMAGEPLUS_TIME_ORIGIN_TAG =
			"imageplus_time_origin";

	@Override
	public Element toXml(ImagePlusImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
				.getAnnotation(ImgLoaderIo.class).format());
		// For potential extensibility
		elem.addContent(XmlHelpers.textElement(IMAGEPLUS_FILEPATH_TAG, imgLoader
				.getImagePlus().getFileInfo().getFilePath()));
		elem.addContent(XmlHelpers.intElement(IMAGEPLUS_TIME_ORIGIN_TAG, imgLoader
				.getTimeShift()));
		return elem;
	}

	@Override
	public ImagePlusImageLoader fromXml(Element elem, File basePath,
										AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		String imagePlusFilePath = XmlHelpers.getText(elem, IMAGEPLUS_FILEPATH_TAG);
		int originTimePoint = XmlHelpers.getInt(elem, IMAGEPLUS_TIME_ORIGIN_TAG);

		ImagePlus imp = IJ.openImage(imagePlusFilePath);

		final ImagePlusImageLoader<?, ?, ?> imgLoader;

		{
			switch (imp.getType()) {
				case ImagePlus.GRAY8:
					imgLoader = ImagePlusImageLoader.createUnsignedByteInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.GRAY16:
					imgLoader = ImagePlusImageLoader.createUnsignedShortInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.GRAY32:
					imgLoader = ImagePlusImageLoader.createFloatInstance(imp,
							originTimePoint);
					break;
				case ImagePlus.COLOR_RGB:
				default:
					imgLoader = ImagePlusImageLoader.createARGBInstance(imp,
							originTimePoint);
					break;
			}
		}

		return imgLoader;
	}
}
