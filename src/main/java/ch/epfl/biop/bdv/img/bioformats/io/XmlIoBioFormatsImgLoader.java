/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.bdv.img.bioformats.io;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsBdvOpener;
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
			throw new RuntimeException(e);
		}
	}
}
