package com.vega.sdk.file;

import com.alibaba.fastjson.JSONObject;
import com.vega.sdk.http.Request;

import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class FileManager {

    public static String tokenUrl = "http://%s/api/v1/auth/";
    public static String fileUrl = "http://%s/api/v1/file/";
    private static String token;
    private String username;
    private String password;
    public long fileSize;
    public String fileName;
    public String name;
    public String exts;
    public String path;
    public String filePath;
    public String ext;

    public FileManager(String serverURL, String username, String password) {
        if (serverURL==null || serverURL.equals("")) {
            serverURL = "localhost:8080";
        }
        FileManager.tokenUrl = String.format(FileManager.tokenUrl, serverURL);
        FileManager.fileUrl = String.format(FileManager.fileUrl, serverURL);

        this.username = username;
        this.password = password;
    }

    private enum fileHttpURLs {
        urlFileUpload("urlFileUpload"),
        localFileUpload("localFileUpload"),
        processStatus("processStatus"),
        deleteFolder("deleteFolder"),
        deleteFiles("deleteFiles"),
        lookupFiles("lookupFiles"),
        download("download"),
        img("img"),
        downloadZip("downloadZip");

        private String name;
        private fileHttpURLs(String name) {
            this.name = FileManager.fileUrl+name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private enum authHttpURLs {
        refreshToken("refreshToken"),
        token("token");

        private String name;
        private authHttpURLs(String name) {
            this.name = FileManager.tokenUrl+name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public String token() {
        String url = authHttpURLs.token.toString();
        Map map = new HashMap();
        map.put("username", this.username);
        map.put("password", this.password);
        try {
            String response = Request.post(url).setParams(map).sendPost();
            JSONObject jsonObject = JSONObject.parseObject(response);
            int status = Integer.parseInt(jsonObject.getString("status"));
            if (status==200) {
                FileManager.token = jsonObject.getString("result");
            } else {
                throw new RuntimeException("Token failed!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Token failed："+e.getMessage());
        }
        return FileManager.token;
    }

    public String refreshToken() {
        String response = "";
        Map map = new HashMap();
        map.put("username", this.username);
        map.put("password", this.password);
        try {
            response = Request.post(authHttpURLs.refreshToken.toString()).setParams(map).sendPost();
        } catch (Exception e) {
            throw new RuntimeException("token refresh the failure："+e.getMessage());
        }
        return response;
    }

    public String splitFileUpload(Map map, String range, String file, InputStream is) {
        String response = null;
        if (FileManager.token==null || FileManager.token.equals("")) {
            this.token();
        }
        try {
            response = Request.post(fileHttpURLs.localFileUpload.toString())
                    .header("Authorization", "Bearer " + FileManager.token)
                    .header("Range", "bytes="+range)
                    .setParams(map)
                    .sendFile(file, is);
        } catch (Exception e) {
            throw new RuntimeException("The request failed:"+e.toString());
        }
        return response;
    }

    public String localFileUpload(Map map, String file) {
        String response = null;
        if (FileManager.token==null || FileManager.token.equals("")) {
            this.token();
        }
        try {
            response = Request.post(fileHttpURLs.localFileUpload.toString())
                    .header("Authorization", "Bearer " + FileManager.token)
                    .setParams(map)
                    .sendFile(file, null);
        } catch (Exception e) {
            throw new RuntimeException("The request failed:"+e.toString());
        }
        return response;
    }

    private String commonManage(Map map, String url, String method) {
        String response = null;
        if (FileManager.token==null || FileManager.token.equals("")) {
            this.token();
        }
        try {
            if (method.toLowerCase()=="get") {
                response = Request.get(url)
                        .header("Authorization", "Bearer " + FileManager.token)
                        .setParams(map)
                        .sendGet();
            } else {
                response = Request.post(url)
                        .header("Authorization", "Bearer " + FileManager.token)
                        .setParams(map)
                        .sendPost();
            }
        } catch (Exception e) {
            throw new RuntimeException("The request failed:"+e.toString());
        }
        return response;
    }

    public String urlFileUpload(Map<String, Object> map) {
        if (!map.containsKey("url") || map.get("url").equals("")) {
            throw new RuntimeException("Parameter `url` cannot be empty!");
        }
        return this.commonManage(map, fileHttpURLs.urlFileUpload.toString(), "post");
    }

    public String processStatus(Map<String, Object> map) {
        if (!map.containsKey("name") || map.get("name").equals("")) {
            throw new RuntimeException("Parameter `name` cannot be empty!");
        }
        if (map.containsKey("isLocalFlag") && !map.get("isLocalFlag").equals("")) {
            String isLocalFlag = String.valueOf(map.get("isLocalFlag"));
            if (isLocalFlag.equals("0")) {
                if (!map.containsKey("url") || map.get("url").equals("")) {
                    throw new RuntimeException("Parameter `url` cannot be empt!");
                }
            } else if(isLocalFlag.equals("0")) {

            } else {
                throw new RuntimeException("Parameter `isLocalFlag` error!");
            }
        }
        return this.commonManage(map, fileHttpURLs.processStatus.toString(), "post");
    }

    public String deleteFolder(Map<String, Object> map) {
        if (!map.containsKey("folder") || map.get("folder").equals("")) {
            throw new RuntimeException("Parameter `folder` cannot be empty!");
        }
        return this.commonManage(map, fileHttpURLs.deleteFolder.toString(), "post");
    }

    public String deleteFiles(IdentityHashMap<String, Object> map) {
        if (map.size()==0) {
            throw new RuntimeException("Parameter `files[]` cannot be empty!");
        }
        return this.commonManage(map, fileHttpURLs.deleteFiles.toString(), "post");
    }

    public String lookupFiles(Map<String, String> map) {
        return this.commonManage(map, fileHttpURLs.lookupFiles.toString(), "get");
    }

    public Object download(Map<String, String> map) {
        if (!map.containsKey("filePath")) {
            throw new RuntimeException("Parameter `filePath` cannot be empty!");
        }
        return this.commonManage(map, fileHttpURLs.download.toString(), "get");
    }

    public Object downloadZip(IdentityHashMap<String, String> map) {
        if (!map.containsKey("zipName")) {
            throw new RuntimeException("Parameter `zipName` cannot be empty!");
        }
        if (map.size()<2) {
            throw new RuntimeException("Parameter error!");
        }
        return this.commonManage(map, fileHttpURLs.downloadZip.toString(), "post");
    }

    public Object img(Map<String, String> map) {
        if (!map.containsKey("path")) {
            throw new RuntimeException("The parameter `path` cannot be empty");
        }
        return this.commonManage(map, fileHttpURLs.img.toString(), "get");
    }
}
