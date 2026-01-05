
package ch.epfl.biop.bdv.img.legacy.qupath.struct;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Class copied from QuPath to facilitates deserialisation of a QuPath project
 * Read QuPath projects.
 * <p>
 *
 * @author Pete Bankhead
 */

@Deprecated
public class ProjectIO {

	final private static Logger logger = LoggerFactory.getLogger(ProjectIO.class);

	/**
	 * Default file name for a QuPath project.
	 */
	public static final String DEFAULT_PROJECT_NAME = "project";

	/**
	 * Default file extension for a QuPath project.
	 */
	public static final String DEFAULT_PROJECT_EXTENSION = "qpproj";

	public static JsonObject loadRawProject(final File fileProject)
		throws IOException
	{
		logger.debug("Loading project from {}", fileProject);
		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))) {
			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			// Didn't have the foresight to add a version number from the start...
			String version = element.has("version") ? element.get("version")
				.getAsString() : null;
			if (version == null || Arrays.asList("v0.2.0-m2", "v0.2.0-m1").contains(
				version))
			{
				throw new IllegalArgumentException(
					"Older-style project is not compatible with this current FIJI QuPath bridge ");
				// return LegacyProject.readFromFile(fileProject, cls);
			}
			return element;
		}
	}

}
