package ch.epfl.biop.bdv.img.entity;

import mpicbg.spim.data.generic.base.ViewSetupAttributeIo;
import mpicbg.spim.data.generic.base.XmlIoNamedEntity;

@ViewSetupAttributeIo(name = "channelname", type = ChannelName.class)
public class XmlIoChannelName extends XmlIoNamedEntity<ChannelName> {

    public XmlIoChannelName() {
        super("channelname", ChannelName.class);
    }

}