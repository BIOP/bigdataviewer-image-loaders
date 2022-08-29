package ch.epfl.biop;

import loci.common.DebugTools;
import net.imagej.ImageJ;

public class SimpleIJLaunch {

    static public void main(String... args) {
        // Arrange
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        // DebugTools.enableLogging("DEBUG");
        DebugTools.enableLogging("OFF");
    }
}
