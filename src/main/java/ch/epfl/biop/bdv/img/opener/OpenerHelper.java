/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
package ch.epfl.biop.bdv.img.opener;

import ch.epfl.biop.bdv.img.bioformats.entity.FileName;
import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import ch.epfl.biop.bdv.img.entity.ImageName;
import ch.epfl.biop.bdv.img.omero.entity.OmeroHostId;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileIntType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class OpenerHelper {
    private static final Logger logger = LoggerFactory.getLogger(OpenerHelper.class);

    static public <T> T memoize(String key, Map<String, Object> cache, Supplier<T> getter) {
        if (!cache.containsKey(key)) {
            cache.put(key, getter.get());
        } else {
            logger.debug(key+" is reused! ");
        }
        return (T) cache.get(key);
    }

    /**
     *
     * @param t
     * @return volatile pixel type from t
     */
    static public Volatile getVolatileOf(NumericType t) {
        if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

        if (t instanceof IntType) return new VolatileIntType();

        if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

        if (t instanceof FloatType) return new VolatileFloatType();

        if (t instanceof ARGBType) return new VolatileARGBType();
        return null;
    }


    public static Map<String, Class<? extends Entity>> getEntities() {
        Map<String, Class<? extends Entity>> entityClasses = new HashMap<>();

        entityClasses.put(Tile.class.getSimpleName().toUpperCase(), Tile.class);
        entityClasses.put(Illumination.class.getSimpleName().toUpperCase(), Illumination.class);
        entityClasses.put(Angle.class.getSimpleName().toUpperCase(), Angle.class);
        entityClasses.put(Channel.class.getSimpleName().toUpperCase(), Channel.class);
        entityClasses.put(FileName.class.getSimpleName().toUpperCase(), FileName.class);
        entityClasses.put(SeriesIndex.class.getSimpleName().toUpperCase(), SeriesIndex.class);
        entityClasses.put(ImageName.class.getSimpleName().toUpperCase(), ImageName.class);
        entityClasses.put(OmeroHostId.class.getSimpleName().toUpperCase(), OmeroHostId.class);

        return entityClasses;
    }
}
