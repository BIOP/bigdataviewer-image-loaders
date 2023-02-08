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

package ch.epfl.biop.bdv.img.legacy.qupath.entity;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import mpicbg.spim.data.generic.base.NamedEntity;

import java.net.URI;

@Deprecated
public class QuPathEntryEntity extends NamedEntity implements
	Comparable<QuPathEntryEntity>
{

	String quPathProjectionLocation;

	public void setQuPathProjectionLocation(String quPathProjectionLocation) {
		this.quPathProjectionLocation = quPathProjectionLocation;
	}

	public String getQuPathProjectionLocation() {
		return quPathProjectionLocation;
	}

	public QuPathEntryEntity(final int id, final String name) {
		super(id, name);
	}

	public QuPathEntryEntity(final int id) {
		this(id, Integer.toString(id));
	}

	/**
	 * Set the name of this tile.
	 */
	@Override
	public void setName(final String name) {
		super.setName(name);
	}

	/**
	 * Compares the {@link #getId() ids}.
	 */
	@Override
	public int compareTo(final QuPathEntryEntity o) {
		return getId() - o.getId();
	}

	protected QuPathEntryEntity() {}

	public static String getNameFromURIAndSerie(URI uri, int iSerie) {
		return new Gson().toJson(new QuPathSourceIdentifier(uri, iSerie));
	}

	public static URI getUri(String name) {
		return new Gson().fromJson(JsonParser.parseString(name),
			QuPathSourceIdentifier.class).uri;
	}

	public static int getSerie(String name) {
		return new Gson().fromJson(JsonParser.parseString(name),
			QuPathSourceIdentifier.class).iSerie;
	}

	public static class QuPathSourceIdentifier {

		final URI uri;
		final int iSerie;

		public QuPathSourceIdentifier(URI uri, int iSerie) {
			this.iSerie = iSerie;
			this.uri = uri;
		}
	}
}
