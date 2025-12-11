/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
package ch.epfl.biop.bdv.img.opener;

import com.google.gson.Gson;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import ome.model.units.BigResult;
import ome.units.UNITS;
import loci.formats.meta.IMetadata;

import omero.gateway.model.ChannelData;
import omero.model.ChannelBinding;
import omero.model.RenderingDef;
import omero.model.enums.UnitsLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;


/**
 *
 */
public class ChannelProperties {

    final protected static Logger logger = LoggerFactory.getLogger(
            ChannelProperties.class);

    // Channel core
    String name = "";
    int nChannels = 1;
    final int iChannel;


    // pixel infos
    double displayRangeMin = 0.0;
    double displayRangeMax = 255.0;
    transient Type<? extends  NumericType<?>> pixelType;
    Boolean isRGB = false;

    // Wavelength and color
    transient public ARGBType color;
    int emissionWavelength = -1;
    int excitationWavelength = -1;


    // LUT
    final static int[] loopR = { 1, 0, 0, 1, 1, 1, 0 };
    final static int[] loopG = { 0, 1, 0, 1, 1, 0, 1 };
    final static int[] loopB = { 0, 0, 1, 1, 0, 1, 1 };


    // GETTERS
    public ARGBType getColor() {
        return color;
    }
    public String getChannelName() {
        return name;
    }
    public double getDisplayRangeMin() {
        return displayRangeMin;
    }
    public double getDisplayRangeMax() {
        return displayRangeMax;
    }

    /**
     * Constructor with the channel ID
     * @param iChannel
     */
    public ChannelProperties(int iChannel){
        this.iChannel = iChannel;
    }

    // BUILDER PATTERN

    /**
     * For BioFormats
     * @param iSerie
     * @param metadata
     * @return
     */
    public ChannelProperties setEmissionWavelength(int iSerie, IMetadata metadata){
        try {
            if (metadata.getChannelEmissionWavelength(iSerie, iChannel) != null) {
                this.emissionWavelength = metadata.getChannelEmissionWavelength(iSerie, iChannel)
                        .value(UNITS.NANOMETER).intValue();
            } else {
                this.emissionWavelength = -1;
            }
        } catch (Exception e) {
            logger.warn("Error: "+e.getMessage()+" caught when trying to get the channel emission wavelength");
            emissionWavelength = -1;
        }
        return this;
    }

    /**
     * For OMERO
     * @param channelData
     * @return
     * @throws BigResult
     */
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

    /**
     * For BioFormats
     * @param iSerie
     * @param metadata
     * @return
     */
    public ChannelProperties setExcitationWavelength(int iSerie, IMetadata metadata){
        try {
            if (metadata.getChannelExcitationWavelength(iSerie, iChannel) != null) {
                this.excitationWavelength = metadata.getChannelExcitationWavelength(iSerie, iChannel)
                        .value(UNITS.NANOMETER).intValue();
            } else {
                this.excitationWavelength = -1;
            }
        } catch (Exception e) {
            logger.warn("Error: "+e.getMessage()+" caught when trying to get the channel excitation wavelength.");
            excitationWavelength = -1;
        }
        return this;
    }

    /**
     * For OMERO
     * @param channelData
     * @return
     * @throws BigResult
     */
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


