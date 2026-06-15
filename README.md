# Image loaders for BigDataViewer

[![](https://github.com/BIOP/bigdataviewer-image-loaders/actions/workflows/build-main.yml/badge.svg)](https://github.com/BIOP/bigdataviewer-image-loaders/actions/workflows/build-main.yml)
[![Maven Scijava Version](https://img.shields.io/github/v/tag/BIOP/bigdataviewer-image-loaders?label=Version-[Maven%20Scijava])](https://maven.scijava.org/#browse/browse:releases:ch%2Fepfl%2Fbiop%2Fbigdataviewer-image-loaders)

This library provides image loaders that bridge various image sources to
[BigDataViewer](https://imagej.net/plugins/bdv/) (BDV) and the SpimData format.
Images can be opened from:

- **Bio-Formats** API (multiresolution supported)
- **OMERO** Raw Pixels API (Java Ice)
- **QuPath** projects (backed by Bio-Formats and OMERO Raw image servers)
- **ImagePlus** (the currently open Fiji image)

Each set of sources can be saved as an XML dataset, which brings compatibility
with the whole `BIG` ecosystem:
[BigStitcher](https://github.com/PreibischLab/BigStitcher),
[BigWarp](https://imagej.net/plugins/bigwarp),
[Labkit](https://github.com/juglab/labkit-ui),
[ABBA](https://biop.github.io/ijp-imagetoatlas/) and
[Warpy](https://imagej.net/plugins/bdv/warpy/warpy).

## How it works

The central abstraction is the `Opener<T>` interface
(`ch.epfl.biop.bdv.img.opener.Opener`), a strategy that knows how to read pixels
and metadata from one kind of source. Openers are turned into a `SpimData`
object that BDV (and the `BIG` tools) can consume:

```
Opener -> OpenersToSpimData -> SpimData
              -> OpenersImageLoader -> OpenerSetupLoader -> BDV tile cache
```

Concurrent tile loading relies on a thread-safe `ResourcePool<T>` that pools
expensive resources (Bio-Formats readers, OMERO connections).

## Installation

The library is a Maven artifact built against the SciJava parent POM and is
deployed to the [SciJava Maven repository](https://maven.scijava.org/). To use
it as a dependency:

```xml
<dependency>
    <groupId>ch.epfl.biop</groupId>
    <artifactId>bigdataviewer-image-loaders</artifactId>
    <version>LATEST</version>
</dependency>
```

When the library is on Fiji's classpath, the commands below appear in the Fiji
menus.

## Fiji commands

| Command | Menu |
| --- | --- |
| Create a dataset from one or more files | `Plugins > BigDataViewer-Playground > Import > Dataset - Create [Bio-Formats]` |
| Create a dataset from an OMERO server | `Plugins > BigDataViewer-Playground > Import > Dataset - Create [OMERO]` |
| Create a dataset from a QuPath project | `Plugins > BigDataViewer-Playground > Import > Dataset - Create [QuPath]` |
| Create a dataset from the current ImagePlus | `Plugins > BigDataViewer-Playground > Import > Dataset - Create [Current ImagePlus]` |
| Open a sample dataset | `Plugins > BigDataViewer-Playground > Import > Dataset - Samples` |
| Open a single file directly in BDV | `Plugins > BigDataViewer > Bio-Formats > Open File with Bio-Formats` |
| Connect / disconnect from OMERO | `Plugins > BIOP > OMERO > Omero - Connect` / `Omero - Disconnect` |
| Set the Bio-Formats memo directory | `Plugins > BigDataViewer-Playground > Workspace > Set Bio-Formats Memo Directory` |

## Bio-Formats memoization

[Memoization](https://bio-formats.readthedocs.io/en/latest/developers/matlab-dev.html#improving-reading-performance)
lets Bio-Formats cache the (sometimes slow) reader initialization of a file into
a small `.bfmemo` file. The next time the same file is opened, initialization is
read back from that cache instead of being recomputed, which can dramatically
speed up opening large or complex files.

### Default behaviour

- **Memoization is enabled** by default.
- Memo files are written to a **dedicated directory**, `<user.home>/.bf-memo`,
  **not** next to the image data. This avoids the two classic problems of the
  default Bio-Formats behaviour: failures when the image folder is read-only,
  and `.bfmemo` files scattered alongside the data.

The directory is created automatically if it does not exist.

### Choosing the memo directory

The directory is resolved with the following precedence (first match wins):

1. A directory set programmatically with `BioFormatsHelper.setMemoDir(File)`
   (current session only).
2. The system property `bigdataviewer.bioformats.memodir`, e.g. when launching
   on a cluster or in headless mode:
   ```
   -Dbigdataviewer.bioformats.memodir=/scratch/$USER/bfmemo
   ```
3. A directory persisted from the GUI command
   `Plugins > BigDataViewer-Playground > Workspace > Set Bio-Formats Memo Directory`.
   This is the easiest option for interactive users: it is remembered across
   restarts and applied immediately, without editing the Fiji launcher.
4. The default `<user.home>/.bf-memo`.

### Disabling memoization

- **For a single import**: tick *Disable Memoization* in the
  *Dataset - Create [Bio-Formats]* dialog.
- **Globally** (useful for tests or debugging): set the system property
  ```
  -Dbigdataviewer.bioformats.memo.disable=true
  ```
  This overrides every per-opener setting and turns memoization off everywhere.

You normally only need to disable memoization if a specific file fails to open
because of a stale or incompatible memo file.

## Scripting

Datasets can be built programmatically with `OpenerSettings`. For example, with
Bio-Formats:

```java
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.OpenersToSpimData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

File file = new File("/path/to/image.czi");

List<OpenerSettings> settings = new ArrayList<>();
int nSeries = BioFormatsHelper.getNSeries(file);
for (int i = 0; i < nSeries; i++) {
    settings.add(OpenerSettings.BioFormats()
            .location(file)
            .setSerie(i)
            .unit("MILLIMETER")
            .splitRGBChannels(false)
            .positionConvention("CENTER")
            .pyramidize(true)
            .useBFMemo(true)   // false to skip the .bfmemo cache for this opener
            .context(ctx));    // a SciJava Context
}

AbstractSpimData<?> spimData = OpenersToSpimData.getSpimData(settings);
```

The memo directory can be overridden for the current session before opening:

```java
BioFormatsHelper.setMemoDir(new File("/tmp/my-bfmemo"));
```

## Compatibility notes

- XML datasets remain compatible with BigStitcher, BigWarp, Labkit, ABBA and
  Warpy.
- Legacy loaders under `ch.epfl.biop.bdv.img.legacy` are kept for backward
  compatibility with existing XML datasets and should not be removed.

## License

GPL v3. See the license headers in the source files for details.