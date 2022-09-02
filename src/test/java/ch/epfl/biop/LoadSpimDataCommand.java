package ch.epfl.biop;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class,
        menuPath = "Test>Load BDV Dataset")
public class LoadSpimDataCommand implements Command {

    @Parameter(style = "save")
    File file;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData<?> spimData;

    @Override
    public void run() {
        try {
            spimData = new XmlIoSpimData().load(file.getAbsolutePath());
        } catch (SpimDataException e) {
            e.printStackTrace();
        }
    }
}
