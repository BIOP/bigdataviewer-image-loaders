
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

package ch.epfl.biop.bdv.img.legacy.bioformats.command;

import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.lang.time.StopWatch;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Deprecated
@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
        menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Open [BioFormats Bdv Bridge (legacy)]",
        description = "Support bioformats multiresolution api. Attempts to set colors based " +
                "on bioformats metadata. Do not attempt auto contrast.")
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
    AbstractSpimData spimdata;

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
