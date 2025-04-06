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
    private static final String API_BASE_URL = "http://8.134.99.69:3000/api";
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 30000; // 30秒
    private static final int MAX_RETRIES = 3; // 最大重试次数
    
    // 是否使用云服务器存储日记
    private static boolean useCloudStorage = false;
    
    // 设置是否使用云存储
    public static void setUseCloudStorage(boolean useCloud) {
        useCloudStorage = useCloud;
        logger.info("Cloud storage for diaries " + (useCloud ? "enabled" : "disabled"));
    }
    
    // 获取是否使用云存储
    public static boolean isCloudStorageEnabled() {
        return useCloudStorage;
    }

    // 添加日记
    public static int addDiary(String content, String mood) {
        logger.info("开始添加新日记，内容长度=" + content.length() + ", 心情=" + mood);
        
        LocalDateTime now = LocalDateTime.now();
        
        // 首先尝试保存到本地数据库
        int diaryId = addDiaryToLocalDB(content, mood, now);
        
        if (diaryId <= 0) {
            logger.severe("添加日记到本地数据库失败");
            return -1;
        }
        
        logger.info("日记已成功保存到本地数据库，ID=" + diaryId);
        
        // 如果启用了云存储，则同步到云端
        if (useCloudStorage) {
            try {
                // 创建新的Diary对象用于同步到云端
                Diary diary = new Diary(diaryId, content, mood, now);
                
                // 同步到云端（使用同步方式确保操作完成）
                boolean syncSuccess = ApiService.saveDiary(diary);
                
                if (syncSuccess) {
                    logger.info("日记ID=" + diaryId + "已成功同步到云端");
                } else {
                    logger.warning("日记ID=" + diaryId + "同步到云端失败，但已保存在本地");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "同步日记到云端时出错，ID=" + diaryId, e);
                // 失败不影响返回结果，本地保存已完成
            }
        } else {
            logger.info("云存储未启用，日记仅保存在本地");
        }
        
        return diaryId;
    }
    
    // 添加日记到本地数据库
    private static int addDiaryToLocalDB(String content, String mood, LocalDateTime timestamp) {
        String date = timestamp.toLocalDate().toString();
        String formattedTimestamp = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        logger.info("向本地数据库添加日记，日期=" + date + ", 时间戳=" + formattedTimestamp);
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO diary_entries (content, tags, date, timestamp) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, content);
            pstmt.setString(2, mood); // 使用mood作为tags
            pstmt.setString(3, date);
            pstmt.setString(4, formattedTimestamp);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("添加日记失败，没有行受影响。");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("添加日记失败，未获取到ID。");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "添加日记到本地数据库出错", e);
            return -1;
        }
    }
    
    // 更新日记内容 - 统一的更新方法
    public static boolean updateDiary(int id, String content, String mood) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE diary_entries SET content = ?, tags = ? WHERE id = ?")) {
            
            pstmt.setString(1, content);
            pstmt.setString(2, mood); // 使用mood作为tags
            pstmt.setInt(3, id);
            
            int affectedRows = pstmt.executeUpdate();
            
            // 如果启用云存储，则同步到云端
            if (affectedRows > 0 && useCloudStorage) {
                Diary diary = getDiaryById(id);
                if (diary != null) {
                    ApiService.updateDiary(diary);
                    logger.info("Diary ID " + id + " update synced to cloud");
                }
            }
            
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating diary", e);
            return false;
        }
    }
    
    // 删除日记
    public static boolean deleteDiary(int id) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM diary_entries WHERE id = ?")) {
            
            pstmt.setInt(1, id);
            
            // 如果启用云存储，则同步到云端
            if (useCloudStorage) {
                ApiService.deleteDiary(id);
                logger.info("Diary ID " + id + " deletion synced to cloud");
            }
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting diary", e);
            return false;
        }
    }
    
    // 获取指定ID的日记
    public static Diary getDiaryById(int id) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT id, content, tags, date, timestamp FROM diary_entries WHERE id = ?")) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Diary(
                        rs.getInt("id"),
                        rs.getString("date"),
                        rs.getString("content"),
                        rs.getString("timestamp"),
                        rs.getString("tags")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting diary by ID", e);
        }
        return null;
    }
    
    // 获取所有日记
    public static List<Diary> getAllDiaries() {
        if (useCloudStorage) {
            // 尝试通过API获取所有日记
            List<Diary> diaries = getAllDiariesFromCloud();
            if (diaries != null && !diaries.isEmpty()) {
                return diaries;
            } else {
                logger.warning("通过API获取日记失败或为空，将从本地数据库获取");
                return getAllDiariesFromLocalDB();
            }
        } else {
            // 使用本地数据库
            return getAllDiariesFromLocalDB();
        }
    }
    
    // 从云服务器获取所有日记
    private static List<Diary> getAllDiariesFromCloud() {
        logger.info("尝试通过API获取所有日记");
        
        List<Diary> diaries = new ArrayList<>();
        
        try {
            // 构建URL
            URL url = new URL(API_BASE_URL + "/diaries");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
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
                logger.info("通过API获取到日记，响应长度: " + jsonStr.length() + " 字节");
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
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
                        
                        diaries.add(new Diary(id, date, content, timestamp, tags));
                    }
                }
            } else {
                logger.severe("获取日记失败: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.severe("获取日记时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return diaries;
    }
    
    // 从本地数据库获取所有日记
    private static List<Diary> getAllDiariesFromLocalDB() {
        List<Diary> diaries = new ArrayList<>();
        String selectSQL = "SELECT id, date, content, timestamp, tags FROM diary_entries ORDER BY timestamp DESC"; // 按时间排序

        try (Connection connection = DBUtil.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectSQL)) {

            // 遍历结果集，获取所有日记
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String date = resultSet.getString("date");
                String content = resultSet.getString("content");
                String timestamp = resultSet.getString("timestamp");
                String tags = resultSet.getString("tags");

                // 创建Diary对象并添加到列表
                diaries.add(new Diary(id, date, content, timestamp, tags));
            }
            
            logger.info("从本地数据库获取到 " + diaries.size() + " 条日记");
        } catch (SQLException e) {
            logger.severe("获取本地日记时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return diaries;
    }
    
    // 根据关键词和日期过滤日记
    public static List<Diary> searchDiaries(String keyword, String startDate, String endDate) {
        if (useCloudStorage) {
            // 尝试通过API搜索日记
            List<Diary> diaries = searchDiariesFromCloud(keyword, startDate, endDate);
            if (diaries != null && !diaries.isEmpty()) {
                return diaries;
            } else {
                logger.warning("通过API搜索日记失败或为空，将从本地数据库搜索");
                return searchDiariesFromLocalDB(keyword, startDate, endDate);
            }
        } else {
            // 使用本地数据库
            return searchDiariesFromLocalDB(keyword, startDate, endDate);
        }
    }
    
    // 从云服务器搜索日记
    private static List<Diary> searchDiariesFromCloud(String keyword, String startDate, String endDate) {
        logger.info("尝试通过API搜索日记");
        
        List<Diary> diaries = new ArrayList<>();
        
        try {
            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/diaries/search?");
            
            // 添加搜索参数
            if (keyword != null && !keyword.isEmpty()) {
                urlBuilder.append("keyword=").append(java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())).append("&");
            }
            
            if (startDate != null && !startDate.isEmpty()) {
                urlBuilder.append("startDate=").append(java.net.URLEncoder.encode(startDate, StandardCharsets.UTF_8.name())).append("&");
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                urlBuilder.append("endDate=").append(java.net.URLEncoder.encode(endDate, StandardCharsets.UTF_8.name())).append("&");
            }
            
            // 添加用户标识参数
            String currentUser = ApiService.getCurrentUser();
            if (currentUser != null && !currentUser.isEmpty()) {
                urlBuilder.append("user=").append(java.net.URLEncoder.encode(currentUser, StandardCharsets.UTF_8.name()));
            }
            
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
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
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
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
                        
                        diaries.add(new Diary(id, date, content, timestamp, tags));
                    }
                }
            } else {
                logger.severe("搜索日记失败: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.severe("搜索日记时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return diaries;
    }
    
    // 从本地数据库搜索日记
    private static List<Diary> searchDiariesFromLocalDB(String keyword, String startDate, String endDate) {
        List<Diary> diaries = new ArrayList<>();
        String selectSQL = "SELECT id, date, content, timestamp, tags FROM diary_entries WHERE (content LIKE ? OR tags LIKE ?)";

        // 添加日期范围筛选条件
        if (startDate != null && !startDate.isEmpty()) {
            selectSQL += " AND date >= ?";
        }
        if (endDate != null && !endDate.isEmpty()) {
            selectSQL += " AND date <= ?";
        }

        selectSQL += " ORDER BY timestamp DESC"; // 按时间排序

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {

            String searchKey = keyword != null ? "%" + keyword + "%" : "%%";
            preparedStatement.setString(1, searchKey);
            preparedStatement.setString(2, searchKey);

            // 设置日期范围条件
            int paramIndex = 3;
            if (startDate != null && !startDate.isEmpty()) {
                preparedStatement.setString(paramIndex++, startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                preparedStatement.setString(paramIndex, endDate);
            }

            ResultSet resultSet = preparedStatement.executeQuery();

            // 遍历结果集，获取过滤后的日记
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String date = resultSet.getString("date");
                String content = resultSet.getString("content");
                String timestamp = resultSet.getString("timestamp");
                String tags = resultSet.getString("tags");

                // 创建Diary对象并添加到列表
                diaries.add(new Diary(id, date, content, timestamp, tags));
            }
            
            logger.info("从本地数据库搜索到 " + diaries.size() + " 条日记");
        } catch (SQLException e) {
            logger.severe("搜索本地日记时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return diaries;
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

    // 从云端刷新日记
    public static void refreshFromCloud() {
        if (!useCloudStorage) {
            logger.warning("Attempted to refresh diaries from cloud, but cloud storage is disabled");
            return;
        }
        
        try {
            // 从API服务获取日记
            List<Diary> cloudDiaries = ApiService.getDiaries();
            if (cloudDiaries != null && !cloudDiaries.isEmpty()) {
                // 清空本地数据库中的日记
                try (Connection conn = DBUtil.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM diary_entries");
                    logger.info("Deleted all local diaries before sync");
                }
                
                // 插入云端获取的日记
                for (Diary diary : cloudDiaries) {
                    try (Connection conn = DBUtil.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement(
                                 "INSERT INTO diary_entries (id, content, tags, date, timestamp) VALUES (?, ?, ?, ?, ?)")) {
                        
                        String timestamp = diary.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        String date = diary.getDate().toLocalDate().toString();
                        
                        pstmt.setInt(1, diary.getId());
                        pstmt.setString(2, diary.getContent());
                        pstmt.setString(3, diary.getMood()); // 使用mood作为tags
                        pstmt.setString(4, date);
                        pstmt.setString(5, timestamp);
                        pstmt.executeUpdate();
                    }
                }
                logger.info("Successfully synced " + cloudDiaries.size() + " diaries from cloud");
            } else {
                logger.info("No diaries found in cloud to sync");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error refreshing diaries from cloud", e);
        }
    }
}
