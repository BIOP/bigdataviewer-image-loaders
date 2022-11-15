package ch.epfl.biop.bdv.img;

import ch.epfl.biop.bdv.img.qupath.QuPathImageOpener;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import com.google.gson.Gson;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.model.enums.UnitsLength;

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

    //---- Modifications on the location of the dataset ( pixel size, origin, flip)
    double[] positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    double[] positionPostTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
    boolean positionIsImageCenter = true; // Top left corner otherwise

    //---- Target unit : the unit in which the image will be opened
    Length defaultSpaceUnit = new Length(1,UNITS.MICROMETER);
    Length defaultVoxelUnit = new Length(1,UNITS.MICROMETER);
    String unit = UnitsLength.MICROMETER.toString();

    //---- How to open the dataset (block size, number of readers per image)
    int poolSize = 10;
    boolean useDefaultXYBlockSize = true; // The block size chosen is let to be defined by the opener implementation itself
    FinalInterval cacheBlockSize = new FinalInterval(new long[] { 0, 0,
            0 }, new long[] { 512, 512, 1 }); // Default cache block size, if none is defined

    //-------- Channels options
    boolean swZC = false; // Switch Z and Channels, old metadata fix, might be retired
    boolean splitRGBChannels = false; // Should be true for 16 bits RGB channels like we have in CZI, Imglib2, the library used after, do not have a specific type class for 16 bits RGB pixels

    // ---- Opener core options
    OpenerType currentBuilder;
    String dataLocation = "";

    public enum OpenerType {
        BIOFORMATS,
        OMERO,
        IMAGEJ,
        OPENSLIDE,
        QUPATH
    };

    // ---- BioFormats specific opener options
    int iSerie = 0;

    // ---- OMERO specific opener options
    transient Gateway gateway;
    transient SecurityContext ctx;
    transient String host; // TODO : remove, it should be contained in datalocation
    long imageID;

    // --------- QuPath specific
    URI qpProject;
    MinimalQuPathProject.ImageEntry qpImage = null; // Todo : replace by entry index only

    // GETTERS
    /*public MinimalQuPathProject.ImageEntry getQpImage(){return this.qpImage;}
    public URI getQpProject(){return this.qpProject;}
    public String getHost(){return this.host;}
    public String getDataLocation(){return this.dataLocation;}*/

    // ---- cache and readers
    public OpenerSettings poolSize(int pSize){
        this.poolSize = pSize;
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
        this.dataLocation = location;
        return this;
    }

    public OpenerSettings location(URI uri) throws URISyntaxException {
        if(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
            this.dataLocation = uri.toString();
        else {
            URI newuri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
            this.dataLocation = Paths.get(newuri).toString();
        }
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

    public OpenerSettings quPathBuilder(){
        this.currentBuilder = OpenerType.QUPATH;
        return this;
    }



    // OMERO specific
    public OpenerSettings setGateway(Gateway gateway){
        this.gateway = gateway;
        return this;
    }

    public OpenerSettings setContext(SecurityContext ctx){
        this.ctx = ctx;
        this.host = ctx.getServerInformation().getHost();
        return this;
    }

    public OpenerSettings setImageID(long id){
        this.imageID = id;
        return this;
    }


    // BioFormats specific
    public OpenerSettings setSerie(int iSerie){
        this.iSerie = iSerie;
        return this;
    }


    // QuPath specific
    public OpenerSettings setQpImage(MinimalQuPathProject.ImageEntry qpImage) {
        this.qpImage = qpImage;
        return this;
    }
    public OpenerSettings setQpProject(URI qpproj) {
        this.qpProject = qpproj;
        return this;
    }


    public Opener<?> create(Map<String, Object> cachedObjects) throws Exception {
        switch (this.currentBuilder) {
            case OMERO:
                return new OmeroBdvOpener(
                        gateway,
                        ctx,
                        imageID,
                        poolSize,
                        unit,
                        dataLocation,
                        cachedObjects
                );
            case QUPATH: return new QuPathImageOpener().create(
                    dataLocation,
                    iSerie,
                    // Location of the image
                    positionPreTransformMatrixArray,
                    positionPostTransformMatrixArray,
                    positionIsImageCenter,
                    defaultSpaceUnit,
                    defaultVoxelUnit,
                    unit,
                    // How to stream it
                    poolSize,
                    useDefaultXYBlockSize,
                    cacheBlockSize,
                    // Channel options
                    swZC,
                    splitRGBChannels,
                    gateway,
                    ctx,
                    imageID,
                    qpImage,
                    qpProject,
                    cachedObjects
            );
            case BIOFORMATS:
                return new BioFormatsBdvOpener(
                        dataLocation,
                        iSerie,
                        // Location of the image
                        positionPreTransformMatrixArray,
                        positionPostTransformMatrixArray,
                        positionIsImageCenter,
                        defaultSpaceUnit,
                        defaultVoxelUnit,
                        unit,
                        // How to stream it
                        poolSize,
                        useDefaultXYBlockSize,
                        cacheBlockSize,
                        // Channel options
                        swZC,
                        splitRGBChannels,
                        cachedObjects
                );
            case IMAGEJ:
                throw new UnsupportedOperationException("ImageJ opener not supported");

            case OPENSLIDE:
                throw new UnsupportedOperationException("OPENSLIDE opener not supported");

            default:
                throw new UnsupportedOperationException(this.currentBuilder+" opener not supported");
        }

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

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

}