    /**
     * For BioFormats
     * @param iSerie
     * @param metadata
     * @return
     */
    public ChannelProperties setChannelColor(int iSerie, IMetadata metadata){
            try {
                ome.xml.model.primitives.Color c = metadata.getChannelColor(iSerie, this.iChannel);
                if (c != null) {
                    logger.debug("c = [" + c.getRed() + "," + c.getGreen() + "," + c
                            .getBlue() + "]");
                    this.color = new ARGBType(ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(),
                            255));
                } else {
                    // in case channelColor is called before emission Wavelength
                    if (this.emissionWavelength == -1)
                        setEmissionWavelength(iSerie, metadata);
                    if (this.emissionWavelength != -1) {
                        int emission = this.emissionWavelength;

                        logger.debug("emission = " + emission);
                        Color cAwt = getColorFromWavelength(emission);
                        this.color = new ARGBType(ARGBType.rgba(cAwt.getRed(), cAwt.getGreen(), cAwt
                                .getBlue(), 255));
                    } else {
                        // Default colors based on iSerie index
                        this.color = new ARGBType(ARGBType.rgba(255 * loopR[this.iChannel % 7], 255 *
                                loopG[this.iChannel % 7], 255 * loopB[this.iChannel % 7], 255));
                    }
                }
            } catch (Exception e) {
                logger.warn("Error: "+e.getMessage()+" caught when trying to get the channel color");
                this.color = new ARGBType(ARGBType.rgba(255 * loopR[this.iChannel % 7], 255 *
                        loopG[this.iChannel % 7], 255 * loopB[this.iChannel % 7], 255));
            }
            return this;
    }


    /**
     * For OMERO
     * @param renderingDef
     * @return
     */
    public ChannelProperties setChannelColor(RenderingDef renderingDef){
       if (renderingDef == null) {
           logger.warn("No rendering definition found!");
           this.color =  new ARGBType(ARGBType.rgba(
                   (iChannel%3)==0?255:0,
                   ((iChannel+1)%3)==0?255:0,
                   ((iChannel+2)%3)==0?255:0,
                   255));
           return this;
       }

       ChannelBinding cb = renderingDef.getChannelBinding(this.iChannel);

       this.color =  new ARGBType(ARGBType.rgba(cb.getRed().getValue(), cb.getGreen()
                .getValue(), cb.getBlue().getValue(), cb.getAlpha().getValue()));

       return this;
    }


    /**
     * Generic builder for channel color
     * @param colorIdx
     * @return
     */
    public ChannelProperties setChannelColor(int colorIdx){
        Color color = new Color(colorIdx);

        this.color =  new ARGBType(ARGBType.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        return this;
    }

    public ChannelProperties setChannelColor(ARGBType color){
        this.color = color;
        return this;
    }


    /**
     * For BioFormats
     * @param iSerie
     * @param metadata
     * @return
     */
    public ChannelProperties setChannelName(int iSerie, IMetadata metadata){
        String channelName;
        try {
            channelName = metadata.getChannelName(iSerie, this.iChannel);
        } catch (Exception e) {
            logger.warn("Error: "+e.getMessage()+" caught when trying to get the channel name");
            channelName = null;
        }
        if (channelName != null && !channelName.isEmpty()) {
            this.name = metadata.getChannelName(iSerie, this.iChannel);
        }
        else {
            this.name = "ch_" + this.iChannel;
            logger.warn("No name found for serie " + iSerie + " ch " + this.iChannel +
                    " setting name to " + this.name);
        }
        return this;
    }

    /**
     * For OMERO
     * @param channelData
     * @return
     */
    public ChannelProperties setChannelName(ChannelData channelData){
       this.name = channelData.getChannelLabeling();
       return this;
    }


    /**
     * Generic builder for channel name
     * @param name
     * @return
     */
    public ChannelProperties setChannelName(String name){
        this.name = name;
        return this;
    }

    /**
     * For OMERO
     * @param rd
     * @return
     */
    public ChannelProperties setDynamicRange(RenderingDef rd){
        if (rd == null) {
            this.displayRangeMin = 0;
            this.displayRangeMax = 255;
            return this;
        }
        this.displayRangeMin = rd.getChannelBinding(this.iChannel).getInputStart().getValue();
        this.displayRangeMax = rd.getChannelBinding(this.iChannel).getInputEnd().getValue();
        return this;
    }

    /**
     * Default Dynamic range
     * @return
     */
    public ChannelProperties setDisplayRange(double min, double max){
        this.displayRangeMin = min;
        this.displayRangeMax = max;
        return this;
    }

    /**
     * Generic builder
     * @param pixelType
     * @return
     */
    public ChannelProperties setPixelType(Type<? extends  NumericType<?>> pixelType){
        this.pixelType = pixelType;
        return this;
    }

    /**
     * is the channel part of an RGB image
     * @param RGB
     * @return
     */
    public ChannelProperties setRGB(Boolean RGB) {
        this.isRGB = RGB;
        return this;
    }

    /**
     * How many channels the image containing the current channel is made of.
     * @param nChannels
     * @return
     */
    public ChannelProperties setNChannels(int nChannels) {
        this.nChannels = nChannels;
        return this;
    }


    /**
     * taken from <a href="https://stackoverflow.com/questions/1472514/convert-light-frequency-to-rgb">...</a>
     * @param wv
     * @return
     */
    public static Color getColorFromWavelength(int wv) {
        int[] res = waveLengthToRGB(wv);
        return new Color(res[0], res[1], res[2]);
    }

    /**
     * Taken from Earl F. Glynn's web page:
     * <a href="http://www.efg2.com/Lab/ScienceAndEngineering/Spectra.htm">Spectra
     * Lab Report</a> Return an RGB array encoding a color from an input wavelength
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
        return this.name.hashCode() * this.pixelType.getClass().hashCode() * emissionWavelength * excitationWavelength *
                (iChannel + 1) * 1/*this.color.hashCode()*/ * this.nChannels;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChannelProperties) {
            ChannelProperties bc = (ChannelProperties) obj;
            return (isRGB == bc.isRGB) && (name.equals(bc.name)) && (pixelType.getClass()
                    .equals(bc.pixelType.getClass())) && (iChannel == bc.iChannel) &&
                    (emissionWavelength == (bc.emissionWavelength)) &&
                    (excitationWavelength == (bc.excitationWavelength)) &&
                    (nChannels == (bc.nChannels)) && (color.get() == (bc.color.get()));
        }
        else {
            return false;
        }
    }

    public String toString() {
        return new Gson().toJson(this);
    }
}
