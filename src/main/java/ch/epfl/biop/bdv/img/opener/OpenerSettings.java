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
package ch.epfl.biop.bdv.img.opener;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsOpener;
import ch.epfl.biop.bdv.img.omero.OmeroOpener;
import ch.epfl.biop.bdv.img.qupath.QuPathOpener;
import com.google.gson.Gson;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
//import omero.model.enums.UnitsLength;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Equivalent to a Builder class, serializable, which can create an {@link Opener}
 * An opener can open and stream data for a 5D image (XYZCT)
 *
 * This builder class should be easily serializable
 *
 * Opener can open data from
 * - BioFormats
 * - Omero
 * - More to come...
 *
 * Depending on the kind of opener, transient fields are required (Gateway and Context) for Omero
 *
 * */
public class OpenerSettings {

    transient static Logger logger = LoggerFactory.getLogger(OpenerSettings.class);

    transient Context scijavaContext;

    // --------- Extensibility
    String opt = ""; // options

    //---- Modifications on the location of the dataset ( pixel size, origin, flip)
    // all transient because they are used only on first initialisation,
    // after, all these modifications are stored and serialized in the view transforms
    transient double[] positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    transient double[] positionPostTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    transient boolean positionIsImageCenter = false; // Top left corner otherwise

    //---- Target unit : the unit in which the image will be opened
    transient Length defaultSpaceUnit = new Length(1,UNITS.MICROMETER);
    transient Length defaultVoxelUnit = new Length(1,UNITS.MICROMETER);
    String unit = "MICROMETER";//UnitsLength.MICROMETER.toString();

    //---- How to open the dataset (block size, number of readers per image)
    int nReader = 10; // parallel reading : number of pixel readers allowed
    boolean defaultBlockSize = true; // The block size chosen is let to be defined by the opener implementation itself
    //FinalInterval blockSize = new FinalInterval(new long[] { 0, 0,
    //        0 }, new long[] { 512, 512, 1 }); // Default cache block size, if none is defined
    int[] blockSize = new int[]{512,512,1};

    //-------- Channels options
    boolean splitRGB = false; // Should be true for 16 bits RGB channels like we have in CZI, Imglib2, the library used after, do not have a specific type class for 16 bits RGB pixels

    // ---- Opener core options
    OpenerType type = OpenerType.UNDEF;
    String location = "";

    // ---- For BioFormats: series index
    // ---- For QuPath: entryID
    int id = -1;

    // In case the opener can't be opened, we need at least to know the number of channels in order
    // to open a fake dataset on the next time
    int nChannels = -1;

    public OpenerSettings positionConvention(String position_convention) {
        if (position_convention.equals("CENTER")) {
            return this.centerPositionConvention();
        }
        return this.cornerPositionConvention();
    }

    public OpenerType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public int getNChannels() {
        return nChannels;
    }

    public int getEntryId() { return id; }
    public int getSeries() { return id; }

    public enum OpenerType {
        BIOFORMATS,
        OMERO,
        IMAGEJ,
        OPENSLIDE,
        QUPATH,
        UNDEF
    }

    public OpenerSettings context(Context context) {
        this.scijavaContext = context;
        return this;
    }

    // ---- cache and readers
    public OpenerSettings readerPoolSize(int pSize){
        this.nReader = pSize;
        return this;
    }

    public OpenerSettings useDefaultCacheBlockSize(boolean flag) {
        defaultBlockSize = flag;
        return this;
    }

    public void setNChannels(int nChannels) {
        this.nChannels = nChannels;
    }

    public OpenerSettings cacheBlockSize(int sx, int sy, int sz) {
        defaultBlockSize = false;
        blockSize = new int[]{sx,sy,sz}; //new FinalInterval(sx, sy, sz);
        return this;
    }

