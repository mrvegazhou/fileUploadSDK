package com.vega.sdk.file;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SplitFileManager extends FileManager{
    public int blockTotal = 0;
    public long blockSize;
    public File srcObj;
    private static String lock = "lock";

    public SplitFileManager(String serverURL, String username, String password) {
        super(serverURL, username, password);
    }

    private void init() {
        File srcObj = null;
        if (null==this.filePath||!(srcObj=new File(this.filePath)).isFile()) {
            throw new RuntimeException("File does not exist!");
        }
        if (this.path==null || this.path.equals("")) {
            throw new RuntimeException("The file store path is empty!");
        }
        if (this.blockTotal<=0) {
            throw new RuntimeException("The total number of pieces should be greater than or equal to 1!");
        }
        this.fileSize = srcObj.length();
        this.blockSize = (long) Math.ceil((double)this.fileSize / (double)this.blockTotal);

        this.fileName = srcObj.getName();
        this.srcObj = srcObj;
        if (this.fileName.indexOf(".")!=-1) {
            this.ext = this.fileName.substring(this.fileName.lastIndexOf(".")+1);
            this.name = this.fileName.substring(0, this.fileName.lastIndexOf("."));
        } else {
            this.ext = "";
            this.name = this.fileName;
        }
    }

    public String splitUploadFile(String filePath, String path, int blockTotal) {
        String response = "";
        try {
            this.initialize(filePath, path, blockTotal);

            ExecutorService exec = Executors.newCachedThreadPool();

            ArrayList<Future<String>>  results = new ArrayList<Future<String>>();
            for (int i=0; i<this.blockTotal; i++) {
                results.add(exec.submit(new TaskResult(this.filePath, i, this.blockSize, this)));
            }

            long tmpTotal = 0;
            for (Future<String> fs : results) {
                String tmp = fs.get();
                if (!tmp.equals("")) {
                    JSONObject resObj = JSONObject.parseObject(tmp);
                    if (Integer.valueOf(resObj.getString("status"))==200) {
                        JSONObject result = resObj.getJSONObject("result");
                        if (result.getJSONObject("progressInfo").getBoolean("finish")==true) {
                            response = tmp;
                            break;
                        } else {
                            long uploadedSize = result.getJSONObject("progressInfo").getLongValue("uploadedSize");
                            if (uploadedSize>tmpTotal) {
                                tmpTotal = uploadedSize;
                                response = tmp;
                            }
                        }
                    } else {
                        response = tmp;
                        break;
                    }
                }
            }
            exec.shutdown();
        } catch (Exception e) {
                e.printStackTrace();
        }
        return response;
    }

    public void initialize(String filePath, String path, int blockTotal) {
        this.filePath = filePath;
        this.blockTotal = blockTotal;
        if (blockSize!=0) {
            this.blockSize = blockSize;
        }
        this.path = path;
        init();
    }

    public String postRestFile(long uploadStartByte, long uploadEndByte, int blockIdx) throws IOException {
        if (blockIdx>this.blockTotal) {
            throw new RuntimeException("The sharding index is out of range！");
        }
        if (this.blockTotal==(blockIdx+1)) {
            this.blockSize = this.fileSize - blockIdx*this.blockSize;
        }

        ByteArrayOutputStream byteout = null;
        RandomAccessFile tRafSource = null;
        InputStream is = null;
        try {
            tRafSource = new RandomAccessFile(this.filePath, "r");
            tRafSource.seek(uploadStartByte);
            byteout = new ByteArrayOutputStream();
            byte[] flush = new byte[1024];
            int len = 0;
            long maxSize = 0;
            long rangeLen = uploadEndByte - uploadStartByte;
            while (-1!=(len=tRafSource.read(flush)) && maxSize<this.blockSize) {
                byteout.write(flush, 0, (int)rangeLen>len ? len : (int)rangeLen);
                maxSize++;
            }
            is = new ByteArrayInputStream(byteout.toByteArray());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            byteout.close();
            tRafSource.close();
        }

        String range = String.format("%s-%s", uploadStartByte, uploadEndByte);
        Map map = new HashMap();
        map.put("name", this.name);
        map.put("path", this.path);
        map.put("fileSize", this.fileSize);
        map.put("blockIndex", blockIdx);
        map.put("blockTotal", this.blockTotal);
        map.put("ext", this.ext);
        if (is!=null) {
            return this.splitFileUpload(map, range, null, is);
        }
        return null;
    }

    private class TaskResult implements Callable<String> {
        private String rafSource;
        private int blockIndex;
        private long blockSize;
        private SplitFileManager fileManager;
        public TaskResult(String rafSource,
                          int blockIndex,
                          long blockSize,
                          SplitFileManager fileManager){
            this.rafSource = rafSource;
            this.blockIndex = blockIndex;
            this.blockSize = blockSize;
            this.fileManager = fileManager;
        }

        public String call(){
            try {
                return this.uploadBlock(null);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private void handleRespose(JSONObject jsonObject) throws IOException {
            String status = jsonObject.getString("status");
            if (Integer.parseInt(status)==200) {
                JSONArray array = jsonObject.getJSONObject("result").getJSONObject("progressInfo").getJSONArray("process");
                for (int i = 0; i < array.size(); i++) {
                    JSONObject jo = array.getJSONObject(i);
                    if (jo.getInteger("number")==this.blockIndex) {
                        if ((jo.getLong("endByte")-jo.getLong("startByte"))>jo.getLong("uploadedByte")) {
                            this.uploadBlock(jo);
                        }
                    }
                }
            } else {
                throw new RuntimeException("Transmission errors:" + jsonObject.toJSONString()+Thread.currentThread().getName());
            }
        }

        private String uploadBlock(JSONObject info) throws IOException {
            long uploadStartByte = 0;
            long uploadEndByte = 0;

            if (this.fileManager.blockTotal==(this.blockIndex+1)) {
                this.blockSize = this.fileManager.fileSize - this.blockIndex*this.blockSize;
            }

            if (info!=null) {
                uploadStartByte = info.getLong("startByte")+info.getLong("uploadedByte");
                uploadEndByte = info.getLong("endByte");
            } else {
                uploadStartByte = this.blockIndex*this.fileManager.blockSize;
                uploadEndByte = uploadStartByte + this.blockSize;
            }

            RandomAccessFile tRafSource = null;
            FileChannel fileChannel = null;
            InputStream is = null;
            try {
                tRafSource = new RandomAccessFile(this.rafSource, "rw");

                long rangeLen = uploadEndByte - uploadStartByte;

                fileChannel = tRafSource.getChannel();
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, uploadStartByte, rangeLen);
                byte[] result = new byte[(int)rangeLen];
                if (mappedByteBuffer.remaining() > 0) {
                    mappedByteBuffer.get(result, 0, mappedByteBuffer.remaining());
                }
                is = new ByteArrayInputStream(result);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                fileChannel.close();
                tRafSource.close();
            }
            String response = "";
            try {
                synchronized (SplitFileManager.lock) {
                    String range = String.format("%s-%s", uploadStartByte, uploadEndByte);
                    Map map = new HashMap();
                    map.put("name", this.fileManager.name);
                    map.put("path", this.fileManager.path);
                    map.put("fileSize", this.fileManager.fileSize);
                    map.put("blockIndex", this.blockIndex);
                    map.put("blockTotal", this.fileManager.blockTotal);
                    map.put("ext", this.fileManager.ext);
                    response = this.fileManager.splitFileUpload(map, range, null, is);
                    JSONObject jsonObject = JSONObject.parseObject(response);
                    if (Integer.valueOf(jsonObject.getString("status")) == 200 && jsonObject.getJSONObject("result").getJSONObject("progressInfo").getBoolean("finish") == false) {
                        this.handleRespose(jsonObject);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return response;
        }
    }
}
