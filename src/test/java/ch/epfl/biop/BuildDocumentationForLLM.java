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

import org.reflections.Reflections;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class BuildDocumentationForLLM {
    static String doc = "";

    public static void main(String... args) {
        //

        Reflections reflections = new Reflections("ch.epfl.biop");

        Set<Class<? extends Command>> commandClasses =
                reflections.getSubTypesOf(Command.class);

        HashMap<String, String> docPerClass = new HashMap<>();

        commandClasses.forEach(c -> {

            Plugin plugin = c.getAnnotation(Plugin.class);
            if (plugin!=null) {
                //String url = linkGitHubRepoPrefix+c.getName().replaceAll("\\.","\\/")+".java";
                doc = "# " + c.getName() + "\n";
                if (!plugin.label().isEmpty()) {
                    doc += "Label: " + plugin.label() + "\n";
                }
                if (!plugin.description().isEmpty()) {
                    doc += "Description: " + plugin.description() + "\n";
                }

                Field[] fields = c.getDeclaredFields();
                List<Field> inputFields = Arrays.stream(fields)
                        .filter(f -> f.isAnnotationPresent(Parameter.class))
                        .filter(f -> {
                            Parameter p = f.getAnnotation(Parameter.class);
                            return (p.type() == ItemIO.INPUT) || (p.type() == ItemIO.BOTH);
                        }).sorted(Comparator.comparing(Field::getName)).collect(Collectors.toList());
                if (!inputFields.isEmpty()) {
                    doc += "## Input\n";
                    inputFields.forEach(f -> {
                        doc += f.getType().getSimpleName()+" " + f.getName() + "; // " + f.getAnnotation(Parameter.class).label() + "\n";
                        if (!f.getAnnotation(Parameter.class).description().isEmpty())
                            doc += f.getAnnotation(Parameter.class).description() + "\n";
                    });
                }

                List<Field> outputFields = Arrays.stream(fields)
                        .filter(f -> f.isAnnotationPresent(Parameter.class))
                        .filter(f -> {
                            Parameter p = f.getAnnotation(Parameter.class);
                            return (p.type() == ItemIO.OUTPUT) || (p.type() == ItemIO.BOTH);
                        }).sorted(Comparator.comparing(Field::getName)).collect(Collectors.toList());
                if (!outputFields.isEmpty()) {
                    doc += "## Output\n";
                    outputFields.forEach(f -> {
                        doc += f.getType().getSimpleName()+" " + f.getName() + "; // " + f.getAnnotation(Parameter.class).label() + "\n";
                        if (!f.getAnnotation(Parameter.class).description().isEmpty())
                            doc += f.getAnnotation(Parameter.class).description() + "\n";
                    });
                } else {
                    doc += "## Output\n";
                }

                doc+="\n";

                docPerClass.put(c.getName(),doc);
            }
        });
        Object[] keys = docPerClass.keySet().toArray();
        Arrays.sort(keys);
        for (Object key:keys) {
            String k = (String) key;
            System.out.println(docPerClass.get(k));
        }
    }
}
