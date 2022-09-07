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

package ch.epfl.biop.bdv.img.qupath;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.cache.SharedQueue;
import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsImageLoader;
import ch.epfl.biop.bdv.img.OmeroBdvOpener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * QuPath Image Loader. In combination with {@link QuPathToSpimData}, this class
 * is used to convert a QuPath project file into a BDV compatible dataset. There
 * are some limitations: only bioformats image server, rotated image server and
 * omero-raw image server are supported ( among probably other limitations ).
 * Also, editing files in the QuPath project after it has been converted to an
 * xml bdv dataset is not guaranteed to work.
 *
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */
public class QuPathImageLoader implements ViewerImgLoader,
	MultiResolutionImgLoader
{

	private static final Logger logger = LoggerFactory.getLogger(
		QuPathImageLoader.class);
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	protected VolatileGlobalCellCache cache;
	protected SharedQueue sq;
	Map<Integer, QuPathSetupLoader> imgLoaders = new ConcurrentHashMap<>();
	Map<Integer, QuPathImageOpener> openerMap = new HashMap<>();
	public final int numFetcherThreads;
	public final int numPriorities;
	int viewSetupCounter = 0;
	Map<Integer, NumericType> tTypeGetter = new HashMap<>();
	Map<Integer, Volatile> vTypeGetter = new HashMap<>();
	Map<Integer, QuPathEntryAndChannel> viewSetupToQuPathEntryAndChannel =
		new HashMap<>();
	final URI quPathProject;
	final List<QuPathImageOpener> openerModel;

	public QuPathImageLoader(URI quPathProject, List<QuPathImageOpener> qpOpeners,
		final AbstractSequenceDescription<?, ?, ?> sequenceDescription,
		int numFetcherThreads, int numPriorities)
	{
		this.quPathProject = quPathProject;
		this.openerModel = qpOpeners;
		this.sequenceDescription = sequenceDescription;
		this.numFetcherThreads = numFetcherThreads;
		this.numPriorities = numPriorities;
		this.sq = new SharedQueue(numFetcherThreads, numPriorities);

		try {
			// deserialize qupath project
			JsonObject projectJson = ProjectIO.loadRawProject(new File(
				quPathProject));
			Gson gson = new Gson();
			MinimalQuPathProject project = gson.fromJson(projectJson,
				MinimalQuPathProject.class);
			logger.debug("Opening QuPath project " + project.uri);

			qpOpeners.forEach(qpOpener -> {
				// get the image corresponding to the opener
				MinimalQuPathProject.ImageEntry image = qpOpener.getImage();
				logger.debug("Opening qupath image " + image);

				if (image.serverBuilder.builderType.equals("uri")) {
					logger.debug("URI image server");
					if (image.serverBuilder.providerClassName.equals(
						"qupath.lib.images.servers.bioformats.BioFormatsServerBuilder"))
					{
						// get the BioFormats opener
						logger.debug("Building BioFormats image loader");
						QuPathSourceIdentifier identifier = qpOpener.getIdentifier();
						BioFormatsBdvOpener opener = (BioFormatsBdvOpener) qpOpener
							.getOpener();
						//opener.setCache(sq);

						// get the reader
						IFormatReader memo = qpOpener.getReader();

						// get metadata
						IMetadata omeMeta = qpOpener.getOmeMetaIdxOmeXml();

						// get series
						int iSerie = identifier.bioformatsIndex;
						memo.setSeries(iSerie);
						IntStream channels = IntStream.range(0, omeMeta.getChannelCount(iSerie));

						// Register Setups (one per channel and one per timepoint)
						Type<?> t = BioFormatsImageLoader.getBioformatsBdvSourceType(omeMeta,
							iSerie, memo.isRGB());
						Volatile<?> v = BioFormatsImageLoader.getVolatileOf(
							(NumericType<?>) t);
						channels.forEach(iCh -> {
							QuPathEntryAndChannel usc = new QuPathEntryAndChannel(identifier,
								iCh);
							viewSetupToQuPathEntryAndChannel.put(viewSetupCounter, usc);
							tTypeGetter.put(viewSetupCounter, (NumericType<?>) t);
							vTypeGetter.put(viewSetupCounter, v);
							openerMap.put(viewSetupCounter, qpOpener);
							viewSetupCounter++;
						});

					}
					else {
						if (image.serverBuilder.providerClassName.equals(
							"qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder"))
						{
							// get the Omero opener
							logger.debug("Building OMERO-RAW image loader");
							QuPathSourceIdentifier identifier = qpOpener.getIdentifier();
							OmeroBdvOpener opener = (OmeroBdvOpener) qpOpener.getOpener();
							opener.setCache(sq);

							// Register Setups (one per channel and one per timepoint)
							for (int channelIdx = 0; channelIdx < opener
								.getSizeC(); channelIdx++)
							{
								QuPathEntryAndChannel usc = new QuPathEntryAndChannel(
									identifier, channelIdx);
								viewSetupToQuPathEntryAndChannel.put(viewSetupCounter, usc);
								Type<?> t;
								try {
									t = opener.getNumericType(0);
								}
								catch (Exception e) {
									throw new RuntimeException(e);
								}
								tTypeGetter.put(viewSetupCounter, (NumericType) t);
								Volatile v = BioFormatsImageLoader.getVolatileOf(
									(NumericType) t);
								vTypeGetter.put(viewSetupCounter, v);
								openerMap.put(viewSetupCounter, qpOpener);
								viewSetupCounter++;
							}
						}
						else {
							logger.error("Unsupported " +
								image.serverBuilder.providerClassName + " provider Class Name");
							System.out.println("Unsupported " +
								image.serverBuilder.providerClassName + " provider Class Name");
						}
					}
				}
				else {
					logger.error("Unsupported " + image.serverBuilder.builderType +
						" server builder");
					System.out.println("Unsupported " + image.serverBuilder.builderType +
						" server builder");
				}
			});

			cache = new VolatileGlobalCellCache(sq);
		}
		catch (Exception e) {
			logger.error("Exception " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public QuPathSetupLoader getSetupImgLoader(int setupId) {
		if (imgLoaders.containsKey(setupId)) {
			// Already created - return it
			return imgLoaders.get(setupId);
		}
		else {
			QuPathEntryAndChannel qec = viewSetupToQuPathEntryAndChannel.get(setupId);
			QuPathImageOpener opener = this.openerMap.get(setupId);
			int iS = qec.entry.bioformatsIndex;
			int iC = qec.iChannel;
			logger.debug("loading qupath entry number = " + qec.entry + "setupId = " +
				setupId + " series" + iS + " channel " + iC);
			QuPathSetupLoader imgL = null;
			try {
				imgL = new QuPathSetupLoader(opener, iS, iC, setupId, tTypeGetter.get(
					setupId), vTypeGetter.get(setupId), () -> this.cache);
			}
			catch (Exception e) {
				throw new RuntimeException("Error in setup loader creation: " + e
					.getMessage());
			}
			imgLoaders.put(setupId, imgL);
			return imgL;
		}
	}

	@Override
	public CacheControl getCacheControl() {
		return cache;
	}

	public URI getProjectURI() {
		return quPathProject;
	}

	public List<QuPathImageOpener> getModelOpener() {
		return openerModel;
	}

	public static class QuPathSourceIdentifier {

		int indexInQuPathProject;
		int entryID;
		String sourceFile;
		int bioformatsIndex;
		double angleRotationZAxis = 0;
		URI uri;

		public String toString() {
			String str = "";
			str += "sourceFile:" + sourceFile + "[bf:" + bioformatsIndex + " - qp:" +
				indexInQuPathProject + "]";
			return str;
		}
	}

	public static class QuPathEntryAndChannel {

		final public QuPathSourceIdentifier entry;
		final public int iChannel;

		public QuPathEntryAndChannel(QuPathSourceIdentifier entry, int iChannel) {
			this.entry = entry;
			this.iChannel = iChannel;
		}
	}
}
