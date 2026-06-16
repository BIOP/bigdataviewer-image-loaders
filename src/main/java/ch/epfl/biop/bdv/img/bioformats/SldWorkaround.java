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

package ch.epfl.biop.bdv.img.bioformats;

import loci.formats.IFormatReader;
import loci.formats.Memoizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * TEMPORARY WORKAROUND for 3i SlideBook ({@code .sld}) files read through
 * Bio-Formats. It addresses two issues that are expected to be fixed upstream:
 *
 * <ol>
 *   <li><b>Stale memo across JVM restarts.</b> A {@code .bfmemo} file written
 *   for a {@code .sld} image works while the JVM that produced it is alive, but
 *   fails to deserialize after a restart. We detect memo files that predate the
 *   current JVM session and delete them so Bio-Formats regenerates a fresh,
 *   working memo in-session (memoization stays fast within a session).</li>
 *
 *   <li><b>Closing breaks reopening.</b> Closing a {@code .sld} reader makes a
 *   subsequent open of the same file fail. We therefore never close
 *   {@code .sld} readers (an intentional, bounded leak).</li>
 * </ol>
 *
 * Everything here keys solely off the {@code .sld} file extension and depends
 * only on the generic {@link Memoizer} (never on the SlideBook reader class).
 * To remove the workaround once the upstream bugs are fixed: delete this class
 * and revert the call sites marked with {@code SLD WORKAROUND} in
 * {@link BioFormatsOpener} and {@link BioFormatsHelper}.
 */
public class SldWorkaround {

	private static final Logger logger = LoggerFactory.getLogger(SldWorkaround.class);

	/** Marks the start of the current JVM session: any memo file last modified
	 *  before this instant was produced by a previous session. */
	private static final long SESSION_START_MILLIS = System.currentTimeMillis();

	/** Data locations whose stale memo has already been handled this session,
	 *  so the check runs only once per file (and avoids races between the
	 *  concurrent openers that share a file). */
	private static final Set<String> validatedThisSession =
		Collections.synchronizedSet(new HashSet<>());

	private SldWorkaround() {}

	/**
	 * @param dataLocation file path or URL of the image
	 * @return true if the location refers to a SlideBook {@code .sld} file
	 */
	public static boolean isSld(String dataLocation) {
		return dataLocation != null &&
			dataLocation.toLowerCase().trim().endsWith(".sld");
	}

	/**
	 * For {@code .sld} files, deletes any {@code .bfmemo} file left over from a
	 * previous JVM session so that a fresh, working memo is regenerated during
	 * the current session. No-op for other formats. Memoization is kept enabled
	 * in both cases; this only clears stale memos. Runs at most once per file
	 * per session.
	 *
	 * @param dataLocation file path or URL of the image
	 * @param memoDir directory in which Bio-Formats memo files are stored
	 *                (see {@link BioFormatsHelper#getMemoDir()})
	 */
	public static void prepareMemoization(String dataLocation, File memoDir) {
		if (!isSld(dataLocation)) return;
		if (!validatedThisSession.add(dataLocation)) return; // already handled

		try {
			// getMemoFile(String) is public on Memoizer and gives the exact memo
			// path for the configured directory, so we don't replicate its logic.
			File memoFile = new Memoizer(Memoizer.DEFAULT_MINIMUM_ELAPSED, memoDir)
				.getMemoFile(dataLocation);
			if (memoFile != null && memoFile.exists() &&
				memoFile.lastModified() < SESSION_START_MILLIS)
			{
				if (memoFile.delete()) {
					logger.info("SLD workaround: deleted stale memo from a previous " +
						"session for " + dataLocation);
				} else {
					logger.warn("SLD workaround: could not delete stale memo " +
						memoFile.getAbsolutePath());
				}
			}
		} catch (Exception e) {
			logger.warn("SLD workaround: could not check memo for " + dataLocation, e);
		}
	}

	/**
	 * Closes the given reader, unless it is reading a {@code .sld} file, in which
	 * case closing is skipped to keep subsequent reopening working.
	 *
	 * @param reader the reader to close (may be a {@link Memoizer} wrapper)
	 */
	public static void closeUnlessSld(IFormatReader reader) {
		if (reader == null) return;
		if (isSld(reader.getCurrentFile())) {
			logger.debug("SLD workaround: skipping close of " + reader.getCurrentFile());
			return;
		}
		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
