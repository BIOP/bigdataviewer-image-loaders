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
package ch.epfl.biop.bdv.img.legacy.qupath;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
//import ch.epfl.biop.bdv.img.bioformats.BioFormatsImageLoader;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsSetupLoader;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import ch.epfl.biop.bdv.img.qupath.struct.ProjectIO;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * QuPath Image Loader. In combination with {@link QuPathToSpimData}, this class
 * is used to convert a QuPath project file into a BDV compatible dataset.
 *
 * There are some limitations: only bioformats image server and rotated image server
 * are supported ( among probably other limitations ).
 *
 * Also, editing files in the QuPath project after it has been converted to an xml bdv dataset
 * is not guaranteed to work.
 *
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 */

@Deprecated
public class QuPathImageLoader implements ViewerImgLoader, MultiResolutionImgLoader {

    private static final Logger logger = LoggerFactory.getLogger(QuPathImageLoader.class);

    final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
    VolatileGlobalCellCache cache;
    protected SharedQueue sq;
    Map<Integer, BioFormatsSetupLoader<?,?,?>> imgLoaders = new ConcurrentHashMap<>();
    Map<URI, BioFormatsBdvOpener> openerMap = new HashMap<>();

    public final int numFetcherThreads;
    public final int numPriorities;

    int viewSetupCounter = 0;

    Map<Integer, NumericType<?>> tTypeGetter = new HashMap<>();

    Map<Integer, Volatile<?>> vTypeGetter = new HashMap<>();

    Map<Integer, QuPathEntryAndChannel> viewSetupToQuPathEntryAndChannel = new HashMap<>();

    final URI quPathProject;
    final BioFormatsBdvOpener openerModel;

