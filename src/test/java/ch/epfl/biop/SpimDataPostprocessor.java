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

package ch.epfl.biop;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.module.Module;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Plugin(type = PostprocessorPlugin.class)
public class SpimDataPostprocessor extends AbstractPostprocessorPlugin {

	protected static final Logger logger = LoggerFactory.getLogger(
		SpimDataPostprocessor.class);


	@Parameter
	ObjectService objectService;

	@Override
	public void process(Module module) {

		module.getOutputs().forEach((name, object) -> {
			// log.accept("input:\t"+name+"\tclass:\t"+object.getClass().getSimpleName());
			if (object instanceof AbstractSpimData) {
				AbstractSpimData<?> asd = (AbstractSpimData<?>) object;
				BdvFunctions.show(asd);
				module.resolveOutput(name);
				objectService.addObject(asd);
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
					objectService.addObject(asd);
				}
			}
		});
	}
}
