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

import bdv.img.cache.CacheArrayLoader;
import ch.epfl.biop.bdv.img.PixelReadingException;
import ch.epfl.biop.bdv.img.ResourcePool;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Checks what an array loader does when a block of pixels cannot be read - the
 * everyday case being a file whose codec is missing, such as the JPEG-2000
 * compressed .vsi slides on a class path without {@code ome-jai}.
 * <p>
 * This used to be reported as an {@link InterruptedException}, which the imglib2
 * caches take as a cancellation: they re-assert the interrupted flag on the
 * calling thread, and the next Bio-Formats read on that thread then dies inside
 * NIO with a {@code ClosedByInterruptException} mentioning neither the file nor
 * the real problem. The failing reader was not recycled either, so the pool lost
 * one permit per failure and eventually blocked forever.
 */
public class BioFormatsArrayLoaderFailureTest {

	private static final int[] BLOCK = { 4, 4, 1 };
	private static final long[] ORIGIN = { 0, 0, 0 };

	/** Hands out readers that fail the same way a missing codec does. */
	private static class FailingReaderPool extends ResourcePool<IFormatReader> {

		final AtomicInteger created = new AtomicInteger();
		final FormatException failure;

		FailingReaderPool(FormatException failure) {
			super(1, true);
			this.failure = failure;
		}

		@Override
		protected IFormatReader createObject() {
			created.incrementAndGet();
			return (IFormatReader) Proxy.newProxyInstance(
				IFormatReader.class.getClassLoader(),
				new Class[] { IFormatReader.class },
				(proxy, method, args) -> {
					String name = method.getName();
					if (name.equals("openBytes")) throw failure;
					if (name.startsWith("getSize")) return 8;
					if (name.equals("getIndex")) return 0;
					Class<?> returnType = method.getReturnType();
					if (returnType == boolean.class) return false;
					if (returnType == int.class) return 0;
					return null;
				});
		}
	}

	private static CacheArrayLoader<VolatileByteArray> loaderOn(ResourcePool<IFormatReader> pool) {
		return new BioFormatsArrayLoaders.BioFormatsUnsignedByteArrayLoader(pool, 0, 0);
	}

	@Test(timeout = 10_000)
	public void aFailedReadKeepsItsCauseAndLeavesTheThreadAlone() throws Exception {
		FormatException missingCodec = new FormatException(
			"The JAI Image I/O Tools are required to read JPEG-2000 files.");
		CacheArrayLoader<VolatileByteArray> loader = loaderOn(new FailingReaderPool(missingCodec));

		try {
			loader.loadArray(0, 0, 0, BLOCK, ORIGIN);
			fail("the read should have failed");
		}
		catch (PixelReadingException e) {
			assertSame("the original failure has to survive", missingCodec, e.getCause());
		}
		finally {
			Thread.interrupted(); // do not leak the flag if this test regresses
		}

		assertFalse("a read failure is not a cancellation",
			Thread.currentThread().isInterrupted());
	}

	@Test(timeout = 10_000)
	public void repeatedFailuresDoNotDrainTheReaderPool() throws Exception {
		FailingReaderPool pool = new FailingReaderPool(new FormatException("nope"));
		CacheArrayLoader<VolatileByteArray> loader = loaderOn(pool);

		// The pool holds a single reader: unless it is recycled after the first
		// failure, the second call here waits for it forever.
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				loader.loadArray(0, 0, 0, BLOCK, ORIGIN);
				fail("the read should have failed");
			}
			catch (PixelReadingException expected) {
				// every attempt fails the same way, which is the point
			}
		}

		assertEquals("the same reader should have been reused", 1, pool.created.get());
	}

}
