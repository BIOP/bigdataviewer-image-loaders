package ch.epfl.biop.bdv.img.qupath;
/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;


/**
 * Read QuPath projects.
 * <p>
 *
 * @author Pete Bankhead
 */
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


    @SuppressWarnings("unchecked")
    public static  JsonObject loadRawProject(final File fileProject) throws IOException {
            logger.debug("Loading project from {}", fileProject);
            try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
                Gson gson = new Gson();
                JsonObject element = gson.fromJson(fileReader, JsonObject.class);
                // Didn't have the foresight to add a version number from the start...
                String version = element.has("version") ? element.get("version").getAsString() : null;
                if (version == null || Arrays.asList("v0.2.0-m2", "v0.2.0-m1").contains(version)) {
                    throw new IllegalArgumentException("Older-style project is not compatible with this current FIJI QuPath bridge ");
                    //				return LegacyProject.readFromFile(fileProject, cls);
                }
                return element;
            }
    }


    /**
     * Get the default extension for a QuPath project file.
     *
     * @param includePeriod include or not the period
     * @return the project extension (with or without the period)
     */
    public static String getProjectExtension(boolean includePeriod) {
        return includePeriod ? "." + DEFAULT_PROJECT_EXTENSION : DEFAULT_PROJECT_EXTENSION;
    }

    /**
     * Get the default extension for a QuPath project file, without the 'dot'.
     * @return the default extension for a QuPath project file, without the 'dot'.
     *
     * @see ProjectIO#getProjectExtension(boolean)
     */
    public static String getProjectExtension() {
        return DEFAULT_PROJECT_EXTENSION;
    }

}