    public QuPathImageLoader(URI quPathProject, BioFormatsBdvOpener openerModel, final AbstractSequenceDescription<?, ?, ?> sequenceDescription, int numFetcherThreads, int numPriorities) {
        this.quPathProject = quPathProject;
        this.openerModel = openerModel;
        this.sequenceDescription = sequenceDescription;
        this.numFetcherThreads = numFetcherThreads;
        this.numPriorities = numPriorities;
        sq = new SharedQueue(numFetcherThreads, numPriorities);
        cache = new VolatileGlobalCellCache(sq);

        try {

            JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
            Gson gson = new Gson();
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);

            logger.debug("Opening QuPath project " + project.uri);

            Map<BioFormatsBdvOpener, IFormatReader> cachedReaders = new HashMap<>(); // Performance

            project.images.forEach(image -> {
                logger.debug("Opening qupath image "+image);
                QuPathBioFormatsSourceIdentifier identifier = new QuPathBioFormatsSourceIdentifier();
                if (image.serverBuilder.builderType.equals("rotated")) {
                    String angleDegreesStr = image.serverBuilder.rotation.substring(7);//"ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
                    logger.debug("Rotated image server ("+angleDegreesStr+")");
                    if (angleDegreesStr.equals("NONE")) {
                        identifier.angleRotationZAxis = 0;
                    } else {
                        identifier.angleRotationZAxis = (Double.parseDouble(angleDegreesStr) / 180.0) * Math.PI;
                    }
                    image.serverBuilder = image.serverBuilder.builder;
                }

                if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                        try {
                            URI uri = new URI(image.serverBuilder.uri.getScheme(), image.serverBuilder.uri.getHost(), image.serverBuilder.uri.getPath(), null);

                            // This appears to work more reliably than converting to a File
                            String filePath = Paths.get(uri).toString();

                            if (!openerMap.containsKey(image.serverBuilder.uri)) {
                                String location = Paths.get(uri).toString();
                                logger.debug("Creating opener for data location "+location);
                                BioFormatsBdvOpener opener = new BioFormatsBdvOpener(openerModel).location(location);
                                opener.setCache(sq);
                                openerMap.put(image.serverBuilder.uri,opener);
                                cachedReaders.put(opener, opener.getNewReader());
                            }

                            identifier.uri = image.serverBuilder.uri;
                            identifier.sourceFile = filePath;
                            identifier.indexInQuPathProject = project.images.indexOf(image);
                            identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;

                            int iSerie =  image.serverBuilder.args.indexOf("--series");

                            if (iSerie==-1) {
                                logger.error("Series not found in qupath project server builder!");
                                identifier.bioformatsIndex = -1;
                            } else {
                                identifier.bioformatsIndex = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
                            }

                            logger.debug(identifier.toString());

                            BioFormatsBdvOpener opener = openerMap.get(image.serverBuilder.uri);
                            IFormatReader memo = cachedReaders.get(opener);
                            memo.setSeries(identifier.bioformatsIndex);

                            logger.debug("Number of Series : " + memo.getSeriesCount());
                            IMetadata omeMeta = (IMetadata) memo.getMetadataStore();
                            memo.setMetadataStore(omeMeta);

                            logger.debug("\t Serie " + identifier.bioformatsIndex + " Number of timesteps = " + omeMeta.getPixelsSizeT(identifier.bioformatsIndex).getNumberValue().intValue());
                            // ---------- Serie > Channels
                            logger.debug("\t Serie " + identifier.bioformatsIndex + " Number of channels = " + omeMeta.getChannelCount(identifier.bioformatsIndex));

                            IntStream channels = IntStream.range(0, omeMeta.getChannelCount(identifier.bioformatsIndex));
                            // Register Setups (one per channel and one per timepoint)
                            Type<?> t = BioFormatsImageLoader.getBioformatsBdvSourceType(memo, identifier.bioformatsIndex);
                            Volatile<?> v = BioFormatsImageLoader.getVolatileOf((NumericType<?>)t);
                            channels.forEach(
                                    iCh -> {
                                        QuPathEntryAndChannel usc = new QuPathEntryAndChannel(identifier, iCh);
                                        viewSetupToQuPathEntryAndChannel.put(viewSetupCounter,usc);
                                        tTypeGetter.put(viewSetupCounter,(NumericType<?>)t);
                                        vTypeGetter.put(viewSetupCounter, v);
                                        viewSetupCounter++;
                                    });

                        } catch (URISyntaxException e) {
                            logger.error("URI Syntax error "+e.getMessage());
                            e.printStackTrace();
                        }

                    } else {
                        logger.error("Unsupported "+image.serverBuilder.providerClassName+" class name provider");
                    }
                } else {
                    logger.error("Unsupported "+image.serverBuilder.builderType+" server builder");
                }
            });

            // Cleaning opened readers
            cachedReaders.values().forEach(reader -> {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            logger.error("Exception "+e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public BioFormatsSetupLoader<?,?,?> getSetupImgLoader(int setupId) {
        if (imgLoaders.containsKey(setupId)) {
            // Already created - return it
            return imgLoaders.get(setupId);
        } else {
            QuPathEntryAndChannel qec = viewSetupToQuPathEntryAndChannel.get(setupId);
            BioFormatsBdvOpener opener = this.openerMap.get(qec.entry.uri);
            int iS = qec.entry.bioformatsIndex;
            int iC = qec.iChannel;
            logger.debug("loading qupath entry number = "+qec.entry+"setupId = "+setupId+" series"+iS+" channel "+iC);
            BioFormatsSetupLoader imgL = null;
            try {
                imgL = new BioFormatsSetupLoader(
                        opener,
                        iS,
                        iC,
                        setupId,
                        tTypeGetter.get(setupId),
                        vTypeGetter.get(setupId),
                        () -> cache
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            imgLoaders.put(setupId,imgL);
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

    public BioFormatsBdvOpener  getModelOpener() {
        return openerModel;
    }

    public static class QuPathBioFormatsSourceIdentifier {
        int indexInQuPathProject;
        int entryID;
        String sourceFile;
        int bioformatsIndex;
        double angleRotationZAxis = 0;
        URI uri;

        public String toString() {
            String str = "";
            str+="sourceFile:"+sourceFile+"[bf:"+bioformatsIndex+" - qp:"+indexInQuPathProject+"]";
            return str;
        }
    }

    public static class QuPathEntryAndChannel {
        final public QuPathBioFormatsSourceIdentifier entry;
        final public int iChannel;

        public QuPathEntryAndChannel(QuPathBioFormatsSourceIdentifier entry, int iChannel) {
            this.entry = entry;
            this.iChannel = iChannel;
        }
    }
}
