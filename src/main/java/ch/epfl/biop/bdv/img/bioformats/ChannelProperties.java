package ch.epfl.biop.bdv.img.bioformats;

import ch.epfl.biop.bdv.img.BioFormatsBdvOpener;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.model.units.BigResult;
import ome.units.UNITS;
import ome.xml.model.enums.PixelType;
import loci.formats.meta.IMetadata;

import omero.gateway.model.ChannelData;
import omero.gateway.model.PixelsData;
import omero.model.ChannelBinding;
import omero.model.RenderingDef;
import omero.model.enums.UnitsLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import static omero.gateway.model.PixelsData.*;
import static omero.gateway.model.PixelsData.UINT32_TYPE;


public class ChannelProperties {

    final protected static Logger logger = LoggerFactory.getLogger(
            ChannelProperties.class);
    public ARGBType color;
    String name;
    int nChannels;
    Type<? extends  NumericType> pixelType;
    Boolean isRGB;
    int iChannel = -1;
    int emissionWavelength = -1;
    int excitationWavelength = -1;

    final static int[] loopR = { 1, 0, 0, 1, 1, 1, 0 };
    final static int[] loopG = { 0, 1, 0, 1, 1, 0, 1 };
    final static int[] loopB = { 0, 0, 1, 1, 0, 1, 1 };

    public ChannelProperties(int iChannel){
        this.iChannel = iChannel;
    }


    public ChannelProperties setEmissionWavelength(int iSerie, IMetadata metadata){
        if (metadata.getChannelEmissionWavelength(iSerie, iChannel) != null) {
            this.emissionWavelength = metadata.getChannelEmissionWavelength(iSerie, iChannel)
                    .value(UNITS.NANOMETER).intValue();
        }
        else{
            this.emissionWavelength = -1;
        }
        return this;
    }

    public ChannelProperties setEmissionWavelength(ChannelData channelData) throws BigResult {
        if (channelData.getEmissionWavelength(UnitsLength.NANOMETER) != null) {
            this.emissionWavelength = (int) channelData.getEmissionWavelength(
                    UnitsLength.NANOMETER).getValue();
        }
        else{
            this.emissionWavelength = -1;
        }
        return this;
    }

    public ChannelProperties setExcitationWavelength(int iSerie, IMetadata metadata){
        if (metadata.getChannelExcitationWavelength(iSerie, iChannel) != null) {
            this.excitationWavelength = metadata.getChannelExcitationWavelength(iSerie, iChannel)
                    .value(UNITS.NANOMETER).intValue();
        }
        else{
            this.excitationWavelength = -1;
        }
        return this;
    }

    public ChannelProperties setExcitationWavelength(ChannelData channelData) throws BigResult {
        if (channelData.getExcitationWavelength(UnitsLength.NANOMETER) != null) {
            this.excitationWavelength = (int) channelData.getExcitationWavelength(
                    UnitsLength.NANOMETER).getValue();
        }
        else{
            this.excitationWavelength = -1;
        }
        return this;
    }

