package me.demro.dBans.util.geo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpDownloader {
    public static boolean downloadFile(String urlString, File destination) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "DBans Plugin");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}