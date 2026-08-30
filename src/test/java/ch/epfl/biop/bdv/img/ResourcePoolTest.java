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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Both tests below deadlock rather than fail when the behaviour they check
 * regresses, hence the timeouts: a pool that has lost all of its permits makes
 * every further acquisition block forever.
 */
public class ResourcePoolTest {

	/** A pool of one resource, which optionally fails to create the first one. */
	private static class CountingPool extends ResourcePool<Object> {

		final AtomicInteger created = new AtomicInteger();
		volatile boolean failNextCreation;

		CountingPool(boolean failNextCreation) {
			super(1, true);
			this.failNextCreation = failNextCreation;
		}

		@Override
		protected Object createObject() {
			if (failNextCreation) {
				failNextCreation = false;
				throw new IllegalStateException("Could not open the file");
			}
			created.incrementAndGet();
			return new Object();
		}
	}

	@Test(timeout = 10_000)
	public void aFailedCreationDoesNotCostThePoolAPermit() throws Exception {
		CountingPool pool = new CountingPool(true);

		try {
			pool.acquire();
			fail("acquire() should have reported the creation failure");
		}
		catch (IllegalStateException expected) {
			// the resource could not be created, which is what the pool has to survive
		}

		// The permit taken by the failed creation has to be given back, otherwise
		// this second call waits for a resource that will never be recycled.
		assertNotNull(pool.acquire());
		assertEquals(1, pool.created.get());
	}

	@Test(timeout = 10_000)
	public void acquireFailsFastOnAnAlreadyInterruptedThread() throws Exception {
		CountingPool pool = new CountingPool(false);

		Thread.currentThread().interrupt();
		try {
			pool.acquire();
			fail("acquire() should have refused to work on an interrupted thread");
		}
		catch (InterruptedException expected) {
			// Opening a file here would fail inside NIO with a
			// ClosedByInterruptException naming neither the file nor the reason.
		}
		finally {
			Thread.interrupted(); // do not leak the flag into the next test
		}

		assertEquals("no resource should have been created", 0, pool.created.get());
	}

}
