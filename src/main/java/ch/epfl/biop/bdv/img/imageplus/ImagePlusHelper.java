/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.imageplus;

import bdv.viewer.SourceAndConverter;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.process.LUT;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.lazy.Caches;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.LinearRange;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgToVirtualStack;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that facilitate compatibility for going forth and back between
 * bdv and ImagePlus The affine transform located an ImagePlus in 3D cannot be
 * properly defined using the ij.measure.Calibration class - Inner trick used :
 * store and retrieve the affine transform from within the ImagePlus "info"
 * property - This allows to support saving the image as tiff and being able to
 * retrieve its location when loading it - As well, cropping and scaling the
 * image is allowed because the xOrigin, yOrigin (and maybe zOrigin, not tested)
 * allows to compute the offset relative to the original dataset - Calibration
 * is still used in order to store all the useful information that can be
 * contained within it (and is useful for the proper scaling retrieval) - Time
 * Origin is another property which is stored in the image info property. This
 * allows to export and import dataset which are 'cropped in time' Modification
 * 21st Sept : uses caching by Saalfeld lab : it seems to solve memory leak
 * issues
 *
 * @author Nicolas Chiaruttini, EPFL, 2020
 */

public class ImagePlusHelper {

	/**
	 * Regex matching the toString function of AffineTransform3D
	 */
	final public static String regexAffineTransform3D =
		"(3d-affine: \\()(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.*)\\)";

	public static void storeExtendedCalibrationToImagePlus(ImagePlus imp,
		AffineTransform3D at3D, String unit, int timePointBegin)
	{
		storeMatrixToImagePlus(imp, at3D);
		setTimeOriginToImagePlus(imp, timePointBegin);
		if (unit != null) imp.getCalibration().setUnit(unit);
	}

	static void storeMatrixToImagePlus(ImagePlus imp, AffineTransform3D at3D) {
		Calibration cal = new Calibration();

		double[] m = at3D.getRowPackedCopy();
		double[] voxelSizes = new double[3];
		for (int d = 0; d < 3; d++) {
			voxelSizes[d] = Math.sqrt(m[d] * m[d] + m[d + 4] * m[d + 4] + m[d + 8] *
				m[d + 8]);
		}

		double[] voxelSigns = new double[3];
		for (int d = 0; d < 3; d++) {
			// find the sign of the largest entry in the column
			// (not sure if there is a better way)
			final ArrayList<Double> entries = new ArrayList<>();
			entries.add(m[d]);
			entries.add(m[d + 4]);
			entries.add(m[d + 8]);
			entries.sort((o1, o2) -> Math.abs(o1) < Math.abs(o2) ? 1 : -1);
			voxelSigns[d] = Math.signum(entries.get(0));
		}

		cal.pixelWidth = voxelSigns[0] * voxelSizes[0];
		cal.pixelHeight = voxelSigns[1] * voxelSizes[1];
		cal.pixelDepth = voxelSigns[2] * voxelSizes[2];

		final double[] origin = new double[3];
		at3D.inverse().apply(new double[3], origin);
		cal.xOrigin = origin[0];
		cal.yOrigin = origin[1];
		cal.zOrigin = origin[2];

		imp.setCalibration(cal);

		// Calibration is not enough Inner trick : use ImagePlus info property to
		// store matrix of transformation
		if (imp.getInfoProperty() == null) {
			imp.setProperty("Info", " "); // One character should be present
		}
		String info = imp.getInfoProperty();

		// Removes any previously existing stored affine transform
		info = info.replaceAll(regexAffineTransform3D, "");

		// Appends matrix data
		info += at3D + "\n";

		imp.setProperty("Info", info);

	}

	public static AffineTransform3D getMatrixFromImagePlus(ImagePlus imp) {

		// Checks whether the AffineTransform is defined in ImagePlus "info"
		// property
		if (imp.getInfoProperty() != null) {
			AffineTransform3D at3D = new AffineTransform3D();
			Pattern pattern = Pattern.compile(regexAffineTransform3D);
			Matcher matcher = pattern.matcher(imp.getInfoProperty());
			if (matcher.find()) {
				// Looks good, we have something that looks like an affine transform
				double[] m = new double[12];
				for (int i = 0; i < 12; i++) {
					m[i] = Double.parseDouble(matcher.group(i + 2));
				}
				at3D.set(m);

				return at3D;
			}
			else {
				// Affine transform not found in ImagePlus Info
			}
		}

		// Otherwise : use Calibration from ImagePlus
		if (imp.getCalibration() != null) {
			AffineTransform3D at3D = new AffineTransform3D();
			// Matrix built from calibration
			at3D.scale(imp.getCalibration().pixelWidth, imp
				.getCalibration().pixelHeight, imp.getCalibration().pixelDepth);
			at3D.translate(imp.getCalibration().xOrigin * imp
				.getCalibration().pixelWidth, imp.getCalibration().yOrigin * imp
					.getCalibration().pixelHeight, imp.getCalibration().zOrigin * imp
						.getCalibration().pixelDepth);
			return at3D;
		}

		// Default : returns identity
		return new AffineTransform3D();
	}

