# SciJava Command Documentation Status

This document tracks the documentation status of all SciJava commands (`@Plugin` annotated classes) in the bigdataviewer-image-loaders project.

**Total Commands Found: 10** (active, non-deprecated)
**Fully Documented: 10**
**Legacy/Deprecated: 4** (not exposed to users)

---

## Fully Documented Commands (10)

These commands have both `@Plugin(description=...)` and `@Parameter(label=..., description=...)` for all user-facing parameters.

### Bio-Formats Commands (4)
| File | Description |
|------|-------------|
| `bioformats/command/CreateBdvDatasetBioFormatsCommand.java` | Creates a BDV dataset from one or more Bio-Formats compatible files |
| `bioformats/command/CreateBdvDatasetBioFormatsSimpleCommand.java` | Creates a BDV dataset from a single Bio-Formats compatible file |
| `bioformats/command/BdvShowFileBioFormatsCommand.java` | Opens a file in BigDataViewer using Bio-Formats, with colors from metadata |
| `bioformats/command/OpenSampleCommand.java` | Opens a sample dataset from a selection of test images (downloads and caches on first use) |

### OMERO Commands (3)
| File | Description |
|------|-------------|
| `omero/command/CreateBdvDatasetOMEROCommand.java` | Creates a BDV dataset from one or more OMERO image URLs |
| `omero/command/OmeroConnectCommand.java` | Connects to an OMERO server using your credentials |
| `omero/command/OmeroDisconnectCommand.java` | Disconnects from an OMERO server and closes all sessions |

### QuPath Commands (1)
| File | Description |
|------|-------------|
| `qupath/command/CreateBdvDatasetQuPathCommand.java` | Creates a BDV dataset from all images in a QuPath project |

### ImagePlus Commands (1)
| File | Description |
|------|-------------|
| `imageplus/command/ImagePlusToBdvDatasetCommand.java` | Creates a BDV dataset from the current ImagePlus window |

### Utility Commands (1)
| File | Description |
|------|-------------|
| `FixFilePathsCommand.java` | Allows fixing invalid file paths in a BDV dataset by providing replacement paths |

---

## Legacy/Deprecated Commands (4)

These commands are marked `@Deprecated` with their `@Plugin` annotation commented out. They are not exposed to users but are maintained for backward compatibility with existing code.

| File | Notes |
|------|-------|
| `legacy/bioformats/command/BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.java` | Deprecated, @Plugin commented out |
| `legacy/bioformats/command/OpenFilesWithBigdataviewerBioformatsBridgeCommand.java` | Deprecated, @Plugin commented out |
| `legacy/bioformats/command/StandaloneOpenFileWithBigdataviewerBioformatsBridgeCommand.java` | Deprecated, @Plugin commented out |
| `legacy/qupath/command/QuPathProjectToBDVDatasetLegacyCommand.java` | Deprecated, @Plugin commented out |

---

## Documentation Guidelines

See [DOCUMENTATION_GUIDELINES.md](DOCUMENTATION_GUIDELINES.md) for detailed documentation standards.

### Quick Reference

1. **@Plugin annotation** - Add `description` attribute:
   ```java
   @Plugin(type = Command.class,
           menuPath = "...",
           description = "Brief description of what the command does")
   ```

2. **@Parameter annotations** - Add `label` and `description` for user-facing parameters:
   ```java
   @Parameter(label = "Input File",
              description = "The image file to open")
   File file;
   ```

3. **Service parameters** - No documentation needed (not user-facing):
   ```java
   @Parameter
   Context context;  // No label/description needed
   ```

---

*Last updated: 2025-12-29*