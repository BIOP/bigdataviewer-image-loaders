
package ch.epfl.biop;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@SuppressWarnings({ "unused", "CanBeFinal" })
@Plugin(type = Command.class, menuPath = "Test>Save BDV Dataset")
public class SaveSpimDataCommand implements Command {

	@Parameter
	AbstractSpimData<?> spimData;

	@Parameter(style = "save")
	File file;

	@Override
	public void run() {
		try {
			if (spimData instanceof SpimData) {
				(new XmlIoSpimData()).save((SpimData) spimData, file.getAbsolutePath());
			}
			else if (spimData instanceof SpimDataMinimal) {
				(new XmlIoSpimDataMinimal()).save((SpimDataMinimal) spimData, file
					.getAbsolutePath());
			}
		}
		catch (Exception e) {
			IJ.log(e.getMessage());
		}
	}
}
