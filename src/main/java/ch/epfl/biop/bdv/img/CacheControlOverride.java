package ch.epfl.biop.bdv.img;

import bdv.img.cache.VolatileGlobalCellCache;

public interface CacheControlOverride {

    void setCacheControl(VolatileGlobalCellCache cache);
}
