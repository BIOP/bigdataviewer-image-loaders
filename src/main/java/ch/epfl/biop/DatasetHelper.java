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
            return null;
        }
    }

    static Function<String, String> decoder = (str) -> {
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
            dlUnzip(urlBrainSlices+fName, targetDir);
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
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

}
