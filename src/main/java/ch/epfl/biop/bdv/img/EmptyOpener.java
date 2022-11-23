package ch.epfl.biop.bdv.img;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.volatiles.VolatileViews;
import ij.process.ByteProcessor;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.Views;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class EmptyOpener implements Opener<RawArrayReader>{

    List<ChannelProperties> channelProperties = new ArrayList<>();
    String imageName;
    String message;
    ResourcePool<RawArrayReader> pool;

    OpenerMeta meta;

    public EmptyOpener(String imageName, int nChannels, String message, boolean skipMeta) {
        this.message = message;
        for (int iCh = 0; iCh<nChannels; iCh++) {
            ChannelProperties channel = new ChannelProperties(iCh);
            channel.nChannels = nChannels;
            channel.color = new ARGBType(ARGBType.rgba(128, 64, 220,128));
            channel.name = "Channel "+iCh;
            channel.minDynamicRange = 0;
            channel.maxDynamicRange = 255;
            channelProperties.add(channel);
        }

        pool = new ResourcePool<RawArrayReader>(2) {
            @Override
            protected RawArrayReader createObject() {
                return new RawArrayReader();
            }
        };
        meta = new OpenerMeta() {
            @Override
            public String getImageName() {
                return imageName;
            }

            @Override
            public AffineTransform3D getTransform() {
                return new AffineTransform3D();
            }

            @Override
            public List<Entity> getEntities(int iChannel) {
                return new ArrayList<>();
            }

            @Override
            public ChannelProperties getChannel(int iChannel) {
                return channelProperties.get(iChannel);
            }
        };

    }

    @Override
    public int[] getCellDimensions(int level) {
        return new int[]{512,512,1};
    }


    @Override
    public Dimensions[] getDimensions() {
        return new Dimensions[0];
    }

    @Override
    public int getNChannels() {
        return channelProperties.size();
    }

    @Override
    public int getNTimePoints() {
        return 1;
    }

    @Override
    public int getNumMipmapLevels() {
        return 1;
    }

    @Override
    public ResourcePool<RawArrayReader> getPixelReader() {
        return pool;
    }

    @Override
    public Type<? extends NumericType> getPixelType() {
        return new UnsignedByteType();
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return new FinalVoxelDimensions("px",1,1,1);
    }

    @Override
    public boolean isLittleEndian() {
        return false;
    }

    @Override
    public BiopSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
        return new EmptySetupLoader<>(message);
    }

    @Override
    public String getRawPixelDataKey() {
        return "empty."+imageName+"."+message;
    }

    @Override
    public OpenerMeta getMeta() {
        return null;
    }

    @Override
    public void close() throws IOException {
        // Nothing to be done
    }

    static class EmptySetupLoader<A> extends BiopSetupLoader<UnsignedByteType, VolatileUnsignedByteType, A> {

        final AffineTransform3D transform3D = new AffineTransform3D();
        final VoxelDimensions voxelDimensions = new FinalVoxelDimensions("px",1,1,1);
        final double[][] mipmapResolutions;
        final int sx = 512, sy = 512;
        final Dimensions dimensions;
        final RandomAccessibleInterval<UnsignedByteType> zeRAI;
        final RandomAccessibleInterval<VolatileUnsignedByteType> zeVolatileRAI;
        final String message;

        public EmptySetupLoader(String message) {
            super(new UnsignedByteType(), new VolatileUnsignedByteType());
            this.message = message;
            mipmapResolutions = new double[1][3];
            mipmapResolutions[0][0] = 1;
            mipmapResolutions[0][1] = 1;
            mipmapResolutions[0][2] = 1;
            dimensions = new Dimensions() {

                @Override
                public long dimension(int d) {
                    if (d == 0) return sx;
                    if (d == 1) return sy;
                    return 1;
                }

                @Override
                public int numDimensions() {
                    return 3;
                }
            };

            ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory( ReadOnlyCachedCellImgOptions.options().cellDimensions(512,512,1));

            byte[] rawImage = EmptyOpener.getImage(message, 512, 512);
            zeRAI = factory.create(new long[]{512,512,1}, new UnsignedByteType(), cell -> {
                int iPix = 0;
                for (Iterator<UnsignedByteType> it = Views.flatIterable(cell).iterator(); it.hasNext(); ) {
                    UnsignedByteType pixel = it.next();
                    pixel.set(255-rawImage[iPix]);
                    iPix++;
                }
            });
            zeVolatileRAI = VolatileViews.wrapAsVolatile(zeRAI);
        }

        @Override
        public RandomAccessibleInterval<VolatileUnsignedByteType> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
            return zeVolatileRAI;
        }

        @Override
        public RandomAccessibleInterval<UnsignedByteType> getImage(int i, int i1, ImgLoaderHint... imgLoaderHints) {
            return zeRAI;
        }

        @Override
        public Dimensions getImageSize(int timepointId, int level) {
            return dimensions;
        }

        @Override
        public double[][] getMipmapResolutions() {
            return mipmapResolutions;
        }

        @Override
        public AffineTransform3D[] getMipmapTransforms() {
            return new AffineTransform3D[]{transform3D};
        }

        @Override
        public int numMipmapLevels() {
            return 1;
        }

        @Override
        public VoxelDimensions getVoxelSize(int i) {
            return voxelDimensions;
        }
    }

    public static byte[] getImage(String text, int w, int h) {

        Font font = new Font("Arial", Font.BOLD, 30);

        ByteProcessor ip = new ByteProcessor(w,h);
        ip.setColor(Color.GREEN);
        ip.setFont(font);
        ip.drawString(text, 20, 160);
        return (byte[]) ip.getPixels();

        /*
           Because font metrics is based on a graphics context, we need to create
           a small, temporary image so we can ascertain the width and height
           of the final image
         */
        /*BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = img.createGraphics();
        Font font = new Font("Arial", Font.PLAIN, 48);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int width = fm.stringWidth(text);
        int height = fm.getHeight();
        System.out.println("w= "+width+" h= "+height);
        g2d.dispose();

        img = new BufferedImage(512, 512, BufferedImage.TYPE_BYTE_GRAY);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setFont(font);
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, 0, fm.getAscent());
        g2d.dispose();
        /*g2d.get
        try {
            ImageIO.write(img, "png", new File("Text.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }*/
        //int[] pixels = new int[512*512];
        //return img.getRaster().getPixels(0,0,512,512, pixels);
        /*ByteOutputStream bos = null;
        try {
            bos = new ByteOutputStream();
            ImageIO.write(img, "bmp", bos);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (Exception e) {
            }
        }*/

        // return bos == null ? null : bos.getBytes();

    }
}
