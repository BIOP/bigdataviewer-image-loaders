/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.bdv.img.bioformats;

import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BioFormatsTools {

	final protected static Logger logger = LoggerFactory.getLogger(
		BioFormatsTools.class);

	public static class BioformatsChannel {

		final int iSerie;
		final int iChannel;
		int emissionWl = 1;
		public String chName = "";
		String pxType = "";
		final boolean isRGB;

		public BioformatsChannel(IMetadata m, int iSerie, int iChannel,
			boolean isRGB)
		{
			this.iSerie = iSerie;
			this.iChannel = iChannel;
			this.isRGB = isRGB;
			if (m.getChannelEmissionWavelength(iSerie, iChannel) != null) {
				this.emissionWl = m.getChannelEmissionWavelength(iSerie, iChannel)
					.value(UNITS.NANOMETER).intValue();
			}
			if (m.getChannelName(iSerie, iChannel) != null) {
				this.chName = m.getChannelName(iSerie, iChannel);
			}
			else {
				this.chName = "ch_" + iChannel;
				logger.warn("No name found for serie " + iSerie + " ch " + iChannel +
					" setting name to " + this.chName);
			}
			if (m.getPixelsType(iSerie) != null) {
				this.pxType = m.getPixelsType(iSerie).getValue();
			}
		}

		@Override
		public int hashCode() {
			return this.chName.hashCode() * this.pxType.hashCode() * emissionWl *
				(iChannel + 1);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof BioformatsChannel) {
				BioformatsChannel bc = (BioformatsChannel) obj;
				return (isRGB == bc.isRGB) && (chName.equals(bc.chName)) && (pxType
					.equals(bc.pxType)) && (iChannel == bc.iChannel) &&
					(emissionWl == (bc.emissionWl));
			}
			else {
				return false;
			}
		}
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

	public static AffineTransform3D getSeriesRootTransform(IMetadata omeMeta,
		int iSerie, Unit u,
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

		return getSeriesRootTransform(omeMeta, iSerie, u,
			// Bioformats location fix
			positionPreTransform, positionPostTransform, positionReferenceFrameLength,
			positionIsImageCenter,
			// Bioformats voxSize fix
			voxSizePreTransform, voxSizePostTransform, voxSizeReferenceFrameLength,
			axesFlip);

	}

	public static AffineTransform3D getSeriesRootTransform(IMetadata omeMeta,
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

		Dimensions dims = getSeriesDimensions(omeMeta, iSerie);

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
		translateBwd.translate(+(dims.dimension(0) / 2.0), +(dims.dimension(1) /
			2.0), +(dims.dimension(2) / 2.0));

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

	public static Dimensions getSeriesDimensions(IMetadata omeMeta, int iSerie) {
		// Always set 3d to allow for Big Stitcher compatibility

		int numDimensions = 3;

		int sX = omeMeta.getPixelsSizeX(iSerie).getNumberValue().intValue();
		int sY = omeMeta.getPixelsSizeY(iSerie).getNumberValue().intValue();
		int sZ = omeMeta.getPixelsSizeZ(iSerie).getNumberValue().intValue();

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
			str.trim();
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
			str.trim();
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

	public static ARGBType getColorFromMetadata(IMetadata omeMeta, int iSerie,
		int iCh)
	{
		ome.xml.model.primitives.Color c = omeMeta.getChannelColor(iSerie, iCh);
		ARGBType color = null;
		if (c != null) {
			logger.debug("c = [" + c.getRed() + "," + c.getGreen() + "," + c
				.getBlue() + "]");
			color = new ARGBType(ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(),
				255));
		}
		else {
			if (omeMeta.getChannelEmissionWavelength(iSerie, iCh) != null) {
				int emission = omeMeta.getChannelEmissionWavelength(iSerie, iCh).value(
					UNITS.NANOMETER).intValue();

				logger.debug("emission = " + emission);
				Color cAwt = getColorFromWavelength(emission);
				color = new ARGBType(ARGBType.rgba(cAwt.getRed(), cAwt.getGreen(), cAwt
					.getBlue(), 255));
			}
			else {
				// Default colors based on iSerie index
				color = new ARGBType(ARGBType.rgba(255 * loopR[iCh % 7], 255 *
					loopG[iCh % 7], 255 * loopB[iCh % 7], 255));
			}
		}
		return color;
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

	public static Color getColorFromWavelength(int wv) {
		// https://stackoverflow.com/questions/1472514/convert-light-frequency-to-rgb
		int[] res = waveLengthToRGB(wv);
		return new Color(res[0], res[1], res[2]);
	}

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
						if (f.getName().toUpperCase().equals(unit_string.trim()
							.toUpperCase()) || ((Unit) (f.get(null))).getSymbol()
								.toUpperCase().equals(unit_string.trim().toUpperCase()))
						{// (f.getName().toUpperCase().equals(unit_string.trim().toUpperCase()))
							// {
							// Field found
							return (Unit) f.get(null); // Field is assumed to be static
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

}
