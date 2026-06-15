/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.bioformats.command;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * Lets the user choose where Bio-Formats memo (.bfmemo) files are stored,
 * without having to edit the Fiji launcher or set a system property. The chosen
 * directory is persisted across sessions and applied immediately.
 * <p>
 * Note: a {@code -D}{@value BioFormatsHelper#MEMO_DIR_PROPERTY} system property,
 * if set at launch, only matters before this command is used: choosing a
 * directory here overrides it for the running session.
 */
@SuppressWarnings({ "Unused", "CanBeFinal", "unused" })
@Plugin(type = Command.class,
	menu = {
		@Menu(label = "Plugins"),
		@Menu(label = "BigDataViewer-Playground"),
		@Menu(label = "Workspace"),
		@Menu(label = "Set Bio-Formats Memo Directory")
	},
	description = "Sets the folder where Bio-Formats stores its memo (.bfmemo) caching files. " +
		"Useful when image folders are read-only.")
public class SetBioFormatsMemoDirectoryCommand implements Command {

	@Parameter(label = "Memo directory",
		style = "directory",
		description = "Folder where Bio-Formats memo (.bfmemo) files will be stored.",
		persist = false)
	File memo_directory = BioFormatsHelper.getMemoDir();

	@Parameter(label = "Reset to default location",
		description = "Ignore the chosen folder and revert to the default (<user.home>/.bf-memo).",
		required = false)
	boolean reset_to_default = false;

	@Parameter
	LogService logger;

	@Override
	public void run() {
		if (reset_to_default) {
			BioFormatsHelper.setPersistedMemoDir(null);
			BioFormatsHelper.setMemoDir(null);
			logger.info("Bio-Formats memo directory reset to default: " +
				BioFormatsHelper.getMemoDir().getAbsolutePath());
			return;
		}

		if (memo_directory == null) {
			logger.warn("No memo directory provided, leaving the setting unchanged.");
			return;
		}

		// Persist across sessions and apply immediately for the current one
		BioFormatsHelper.setPersistedMemoDir(memo_directory);
		BioFormatsHelper.setMemoDir(memo_directory);
		logger.info("Bio-Formats memo directory set to: " +
			memo_directory.getAbsolutePath());
	}

}
