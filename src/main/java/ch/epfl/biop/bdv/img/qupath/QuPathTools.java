package ch.epfl.biop.bdv.img.qupath;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import ch.epfl.biop.bdv.img.qupath.struct.MinimalQuPathProject;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuPathTools {

    protected static Logger logger = LoggerFactory.getLogger(
            QuPathTools.class);

    public static AffineTransform3D getTransform(MinimalQuPathProject.PixelCalibrations pixelCalibrations, String outputUnit,
                                                 AffineTransform3D rootTransform, VoxelDimensions voxSizes) {

        // create a new AffineTransform3D based on pixelCalibration
        AffineTransform3D quPathRescaling = new AffineTransform3D();

        if (pixelCalibrations != null) {
            double scaleX = 1.0;
            double scaleY = 1.0;
            double scaleZ = 1.0;

            double voxSizeX = voxSizes.dimension(0);
            double voxSizeY = voxSizes.dimension(1);
            double voxSizeZ = voxSizes.dimension(2);

            if (pixelCalibrations.pixelWidth != null) {
                MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
                Length voxLengthX = new Length(voxSizeX, BioFormatsTools.getUnitFromString(voxSizes.unit()));

                if (voxLengthX.value(UNITS.MICROMETER) != null) {
                    logger.debug("xVox size = " + pc.value + " micrometer");
                    scaleX = pc.value / voxLengthX.value(UNITS.MICROMETER).doubleValue();
                } else {
                    Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
                    scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                    logger.debug("rescaling x");
                }
            }
            if (pixelCalibrations.pixelHeight != null) {
                MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
                Length voxLengthY = new Length(voxSizeY, BioFormatsTools.getUnitFromString(voxSizes.unit()));
                // if (pc.unit.equals("um")) {
                if (voxLengthY.value(UNITS.MICROMETER) != null) {
                    logger.debug("yVox size = " + pc.value + " micrometer");
                    scaleY = pc.value / voxLengthY.value(UNITS.MICROMETER).doubleValue();
                } else {
                    Length defaultxPix = new Length(1, BioFormatsTools.getUnitFromString(outputUnit));
                    scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                    logger.debug("rescaling y");
                }
            }
            if (pixelCalibrations.zSpacing != null) {
                MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.zSpacing;
                Length voxLengthZ = new Length(voxSizeZ, BioFormatsTools.getUnitFromString(voxSizes.unit()));
                // if (pc.unit.equals("um")) { problem with micrometer character
                if (voxLengthZ.value(UNITS.MICROMETER) != null) {
                    logger.debug("zVox size = " + pc.value + " micrometer");
                    scaleZ = pc.value / voxLengthZ.value(UNITS.MICROMETER).doubleValue();
                } else {
                   /* if ((voxLengthZ != null)) {
                    }
                    else {*/
                    logger.warn("Null Z voxel size");
                }
            }

            logger.debug("ScaleX: " + scaleX + " scaleY:" + scaleY + " scaleZ:" + scaleZ);

            final double finalScalex = scaleX;
            final double finalScaley = scaleY;
            final double finalScalez = scaleZ;

            if ((Math.abs(finalScalex - 1.0) > 0.0001) || (Math.abs(
                    finalScaley - 1.0) > 0.0001) || (Math.abs(finalScalez -
                    1.0) > 0.0001)) {
                logger.debug("Perform QuPath rescaling");
                quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
                double oX = rootTransform.get(0, 3);
                double oY = rootTransform.get(1, 3);
                double oZ = rootTransform.get(2, 3);
                rootTransform.preConcatenate(quPathRescaling);
                rootTransform.set(oX, 0, 3);
                rootTransform.set(oY, 1, 3);
                rootTransform.set(oZ, 2, 3);
            }

        }

        return rootTransform;
    }
}
