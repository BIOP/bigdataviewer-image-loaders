/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2024 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.bioformats;

import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.ReaderWrapper;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BioFormatsHelper {
	/**
	 * Class logger
	 */
	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsHelper.class);


	// create OME-XML metadata store
	static ServiceFactory factory;
	static OMEXMLService service;

	static {
		try {
			factory = new ServiceFactory();
			service = factory.getInstance(OMEXMLService.class);
		} catch (DependencyException e) {
			throw new RuntimeException(e);
		}
	}

	// TODO : avoid creating more than one reader on initialization
	public static int getNSeries(File f){
		return getNSeries(f, "");
	}

	public static int getNSeries(File f, String options){
		logger.debug("Getting opener for file f " + f.getAbsolutePath());
		IFormatReader reader = new ImageReader();
		reader.setFlattenedResolutions(false);
		Map<String, String> readerOptions = BioFormatsOpener.bfOptionsToMap(options);
		MetadataOptions metadataOptions = reader.getMetadataOptions();
		if (!readerOptions.isEmpty() && metadataOptions instanceof DynamicMetadataOptions) {
			// We need to set an xml metadata backend or else a Dummy metadata store is created and
			// all metadata are discarded
			try {
				reader.setMetadataStore(service.createOMEXMLMetadata());
			} catch (Exception e) {
				e.printStackTrace();
			}
			for (Map.Entry<String,String> option : readerOptions.entrySet()) {
				logger.debug("setting reader option:"+option.getKey()+":"+option.getValue());
				((DynamicMetadataOptions)metadataOptions).set(option.getKey(), option.getValue());
			}
		}

		if (!readerOptions.isEmpty()) reader = new Memoizer(reader); // memoize
		int nSeries = 0;
		try {
			logger.debug("setId for reader " + f.getAbsolutePath());
			StopWatch watch = new StopWatch();
			watch.start();
			reader.setId(f.getAbsolutePath());
			nSeries = reader.getSeriesCount();
			watch.stop();
			logger.debug("id set in " + (int) (watch.getTime() / 1000) + " s");
		} catch (Exception e) {
			System.err.println("Error in file "+f.getAbsolutePath()+": "+e.getMessage());
			e.printStackTrace();
		}

		return nSeries;
	}

	public static Length[] getSeriesPositionAsLengths(IMetadata omeMeta,
		int iSerie)
	{
		Length[] pos = new Length[3];
		try {
			if (omeMeta.getPlanePositionX(iSerie, 0) != null) {
				pos[0] = omeMeta.getPlanePositionX(iSerie, 0);
			}
			else {
				pos[0] = new Length(0, UNITS.REFERENCEFRAME);
			}

			if (omeMeta.getPlanePositionY(iSerie, 0) != null) {
				pos[1] = omeMeta.getPlanePositionY(iSerie, 0);
			}
			else {
				pos[1] = new Length(0, UNITS.REFERENCEFRAME);
			}

			if (omeMeta.getPlanePositionZ(iSerie, 0) != null) {
				pos[2] = omeMeta.getPlanePositionZ(iSerie, 0);
			}
			else {
				pos[2] = new Length(0, UNITS.REFERENCEFRAME);
			}
		}
		catch (Exception e) {
			// e.printStackTrace();
			logger.warn("Could not access omeMeta.getPlanePosition serie " + iSerie);
			pos[0] = new Length(0, UNITS.REFERENCEFRAME);
			pos[1] = new Length(0, UNITS.REFERENCEFRAME);
			pos[2] = new Length(0, UNITS.REFERENCEFRAME);
		}
		logger.debug("Ch Name=" + omeMeta.getChannelName(iSerie, 0));
		logger.debug("pos[0]=" + pos[0].value() + " " + pos[0].unit().getSymbol());
		logger.debug("pos[1]=" + pos[1].value() + " " + pos[1].unit().getSymbol());
		logger.debug("pos[2]=" + pos[2].value() + " " + pos[2].unit().getSymbol());

		return pos;
	}

	public static Length[] getSeriesVoxelSizeAsLengths(IMetadata omeMeta,
		int iSerie)
	{
		Length[] vox = new Length[3];

		if (omeMeta.getPixelsPhysicalSizeX(iSerie) != null) {
			vox[0] = omeMeta.getPixelsPhysicalSizeX(iSerie);
		}
		else {
			vox[0] = new Length(1, UNITS.REFERENCEFRAME);
		}

		if (omeMeta.getPixelsPhysicalSizeY(iSerie) != null) {
			vox[1] = omeMeta.getPixelsPhysicalSizeY(iSerie);
		}
		else {
			vox[1] = new Length(1, UNITS.REFERENCEFRAME);
		}

		if (omeMeta.getPixelsPhysicalSizeZ(iSerie) != null) {
			vox[2] = omeMeta.getPixelsPhysicalSizeZ(iSerie);
		}
		else {
			vox[2] = new Length(1, UNITS.REFERENCEFRAME);
		}

		logger.debug("Ch Name=" + omeMeta.getChannelName(iSerie, 0));
		logger.debug("vox[0]=" + vox[0].value() + " " + vox[0].unit().getSymbol());
		logger.debug("vox[1]=" + vox[1].value() + " " + vox[1].unit().getSymbol());
		logger.debug("vox[2]=" + vox[2].value() + " " + vox[2].unit().getSymbol());

		return vox;
	}

	protected static AffineTransform3D getSeriesRootTransform(IMetadata omeMeta,
		IFormatReader reader,
		int iSerie, Unit<Length> u,
		// Bioformats location fix
		double[] positionPreTransformMA, double[] positionPostTransformMA,
		Length positionReferenceFrameLength, boolean positionIsImageCenter,
		// Bioformats voxSize fix
		double[] voxSizePreTransformMA, double[] voxSizePostTransformMA,
		Length voxSizeReferenceFrameLength, boolean[] axesFlip)
	{

		AffineTransform3D positionPreTransform = null;
		if (positionPreTransformMA != null) {
			positionPreTransform = new AffineTransform3D();
			positionPreTransform.set(positionPreTransformMA);
		}

		AffineTransform3D positionPostTransform = null;
		if (positionPostTransformMA != null) {
			positionPostTransform = new AffineTransform3D();
			positionPostTransform.set(positionPostTransformMA);
		}

		AffineTransform3D voxSizePreTransform = null;
		if (voxSizePreTransformMA != null) {
			voxSizePreTransform = new AffineTransform3D();
			voxSizePreTransform.set(voxSizePreTransformMA);
		}

		AffineTransform3D voxSizePostTransform = null;
		if (voxSizePreTransformMA != null) {
			voxSizePostTransform = new AffineTransform3D();
			voxSizePostTransform.set(voxSizePostTransformMA);
		}

		return getSeriesRootTransform(omeMeta, reader, iSerie, u,
			// Bioformats location fix
			positionPreTransform, positionPostTransform, positionReferenceFrameLength,
			positionIsImageCenter,
			// Bioformats voxSize fix
			voxSizePreTransform, voxSizePostTransform, voxSizeReferenceFrameLength,
			axesFlip);

	}

	public static AffineTransform3D getSeriesRootTransform(IMetadata omeMeta,
		IFormatReader reader,
		int iSerie, Unit<Length> u,
		// Bioformats location fix
		AffineTransform3D positionPreTransform,
		AffineTransform3D positionPostTransform,
		Length positionReferenceFrameLength, boolean positionIsImageCenter,
		// Bioformats voxSize fix
		AffineTransform3D voxSizePreTransform,
		AffineTransform3D voxSizePostTransform, Length voxSizeReferenceFrameLength,
		boolean[] axesFlip)
	{

		Length[] voxSize = getSeriesVoxelSizeAsLengths(omeMeta, iSerie);
		double[] d = new double[3];

		for (int iDimension = 0; iDimension < 3; iDimension++) { // X:0; Y:1; Z:2
			if ((voxSize[iDimension].unit() != null) && (voxSize[iDimension].unit()
				.isConvertible(u)))
			{
				d[iDimension] = voxSize[iDimension].value(u).doubleValue();
			}
			else if (voxSize[iDimension].unit().getSymbol().equals(
				"reference frame"))
			{
				Length l = new Length(voxSize[iDimension].value().doubleValue() *
					voxSizeReferenceFrameLength.value().doubleValue(),
					voxSizeReferenceFrameLength.unit());
				d[iDimension] = l.value(u).doubleValue();
			}
			else {
				d[iDimension] = 1;
			}
		}

		Length[] pos = getSeriesPositionAsLengths(omeMeta, iSerie);
		double[] p = new double[3];

		Dimensions dims = getSeriesDimensions(omeMeta, reader, iSerie);

		for (int iDimension = 0; iDimension < 3; iDimension++) { // X:0; Y:1; Z:2
			if ((pos[iDimension].unit() != null) && (pos[iDimension].unit()
				.isConvertible(u)))
			{
				p[iDimension] = pos[iDimension].value(u).doubleValue();
			}
			else if (pos[iDimension].unit().getSymbol().equals("reference frame")) {
				Length l = new Length(pos[iDimension].value().doubleValue() *
					positionReferenceFrameLength.value().doubleValue(),
					positionReferenceFrameLength.unit());
				p[iDimension] = l.value(u).doubleValue();
			}
			else {
				p[iDimension] = 0;
			}
		}

		AffineTransform3D translateFwd = new AffineTransform3D();
		translateFwd.translate(-(dims.dimension(0) / 2.0), -(dims.dimension(1) /
			2.0), -(dims.dimension(2) / 2.0));

		AffineTransform3D translateBwd = new AffineTransform3D();
		translateBwd.translate((dims.dimension(0) / 2.0), (dims.dimension(1) /
			2.0), (dims.dimension(2) / 2.0));

		AffineTransform3D flip = new AffineTransform3D();
		flip.scale(axesFlip[0] ? -1 : 1, axesFlip[1] ? -1 : 1, axesFlip[2] ? -1
			: 1);

		AffineTransform3D scaleVox = new AffineTransform3D();
		scaleVox.scale(d[0], d[1], d[2]);

		AffineTransform3D position = new AffineTransform3D();
		position.translate(p[0], p[1], p[2]);

		AffineTransform3D rootTransform = new AffineTransform3D();

		if (positionPostTransform != null) {
			rootTransform.concatenate(positionPostTransform);
		}
		rootTransform.concatenate(position);
		if (positionPreTransform != null) {
			rootTransform.concatenate(positionPreTransform);
		}
		if (voxSizePostTransform != null) {
			rootTransform.concatenate(voxSizePostTransform);
		}
		rootTransform.concatenate(scaleVox);
		if (positionIsImageCenter) rootTransform.concatenate(translateFwd);
		rootTransform.concatenate(translateBwd);
		rootTransform.concatenate(flip);
		rootTransform.concatenate(translateFwd);
		if (voxSizePreTransform != null) {
			rootTransform.concatenate(voxSizePreTransform);
		}
		return rootTransform;
	}

	public static VoxelDimensions getSeriesVoxelDimensions(IMetadata omeMeta,
		int iSerie, Unit<Length> u, Length voxSizeReferenceFrameLength)
	{
		// Always 3 to allow for big stitcher compatibility
		int numDimensions = 3;
		Length[] voxSize = getSeriesVoxelSizeAsLengths(omeMeta, iSerie);
		double[] d = new double[3];

		for (int iDimension = 0; iDimension < 3; iDimension++) { // X:0; Y:1; Z:2
			if ((voxSize[iDimension].unit() != null) && (voxSize[iDimension].unit()
				.isConvertible(u)))
			{
				d[iDimension] = voxSize[iDimension].value(u).doubleValue();
			}
			else if (voxSize[iDimension].unit().getSymbol().equals(
				"reference frame"))
			{
				Length l = new Length(voxSize[iDimension].value().doubleValue() *
					voxSizeReferenceFrameLength.value().doubleValue(),
					voxSizeReferenceFrameLength.unit());
				d[iDimension] = l.value(u).doubleValue();
			}
			else {
				d[iDimension] = 1;
			}
		}

		VoxelDimensions voxelDimensions;

		{
			assert numDimensions == 3;
			voxelDimensions = new VoxelDimensions() {

				final Unit<Length> targetUnit = u;

				final double[] dims = { d[0], d[1], d[2] };

				@Override
				public String unit() {
					return targetUnit.getSymbol();
				}

				@Override
				public void dimensions(double[] doubles) {
					doubles[0] = dims[0];
					doubles[1] = dims[1];
					doubles[2] = dims[2];
				}

				@Override
				public double dimension(int i) {
					return dims[i];
				}

				@Override
				public int numDimensions() {
					return numDimensions;
				}
			};
		}
		return voxelDimensions;
	}

	public static Dimensions getSeriesDimensions(IMetadata omeMeta, IFormatReader reader, int iSerie) {
		// Always set 3d to allow for Big Stitcher compatibility

		int numDimensions = 3;
		omeMeta.getPixelsSizeX(iSerie);
		reader.setSeries(iSerie);

		int sX = reader.getSizeX();
		int sY = reader.getSizeY();
		int sZ = reader.getSizeZ();

		long[] dims = new long[3];

		dims[0] = sX;

		dims[1] = sY;

		dims[2] = sZ;

		@SuppressWarnings("UnnecessaryLocalVariable")
		Dimensions dimensions = new Dimensions() {

			@Override
			public void dimensions(long[] dimensions) {
				dimensions[0] = dims[0];
				dimensions[1] = dims[1];
				dimensions[2] = dims[2];
			}

			@Override
			public long dimension(int d) {
				return dims[d];
			}

			@Override
			public int numDimensions() {
				return numDimensions;
			}
		};

		return dimensions;
	}

	public static ArrayList<Pair<Integer, ArrayList<Integer>>>
		getListOfSeriesAndChannels(IFormatReader reader, String code)
	{
		@SuppressWarnings("UnnecessaryLocalVariable")
		ArrayList<Pair<Integer, ArrayList<Integer>>> listOfSources =

			commaSeparatedListToArrayOfArray(code, idxSeries -> (idxSeries >= 0)
				? idxSeries : reader.getSeriesCount() + idxSeries, // apparently -1 is
																														// necessary -> I
																														// don't really
																														// understand
				(idxSeries, idxChannel) -> (idxChannel >= 0) ? idxChannel
					: ((IMetadata) reader.getMetadataStore()).getChannelCount(idxSeries) +
						idxChannel);

		return listOfSources;
	}

	/**
	 * BiFunction necessary to be able to find index of negative values
	 * 
	 * @param expression to be parsed
	 * @param fbounds description to do
	 * @param f to do
	 * @return to do
	 */
	static public ArrayList<Pair<Integer, ArrayList<Integer>>>
		commaSeparatedListToArrayOfArray(String expression,
			Function<Integer, Integer> fbounds,
			BiFunction<Integer, Integer, Integer> f)
	{
		String[] splitIndexes = expression.split(";");

		ArrayList<Pair<Integer, ArrayList<Integer>>> arrayOfArrayOfIndexes =
			new ArrayList<>();

		for (String str : splitIndexes) {
			str = str.trim();
			String seriesIdentifier = str;
			String channelIdentifier = "*";
			if (str.contains(".")) {
				String[] boundIndex = str.split("\\.");
				if (boundIndex.length == 2) {
					seriesIdentifier = boundIndex[0];
					channelIdentifier = boundIndex[1];
				}
				else {
					logger.warn("Number format problem with expression:" + str +
						" - Expression ignored");
					break;
				}
			}
			// TODO Need to split by comma
			// No sub array specifier -> equivalent to * in subchannel
			try {
				if (seriesIdentifier.trim().equals("*")) {
					int maxIndex = fbounds.apply(-1);
					// System.out.println("maxIndex="+maxIndex);
					for (int index = 0; index <= maxIndex; index++) {
						MutablePair<Integer, ArrayList<Integer>> current =
							new MutablePair<>();
						final int idxCp = index;
						current.setLeft(idxCp);
						current.setRight(expressionToArray(channelIdentifier, i -> f.apply(
							idxCp, i)));
						arrayOfArrayOfIndexes.add(current);
					}
				}
				else {
					int indexMin, indexMax;

					if (seriesIdentifier.trim().contains(":")) {
						String[] boundIndex = seriesIdentifier.split(":");
						assert boundIndex.length == 2;
						indexMin = fbounds.apply(Integer.valueOf(boundIndex[0].trim()));
						indexMax = fbounds.apply(Integer.valueOf(boundIndex[1].trim()));
					}
					else {
						indexMin = fbounds.apply(Integer.valueOf(seriesIdentifier.trim()));
						indexMax = indexMin;
					}
					if (indexMax >= indexMin) {
						for (int index = indexMin; index <= indexMax; index++) {
							MutablePair<Integer, ArrayList<Integer>> current =
								new MutablePair<>();
							final int idxCp = index;
							current.setLeft(index);
							current.setRight(expressionToArray(channelIdentifier, i -> f
								.apply(idxCp, i)));
							arrayOfArrayOfIndexes.add(current);
						}
					}
					else {
						for (int index = indexMax; index >= indexMin; index--) {
							MutablePair<Integer, ArrayList<Integer>> current =
								new MutablePair<>();
							final int idxCp = index;
							current.setLeft(index);
							current.setRight(expressionToArray(channelIdentifier, i -> f
								.apply(idxCp, i)));
							arrayOfArrayOfIndexes.add(current);
						}
					}

				}
			}
			catch (NumberFormatException e) {
				logger.warn("Number format problem with expression:" + str +
					" - Expression ignored");
			}

		}
		return arrayOfArrayOfIndexes;
	}

	/**
	 * Convert a comma separated list of indexes into an arraylist of integer For
	 * instance 1,2,5:7,10:12,14 returns an ArrayList containing
	 * [1,2,5,6,7,10,11,12,14] Invalid format are ignored and an error message is
	 * displayed
	 * 
	 * @param expression expression to parse
	 * @return list of indexes in ArrayList
	 */
	static public ArrayList<Integer> expressionToArray(String expression,
		Function<Integer, Integer> fbounds)
	{
		String[] splitIndexes = expression.split(",");
		ArrayList<Integer> arrayOfIndexes = new ArrayList<>();
		for (String str : splitIndexes) {
			str = str.trim();
			if (str.contains(":")) {
				// Array of source, like 2:5 = 2,3,4,5
				String[] boundIndex = str.split(":");
				if (boundIndex.length == 2) {
					try {
						int b1 = fbounds.apply(Integer.valueOf(boundIndex[0].trim()));
						int b2 = fbounds.apply(Integer.valueOf(boundIndex[1].trim()));
						if (b1 < b2) {
							for (int index = b1; index <= b2; index++) {
								arrayOfIndexes.add(index);
							}
						}
						else {
							for (int index = b2; index >= b1; index--) {
								arrayOfIndexes.add(index);
							}
						}
					}
					catch (NumberFormatException e) {
						logger.warn("Number format problem with expression:" + str +
							" - Expression ignored");
					}
				}
				else {
					logger.warn("Cannot parse expression " + str +
						" to pattern 'begin-end' (2-5) for instance, omitted");
				}
			}
			else {
				// Single source
				try {
					if (str.trim().equals("*")) {
						int maxIndex = fbounds.apply(-1);
						for (int index = 0; index <= maxIndex; index++) {
							arrayOfIndexes.add(index);
						}
					}
					else {
						int index = fbounds.apply(Integer.valueOf(str.trim()));
						arrayOfIndexes.add(index);
					}
				}
				catch (NumberFormatException e) {
					logger.warn("Number format problem with expression:" + str +
						" - Expression ignored");
				}
			}
		}
		return arrayOfIndexes;
	}

	final static int[] loopR = { 1, 0, 0, 1, 1, 1, 0 };
	final static int[] loopG = { 0, 1, 0, 1, 1, 0, 1 };
	final static int[] loopB = { 0, 0, 1, 1, 0, 1, 1 };


	/**
	 * Look into Fields of BioFormats UNITS class that matches the input string
	 * Return the corresponding Unit Field Case insensitive
	 * 
	 * @param unit_string
	 * @return corresponding BF Unit object
	 */
	public static Unit<Length> getUnitFromString(String unit_string) {
		Field[] bfUnits = UNITS.class.getFields();
		for (Field f : bfUnits) {
			if (f.getType().equals(Unit.class)) {
				if (f.getName() != null) {
					try {
						if (f.getName().equals(unit_string.trim()) || ((Unit<Length>) (f.get(null))).getSymbol().equals(unit_string.trim()))
						{// (f.getName().toUpperCase().equals(unit_string.trim().toUpperCase()))
							// {
							// Field found
							return (Unit<Length>) f.get(null); // Field is assumed to be static
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		// Field not found
		return null;
	}

	public static boolean hasCopyMethod(IFormatReader reader) {
		if (reader instanceof Memoizer) {
			reader = ((Memoizer) reader).getReader();
		}

		if (reader instanceof ChannelSeparator) {
			reader = ((ChannelSeparator) reader).getReader();
		}

		if (reader instanceof ImageReader) {
			ImageReader ir = (ImageReader) reader;
			reader = ir.getReader();
		}

		if (reader instanceof ReaderWrapper) {
			ReaderWrapper rw = (ReaderWrapper) reader;
			reader = rw.getReader();
		}

		Optional<Method> copyMethod = Arrays.stream(reader.getClass().getMethods())
				.filter(m -> m.getName().equals("copy") && m.getParameterCount()==0)
				.findFirst();

		return copyMethod.isPresent();
	}

	public static IFormatReader copy(IFormatReader reader) throws UnsupportedOperationException {
		if (reader instanceof Memoizer) {
			reader = ((Memoizer) reader).getReader();
		}

		boolean hasChannelSeparator = false;
		if (reader instanceof ChannelSeparator) {
			reader = ((ChannelSeparator) reader).getReader();
			hasChannelSeparator = true;
		}

		if (reader instanceof ImageReader) {
			ImageReader ir = (ImageReader) reader;
			reader = ir.getReader();
		}

		if (reader instanceof ReaderWrapper) {
			ReaderWrapper rw = (ReaderWrapper) reader;
			reader = rw.getReader();
		}

		Optional<Method> copyMethod = Arrays.stream(reader.getClass().getMethods())
				.filter(m -> m.getName().equals("copy") && m.getParameterCount()==0)
				.findFirst();

		if (copyMethod.isPresent()) {
			try {
				IFormatReader newReader = (IFormatReader) copyMethod.get().invoke(reader);
				if (hasChannelSeparator) {
					return new ChannelSeparator(newReader);
				} else {
					return newReader;
				}
			} catch (IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		} else {
			throw new UnsupportedOperationException("The reader "+reader+" has no copy method");
		}
	}

}
