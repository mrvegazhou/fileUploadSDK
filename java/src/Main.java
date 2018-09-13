import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import com.vega.sdk.file.FileManager;
import com.vega.sdk.file.SplitFileManager;

public class Main {

    public static void main(String[] args) throws Throwable, ExecutionException {
//        SplitFileManager splitFileManager = new SplitFileManager("192.168.33.18:8080", "admin1", "123");
//        SplitFileManager splitFileManager = new SplitFileManager("localhost:8080", "admin1", "123");
//        System.out.println(splitFileManager.splitUploadFile(args[0], args[1], Integer.parseInt(args[2])));
//        System.out.println(splitFileManager.splitUploadFile("C:\\Users\\vega\\Desktop\\3333.rar", "vega4", 2));


//        splitFileManager.initialize("C:/Users/vega/Desktop/3333.rar", "vega-test-22", 2);
//        System.out.println(splitFileManager.postRestFile(0, 30, 0));
//        File file = new File("C:/Users/vega/Desktop/1.txt");
//        System.out.println(file.isFile());
        FileManager fm = new FileManager("192.168.33.18:8080","admin1", "123");
        Map map = new HashMap();
        map.put("name", "222");
        map.put("path", "vega-test-2ss22");
        map.put("ext", "txt");
        System.out.println(fm.localFileUpload(map, "C:/Users/vega/Desktop/3333.rar"));
        System.out.println("-=-=-=-=");
    }
}
