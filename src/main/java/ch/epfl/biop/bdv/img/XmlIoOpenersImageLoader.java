/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;
import org.scijava.util.VersionUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo(format = "spimreconstruction.openersimageloader",
	type = OpenersImageLoader.class)
public class XmlIoOpenersImageLoader implements
	XmlIoBasicImgLoader<OpenersImageLoader>
{

	public static final String OPENERS_TAG = "openers";
	public static final String VERSION_TAG = "version";

	/**
	 * Write QuPathImageOpener class in a xml file
	 * 
	 * @param imgLoader
	 * @param basePath
	 * @return
	 */
	@Override
	public Element toXml(OpenersImageLoader imgLoader, File basePath) {
		final Element elem = new Element("ImageLoader");
		elem.setAttribute(IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass()
			.getAnnotation(ImgLoaderIo.class).format());
		String allOpeners = new GsonBuilder()
				//.setPrettyPrinting()
				.create().toJson(imgLoader.getOpenerSettings().toArray(new OpenerSettings[0]));
		elem.addContent(XmlHelpers.textElement(VERSION_TAG, VersionUtils.getVersion(OpenersImageLoader.class)));
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
	public OpenersImageLoader fromXml(Element elem, File basePath,
									  AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		try {
			String allOpeners = XmlHelpers.getText(elem, OPENERS_TAG);
			List<OpenerSettings> openerSettingsList = Arrays.asList(new Gson().fromJson(allOpeners, OpenerSettings[].class));
			validateOpeners(openerSettingsList);
			openerSettingsList.forEach(opener ->
					opener.context(Services.commandService.context())
							.skipMeta());
			return new OpenersImageLoader(openerSettingsList, sequenceDescription);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void validateOpeners(List<OpenerSettings> openerSettingsList) {
		// Check Bioformats opener
		Map<String, List<OpenerSettings>> invalidLocations = openerSettingsList.stream()
				.filter(openerSettings ->
						(openerSettings.getType().equals(OpenerSettings.OpenerType.BIOFORMATS)||(openerSettings.getType().equals(OpenerSettings.OpenerType.QUPATH))))
				.filter(openerSettings -> !new File(openerSettings.getLocation()).exists())
				.collect(Collectors.groupingBy(OpenerSettings::getLocation, LinkedHashMap::new, Collectors.toList()));

		if (!invalidLocations.isEmpty()) {
			// Houston we have an issue
			String[] in = invalidLocations.keySet().toArray(new String[0]);
			String message_in;
			if ((in.length)>1) {
				message_in = "<html> Please enter updated file paths for the following files:<br> ";
			} else {
				message_in = "<html> Please enter updated file paths for the following file:<br> ";
			}
			for (String path : in) {
				message_in+=path+"<br>";
			}
			message_in+="</html>";
			try {
				FixFilePathsCommand.message_in = message_in;
				File[] out = (File[]) Services.commandService.run(FixFilePathsCommand.class, true,
						"invalidFilePaths", in).get().getOutput("fixedFilePaths");
				if (out.length!=in.length) {
					System.err.println("You did not enter the requested number of files");
					return;
				}
				Map<String, String> oldToNew = new HashMap<>();
				for (int i = 0;i<in.length;i++) {
					oldToNew.put(in[i], out[i].getAbsolutePath());
				}
				invalidLocations.values().forEach(openerSettingsL -> {
					openerSettingsL.forEach(openerSettings -> {
						openerSettings.location(oldToNew.get(openerSettings.getLocation()));
					});
				});
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}
}