    // All space transformation methods
    public OpenerSettings flipPositionXYZ() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D()
                    .getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(-1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OpenerSettings flipPositionX() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D()
                    .getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(-1, 1, 1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OpenerSettings flipPositionY() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D()
                    .getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(1, -1, 1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OpenerSettings flipPositionZ() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D()
                    .getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(1, 1, -1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OpenerSettings setPositionPreTransform(AffineTransform3D at3d) {
        positionPreTransformMatrixArray = at3d.getRowPackedCopy();
        return this;
    }

    public OpenerSettings setPositionPostTransform(AffineTransform3D at3d) {
        positionPostTransformMatrixArray = at3d.getRowPackedCopy();
        return this;
    }

    public OpenerSettings centerPositionConvention() {
        this.positionIsImageCenter = true;
        return this;
    }

    public OpenerSettings cornerPositionConvention() {
        this.positionIsImageCenter = false;
        return this;
    }

    // reference frames
    public OpenerSettings positionReferenceFrameLength(Length l)
    {
        this.defaultSpaceUnit = l;
        return this;
    }

    public OpenerSettings voxSizeReferenceFrameLength(Length l)
    {
        this.defaultVoxelUnit = l;
        return this;
    }


    // data location
    public OpenerSettings location(String location) {
        this.location = location;
        return this;
    }

    public OpenerSettings location(URI uri) throws URISyntaxException {
        if(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
            this.location = uri.toString();
        else {
            URI newuri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
            this.location = Paths.get(newuri).toString();
        }
        return this;
    }

    public OpenerSettings location(File f) {
        this.location = f.getAbsolutePath();
        return this;
    }


    // channels
    public OpenerSettings splitRGBChannels() {
        splitRGB = true;
        return this;
    }

    // channels
    public OpenerSettings splitRGBChannels(boolean flag) {
        splitRGB = flag;
        return this;
    }

    // define unit
    /*public OpenerSettings unit(UnitsLength u) {
        this.unit = u.toString();
        return this;
    }*/

   public OpenerSettings unit(String u) {
       this.unit = u;
       return this;
    }

    public OpenerSettings unit(UNITS u) {
        this.unit = u.getName();
        return this;
    }

    public OpenerSettings unit(Unit<Length> u) {
        this.unit = u.getSymbol();
        return this;
    }

    public OpenerSettings millimeter() {
        this.unit = UNITS.MILLIMETER.toString();//UnitsLength.MILLIMETER.toString();
        return this;
    }

    public OpenerSettings micrometer() {
        this.unit = UNITS.MICROMETER.toString();//UnitsLength.MICROMETER.toString();
        return this;
    }

    public OpenerSettings nanometer() {
        this.unit = UNITS.NANOMETER.toString();//UnitsLength.NANOMETER.toString();
        return this;
    }


    // define which kind of builder to deal with
    public OpenerSettings omeroBuilder(){
        this.type = OpenerType.OMERO;
        return this;
    }

    public OpenerSettings bioFormatsBuilder(){
        this.type = OpenerType.BIOFORMATS;
        return this;
    }

    public OpenerSettings imageJBuilder(){
        this.type = OpenerType.IMAGEJ;
        return this;
    }

    public OpenerSettings openSlideBuilder(){
        this.type = OpenerType.OPENSLIDE;
        return this;
    }

    public OpenerSettings quPathBuilder(){
        this.type = OpenerType.QUPATH;
        return this;
    }

    // BioFormats specific
    public OpenerSettings setSerie(int iSerie){
        this.id = iSerie;
        return this;
    }

    public OpenerSettings setEntry(int entryId){
        this.id = entryId;
        return this;
    }

    transient boolean skipMeta = false;

    public OpenerSettings skipMeta() {
        this.skipMeta = true;
        return this;
    }

    public Opener<?> create(Map<String, Object> cachedObjects) throws Exception {
        Opener<?> opener;
        switch (this.type) {
            case OMERO:
                opener = new OmeroOpener(
                        scijavaContext,
                        location,
                        nReader,
                        unit,
                        positionIsImageCenter,
                        cachedObjects,
                        nChannels,
                        skipMeta
                );
                break;
            case QUPATH:
                opener = new QuPathOpener<>(
                        scijavaContext,
                        location,
                        id,
                        unit,
                        positionIsImageCenter,
                        nReader,
                        defaultBlockSize,
                        blockSize,
                        splitRGB,
                        cachedObjects,
                        nChannels, skipMeta);
                break;
            case BIOFORMATS:
                opener = new BioFormatsOpener(
                        scijavaContext,
                        location,
                        id,
                        // Location of the image
                        positionPreTransformMatrixArray,
                        positionPostTransformMatrixArray,
                        positionIsImageCenter,
                        defaultSpaceUnit,
                        defaultVoxelUnit,
                        unit,
                        // How to stream it
                        nReader,
                        defaultBlockSize,
                        blockSize,
                        // Channel options
                        splitRGB,
                        cachedObjects,
                        nChannels, skipMeta
                );
                break;
            case IMAGEJ:
                throw new UnsupportedOperationException("ImageJ opener not supported");

            case OPENSLIDE:
                throw new UnsupportedOperationException("OPENSLIDE opener not supported");

            default:
                throw new UnsupportedOperationException(this.type +" opener not supported");
        }

        if (opener.getNChannels()!=-1) {
            nChannels = opener.getNChannels();
        }

        return opener;

    }

    public static OpenerSettings BioFormats() {
        return new OpenerSettings().bioFormatsBuilder();
    }

    public static OpenerSettings OMERO() {
        return new OpenerSettings().omeroBuilder();
    }

    public static OpenerSettings QuPath() {
        return new OpenerSettings().quPathBuilder();
    }

    /*public static OpenerSettings getDefaultSettings(OpenerType type){
        switch (type){
            case OMERO: return
            case IMAGEJ: return new OpenerSettings().imageJBuilder();
            case BIOFORMATS: return
            case OPENSLIDE: return new OpenerSettings().openSlideBuilder();
            case QUPATH: return
            default:
                logger.error("Unrecognized opener type "+ type);
                return null;
        }
    }*/

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

}
