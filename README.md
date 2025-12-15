# Images loaders for BigDataViewver

[![](https://github.com/BIOP/bigdataviewer-image-loaders/actions/workflows/build-main.yml/badge.svg)](https://github.com/BIOP/bigdataviewer-image-loaders/actions/workflows/build-main.yml)
[![Maven Scijava Version](https://img.shields.io/github/v/tag/BIOP/bigdataviewer-image-loaders?label=Version-[Maven%20Scijava])](https://maven.scijava.org/#browse/browse:releases:ch%2Fepfl%2Fbiop%2Fbigdataviewer-image-loaders)

Allows to open images in BigDataViewer using:
- Bio-Formats API (Multiresolution API supported)
- OMERO Raw API (Java Ice)
- QuPath projects (Bio-Formats and OMERO Raw image servers)

A xml dataset can be defined for each set of sources, thus brings compatibility to all `BIG` tools ([BigStitcher](https://github.com/PreibischLab/BigStitcher), [BigWarp](https://imagej.net/plugins/bigwarp), [Labkit](https://github.com/juglab/labkit-ui), [ABBA](https://biop.github.io/ijp-imagetoatlas/), [Warpy](https://imagej.net/plugins/bdv/warpy/warpy)...)
