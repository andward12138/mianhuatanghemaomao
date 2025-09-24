package com.example.message.services;
import com.example.message.util.DBUtil;
import com.example.message.model.Diary;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiaryService {
    private static final Logger logger = Logger.getLogger(DiaryService.class.getName());
    // 云服务器API相关设置
    private static final String API_BASE_URL = "http://8.134.99.69:3001/api";
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 30000; // 30秒
    private static final int MAX_RETRIES = 3; // 最大重试次数
    
    // 强制使用云服务器存储日记
    private static final boolean useCloudStorage = true;
    
    // 设置是否使用云存储 - 为了兼容现有代码，但总是返回true
    public static void setUseCloudStorage(boolean useCloud) {
        // 忽略参数，强制使用云存储
        logger.info("Cloud storage for diaries is always enabled");
    }
    
    // 获取是否使用云存储 - 始终返回true
    public static boolean isCloudStorageEnabled() {
        return true;
    }

    // 添加日记
    public static int addDiary(String content, String mood) {
        logger.info("开始添加新日记，内容长度=" + content.length() + ", 心情=" + mood);
        
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // 创建新的Diary对象用于发送到云端
            Diary diary = new Diary(-1, content, mood, now);
            
            // 直接保存到云端
            boolean success = ApiService.saveDiary(diary);
            
            if (success) {
                logger.info("日记已成功保存到云端");
                // 获取最新的日记列表，找出刚添加的日记
                List<Diary> diaries = ApiService.getDiaries();
                if (diaries != null && !diaries.isEmpty()) {
                    // 找到最新添加的日记（通常是第一个）
                    for (Diary d : diaries) {
                        if (d.getContent().equals(content)) {
                            logger.info("找到新添加的日记ID: " + d.getId());
                            return d.getId();
                        }
                    }
                }
                // 如果找不到确切的ID，返回一个正数表示成功
                return 1;
            } else {
                logger.severe("保存日记到云端失败");
                return -1;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "保存日记时出错", e);
            return -1;
        }
    }
    
    // 更新日记内容
    public static boolean updateDiary(int id, String content, String mood) {
        try {
            // 获取日记详情
            Diary diary = getDiaryById(id);
            if (diary == null) {
                logger.warning("未找到ID为 " + id + " 的日记");
                return false;
            }
            
            // 更新内容和心情
            diary.setContent(content);
            diary.setMood(mood);
            
            // 直接更新到云端
            boolean success = ApiService.updateDiary(diary);
            logger.info("日记ID " + id + " 更新" + (success ? "成功" : "失败"));
            return success;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "更新日记时出错", e);
            return false;
        }
    }
    
    // 删除日记
    public static boolean deleteDiary(int id) {
        try {
            // 直接从云端删除
            boolean success = ApiService.deleteDiary(id);
            logger.info("日记ID " + id + " 删除" + (success ? "成功" : "失败"));
            return success;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "删除日记时出错", e);
            return false;
        }
    }
    
    // 获取指定ID的日记
    public static Diary getDiaryById(int id) {
        // 从云端获取所有日记
        List<Diary> diaries = ApiService.getDiaries();
        if (diaries != null) {
            // 查找指定ID的日记
            for (Diary diary : diaries) {
                if (diary.getId() == id) {
                    return diary;
                }
            }
        }
        logger.warning("未找到ID为 " + id + " 的日记");
        return null;
    }
    
    // 获取所有日记 - 直接从云端获取
    public static List<Diary> getAllDiaries() {
        logger.info("从云端获取所有日记");
        List<Diary> diaries = ApiService.getDiaries();
        if (diaries != null) {
            logger.info("从云端获取到 " + diaries.size() + " 条日记");
            return diaries;
        } else {
            logger.warning("从云端获取日记失败，返回空列表");
            return new ArrayList<>();
        }
    }
    
    // 根据关键词和日期过滤日记 - 直接从云端搜索
    public static List<Diary> searchDiaries(String keyword, String startDate, String endDate) {
        logger.info("从云端搜索日记，关键词=\"" + keyword + "\", 开始日期=\"" + startDate + "\", 结束日期=\"" + endDate + "\"");
        
        try {
            // 如果没有搜索条件，直接返回所有日记
            if ((keyword == null || keyword.isEmpty()) && 
                (startDate == null || startDate.isEmpty()) && 
                (endDate == null || endDate.isEmpty())) {
                logger.info("没有提供搜索条件，返回所有日记");
                return getAllDiaries();
            }
            
            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/diaries/search?");
            
            // 添加搜索参数
            boolean hasParams = false;
            
            if (keyword != null && !keyword.isEmpty()) {
                urlBuilder.append("keyword=").append(java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()));
                hasParams = true;
                logger.info("添加关键词搜索参数: " + keyword);
            }
            
            if (startDate != null && !startDate.isEmpty()) {
                if (hasParams) urlBuilder.append("&");
                urlBuilder.append("startDate=").append(java.net.URLEncoder.encode(startDate, StandardCharsets.UTF_8.name()));
                hasParams = true;
                logger.info("添加开始日期搜索参数: " + startDate);
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                if (hasParams) urlBuilder.append("&");
                urlBuilder.append("endDate=").append(java.net.URLEncoder.encode(endDate, StandardCharsets.UTF_8.name()));
                hasParams = true;
                logger.info("添加结束日期搜索参数: " + endDate);
            }
            
            // 添加用户标识参数
            String currentUser = ApiService.getCurrentUser();
            if (currentUser != null && !currentUser.isEmpty()) {
                if (hasParams) urlBuilder.append("&");
                urlBuilder.append("user=").append(java.net.URLEncoder.encode(currentUser, StandardCharsets.UTF_8.name()));
                logger.info("添加用户参数: " + currentUser);
            }
            
            String finalUrl = urlBuilder.toString();
            logger.info("构建的搜索URL: " + finalUrl);
            
            URL url = new URL(finalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            List<Diary> diaries = new ArrayList<>();
            int responseCode = conn.getResponseCode();
            logger.info("搜索请求响应码: " + responseCode);
            
            if (responseCode >= 200 && responseCode < 300) {
                // 读取响应
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // 解析JSON
                String jsonStr = response.toString();
                logger.info("通过API搜索到日记，响应长度: " + jsonStr.length() + " 字节");
                logger.fine("搜索响应内容: " + jsonStr);
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    if (jsonStr.equals("[]")) {
                        logger.info("搜索结果为空");
                        return diaries;
                    }
                    
                    String[] items = jsonStr.substring(1, jsonStr.length() - 1).split("\\},\\{");
                    logger.info("解析到 " + items.length + " 条日记");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                        if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                        else item = "{" + item + "}";
                        
                        // 提取字段
                        int id = extractIntField(item, "id");
                        String date = extractStringField(item, "date");
                        String content = extractStringField(item, "content");
                        String timestamp = extractStringField(item, "timestamp");
                        String tags = extractStringField(item, "tags");
                        
                        Diary diary = new Diary(id, date, content, timestamp, tags);
                        diaries.add(diary);
                        logger.info("搜索到日记: ID=" + id + ", 日期=" + diary.getDate());
                    }
                }
            } else {
                // 出错时记录详细信息
                String errorMsg = "";
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder errResponse = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        errResponse.append(line);
                    }
                    errorMsg = errResponse.toString();
                } catch (Exception e) {
                    errorMsg = "无法读取错误响应";
                }
                
                logger.severe("搜索日记失败: " + responseCode + " - " + conn.getResponseMessage() + "; 错误详情: " + errorMsg);
            }
            
            logger.info("搜索结果返回 " + diaries.size() + " 条日记");
            return diaries;
        } catch (Exception e) {
            logger.severe("搜索日记时发生异常: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    // 辅助方法：从JSON字符串中提取字符串字段
    private static String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
    
    // 辅助方法：从JSON字符串中提取整数字段
    private static int extractIntField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    // 从云端刷新日记 - 为了兼容性保留，但功能已不需要
    public static void refreshFromCloud() {
        logger.info("刷新日记功能调用 - 不需要额外操作，日记始终从云端获取");
    }
}
