package app.musicplayer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ApiService {
    private static final Object fileLock  = new Object();
    private static final String API_BASE_URL = "https://neapi.widcard.win/cloudsearch?keywords=";
    private static final String FIXED_FILENAME = "api_search_results.json";

    public static void searchAndSave(String searchTerm) throws IOException, InterruptedException {
        int responseCode = 0;
        synchronized(fileLock) {
            try{
                String encodedSearch = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.toString());
                String apiUrl = API_BASE_URL + encodedSearch;

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // 设置User-Agent请求头
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3");

                responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }

                        saveKeywordToTxt(searchTerm);
                        saveApiResponseToJson(response.toString());
                    }
                } else {
                    System.out.println("Response Code: " + responseCode);
                }
            }catch (Exception e) {
                e.printStackTrace(); // 打印详细的异常信息
                throw new IOException("HTTP error code: " + responseCode);
            }
        }

        // 增加请求间隔
        TimeUnit.SECONDS.sleep(1); // 1秒
    }

    private static void saveApiResponseToJson(String jsonResponse) throws IOException {
        // 获取项目out目录
        File saveDir = new File(Resources.JAR);

        // 使用固定文件名
        File jsonFile = new File(saveDir, FIXED_FILENAME);

        // 覆盖写入JSON文件
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(jsonResponse);
        }
    }

    private static void saveKeywordToTxt(String keyword) throws IOException {
        // 获取项目out目录
        File saveDir = new File(Resources.JAR);
        File keyFile = new File(saveDir, "keyword.txt");

        // 覆盖写入TXT文件
        try (FileWriter writer = new FileWriter(keyFile)) {
            writer.write(keyword);
        }
    }
}