	/**
	 * Regex matching the toString function of AffineTransform3D
	 */
	final public static String regexTimePointOrigin = "(TimePoint: \\()(.+)\\)";

	// TODO
	static void setTimeOriginToImagePlus(ImagePlus imp, int timePoint) {
		if (imp.getInfoProperty() == null) {
			imp.setProperty("Info", " "); // One character should be present
		}
		String info = imp.getInfoProperty();

		// Removes any previously existing stored time origin
		info = info.replaceAll(regexTimePointOrigin, "");

		// Appends time origin data
		info += "TimePoint: (" + timePoint + ")\n";

		imp.setProperty("Info", info);
	}

	// TODO
	public static int getTimeOriginFromImagePlus(ImagePlus imp) {
		// Checks whether the time origin is defined in ImagePlus "info" property
		if (imp.getInfoProperty() != null) {
			Pattern pattern = Pattern.compile(regexTimePointOrigin);
			Matcher matcher = pattern.matcher(imp.getInfoProperty());
			if (matcher.find()) {
				// Looks good, we have something that looks like an affine transform
				return Integer.parseInt(matcher.group(2));
			}
		}
		return 0;
	}

	/**
	 * @param sac source
	 * @param mipmapLevel mipmap level of the source to wrap
	 * @param beginTimePoint start timepoint (included)
	 * @param nTimePoints number of timepoints exported
	 * @param timeStep steps between each timepoint resampled
	 * @return wrapped ImagePlus
	 */
	public static <T extends NativeType<T> & NumericType<T>> ImagePlus wrap(
		SourceAndConverter<T> sac, int mipmapLevel, int beginTimePoint,
		int nTimePoints, int timeStep)
	{

		// Avoids no mip map exception
		mipmapLevel = Math.min(mipmapLevel, sac.getSpimSource()
			.getNumMipmapLevels() - 1);

		RandomAccessibleInterval<T>[] rais =
			new RandomAccessibleInterval[nTimePoints];
		int endTimePoint = beginTimePoint + timeStep * nTimePoints;
		long xSize = 1, ySize = 1, zSize = 1;
		int i = 0;
		for (int iTp = beginTimePoint; iTp < endTimePoint; iTp += timeStep) {
			if (sac.getSpimSource().isPresent(iTp)) {
				rais[i] = sac.getSpimSource().getSource(iTp, mipmapLevel);
				xSize = rais[i].dimension(0);
				ySize = rais[i].dimension(1);
				zSize = rais[i].dimension(2);
				break;
			}
		}

		i = 0;
		for (int iTp = beginTimePoint; iTp < endTimePoint; iTp += timeStep) {
			if (sac.getSpimSource().isPresent(iTp)) {
				rais[i] = sac.getSpimSource().getSource(iTp, mipmapLevel);
			}
			else {
				rais[i] = new ZerosRAI<>(sac.getSpimSource().getType(), new long[] {
					xSize, ySize, zSize });
			}
			i++;
		}

		ImgPlus<T> imgPlus;
		ImagePlus imp;

		Img<T> img = (Img<T>) (wrapAsVolatileCachedCellImg(Views.stack(rais),
			new int[] { (int) rais[0].dimension(0), (int) rais[0].dimension(1), 1,
				1 }));

		imgPlus = new ImgPlus<>(img, sac.getSpimSource().getName(), new AxisType[] {
			Axes.X, Axes.Y, Axes.Z, Axes.TIME });
		imp = ImageJFunctions.wrap(imgPlus, "");

		imp.setTitle(sac.getSpimSource().getName());

		imp.setDimensions(1, (int) rais[0].dimension(2), nTimePoints); // Set 3
																																		// dimension
																																		// as Z, not
																																		// as
																																		// Channel

		// Simple Color LUT
		if (!(sac.getSpimSource().getType() instanceof ARGBType)) {
			if (sac.getConverter() instanceof ColorConverter) {
				ColorConverter converter = (ColorConverter) sac.getConverter();
				ARGBType c = converter.getColor();
				imp.setLut(LUT.createLutFromColor(new Color(ARGBType.red(c.get()),
					ARGBType.green(c.get()), ARGBType.blue(c.get()))));
			}
			if (sac.getConverter() instanceof LinearRange) {
				LinearRange converter = (LinearRange) sac.getConverter();
				imp.setDisplayRange(converter.getMin(), converter.getMax());
			}
		}

		return imp;
	}

