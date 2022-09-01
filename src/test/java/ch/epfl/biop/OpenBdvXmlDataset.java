package ch.epfl.biop;

import bdv.util.BdvFunctions;
import loci.common.DebugTools;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;

public class OpenBdvXmlDataset {
    static public void main(String... args) throws Exception {
        // Arrange
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        // DebugTools.enableLogging("DEBUG");
        DebugTools.enableLogging("OFF");
        AbstractSpimData<?> sd = new XmlIoSpimData().load("C:\\Users\\chiarutt\\Desktop\\ABBA_Workshop\\PCB06-DemoProject\\abba\\_bdvdataset_0.xml");
        BdvFunctions.show(sd);
    }
}
