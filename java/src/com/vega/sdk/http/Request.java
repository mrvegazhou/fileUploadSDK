package com.vega.sdk.http;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Request {

    private int timeout = 120000;
    private String url;
    private InputStream inputStream;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, Object> formdatas = new HashMap<>();

    private enum HttpMethod {
        GET, POST
    }

    private String getBoundary() {
        return UUID.randomUUID().toString();
    }

    private HttpMethod httpMethod;

    private Request(String url, HttpMethod httpMethod) {
        this.url = url;
        this.httpMethod = httpMethod;
    }

    public Request header(String name, String value){
        this.headers.put(name, value);
        return this;
    }

    public Request timeout(int timeout){
        this.timeout = timeout;
        return this;
    }

    public Request setParams(Map<String, Object> params){
        this.formdatas.putAll(params);
        return this;
    }

    private void setPostFormData(StringBuilder sb, String boundary, String key, String val) {
        sb = sb.append("--").append(boundary);
        sb.append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"");
        sb.append(key);
        sb.append("\"");
        sb.append("\r\n\r\n");
        sb.append(val);
        sb.append("\r\n");
    }

    private HttpURLConnection excuteConn() throws IOException {
        URL urlObj = new URL(this.url);
        HttpURLConnection connection = url.startsWith("https") ? (HttpsURLConnection) urlObj.openConnection() : (HttpURLConnection) urlObj.openConnection();
        connection.setRequestMethod(this.httpMethod.toString());
        connection.setReadTimeout(this.timeout);
        connection.setRequestProperty("Charsert", "UTF-8");
        headers.forEach((key, value) -> connection.setRequestProperty(key, value.toString()));

        return connection;
    }

    public String sendGet() {
        String result = "";
        BufferedReader in = null;
        try {
            StringBuilder sb = new StringBuilder();
            if (this.formdatas.size()>0) {
                this.url = this.url.indexOf("?")!=-1 ? this.url + "&" : this.url + "?";
            }
            sb.append(this.url);
            for (Map.Entry<String, Object> entry : this.formdatas.entrySet()) {
                sb.append(entry.getKey());
                sb.append("=");
                sb.append(entry.getValue());
            }
            this.url = sb.toString();
            System.out.println(this.url);
            final HttpURLConnection connection = this.excuteConn();
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }  finally{
            try{
                if(in!=null){
                    in.close();
                }
            }
            catch(IOException ex){
                ex.printStackTrace();
            }
        }
        return result;
    }

    public String sendFile(String filePath, InputStream is) {
        StringBuilder resSb = new StringBuilder();
        DataInputStream in = null;
        DataOutputStream out = null;
        BufferedReader bf = null;
        final String BOUNDARY = this.getBoundary();
        try {
            final HttpURLConnection connection = this.excuteConn();
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            StringBuilder sb = new StringBuilder();
            if (this.formdatas.size()>0) {
                this.formdatas.forEach((k,v)->{
                    this.setPostFormData(sb, BOUNDARY, k, v.toString());
                });
            }

            out = new DataOutputStream(connection.getOutputStream());
            byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();

            sb.append("--");
            sb.append(BOUNDARY);
            sb.append("\r\n");

            if (filePath!=null && !filePath.equals("")) {
                File file = new File(filePath);
                sb.append("Content-Disposition: form-data;name=\"file\";filename=\""+ file.getName() + "\"\r\n");
                sb.append("Content-Type:application/octet-stream; charset=utf-8\r\n\r\n");

                byte[] data = sb.toString().getBytes();
                out.write(data);

                in = new DataInputStream(new FileInputStream(file));
                int bytes = 0;
                byte[] bufferOut = new byte[1024];
                while ((bytes = in.read(bufferOut)) != -1) {
                    out.write(bufferOut, 0, bytes);
                }
                in.close();
            } else {
                sb.append("Content-Disposition: form-data;name=\"file\";filename=\"file\"\r\n");
                sb.append("Content-Type:application/octet-stream; charset=utf-8\r\n\r\n");
                byte[] data = sb.toString().getBytes();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] flush = new byte[1024];
                while ((nRead = is.read(flush, 0, data.length)) != -1) {
                    buffer.write(flush, 0, nRead);
                }
                buffer.flush();

                byte[] byteRes = byteMerger(data, buffer.toByteArray());
                out.write(byteRes);
            }

            out.write(end_data);
            out.flush();
            out.close();
            if (is!=null)
                is.close();

            InputStream inputStream = connection.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            bf = new BufferedReader(inputStreamReader);
            String line = null;

            while ((line=bf.readLine())!=null) {
                resSb.append(line);
            }
            bf.close();
            inputStream.close();
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                if (in!=null)
                    in.close();
                if (out!=null)
                    out.close();
                if (bf!=null)
                    bf.close();
                if (is!=null)
                    is.close();
            } catch (IOException ex){
                in = null;
                out = null;
                bf = null;
                is = null;
                ex.printStackTrace();
            }
        }
        return resSb.toString();
    }

    private byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        int i=0;
        for(byte bt: bt1){
            bt3[i]=bt;
            i++;
        }
        for(byte bt: bt2){
            bt3[i]=bt;
            i++;
        }
        return bt3;
    }

    public String sendPost() throws IOException {
        StringBuilder resSb = new StringBuilder();
        BufferedReader bf = null;
        DataOutputStream out = null;
        try {
            final HttpURLConnection connection = this.excuteConn();
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            String urlParameters = "";
            if (this.formdatas.size() > 0) {
                StringBuffer sb = new StringBuffer();
                this.formdatas.forEach((key, value) -> sb.append("&" + key + "=" + formdatas.get(key)));
                urlParameters = sb.toString();
            }
            out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(urlParameters);
            out.flush();
            out.close();

            bf = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            String line = null;

            while ((line = bf.readLine()) != null) {
                resSb.append(line);
            }

        } finally{
            try{
                if (out!=null) {
                    out.close();
                }
                if (bf!=null) {
                    bf.close();
                }
            } catch (IOException ex){
                out = null;
                bf = null;
                ex.printStackTrace();
            }
        }
        return resSb.toString();
    }

    public static Request post(String url){
        return new Request(url, HttpMethod.POST);
    }

    public static Request get(String url){
        return new Request(url, HttpMethod.GET);
    }

}
