Goal

Add documentation to all SciJava commands (@Plugin annotated classes that implement Command or a command interface). Documentation should include:

1. @Plugin annotation description attribute - A concise sentence describing what the command does
2. @Parameter label attribute - A short, user-friendly name for the parameter (displayed in dialogs)
3. @Parameter description attribute - A sentence explaining what the parameter does

Documentation Style Guidelines

- Descriptions should be user-facing: Write for end users, not developers
- Use active voice: "Creates a window" not "A window is created"
- Be concise: One sentence is usually enough
- Avoid jargon: Prefer "image source" over "SourceAndConverter" in descriptions
- Labels should be short: 2-4 words, use Title Case ("Select Source(s)", "Output File")

Example: Before

@Plugin(type = Command.class, menuPath = "Plugins>My Plugin>Do Something")
public class MyCommand implements Command {
@Parameter
SourceAndConverter<?>[] sacs;

      @Parameter
      double threshold;

      @Parameter(type = ItemIO.OUTPUT)
      SourceAndConverter<?> result;
}

Example: After

@Plugin(type = Command.class,
menuPath = "Plugins>My Plugin>Do Something",
description = "Applies a threshold to one or more sources and creates a binary mask")
public class MyCommand implements Command {
@Parameter(label = "Select Source(s)",
description = "The source(s) to threshold")
SourceAndConverter<?>[] sacs;

      @Parameter(label = "Threshold Value",
              description = "Pixel values above this threshold will be set to 1, below to 0")
      double threshold;

      @Parameter(type = ItemIO.OUTPUT,
              label = "Thresholded Source",
              description = "The resulting binary mask source")
      SourceAndConverter<?> result;
}

  ---
Common Parameter Types and Suggested Documentation

Input Parameters

| Type                    | Example Label             | Example Description                             |
  |-------------------------|---------------------------|-------------------------------------------------|
| SourceAndConverter<?>   | "Select Source"           | "The source to process"                         |
| SourceAndConverter<?>[] | "Select Source(s)"        | "The source(s) to process"                      |
| BdvHandle               | "Select BDV Window"       | "The BigDataViewer window to use"               |
| BdvHandle[]             | "Select BDV Window(s)"    | "The BigDataViewer window(s) to use"            |
| File (input)            | "Input File"              | "The file to open/import"                       |
| File (output)           | "Output File"             | "The file where results will be saved"          |
| String (name)           | "Name"                    | "The name for the new source/object"            |
| double (min/max)        | "Min Value" / "Max Value" | "Minimum/Maximum value of the range"            |
| boolean (flag)          | "Enable Feature"          | "When checked, enables the feature"             |
| int (count)             | "Number of X"             | "The number of X to create/use"                 |
| int (timepoint)         | "Timepoint"               | "The timepoint to use (0-based index)"          |
| int (level)             | "Resolution Level"        | "The resolution level (0 = highest resolution)" |
| ColorRGB / ARGBType     | "Color"                   | "The display color for the source"              |
| AffineTransform3D       | "Transform"               | "The 3D affine transformation to apply"         |

Output Parameters

| Type                    | Example Label        | Example Description                      |
  |-------------------------|----------------------|------------------------------------------|
| SourceAndConverter<?>   | "Created Source"     | "The newly created source"               |
| SourceAndConverter<?>[] | "Created Sources"    | "The newly created sources"              |
| BdvHandle               | "Created BDV Window" | "The newly created BigDataViewer window" |
| SpimData                | "Loaded Dataset"     | "The imported SpimData dataset"          |

Service Parameters (no label/description needed)

Services are injected by SciJava and not shown to users:

@Parameter
SourceAndConverterService sacService;

@Parameter
SourceAndConverterBdvDisplayService bdvDisplayService;

@Parameter
Context context;

@Parameter
LogService logService;

  ---
Command Categories and Description Templates

Source Creation/Import Commands

- "Creates a new source from..."
- "Imports sources from..."
- "Opens... and registers it as a source"

Source Transformation Commands

- "Applies... to the selected source(s)"
- "Transforms source(s) using..."
- "Resamples source(s) to match..."

Source Display Commands

- "Sets the display... of one or more sources"
- "Changes the... of the selected source(s)"
- "Adjusts... for the selected source(s)"

BDV Window Commands

- "Creates a new BDV window..."
- "Adds source(s) to a BDV window"
- "Removes source(s) from a BDV window"
- "Synchronizes... between BDV windows"

Export Commands

- "Exports source(s) to..."
- "Saves... to a file"
- "Writes... in... format"

  ---
Checklist for Each Command

- @Plugin has description attribute
- All @Parameter fields have label attribute (except services)
- All @Parameter fields have description attribute (except services)
- Output parameters (type = ItemIO.OUTPUT) are documented
- Labels use Title Case and are concise
- Descriptions are complete sentences

  ---
Notes

- Commands implementing BdvPlaygroundActionCommand appear in contextual menus - their descriptions should focus on the action performed
- The menuPath attribute determines where the command appears in Fiji menus
- SciJava services (injected via @Parameter) don't need labels or descriptions as they're not shown to users
