
/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.legacy.bioformats.command;

import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.lang.time.StopWatch;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Deprecated
@SuppressWarnings({ "Unused", "CanBeFinal" })
/*@Plugin(type = Command.class,
        menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>(Legacy) Open [BioFormats Bdv Bridge]",
        description = "Support bioformats multiresolution api. Attempts to set colors based " +
                "on bioformats metadata. Do not attempt auto contrast.")
 */
public class OpenFilesWithBigdataviewerBioformatsBridgeCommand extends
        BioformatsBigdataviewerBridgeDatasetCommand
{

    final private static Logger logger = LoggerFactory.getLogger(
            OpenFilesWithBigdataviewerBioformatsBridgeCommand.class);

    @Parameter(label = "Name of this dataset")
    public String datasetname = "dataset";

    @Parameter(label = "Dataset files")
    File[] files;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData<?> spimdata;


    public void run() {
        List<BioFormatsBdvOpener> openers = new ArrayList<>();
        for (File f : files) {
            logger.debug("Getting opener for file f " + f.getAbsolutePath());
            openers.add(getOpener(f));
        }
        StopWatch watch = new StopWatch();
        logger.debug("All openers obtained, converting to spimdata object ");
        watch.start();
        spimdata = BioFormatsToSpimData.getSpimData(openers);
        watch.stop();
        logger.debug("Converted to SpimData in " + (int) (watch.getTime() / 1000) +
                " s");

    }

}
