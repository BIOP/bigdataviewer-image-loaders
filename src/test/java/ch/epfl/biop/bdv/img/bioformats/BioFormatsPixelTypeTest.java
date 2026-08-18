/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.opener.Opener;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import loci.common.DebugTools;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that pixels reach BigDataViewer unaltered for each combination of pixel
 * layout that {@link BioFormatsArrayLoaders} has a dedicated branch for.
 * <p>
 * These are cheap but easy to get wrong silently: a mangled alpha component or a
 * flipped byte order stays invisible until something downstream starts honouring
 * it. Fixtures live in {@code src/test/resources/imgloaders} and are 4x4 pixels.
 * <p>
 * The RGB(A) fixtures all encode, at (x, y):
 * <pre>
 *     R = 10 + x   G = 100 + y   B = 200 + x + y
 *     A = 255, 128, 0, 64 for x = 0..3 on row 0, else 255
 * </pre>
 * Note that Bio-Formats reports {@code isInterleaved() == false} for TIFF whatever
 * its PlanarConfiguration is, and {@code true} for PNG, so the TIFF fixtures cover
 * the planar branch and the PNG ones the interleaved branch.
 */
public class BioFormatsPixelTypeTest {

	private static final int W = 4;
	private static final int H = 4;

	/** Alpha stored on row 0 of the RGBA fixtures. Every other pixel is opaque. */
	private static final int[] ALPHA_ROW_0 = { 255, 128, 0, 64 };

	private static final long[] UINT32 = {
			0x00000001L, 0x00000100L, 0x00010000L, 0x01000000L,
			0x01020304L, 0x000000FFL, 0xFF000000L, 0xFFFFFFFFL,
			0x7FFFFFFFL, 0x80000000L, 0x0A0B0C0DL, 0x00FF00FFL,
			0x12345678L, 0x87654321L, 0x000FF000L, 0xDEADBEEFL };

	private static final long[] UINT16 = {
			1, 256, 4096, 65535,
			0, 32768, 32767, 1234,
			4660, 22136, 255, 65280,
			100, 200, 300, 400 };

	@BeforeClass
	public static void silenceBioFormats() {
		DebugTools.enableLogging("OFF");
	}

	// ---------------------------------------------------------------- RGB / RGBA

	@Test
	public void rgbChunkyTiff() throws Exception {
		assertRGB("rgb_chunky.tif", false);
	}

	@Test
	public void rgbPlanarTiff() throws Exception {
		assertRGB("rgb_planar.tif", false);
	}

	@Test
	public void rgbaChunkyTiff() throws Exception {
		assertRGB("rgba_chunky.tif", true);
	}

	@Test
	public void rgbaPlanarTiff() throws Exception {
		assertRGB("rgba_planar.tif", true);
	}

	@Test
	public void rgbPng() throws Exception {
		assertRGB("rgb.png", false);
	}

	@Test
	public void rgbaPng() throws Exception {
		assertRGB("rgba.png", true);
	}

	// ------------------------------------------------------- integer byte orders

	@Test
	public void uint32LittleEndian() throws Exception {
		assertIntegers("uint32_le.tif", UINT32, UnsignedIntType.class);
	}

	@Test
	public void uint32BigEndian() throws Exception {
		assertIntegers("uint32_be.tif", UINT32, UnsignedIntType.class);
	}

	@Test
	public void uint16LittleEndian() throws Exception {
		assertIntegers("uint16_le.tif", UINT16, UnsignedShortType.class);
	}

	@Test
	public void uint16BigEndian() throws Exception {
		assertIntegers("uint16_be.tif", UINT16, UnsignedShortType.class);
	}

	// ------------------------------------------------------------------- helpers

	@SuppressWarnings("unchecked")
	private static void assertRGB(String resource, boolean withAlpha)
			throws Exception
	{
		try (Opened img = open(resource)) {
			assertTrue(resource + ": expected ARGBType but got " + img.opener
					.getPixelType().getClass().getSimpleName(), img.opener
							.getPixelType() instanceof ARGBType);

			RandomAccess<ARGBType> ra =
					((RandomAccessibleInterval<ARGBType>) img.rai).randomAccess();
			for (int y = 0; y < H; y++) {
				for (int x = 0; x < W; x++) {
					ra.setPosition(new long[] { x, y, 0 });
					int v = ra.get().get();
					String at = resource + " at (" + x + "," + y + ")";
					assertEquals(at + " alpha", expectedAlpha(withAlpha, x, y), ARGBType
							.alpha(v));
					assertEquals(at + " red", 10 + x, ARGBType.red(v));
					assertEquals(at + " green", 100 + y, ARGBType.green(v));
					assertEquals(at + " blue", 200 + x + y, ARGBType.blue(v));
				}
			}
		}
	}

	private static int expectedAlpha(boolean withAlpha, int x, int y) {
		if (!withAlpha) return 255;
		return y == 0 ? ALPHA_ROW_0[x] : 255;
	}

	@SuppressWarnings("unchecked")
	private static void assertIntegers(String resource, long[] expected,
			Class<?> expectedType) throws Exception
	{
		try (Opened img = open(resource)) {
			assertEquals(resource + ": pixel type", expectedType, img.opener
					.getPixelType().getClass());

			RandomAccess<? extends IntegerType<?>> ra =
					((RandomAccessibleInterval<? extends IntegerType<?>>) img.rai)
							.randomAccess();
			for (int y = 0; y < H; y++) {
				for (int x = 0; x < W; x++) {
					ra.setPosition(new long[] { x, y, 0 });
					assertEquals(resource + " at (" + x + "," + y + ")", expected[y * W +
							x], ra.get().getIntegerLong());
				}
			}
		}
	}

	private static Opened open(String resource) throws Exception {
		String path = new File(BioFormatsPixelTypeTest.class.getResource(
				"/imgloaders/" + resource).toURI()).getAbsolutePath();

		Opener<?> opener = OpenerSettings.BioFormats()
				.location(path)
				.setSerie(0)
				.micrometer()
				.useBFMemo(false) // keep the fixtures free of .bfmemo side files
				.create(new HashMap<>());

		assertEquals(resource + ": width", W, opener.getDimensions()[0].dimension(0));
		assertEquals(resource + ": height", H, opener.getDimensions()[0].dimension(1));

		VolatileGlobalCellCache cache = new VolatileGlobalCellCache(1, 1);
		OpenerSetupLoader<?, ?, ?> setupLoader = opener.getSetupLoader(0, 0,
				() -> cache);
		return new Opened(opener, setupLoader.getImage(0, 0));
	}

	/** Keeps the opener alive as long as its image is being read. */
	private static final class Opened implements AutoCloseable {

		final Opener<?> opener;
		final RandomAccessibleInterval<?> rai;

		Opened(Opener<?> opener, RandomAccessibleInterval<?> rai) {
			this.opener = opener;
			this.rai = rai;
		}

		@Override
		public void close() throws IOException {
			opener.close();
		}
	}
}