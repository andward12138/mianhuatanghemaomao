package com.example.message.services;

import com.example.message.model.Anniversary;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 纪念日服务类
 * 负责纪念日的CRUD操作和业务逻辑
 */
public class AnniversaryService {
    private static final Logger logger = Logger.getLogger(AnniversaryService.class.getName());
    private static final String API_BASE_URL = "http://8.134.99.69:3001/api";
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 30000; // 30秒
    
    /**
     * 添加新纪念日
     * @param anniversary 纪念日对象
     * @return 是否成功
     */
    public static boolean addAnniversary(Anniversary anniversary) {
        logger.info("尝试添加纪念日: " + anniversary.getTitle());
        
        try {
            URL url = new URL(API_BASE_URL + "/anniversaries");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 构建JSON请求体
            String jsonInputString = String.format(
                "{\"title\":\"%s\",\"date\":\"%s\",\"description\":\"%s\",\"photos\":\"%s\",\"is_recurring\":%s,\"reminder_days\":%d,\"category\":\"%s\",\"created_by\":\"%s\"}",
                anniversary.getTitle(),
                anniversary.getDate().toString(),
                anniversary.getDescription() != null ? anniversary.getDescription().replace("\"", "\\\"") : "",
                anniversary.getPhotos() != null ? anniversary.getPhotos() : "",
                anniversary.isRecurring(),
                anniversary.getReminderDays(),
                anniversary.getCategory() != null ? anniversary.getCategory() : "love",
                anniversary.getCreatedBy()
            );
            
            logger.info("发送纪念日数据: " + jsonInputString);
            
            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                // 读取响应获取生成的ID
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }
                
                logger.info("纪念日添加成功: " + response.toString());
                return true;
            } else {
                logger.severe("添加纪念日失败: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "添加纪念日时发生异常", e);
            return false;
        }
    }
    
