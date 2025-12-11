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
package ch.epfl.biop;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A utility class that helps loading and caching file from the internet
 */

public class DatasetHelper {

    public static final File cachedSampleDir = new File(System.getProperty("user.home"),"CachedSamples");


    final public static String JPG_RGB =
            "https://biop.epfl.ch/img/splash/physicsTemporal_byRGUIETcrop.jpg";
    final public static String OLYMPUS_OIR =
            "https://downloads.openmicroscopy.org/images/Olympus-OIR/etienne/venus%20stack.ome.tif";
    final public static String VSI =
            "https://github.com/NicoKiaru/TestImages/raw/master/VSI/Fluo3DFluoImage2Channels_01.vsi";
    final public static String LIF =
            "https://downloads.openmicroscopy.org/images/Leica-LIF/michael/PR2729_frameOrderCombinedScanTypes.lif";
    final public static String TIF_TIMELAPSE_3D =
            "https://github.com/NicoKiaru/TestImages/raw/master/CElegans/dub-0.5xy-TP1-22.tif";
    final public static String ND2_20X =
            "https://github.com/NicoKiaru/TestImages/raw/master/ND2/20x_g5_a1.nd2";
    final public static String ND2_60X =
            "https://github.com/NicoKiaru/TestImages/raw/master/ND2/60x_g5_a1.nd2";

    public static File urlToFile(URL url, Function<String, String> decoder) {
        try {
            File file_out = new File(cachedSampleDir,decoder.apply(url.getFile()));
            if (file_out.exists()) {
                return file_out;
            } else {
                System.out.println("Downloading and caching: "+url+" size = "+ (getFileSize(url)/1024) +" kb");
                FileUtils.copyURLToFile(url, file_out, 10000, 10000);
                System.out.println("Downloading and caching of "+url+" completed successfully ");
                return file_out;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static final Function<String, String> decoder = (str) -> {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch(Exception e){
            e.printStackTrace();
            return str;
        }
    };

    public static File getDataset(String urlString) {
        return getDataset(urlString, decoder);
    }

    public static File getDataset(String urlString, Function<String, String> decoder) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return urlToFile(url, decoder);
    }

    // https://stackoverflow.com/questions/12800588/how-to-calculate-a-file-size-from-url-in-java
    private static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
        }
    }

    public static Thread ASyncDL(String str) {
        Thread thread = new Thread(() -> getDataset(str));
        thread.start();
        return thread;
    }

    public static String getSampleVSIDataset() {
        Thread t0 = ASyncDL(VSI);
        Thread t1 = ASyncDL(
                "https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack1/frame_t_0.ets");
        Thread t2 = ASyncDL(
                "https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack10001/frame_t_0.ets");
        Thread t3 = ASyncDL(
                "https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack10004/frame_t_0.ets");
        try {
            t0.join();
            t1.join();
            t2.join();
            t3.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return getDataset(VSI).getAbsolutePath();
    }

    public static String dowloadBrainVSIDataset() throws IOException {
        dowloadBrainVSIDataset(0);
        dowloadBrainVSIDataset(1);
        dowloadBrainVSIDataset(2);
        dowloadBrainVSIDataset(3);
        dowloadBrainVSIDataset(4);
        dowloadBrainVSIDataset(5);
        return dowloadBrainVSIDataset(6);
    }

    public static String dowloadBrainVSIDataset(int i) throws IOException {
        // URL = https://zenodo.org/records/6553641
        String targetDir = cachedSampleDir.getAbsolutePath()+File.separator+"records"+File.separator+"6553641"+File.separator+"files"+File.separator;
        String urlBrainSlices = "https://zenodo.org/records/6553641/files/";
        String fName = "Slide_0"+i+".vsi";
        if (!new File(targetDir+fName).exists())
            dlUnzip(urlBrainSlices+"Slide_0"+i+".zip", targetDir);
        return targetDir;
    }

    private static void dlUnzip(String url, String targetDir) throws IOException{
        File tempZip = getDataset(url);
        unpack(tempZip.getAbsolutePath(), targetDir);
        tempZip.delete();
    }

    private static void unpack(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    private static final int BUFFER_SIZE = 4096;

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

}
