/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * <a href="https://www.dbtsai.com/blog/2013/java-concurrent-dynamic-object-pool-for-non-thread-safe-objects-using-blocking-queue/">...</a>
 * Created with IntelliJ IDEA. User: dtsai Date: 2/18/13 Time: 3:42 PM
 * <p>
 * Copyright 2013 DB TSAI
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
public abstract class ResourcePool<Resource> {

	private final BlockingQueue<Resource> pool;
	private final ReentrantLock lock = new ReentrantLock();
	private int createdObjects = 0;
	final private int size;

	protected ResourcePool(int size) {
		this(size, false);
	}

	protected ResourcePool(int size, Boolean dynamicCreation) {
		// Enable the fairness; otherwise, some threads may wait forever.
		pool = new ArrayBlockingQueue<>(size, true);
		this.size = size;
		if (!dynamicCreation) {
			lock.lock();
		}
	}

	/**
	 * @return a resource from the existing pool if one is available, instead of creating a new one
	 * @throws Exception if the resource can't be created
	 */
	public Resource takeOrCreate() throws Exception {
		if (isClosed) throw new IllegalStateException("The pool has been closed");
		if (createdObjects>0) {
			return pool.take();
		}
		if (!lock.isLocked()) {
			if (lock.tryLock()) {
				try {
					++createdObjects;
					return createObject();
				}
				finally {
					if (createdObjects < size) lock.unlock();
				}
			}
		}
		return pool.take();
	}

	public Resource acquire() throws Exception {
		if (isClosed) throw new IllegalStateException("The pool has been closed");
		if (!lock.isLocked()) {
			if (lock.tryLock()) {
				try {
					++createdObjects;
					return createObject();
				}
				finally {
					if (createdObjects < size) lock.unlock();
				}
			}
		}
		return pool.take();
	}

	public void recycle(Resource resource) throws Exception {
		// Will throws Exception when the queue is full,
		// but it should never happen.
		pool.add(resource);
	}

	public void createPool() {
		if (isClosed) throw new IllegalStateException("The pool has been closed");
		if (lock.isLocked()) {
			for (int i = 0; i < size; ++i) {
				pool.add(createObject());
				createdObjects++;
			}
		}
	}

	protected abstract Resource createObject();

	volatile boolean isClosed = false;

	public synchronized void shutDown(Consumer<Resource> closer) {
		if (!isClosed) {
			isClosed = true;
			ArrayList<Resource> resources = new ArrayList<>(size);
			pool.drainTo(resources);
			resources.forEach(closer);
		}
	}

}