    public ChannelProperties setChannelColor(int iSerie, IMetadata metadata){

            ome.xml.model.primitives.Color c = metadata.getChannelColor(iSerie, this.iChannel);
            if (c != null) {
                logger.debug("c = [" + c.getRed() + "," + c.getGreen() + "," + c
                        .getBlue() + "]");
                this.color = new ARGBType(ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(),
                        255));
            }
            else {
                // in case channelColor is called before emissisonWavelength
                if(this.emissionWavelength == -1)
                    setEmissionWavelength(iSerie,metadata);
                if (this.emissionWavelength != -1) {
                    int emission = this.emissionWavelength;

                    logger.debug("emission = " + emission);
                    Color cAwt = getColorFromWavelength(emission);
                    this.color = new ARGBType(ARGBType.rgba(cAwt.getRed(), cAwt.getGreen(), cAwt
                            .getBlue(), 255));
                }
                else {
                    // Default colors based on iSerie index
                    this.color = new ARGBType(ARGBType.rgba(255 * loopR[this.iChannel % 7], 255 *
                            loopG[this.iChannel % 7], 255 * loopB[this.iChannel % 7], 255));
                }
            }
            return this;
    }


    public ChannelProperties setChannelColor(RenderingDef renderingDef){
        ChannelBinding cb = renderingDef.getChannelBinding(this.iChannel);

       this.color =  new ARGBType(ARGBType.rgba(cb.getRed().getValue(), cb.getGreen()
                .getValue(), cb.getBlue().getValue(), cb.getAlpha().getValue()));

       return this;
    }


    public ChannelProperties setChannelName(int iSerie, IMetadata metadata){
        if (metadata.getChannelName(iSerie, this.iChannel) != null) {
            this.name = metadata.getChannelName(iSerie, this.iChannel);
        }
        else {
            this.name = "ch_" + this.iChannel;
            logger.warn("No name found for serie " + iSerie + " ch " + this.iChannel +
                    " setting name to " + this.name);
        }
        return this;
    }

    public ChannelProperties setChannelName(ChannelData channelData){
       this.name = channelData.getChannelLabeling();
       return this;
    }

    public ChannelProperties setPixelType(Type<? extends  NumericType> pixelType){
        this.pixelType = pixelType;
        return this;
    }

    public ChannelProperties setRGB(Boolean RGB) {
        this.isRGB = RGB;
        return this;
    }

    public ChannelProperties setNChannels(int nChannels) {
        this.nChannels = nChannels;
        return this;
    }



    public static Color getColorFromWavelength(int wv) {
        // https://stackoverflow.com/questions/1472514/convert-light-frequency-to-rgb
        int[] res = waveLengthToRGB(wv);
        return new Color(res[0], res[1], res[2]);
    }

    /**
     * Taken from Earl F. Glynn's web page:
     * <a href="http://www.efg2.com/Lab/ScienceAndEngineering/Spectra.htm">Spectra
     * Lab Report</a> Return a RGB array encoding a color from an input wavelength
     * in nm
     */

    public static int[] waveLengthToRGB(double Wavelength) {
        double Gamma = 0.80;
        double IntensityMax = 255;
        double factor;
        double Red, Green, Blue;

        if ((Wavelength >= 380) && (Wavelength < 440)) {
            Red = -(Wavelength - 440) / (440 - 380);
            Green = 0.0;
            Blue = 1.0;
        }
        else if ((Wavelength >= 440) && (Wavelength < 490)) {
            Red = 0.0;
            Green = (Wavelength - 440) / (490 - 440);
            Blue = 1.0;
        }
        else if ((Wavelength >= 490) && (Wavelength < 510)) {
            Red = 0.0;
            Green = 1.0;
            Blue = -(Wavelength - 510) / (510 - 490);
        }
        else if ((Wavelength >= 510) && (Wavelength < 580)) {
            Red = (Wavelength - 510) / (580 - 510);
            Green = 1.0;
            Blue = 0.0;
        }
        else if ((Wavelength >= 580) && (Wavelength < 645)) {
            Red = 1.0;
            Green = -(Wavelength - 645) / (645 - 580);
            Blue = 0.0;
        }
        else if ((Wavelength >= 645) && (Wavelength < 781)) {
            Red = 1.0;
            Green = 0.0;
            Blue = 0.0;
        }
        else {
            Red = 0.0;
            Green = 0.0;
            Blue = 0.0;
        }

        // Let the intensity fall off near the vision limits

        if ((Wavelength >= 380) && (Wavelength < 420)) {
            factor = 0.3 + 0.7 * (Wavelength - 380) / (420 - 380);
        }
        else if ((Wavelength >= 420) && (Wavelength < 701)) {
            factor = 1.0;
        }
        else if ((Wavelength >= 701) && (Wavelength < 781)) {
            factor = 0.3 + 0.7 * (780 - Wavelength) / (780 - 700);
        }
        else {
            factor = 0.0;
        }

        int[] rgb = new int[3];

        // Don't want 0^x = 1 for x <> 0
        rgb[0] = Red == 0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Red *
                factor, Gamma));
        rgb[1] = Green == 0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Green *
                factor, Gamma));
        rgb[2] = Blue == 0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Blue *
                factor, Gamma));

        return rgb;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode() * this.pixelType.hashCode() * emissionWavelength * excitationWavelength *
                (iChannel + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChannelProperties) {
            ChannelProperties bc = (ChannelProperties) obj;
            return (isRGB == bc.isRGB) && (name.equals(bc.name)) && (pixelType
                    .equals(bc.pixelType)) && (iChannel == bc.iChannel) &&
                    (emissionWavelength == (bc.emissionWavelength)) &&
                    (excitationWavelength == (bc.excitationWavelength));
        }
        else {
            return false;
        }
    }
}
