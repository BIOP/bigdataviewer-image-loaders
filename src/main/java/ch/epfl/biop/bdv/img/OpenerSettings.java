package ch.epfl.biop.bdv.img;

import loci.formats.IFormatReader;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.model.enums.UnitsLength;

import java.io.File;
import java.net.URI;

public class OpenerSettings {
    protected double[] positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    protected double[] positionPostTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    protected int poolSize = 10;

    protected int numFetcherThreads = 2;
    protected int numPriorities = 4;
    protected boolean useDefaultXYBlockSize = true; // Block size : use the one
    // defined by BioFormats or
    protected FinalInterval cacheBlockSize = new FinalInterval(new long[] { 0, 0,
            0 }, new long[] { 512, 512, 1 }); // needs a default size for z

    // Channels options
    protected boolean swZC = false; // Switch Z and Channels
   //PUT IT PUBLIC to be able to access the type outside
    public enum OpenerType {
        BIOFORMATS,
        OMERO,
        IMAGEJ,
        OPENSLIDE
    };
    protected OpenerType currentBuilder;
    protected String dataLocation = "";
    protected Length positionReferenceFrameLength = new Length(1,UNITS.MICROMETER);
    protected Length voxSizeReferenceFrameLength = new Length(1,UNITS.MICROMETER);
    protected boolean positionIsImageCenter = true; // Top left corner otherwise
    protected double[] voxSizePreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    protected double[] voxSizePostTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();

    protected String unit = UNITS.MICROMETER.toString();

    // Channels options
    protected boolean splitRGBChannels = false;
    protected int iSerie = 0;



    // cache and readers
    public OpenerSettings poolSize(int pSize){
        this.poolSize = pSize;
        return this;
    }
    public OpenerSettings numFetcherThread(int nThread){
        this.numFetcherThreads = nThread;
        return this;
    }
    public OpenerSettings numPriorities(int nPriorities){
        this.numPriorities = nPriorities;
        return this;
    }

    public OpenerSettings useDefaultCacheBlockSize(boolean flag) {
        useDefaultXYBlockSize = flag;
        return this;
    }

    public OpenerSettings cacheBlockSize(int sx, int sy, int sz) {
        useDefaultXYBlockSize = false;
        cacheBlockSize = new FinalInterval(sx, sy, sz);
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
        this.positionReferenceFrameLength = l;
        return this;
    }

    public OpenerSettings voxSizeReferenceFrameLength(Length l)
    {
        this.voxSizeReferenceFrameLength = l;
        return this;
    }


    // data location
    public OpenerSettings location(String location) {
        this.dataLocation = location;
        return this;
    }

    public OpenerSettings location(URI uri) {
        this.dataLocation = uri.toString();
        return this;
    }

    public OpenerSettings location(File f) {
        this.dataLocation = f.getAbsolutePath();
        return this;
    }


    // channels
    public OpenerSettings splitRGBChannels() {
        splitRGBChannels = true;
        return this;
    }


    public OpenerSettings switchZandC(boolean flag) {
        this.swZC = flag;
        return this;
    }


    // define unit
    // TODO see if there is no issue using toString() on unit type directly
    public OpenerSettings unit(UnitsLength u) {
        this.unit = u.toString();
        return this;
    }

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
        this.unit = UnitsLength.MILLIMETER.toString();
        return this;
    }

    public OpenerSettings micrometer() {
        this.unit = UnitsLength.MICROMETER.toString();
        return this;
    }

    public OpenerSettings nanometer() {
        this.unit = UnitsLength.NANOMETER.toString();
        return this;
    }

    /*public OpenerSettings openedReaders(Map<String, IFormatReader> openedReaders) {

    }*/


    // define which kind of builder to deal with
    public OpenerSettings omeroBuilder(){
        this.currentBuilder = OpenerType.OMERO;
        return this;
    }

    public OpenerSettings bioFormatsBuilder(){
        this.currentBuilder = OpenerType.BIOFORMATS;
        return this;
    }

    public OpenerSettings imageJBuilder(){
        this.currentBuilder = OpenerType.IMAGEJ;
        return this;
    }

    public OpenerSettings openSlideBuilder(){
        this.currentBuilder = OpenerType.OPENSLIDE;
        return this;
    }


    public OpenerSettings fixNikonND2(){
        return this.flipPositionX().flipPositionY();
    }

    public Opener create(){

        switch (this.currentBuilder) {
            case OMERO:
                //return new OmeroBdvOpener(this);
            case IMAGEJ: break;
            case OPENSLIDE: break;
            case BIOFORMATS:
                return new BioFormatsBdvOpener(this);
        }
        return null;
    }

    public static OpenerSettings getDefaultSettings(OpenerType type, String location){
        switch (type){
            case OMERO: return new OpenerSettings().omeroBuilder().location(location);
            case IMAGEJ: return new OpenerSettings().imageJBuilder().location(location);
            case BIOFORMATS: return new OpenerSettings().bioFormatsBuilder().location(location);
            case OPENSLIDE:; return new OpenerSettings().openSlideBuilder().location(location);
        }
        return null;
    }

}
