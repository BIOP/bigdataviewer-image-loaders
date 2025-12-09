package ch.epfl.biop.bdv.img.bioformats;

// In a different class to avoid issues with this extra reader not being present in the classpath

import ch.epfl.biop.formats.in.ZeissQuickStartCZIReader;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.ReaderWrapper;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;

import java.util.ArrayList;
import java.util.Map;

public class ZeissEntitiesAdder {

    public static void addCZIAdditionalEntities(ArrayList<Entity> entityList, BioFormatsOpener opener, int iSerie, int iChannel) {
        IFormatReader inireader = null;
        try  {
            inireader = opener.getPixelReader().acquire();

            // Try to get the underlying reader. Unwraps everything.

            IFormatReader reader = inireader;

            if (reader instanceof Memoizer) {
                reader = ((Memoizer) reader).getReader();
            }

            boolean hasChannelSeparator = false;
            if (reader instanceof ChannelSeparator) {
                reader = ((ChannelSeparator) reader).getReader();
                hasChannelSeparator = true;
            }

            if (reader instanceof ImageReader) {
                ImageReader ir = (ImageReader) reader;
                reader = ir.getReader();
            }

            if (reader instanceof ReaderWrapper) {
                ReaderWrapper rw = (ReaderWrapper) reader;
                reader = rw.getReader();
            }

            if (reader instanceof ZeissQuickStartCZIReader) {
                ZeissQuickStartCZIReader r = (ZeissQuickStartCZIReader) reader;
                Map<String, Integer> dimensions = r.getDimensions(iSerie);
                dimensions.keySet().forEach(dimension -> {
                    int id = dimensions.get(dimension);
                    switch (dimension) {
                        case "R": // Rotation, maybe obsolete ?
                            entityList.add(new Angle(id, r.rotationLabels[id]));
                            break;
                        case "I": // Illumination
                            entityList.add(new Illumination(id, r.illuminationLabels[id]));
                            break;
                        case "V": // View - how is this different from angle ?
                            entityList.add(new Angle(id, r.rotationLabels[id]));
                            break;
                    }
                });
            } else {
                System.err.println("Could not get underlying reader - skipping extra entities (phase, illumination, rotation) detection.");
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            if (inireader!=null) {
                try {
                    opener.getPixelReader().recycle(inireader);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
    }

}