	/**
	 * @param sacs sources
	 * @param mipmapMap mipmap level of each source
	 * @param beginTimePoint start timepoint (included)
	 * @param nTimePoints number of timepoints exported
	 * @param timeStep steps between each timepoint resampled
	 * @return wrapped sources as a multichannel ImagePlus
	 */
	public static <T extends NumericType<T> & NativeType<T>> ImagePlus wrap(
		List<SourceAndConverter<T>> sacs,
		Map<SourceAndConverter<T>, Integer> mipmapMap, int beginTimePoint,
		int nTimePoints, int timeStep)
	{

		if (sacs.size() == 1) {
			return wrap(sacs.get(0), mipmapMap.get(sacs.get(0)), beginTimePoint,
				nTimePoints, timeStep);
		}

		int endTimePoint = beginTimePoint + timeStep * nTimePoints;
		RandomAccessibleInterval<T>[] raisList = new RandomAccessibleInterval[sacs
			.size()];

		for (int c = 0; c < sacs.size(); c++) {
			SourceAndConverter<T> sac = sacs.get(c);
			RandomAccessibleInterval<T>[] rais =
				new RandomAccessibleInterval[nTimePoints];
			int mipmapLevel = Math.min(mipmapMap.get(sac), sac.getSpimSource()
				.getNumMipmapLevels() - 1); // mipmap level should exist
			long xSize = 1, ySize = 1, zSize = 1;

			int i = 0;
			for (int iTp = beginTimePoint; iTp < endTimePoint; iTp += timeStep) {
				if (sac.getSpimSource().isPresent(iTp)) {
					rais[i] = sac.getSpimSource().getSource(iTp, mipmapLevel);
					xSize = rais[i].dimension(0);
					ySize = rais[i].dimension(1);
					zSize = rais[i].dimension(2);
					break;
				}
			}

			i = 0;
			for (int iTp = beginTimePoint; iTp < endTimePoint; iTp += timeStep) {
				if (sac.getSpimSource().isPresent(iTp)) {
					rais[i] = sac.getSpimSource().getSource(iTp, mipmapLevel);
				}
				else {
					rais[i] = new ZerosRAI<>(sac.getSpimSource().getType(), new long[] {
						xSize, ySize, zSize });
				}
				i++;
			}
			raisList[c] = Views.stack(rais); // Very inefficient TODO : better perf
																				// for time stack
		}

		Img<T> img = (Img<T>) (wrapAsVolatileCachedCellImg(Views.stack(raisList),
			new int[] { (int) raisList[0].dimension(0), (int) raisList[0].dimension(
				1), 1, 1, 1 }));

		ImgPlus<T> imgPlus = new ImgPlus<>(img, // cacheRAI(Views.stack(raisList)),
			"", new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.TIME, Axes.CHANNEL });
		ImagePlus imp = HyperStackConverter.toHyperStack(ImgToVirtualStack.wrap(
			imgPlus), sacs.size(), (int) raisList[0].dimension(2), nTimePoints,
			"composite");

		LUT[] luts = new LUT[sacs.size()];
		for (SourceAndConverter<T> sac : sacs) {
			if (!(sac.getSpimSource().getType() instanceof ARGBType)) {
				LUT lut;
				if (sac.getConverter() instanceof ColorConverter) {
					ColorConverter converter = (ColorConverter) sac.getConverter();
					ARGBType c = converter.getColor();
					lut = LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType
						.green(c.get()), ARGBType.blue(c.get())));
				}
				else {
					lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType
						.green(255), ARGBType.blue(255)));
				}

				luts[sacs.indexOf(sac)] = lut;
				imp.setC(sacs.indexOf(sac) + 1);

				if (sac.getConverter() instanceof LinearRange) {
					LinearRange converter = (LinearRange) sac.getConverter();
					//imp.setDisplayRange(converter.getMin(), converter.getMax());
					lut.min = converter.getMin();
					lut.max = converter.getMax();
				}
				imp.getProcessor().setLut(lut);
			}
		}

		boolean oneIsNull = false;
		for (LUT lut : luts) {
			if (lut == null) {
				oneIsNull = true;
				break;
			}
		}
		if (!oneIsNull) ((CompositeImage) imp).setLuts(luts);

		return imp;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T extends NativeType<T>> RandomAccessibleInterval<T>
		wrapAsVolatileCachedCellImg(final RandomAccessibleInterval<T> source,
			final int[] blockSize)
	{

		final long[] dimensions = Intervals.dimensionsAsLongArray(source);

		final CellGrid grid = new CellGrid(dimensions, blockSize);

		final Caches.RandomAccessibleLoader<T> loader =
			new Caches.RandomAccessibleLoader<>(Views.zeroMin(source));

		final T type = Util.getTypeFromInterval(source);

		final CachedCellImg<T, ?> img;
		final Cache<Long, Cell<?>> cache = new SoftRefLoaderCache().withLoader(
			LoadedCellCacheLoader.get(grid, loader, type, AccessFlags.setOf(
				VOLATILE)));

		if (GenericByteType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
				BYTE, AccessFlags.setOf(VOLATILE)));
		}
		else if (GenericShortType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
				SHORT, AccessFlags.setOf(VOLATILE)));
		}
		else if (GenericIntType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT,
				AccessFlags.setOf(VOLATILE)));
		}
		else if (GenericLongType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
				LONG, AccessFlags.setOf(VOLATILE)));
		}
		else if (FloatType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
				FLOAT, AccessFlags.setOf(VOLATILE)));
		}
		else if (DoubleType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
				DOUBLE, AccessFlags.setOf(VOLATILE)));
		}
		else if (ARGBType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT,
				AccessFlags.setOf(VOLATILE)));
		}
		else {
			System.err.println("Unsupported caching of type " + type.getClass()
				.getSimpleName());
			img = null;
		}

		return img;
	}

}
