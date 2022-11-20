package ch.epfl.biop.bdv.img;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class, initializer = "init")
public class FixFilePathsCommand implements Command {

    public static String message_in = "";

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = message_in;

    @Parameter(label = "Non valid files", type = ItemIO.BOTH)
    File[] invalidFilePaths;

    @Parameter(type = ItemIO.BOTH, label = "Replacement files (in the same order)")
    File[] fixedFilePaths;

    @Override
    public void run() {

    }

    public void init() {
        message = message_in;
    }
}
