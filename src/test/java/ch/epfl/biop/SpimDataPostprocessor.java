/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.module.Module;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Plugin(type = PostprocessorPlugin.class)
public class SpimDataPostprocessor extends AbstractPostprocessorPlugin {

	protected static final Logger logger = LoggerFactory.getLogger(
		SpimDataPostprocessor.class);


	@Override
	public void process(Module module) {

		module.getOutputs().forEach((name, object) -> {
			// log.accept("input:\t"+name+"\tclass:\t"+object.getClass().getSimpleName());
			if (object instanceof AbstractSpimData) {
				AbstractSpimData<?> asd = (AbstractSpimData<?>) object;
				BdvFunctions.show(asd);
				module.resolveOutput(name);
			}
			if (object instanceof AbstractSpimData<?>[]) {
				BdvHandle bdvh = null;
				AbstractSpimData<?>[] asds = (AbstractSpimData<?>[]) object;
				module.resolveOutput(name);
				for (AbstractSpimData<?> asd : asds) {
					if (bdvh == null) {
						bdvh = BdvFunctions.show(asd).get(0).getBdvHandle();
					} else {
						BdvFunctions.show(asd, BdvOptions.options().addTo(bdvh));
					}
				}
			}
		});
	}
}
