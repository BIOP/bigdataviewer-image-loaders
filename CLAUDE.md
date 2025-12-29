# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build and install locally
mvn clean install

# Build and package JAR (without installing)
mvn clean package

# Full build with tests
mvn clean verify

# Run a single test class
mvn test -Dtest=JITTester

# Generate Javadoc and reports
mvn site:site
```

**Note:** This is a Maven project with SciJava parent POM (`org.scijava:pom-scijava:43.0.0`). Platform-specific profiles activate automatically.

## Architecture Overview

This library provides image loaders for BigDataViewer (BDV) that bridge various image sources (Bio-Formats, OMERO, QuPath, ImagePlus) to the SPIMDATA format.

### Core Pattern: Opener Interface

The central abstraction is `Opener<T>` (`ch.epfl.biop.bdv.img.opener.Opener`), a strategy pattern where `T` represents the pooled resource type:

- **`BioFormatsOpener`** (`Opener<IFormatReader>`) - Reads Bio-Formats compatible files
- **`OmeroOpener`** (`Opener<RawPixelsStorePrx>`) - Reads from OMERO servers via Raw Pixels API
- **`QuPathOpener`** - Reads QuPath project entries
- **`PyramidizeOpener`** - Wraps other openers to add multi-resolution support

Each Opener encapsulates image metadata (dimensions, channels, timepoints, resolution levels, voxel sizes) and provides factory methods for SetupLoaders.

### Resource Pooling

`ResourcePool<T>` provides thread-safe pooling of expensive objects (Bio-Formats readers, OMERO connections) for concurrent tile loading. This is critical for BDV's multithreaded rendering.

### SPIMDATA Conversion Flow

```
Opener → OpenersToSpimData → SpimData
                ↓
         OpenersImageLoader (implements ImageLoader)
                ↓
         OpenerSetupLoader (per channel/series)
                ↓
         BDV VolatileGlobalCellCache (tile caching)
```

### XML Persistence

`XmlIoOpenersImageLoader` serializes datasets to BigDataViewer-compatible XML. Entity classes with `XmlIo*` adapters handle domain-specific metadata (FileName, SeriesIndex, OmeroHostId, etc.).

### SciJava Plugin Commands

Commands are exposed as Fiji menu items under `Plugins>BigDataViewer-Playground>BDVDataset>`. Key commands:
- `CreateBdvDatasetBioFormatsCommand` - Create BDV dataset from file
- `CreateBdvDatasetOMEROCommand` - Create BDV dataset from OMERO
- `CreateBdvDatasetQuPathCommand` - Create BDV dataset from QuPath project

## Package Structure

- `ch.epfl.biop.bdv.img.opener/` - Core Opener interface and settings
- `ch.epfl.biop.bdv.img.bioformats/` - Bio-Formats integration
- `ch.epfl.biop.bdv.img.omero/` - OMERO integration
- `ch.epfl.biop.bdv.img.qupath/` - QuPath integration
- `ch.epfl.biop.bdv.img.imageplus/` - ImagePlus (Fiji) bridge
- `ch.epfl.biop.bdv.img.pyramidize/` - Multi-resolution generation
- `ch.epfl.biop.bdv.img.legacy/` - Deprecated implementations (maintain for compatibility)
- `ch.epfl.biop.bdv.img.entity/` - Domain entities for XML serialization

## Key Dependencies

- **Bio-Formats** - Image file format reading
- **OMERO** (omero-blitz, omero-gateway) - Remote image server access
- **BigDataViewer Core** (bdv-core) - BDV framework
- **ImgLib2** - N-dimensional image library
- **SciJava** - Plugin framework with `@Plugin` and `@Parameter` annotations
- **Quick-Start CZI Reader** - Zeiss CZI format with illumination/rotation/phase support

## Compatibility Notes

- Datasets are compatible with BigStitcher, BigWarp, Labkit, ABBA, and Warpy
- Legacy code in `ch.epfl.biop.bdv.img.legacy` must be maintained for backward compatibility with existing XML datasets
- Thread safety is critical - all pixel access goes through resource pools