    /**
     * 获取所有纪念日
     * @return 纪念日列表
     */
    public static List<Anniversary> getAllAnniversaries() {
        logger.info("获取所有纪念日");
        
        List<Anniversary> anniversaries = new ArrayList<>();
        String currentUser = ApiService.getCurrentUser();
        
        try {
            String urlStr = API_BASE_URL + "/anniversaries";
            if (currentUser != null && !currentUser.isEmpty()) {
                urlStr += "?user=" + java.net.URLEncoder.encode(currentUser, StandardCharsets.UTF_8.name());
            }
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // 解析JSON响应
                String jsonStr = response.toString();
                logger.info("从服务器获取到纪念日数据: " + jsonStr.length() + " 字节");
                
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    if (jsonStr.equals("[]")) {
                        logger.info("服务器返回空的纪念日列表");
                        return anniversaries;
                    }
                    
                    String content = jsonStr.substring(1, jsonStr.length() - 1);
                    String[] items = content.split("\\},\\{");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                        if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                        else item = "{" + item + "}";
                        
                        // 解析JSON对象
                        Anniversary anniversary = parseAnniversaryFromJson(item);
                        if (anniversary != null) {
                            anniversaries.add(anniversary);
                        }
                    }
                }
            } else {
                logger.severe("获取纪念日失败: " + responseCode);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "获取纪念日时发生异常", e);
        }
        
        logger.info("获取到 " + anniversaries.size() + " 个纪念日");
        return anniversaries;
    }
    
    /**
     * 获取即将到来的纪念日
     * @param days 未来几天内的纪念日
     * @return 即将到来的纪念日列表
     */
    public static List<Anniversary> getUpcomingAnniversaries(int days) {
        logger.info("获取未来 " + days + " 天内的纪念日");
        
        List<Anniversary> upcomingAnniversaries = new ArrayList<>();
        String currentUser = ApiService.getCurrentUser();
        
        try {
            String urlStr = API_BASE_URL + "/anniversaries/upcoming?days=" + days;
            if (currentUser != null && !currentUser.isEmpty()) {
                urlStr += "&user=" + java.net.URLEncoder.encode(currentUser, StandardCharsets.UTF_8.name());
            }
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // 解析JSON响应
                String jsonStr = response.toString();
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    if (!jsonStr.equals("[]")) {
                        String content = jsonStr.substring(1, jsonStr.length() - 1);
                        String[] items = content.split("\\},\\{");
                        
                        for (int i = 0; i < items.length; i++) {
                            String item = items[i];
                            if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                            if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                            else item = "{" + item + "}";
                            
                            Anniversary anniversary = parseAnniversaryFromJson(item);
                            if (anniversary != null) {
                                upcomingAnniversaries.add(anniversary);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "获取即将到来的纪念日时发生异常", e);
        }
        
        logger.info("获取到 " + upcomingAnniversaries.size() + " 个即将到来的纪念日");
        return upcomingAnniversaries;
    }
    
    /**
     * 更新纪念日
     * @param anniversary 纪念日对象
     * @return 是否成功
     */
    public static boolean updateAnniversary(Anniversary anniversary) {
        logger.info("尝试更新纪念日: ID=" + anniversary.getId());
        
        try {
            URL url = new URL(API_BASE_URL + "/anniversaries/" + anniversary.getId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 构建JSON请求体
            String jsonInputString = String.format(
                "{\"title\":\"%s\",\"date\":\"%s\",\"description\":\"%s\",\"photos\":\"%s\",\"is_recurring\":%s,\"reminder_days\":%d,\"category\":\"%s\"}",
                anniversary.getTitle(),
                anniversary.getDate().toString(),
                anniversary.getDescription() != null ? anniversary.getDescription().replace("\"", "\\\"") : "",
                anniversary.getPhotos() != null ? anniversary.getPhotos() : "",
                anniversary.isRecurring(),
                anniversary.getReminderDays(),
                anniversary.getCategory() != null ? anniversary.getCategory() : "love"
            );
            
            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("纪念日更新成功: ID=" + anniversary.getId());
                return true;
            } else {
                logger.severe("更新纪念日失败: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "更新纪念日时发生异常", e);
            return false;
        }
    }
    
    /**
     * 删除纪念日
     * @param anniversaryId 纪念日ID
     * @return 是否成功
     */
    public static boolean deleteAnniversary(int anniversaryId) {
        logger.info("尝试删除纪念日: ID=" + anniversaryId);
        
        try {
            URL url = new URL(API_BASE_URL + "/anniversaries/" + anniversaryId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("纪念日删除成功: ID=" + anniversaryId);
                return true;
            } else {
                logger.severe("删除纪念日失败: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "删除纪念日时发生异常", e);
            return false;
        }
    }
    
    /**
     * 获取今天的纪念日
     * @return 今天的纪念日列表
     */
    public static List<Anniversary> getTodayAnniversaries() {
        List<Anniversary> allAnniversaries = getAllAnniversaries();
        return allAnniversaries.stream()
                .filter(Anniversary::isToday)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取需要提醒的纪念日
     * @return 需要提醒的纪念日列表
     */
    public static List<Anniversary> getRemindableAnniversaries() {
        List<Anniversary> allAnniversaries = getAllAnniversaries();
        return allAnniversaries.stream()
                .filter(Anniversary::shouldRemind)
                .collect(Collectors.toList());
    }
    
    /**
     * 从JSON字符串解析纪念日对象
     * @param json JSON字符串
     * @return 纪念日对象
     */
    private static Anniversary parseAnniversaryFromJson(String json) {
        try {
            int id = extractIntField(json, "id");
            String title = extractStringField(json, "title");
            String dateStr = extractStringField(json, "date");
            String description = extractStringField(json, "description");
            String photos = extractStringField(json, "photos");
            boolean isRecurring = extractIntField(json, "is_recurring") == 1;
            int reminderDays = extractIntField(json, "reminder_days");
            String category = extractStringField(json, "category");
            String createdBy = extractStringField(json, "created_by");
            String createTimeStr = extractStringField(json, "create_time");
            
            if (title != null && dateStr != null) {
                LocalDate date = LocalDate.parse(dateStr);
                LocalDateTime createTime = createTimeStr != null ? 
                    LocalDateTime.parse(createTimeStr.replace("Z", "")) : LocalDateTime.now();
                
                Anniversary anniversary = new Anniversary(id, title, date, description, photos, 
                    isRecurring, reminderDays, category, createdBy, createTime);
                return anniversary;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "解析纪念日JSON时出错: " + json, e);
        }
        return null;
    }
    
    /**
     * 从JSON提取整数字段
     */
    private static int extractIntField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }
    
    /**
     * 从JSON提取字符串字段
     */
    private static String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
