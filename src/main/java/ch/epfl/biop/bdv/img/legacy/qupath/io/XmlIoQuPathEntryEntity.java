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

package ch.epfl.biop.bdv.img.legacy.qupath.io;


import ch.epfl.biop.bdv.img.legacy.qupath.entity.QuPathEntryEntity;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.base.ViewSetupAttributeIo;
import mpicbg.spim.data.generic.base.XmlIoNamedEntity;
import org.jdom2.Element;

@Deprecated
@ViewSetupAttributeIo(name = "qupathentryentity",
        type = QuPathEntryEntity.class)
public class XmlIoQuPathEntryEntity extends
        XmlIoNamedEntity<QuPathEntryEntity>
{

    public XmlIoQuPathEntryEntity() {
        super("qupathentryentity", QuPathEntryEntity.class);
    }

    @Override
    public Element toXml(final QuPathEntryEntity qpee) {
        final Element elem = super.toXml(qpee);
        elem.addContent(XmlHelpers.textElement("QuPathProjectLocation", qpee
                .getQuPathProjectionLocation()));
        return elem;
    }

    @Override
    public QuPathEntryEntity fromXml(final Element elem)
            throws SpimDataException
    {
        final QuPathEntryEntity qupathEntry = super.fromXml(elem);

        qupathEntry.setQuPathProjectionLocation(elem.getChildText(
                "QuPathProjectLocation"));
        return qupathEntry;
    }
}
