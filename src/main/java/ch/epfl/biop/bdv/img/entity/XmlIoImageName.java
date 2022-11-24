package ch.epfl.biop.bdv.img.entity;

import mpicbg.spim.data.generic.base.ViewSetupAttributeIo;
import mpicbg.spim.data.generic.base.XmlIoNamedEntity;

/**
 * For xml serialization and deserialization of {@link ImageName}
 */
@ViewSetupAttributeIo(name = "imagename", type = ImageName.class)
public class XmlIoImageName extends XmlIoNamedEntity<ImageName> {

    public XmlIoImageName() {
        super("imagename", ImageName.class);
    }

}