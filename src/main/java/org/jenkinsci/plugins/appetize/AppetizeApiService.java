/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Appetize.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.appetize;

import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

/**
 * Developers: Weiyin He and John Snyder
 */
public class AppetizeApiService {
    private final String PRESIGN_URL = "https://api.appetize.io/v1/jenkins/presigned";
    private final String UPDATE_URL = "https://api.appetize.io/v1/app/update";

    private PrintStream logger;
    private Gson gson;

    public AppetizeApiService(PrintStream logger) {
        this.logger = logger;
        this.gson = new Gson();
    }

    public static class AppetizePresignedUrls {
        public String iosUrl;
        public String androidUrl;
    }

    public static class AppetizeUpdateParams {
        public String url;
        public String platform;
        public String token;
        public String privateKey;
        public String source;
        public String jenkinsUUID;
        public String jobUUID;
        public Integer buildNumber;
    }

    public static class AppetizeUpdateResult {
        public String publicKey;
        public String privateKey;
        public String publicURL;
        public String appURL;
        public String manageURL;
    }

    public AppetizePresignedUrls getPresignedUrls() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(PRESIGN_URL);
            connection = getConnection(url);
            connection.setRequestMethod("GET");
            connection.connect();
            int status = connection.getResponseCode();

            if (status >= 200 && status <= 299) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                return gson.fromJson(reader, AppetizePresignedUrls.class);
            } else {
                String errorMessage = readToString(connection.getErrorStream());
                throw new Exception("Status " + status + ": " + errorMessage);
            }
        } catch (Exception e) {
            println("Error getting Appetize.io upload URLs");
            println(e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean uploadData(InputStream in, String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = getConnection(url);
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");

            OutputStream out = connection.getOutputStream();
            copy(in, out);
            in.close();
            out.close();

            int status = connection.getResponseCode();
            if (status >= 200 && status <= 299) {
                return true;
            } else {
                throw new Exception("Status " + status);
            }
        } catch (Exception e) {
            println("Error uploading to " + urlString);
            println(e.getMessage());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public AppetizeUpdateResult updateApp(AppetizeUpdateParams params) {

        HttpURLConnection connection = null;
        try {
            URL url = new URL(UPDATE_URL);
            connection = getConnection(url);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // upload json
            String json = gson.toJson(params);
            OutputStream out = connection.getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(out);
            writer.write(json);
            writer.flush();
            writer.close();

            // get response
            int status = connection.getResponseCode();
            if (status >= 200 && status <= 299) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                return gson.fromJson(reader, AppetizeUpdateResult.class);
            } else {
                String errorMessage = readToString(connection.getErrorStream());
                throw new Exception("Status " + status + ": " + errorMessage);
            }
        } catch (Exception e) {
            println("Error calling Appetize.io API");
            println(e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    void println(String message) {
        if (this.logger != null) logger.println(message);
    }

    String readToString(InputStream is) {
        if (is == null) return null;

        InputStreamReader reader = new InputStreamReader(is);
        StringWriter writer = new StringWriter();
        char[] buf = new char[1024];
        try {
            while (reader.read(buf) > 0) {
                writer.write(buf);
            }
            is.close();

            return writer.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException{
        byte[] buf = new byte[1024 * 10];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
    }

    /**
     * Returns an HttpURLConnection using the Jenkins global proxy if set
     * @param url URL to open the connection
     * @return Instanciated connection
     * @throws IOException
     */
    private HttpURLConnection getConnection(URL url) throws IOException {

        HttpURLConnection connection;
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if (proxyConfig != null) {
            Proxy proxy = proxyConfig.createProxy(url.getHost());
            if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
                connection = (HttpURLConnection)url.openConnection(proxy);
            }
            else {
                connection = (HttpURLConnection)url.openConnection();
            }
        }
        else {
            connection = (HttpURLConnection)url.openConnection();
        }

        return connection;
    }
}
