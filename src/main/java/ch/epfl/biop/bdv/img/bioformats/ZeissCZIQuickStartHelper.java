/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
import mpicbg.spim.data.sequence.Tile;

import java.util.ArrayList;
import java.util.Map;

public class ZeissCZIQuickStartHelper {

    protected static void addCZIAdditionalEntities(ArrayList<Entity> entityList, BioFormatsOpener opener, int iSerie, int iChannel) {
        IFormatReader inireader = null;
        try  {
            inireader = opener.getPixelReader().acquire();

            // Try to get the underlying reader. Unwraps everything.

            IFormatReader reader = inireader;

            if (reader instanceof Memoizer) {
                reader = ((Memoizer) reader).getReader();
            }

            if (reader instanceof ChannelSeparator) {
                reader = ((ChannelSeparator) reader).getReader();
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
                            entityList.add(new Angle(id, r.rotationLabels!=null?r.rotationLabels[id]: String.valueOf(id)));
                            break;
                        case "I": // Illumination
                            entityList.add(new Illumination(id, r.illuminationLabels!=null?r.illuminationLabels[id]: String.valueOf(id)));
                            break;
                        case "V": // View - how is this different from angle ?
                            entityList.add(new Angle(id, r.rotationLabels!=null?r.rotationLabels[id]: String.valueOf(id)));
                            break;
                        case "M": // Mosaic = Tile
                            entityList.add(new Tile(id, String.valueOf(id)));
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

    protected static boolean isLatticeLightSheet(BioFormatsOpener opener) {
        IFormatReader inireader = null;
        try  {
            inireader = opener.getPixelReader().acquire();

            // Try to get the underlying reader. Unwraps everything.

            IFormatReader reader = inireader;

            if (reader instanceof Memoizer) {
                reader = ((Memoizer) reader).getReader();
            }

            if (reader instanceof ChannelSeparator) {
                reader = ((ChannelSeparator) reader).getReader();
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
                return r.isLatticeLightSheet();


            } else {
                System.err.println("Could not get underlying reader - skipping extra entities (phase, illumination, rotation) detection.");
                return false;
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
        return false;
    }

}
