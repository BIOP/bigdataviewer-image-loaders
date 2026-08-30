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

package ch.epfl.biop.bdv.img;

import bdv.img.cache.CacheArrayLoader;

import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileLockInterruptionException;

/**
 * Thrown when a block of pixels could not be read: missing codec, corrupted file,
 * lost connection to an OMERO server, unsupported block geometry, ...
 * <p>
 * {@link CacheArrayLoader#loadArray} may only throw {@link InterruptedException},
 * so every array loader of this library used to report <i>any</i> failure as an
 * {@code InterruptedException}. That is actively harmful: the imglib2 caches take
 * an {@code InterruptedException} as a cancellation and re-assert the interrupted
 * flag on the calling thread (see {@code WeakRefLoaderCache#get}). The next
 * Bio-Formats read on that thread then dies inside NIO with a
 * {@link ClosedByInterruptException} that mentions neither the file nor the real
 * problem - so a plain "missing JPEG-2000 codec" surfaced as an unrelated channel
 * error, several operations later.
 * <p>
 * A read failure is unchecked instead, which {@code loadArray} is free to throw,
 * and which keeps both the type and the cause of the original error. Use
 * {@link #rethrow(Exception)} so that genuine cancellations stay
 * {@code InterruptedException}s.
 */
public class PixelReadingException extends RuntimeException {

	public PixelReadingException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Reports a failure that happened while reading a block of pixels.
	 * <p>
	 * A genuine cancellation - {@link InterruptedException}, or an NIO operation
	 * aborted because the thread was interrupted - is rethrown as an
	 * {@code InterruptedException} with the interrupted flag restored, which is what
	 * the caches expect. Anything else is a real read error and is rethrown
	 * unchecked, cause included.
	 *
	 * @param e the exception caught while reading
	 * @return never returns, the return type only lets callers write
	 *         {@code throw PixelReadingException.rethrow(e);} so that the compiler
	 *         knows the catch block does not fall through
	 * @throws InterruptedException if the read was cancelled rather than failed
	 */
	public static RuntimeException rethrow(Exception e)
		throws InterruptedException
	{
		if (e instanceof InterruptedException) {
			throw (InterruptedException) e;
		}
		if (e instanceof ClosedByInterruptException ||
			e instanceof FileLockInterruptionException)
		{
			// The channel was closed because this thread was interrupted: this is a
			// cancellation, not a broken file.
			Thread.currentThread().interrupt();
			InterruptedException interrupted = new InterruptedException(
				"Reading was interrupted");
			interrupted.initCause(e);
			throw interrupted;
		}
		if (e instanceof PixelReadingException) {
			return (PixelReadingException) e;
		}
		return new PixelReadingException("Could not read pixels: " + e, e);
	}

}
