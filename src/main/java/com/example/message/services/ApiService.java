package com.example.message.services;

import com.example.message.model.ChatMessage;
import com.example.message.model.Diary;
// 替换外部依赖为Java内置类
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 与云服务器上的REST API进行通信的服务类
 */
public class ApiService {
    private static final Logger logger = Logger.getLogger(ApiService.class.getName());
    private static final String API_BASE_URL = "http://8.134.99.69:3001/api";
    private static final int CONNECT_TIMEOUT = 5000; // 5秒（减少超时时间）
    private static final int READ_TIMEOUT = 30000; // 30秒
    private static final int MAX_RETRIES = 3; // 最大重试次数
    private static boolean isApiAvailable = false; // API是否可用
    private static long lastApiCheckTime = 0; // 上次API检查时间
    private static final long API_CHECK_INTERVAL = 60000; // API检查间隔（毫秒）
    private static String currentUser = null; // 当前用户
    
    // 日志队列，存储待发送到服务器的日志
    private static final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    // 日志上传线程池
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    // 日期时间格式化器
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 设置当前用户
     * @param username 用户名
     */
    public static void setCurrentUser(String username) {
        currentUser = username;
        logger.info("Current user set to: " + username);
    }
    
    /**
     * 获取当前用户
     * @return 当前用户名
     */
    public static String getCurrentUser() {
        return currentUser;
    }
    
    /**
     * 初始化API服务
     */
    public static void initialize() {
        if (!isApiAvailable) {
            logger.info("API Service initialized");
            isApiAvailable = true;
        }
        
        // 启动API可用性检查线程
        Thread apiCheckThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                isApiAvailable = testConnection();
                logger.info("API可用性检查结果: " + (isApiAvailable ? "可用" : "不可用"));
                try {
                    Thread.sleep(API_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        apiCheckThread.setDaemon(true);
        apiCheckThread.start();
        
        // 启动日志上传线程
        startLogUploader();
        
        // 立即执行一次检查
        isApiAvailable = testConnection();
        lastApiCheckTime = System.currentTimeMillis();
        
        logger.info("API服务初始化完成，服务器地址: " + API_BASE_URL);
    }
    
    /**
     * 启动日志上传线程
     */
    private static void startLogUploader() {
        logExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 如果队列不为空且API可用，则上传日志
                    if (!logQueue.isEmpty() && isApiAvailable()) {
                        // 一次最多取10条日志上传
                        List<String> logsToUpload = new ArrayList<>(10);
                        for (int i = 0; i < 10 && !logQueue.isEmpty(); i++) {
                            String log = logQueue.poll();
                            if (log != null) {
                                logsToUpload.add(log);
                            }
                        }
                        
                        if (!logsToUpload.isEmpty()) {
                            uploadLogs(logsToUpload);
                        }
                    }
                    
                    // 每5秒检查一次
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.severe("日志上传线程异常: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        Thread.sleep(60000); // 发生错误后等待1分钟再尝试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * 上传日志到服务器
     * @param logs 日志列表
     */
    private static void uploadLogs(List<String> logs) {
        try {
            // 构建JSON数组
            StringBuilder jsonBuilder = new StringBuilder("[");
            for (int i = 0; i < logs.size(); i++) {
                if (i > 0) {
                    jsonBuilder.append(",");
                }
                jsonBuilder.append(logs.get(i));
            }
            jsonBuilder.append("]");
            
            // 发送POST请求
            URL url = new URL(API_BASE_URL + "/logs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("成功上传 " + logs.size() + " 条日志到服务器");
            } else {
                logger.severe("上传日志失败: " + responseCode + " - " + conn.getResponseMessage());
                // 失败时将日志重新加入队列
                for (String log : logs) {
                    logQueue.offer(log);
                }
            }
        } catch (Exception e) {
            logger.severe("上传日志时发生异常: " + e.getMessage());
            e.printStackTrace();
            // 失败时将日志重新加入队列
            for (String log : logs) {
                logQueue.offer(log);
            }
        }
    }
    
    /**
     * 记录日志
     * @param level 日志级别
     * @param message 日志消息
     */
    public static void log(String level, String message) {
        // 打印到控制台
        logger.info("[" + level + "] " + message);
        
        // 构建日志JSON
        String timestamp = LocalDateTime.now().format(dateTimeFormatter);
        String username = currentUser != null ? currentUser : "未登录";
        String logJson = String.format(
            "{\"timestamp\":\"%s\",\"level\":\"%s\",\"user\":\"%s\",\"message\":\"%s\"}",
            timestamp, level, username, message.replace("\"", "\\\"")
        );
        
        // 添加到日志队列
        logQueue.offer(logJson);
    }
    
    /**
     * 检查API是否可用
     * @return API是否可用
     */
    public static boolean isApiAvailable() {
        // 如果距离上次检查超过1分钟，重新检查
        if (System.currentTimeMillis() - lastApiCheckTime > API_CHECK_INTERVAL) {
            isApiAvailable = testConnection();
            lastApiCheckTime = System.currentTimeMillis();
        }
        return isApiAvailable;
    }
    
    /**
     * 保存消息到服务器
     * @param sender 发送者
     * @param receiver 接收者
     * @param content 消息内容
     * @return 是否成功
     */
    public static boolean saveMessage(String sender, String receiver, String content) {
        logger.info("尝试保存消息到云服务器: " + sender + " -> " + receiver);
        
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                
                // 构建JSON字符串
                String jsonBody = String.format(
                    "{\"sender\":\"%s\",\"receiver\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                    sender, receiver, content.replace("\"", "\\\""), timestamp
                );
                
                // 发送POST请求
                URL url = new URL(API_BASE_URL + "/messages");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setDoOutput(true);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    logger.info("消息已成功保存到云服务器: " + sender + " -> " + receiver);
                    return true;
                } else {
                    logger.severe("保存消息失败: " + responseCode + " - " + conn.getResponseMessage());
                    // 如果是服务器错误，尝试重试
                    if (responseCode >= 500 && retries < MAX_RETRIES - 1) {
                        retries++;
                        logger.warning("正在进行第 " + retries + " 次重试...");
                        Thread.sleep(1000 * retries); // 指数退避
                        continue;
                    }
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.severe("保存消息操作被中断");
                return false;
            } catch (Exception e) {
                logger.severe("保存消息时发生异常: " + e.getMessage());
                e.printStackTrace();
                // 如果是网络错误，尝试重试
                if (retries < MAX_RETRIES - 1) {
                    retries++;
                    logger.warning("发生异常，正在进行第 " + retries + " 次重试...");
                    try {
                        Thread.sleep(1000 * retries); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("重试操作被中断");
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取聊天历史
     * @param user1 用户1
     * @param user2 用户2
     * @return 聊天消息列表
     */
    public static List<ChatMessage> getChatHistory(String user1, String user2) {
        logger.info("尝试从云服务器获取聊天历史: " + user1 + " <-> " + user2);
        
        List<ChatMessage> messages = new ArrayList<>();
        
        try {
            // 构建URL
            String urlStr = String.format(
                "%s/messages?user1=%s&user2=%s",
                API_BASE_URL, 
                java.net.URLEncoder.encode(user1, StandardCharsets.UTF_8.name()),
                java.net.URLEncoder.encode(user2, StandardCharsets.UTF_8.name())
            );
            
            URL url = new URL(urlStr);
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
                
                // 解析JSON (简化版，实际应用中应使用更健壮的JSON解析)
                String jsonStr = response.toString();
                logger.info("从云服务器获取到聊天记录，响应长度: " + jsonStr.length() + " 字节");
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    String content = jsonStr.substring(1, jsonStr.length() - 1).trim();
                    
                    // 检查是否为空数组
                    if (content.isEmpty()) {
                        logger.info("服务器返回空的聊天记录");
                        return messages; // 返回空列表
                    }
                    
                    String[] items = content.split("\\},\\{");
                    logger.info("解析到 " + items.length + " 条聊天记录");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                        if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                        else item = "{" + item + "}";
                        
                        // 提取字段
                        int id = extractIntField(item, "id");
                        String sender = extractStringField(item, "sender");
                        String receiver = extractStringField(item, "receiver");
                        String content_field = extractStringField(item, "content");
                        String timestamp = extractStringField(item, "timestamp");
                        boolean isRead = extractIntField(item, "is_read") == 1;
                        
                        // 验证必需字段
                        if (sender != null && receiver != null && content_field != null) {
                            messages.add(new ChatMessage(id, sender, receiver, content_field, timestamp, isRead));
                        } else {
                            logger.warning("跳过无效的消息记录: sender=" + sender + ", receiver=" + receiver + ", content=" + content_field);
                        }
                    }
                } else {
                    logger.warning("从服务器接收到的响应不是有效的JSON数组: " + jsonStr);
                }
            } else {
                logger.severe("获取聊天历史失败: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.severe("获取聊天历史时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return messages;
    }
    
    /**
     * 标记消息为已读
     * @param messageId 消息ID
     * @return 是否成功
     */
    public static boolean markAsRead(int messageId) {
        try {
            logger.info("尝试标记消息 " + messageId + " 为已读");
            
            URL url = new URL(API_BASE_URL + "/messages/" + messageId + "/read");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 空请求体
            try (OutputStream os = conn.getOutputStream()) {
                os.write(new byte[0]);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("消息 " + messageId + " 已标记为已读");
                return true;
            } else {
                logger.severe("标记消息为已读失败: " + responseCode + " - " + conn.getResponseMessage());
                return false;
            }
        } catch (Exception e) {
            logger.severe("标记消息为已读时发生异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 测试API连接
     * @return 是否连接成功
     */
    public static boolean testConnection() {
        try {
            URL url = new URL(API_BASE_URL + "/messages?limit=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch (IOException e) {
            logger.severe("API连接测试失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取所有聊天日志
     * @param limit 限制条数，0表示不限制
     * @return 日志列表
     */
    public static List<String> getChatLogs(int limit) {
        logger.info("尝试从云服务器获取聊天日志，限制: " + (limit > 0 ? limit : "不限制"));
        
        List<String> logs = new ArrayList<>();
        
        try {
            // 构建URL
            String urlStr = API_BASE_URL + "/logs" + (limit > 0 ? "?limit=" + limit : "");
            
            URL url = new URL(urlStr);
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
                logger.info("从云服务器获取到日志，响应长度: " + jsonStr.length() + " 字节");
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    String[] items = jsonStr.substring(1, jsonStr.length() - 1).split("\\},\\{");
                    logger.info("解析到 " + items.length + " 条日志");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                        if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                        else item = "{" + item + "}";
                        
                        // 提取字段
                        String timestamp = extractStringField(item, "timestamp");
                        String level = extractStringField(item, "level");
                        String user = extractStringField(item, "user");
                        String message = extractStringField(item, "message");
                        
                        logs.add(String.format("[%s] [%s] %s: %s", timestamp, level, user, message));
                    }
                } else {
                    logger.warning("从服务器接收到的响应不是有效的JSON数组: " + jsonStr);
                }
            } else {
                logger.severe("获取聊天日志失败: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.severe("获取聊天日志时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return logs;
    }
    
    /**
     * 获取所有日记
     * @return 日记列表
     */
    public static List<Diary> getDiaries() {
        List<Diary> diaries = new ArrayList<>();
        
        try {
            URL url = new URL(API_BASE_URL + "/diaries");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                // 解析JSON响应
                String jsonStr = response.toString();
                logger.info("Retrieved " + jsonStr.length() + " bytes of diary data from server");
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    // 处理空数组
                    if (jsonStr.equals("[]")) {
                        logger.info("No diaries found on server");
                        return diaries;
                    }
                    
                    // 移除数组括号
                    jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
                    
                    // 分割JSON对象
                    String[] items;
                    if (jsonStr.contains("},{")) {
                        items = jsonStr.split("\\},\\{");
                    } else {
                        items = new String[]{jsonStr};
                    }
                    
                    logger.info("Parsing " + items.length + " diaries from server");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0 && !item.startsWith("{")) {
                            item = "{" + item;
                        }
                        if (i == items.length - 1 && !item.endsWith("}")) {
                            item = item + "}";
                        }
                        
                        // 提取ID、内容和日期
                        int id = extractIntField(item, "id");
                        String content = extractStringField(item, "content");
                        String tags = extractStringField(item, "tags");
                        String user = extractStringField(item, "user");
                        String dateStr = extractStringField(item, "date");
                        String timestampStr = extractStringField(item, "timestamp");
                        
                        if (id > 0 && content != null) {
                            // 使用辅助构造函数创建Diary对象，它会处理各种时间戳格式
                            Diary diary = new Diary(id, dateStr, content, timestampStr, tags);
                            diaries.add(diary);
                            logger.info("从服务器解析到日记: ID=" + id + ", 日期=" + diary.getDate());
                        }
                    }
                }
            } else {
                logger.severe("Failed to get diaries: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting diaries from server", e);
        }
        
        logger.info("Retrieved " + diaries.size() + " diaries from server");
        return diaries;
    }
    
    /**
     * 获取所有消息
     * @return 消息列表
     */
    public static List<ChatMessage> getMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        
        try {
            URL url = new URL(API_BASE_URL + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                // 解析JSON响应
                String jsonStr = response.toString();
                logger.info("Retrieved " + jsonStr.length() + " bytes of message data from server");
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    // 处理空数组
                    if (jsonStr.equals("[]")) {
                        logger.info("No messages found on server");
                        return messages;
                    }
                    
                    // 移除数组括号
                    jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
                    
                    // 分割JSON对象
                    String[] items;
                    if (jsonStr.contains("},{")) {
                        items = jsonStr.split("\\},\\{");
                    } else {
                        items = new String[]{jsonStr};
                    }
                    
                    logger.info("Parsing " + items.length + " messages from server");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0 && !item.startsWith("{")) {
                            item = "{" + item;
                        }
                        if (i == items.length - 1 && !item.endsWith("}")) {
                            item = item + "}";
                        }
                        
                        // 提取发送者、内容和时间戳
                        int id = extractIntField(item, "id");
                        String sender = extractStringField(item, "sender");
                        String receiver = extractStringField(item, "receiver");
                        String content = extractStringField(item, "content");
                        String timestampStr = extractStringField(item, "timestamp");
                        
                        if (sender != null && content != null && timestampStr != null) {
                            // 直接使用ChatMessage的构造函数，它内部会处理多种时间戳格式
                            ChatMessage message = new ChatMessage(id, sender, receiver, content, timestampStr, true);
                            messages.add(message);
                            logger.fine("从服务器解析到消息: 发送者=" + sender + ", 接收者=" + receiver + ", 内容=" + content);
                        }
                    }
                }
            } else {
                logger.severe("Failed to get messages: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting messages from server", e);
        }
        
        logger.info("Retrieved " + messages.size() + " messages from server");
        return messages;
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
    
    /**
     * 获取API URL
     * @return API URL
     */
    public static String getApiUrl() {
        return API_BASE_URL;
    }
    
    /**
     * 保存日记到服务器
     * @param diary 日记对象
     * @return 是否成功
     */
    public static boolean saveDiary(Diary diary) {
        logger.info("尝试保存日记到服务器，ID=" + diary.getId() + ", 内容长度=" + diary.getContent().length());
        
        // 验证API连接是否可用
        if (!isApiAvailable()) {
            logger.warning("API服务不可用，无法保存日记");
            return false;
        }
        
        try {
            URL url = new URL(API_BASE_URL + "/diaries");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 准备日期格式
            String dateOnly = diary.getDate().toLocalDate().toString(); // YYYY-MM-DD
            String timestamp = diary.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            
            // 确保用户名非空
            String user = currentUser != null && !currentUser.isEmpty() ? currentUser : "匿名用户";
            
            // 创建JSON格式的请求体，匹配服务器期望的格式
            String jsonInputString = String.format(
                    "{\"user\":\"%s\",\"date\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"tags\":\"%s\"}",
                    user,
                    dateOnly,
                    diary.getContent().replace("\"", "\\\"").replace("\n", "\\n"),
                    timestamp,
                    diary.getMood().replace("\"", "\\\"") // 使用mood字段作为tags
            );
            
            logger.info("发送日记内容到服务器: " + jsonInputString);
            
            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            
            // 记录详细响应信息
            StringBuilder responseContent = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseContent.append(responseLine.trim());
                }
            }
            
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("日记成功保存到服务器，ID: " + diary.getId() + ", 响应: " + responseContent);
                return true;
            } else {
                logger.severe("保存日记失败: 状态码=" + responseCode + ", 响应: " + responseContent + ", 请求内容: " + jsonInputString);
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "保存日记到服务器时发生异常", e);
            return false;
        }
    }
    
    /**
     * 更新服务器上的日记
     * @param diary 日记对象
     * @return 是否成功
     */
    public static boolean updateDiary(Diary diary) {
        logger.info("尝试更新服务器上的日记，ID=" + diary.getId());
        
        // 验证API连接是否可用
        if (!isApiAvailable()) {
            logger.warning("API服务不可用，无法更新日记");
            return false;
        }
        
        try {
            URL url = new URL(API_BASE_URL + "/diaries/" + diary.getId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 准备日期格式
            String dateOnly = diary.getDate().toLocalDate().toString(); // YYYY-MM-DD
            String timestamp = diary.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            
            // 确保用户名非空
            String user = currentUser != null && !currentUser.isEmpty() ? currentUser : "匿名用户";
            
            // 创建JSON格式的请求体，匹配服务器期望的格式
            String jsonInputString = String.format(
                    "{\"user\":\"%s\",\"date\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"tags\":\"%s\"}",
                    user,
                    dateOnly,
                    diary.getContent().replace("\"", "\\\"").replace("\n", "\\n"),
                    timestamp,
                    diary.getMood().replace("\"", "\\\"") // 使用mood字段作为tags
            );
            
            logger.info("发送日记更新内容到服务器: " + jsonInputString);
            
            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            
            // 记录详细响应信息
            StringBuilder responseContent = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseContent.append(responseLine.trim());
                }
            }
            
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("日记成功更新到服务器，ID: " + diary.getId() + ", 响应: " + responseContent);
                return true;
            } else {
                logger.severe("更新日记失败: 状态码=" + responseCode + ", 响应: " + responseContent + ", 请求内容: " + jsonInputString);
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "更新日记到服务器时发生异常", e);
            return false;
        }
    }
    
    /**
     * 从服务器删除日记
     * @param id 日记ID
     * @return 是否成功
     */
    public static boolean deleteDiary(int id) {
        try {
            URL url = new URL(API_BASE_URL + "/diaries/" + id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("User-Agent", "JavaClient");
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("Diary deleted from server successfully, ID: " + id);
                return true;
            } else {
                logger.severe("Failed to delete diary: " + responseCode + " - " + conn.getResponseMessage());
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deleting diary from server", e);
            return false;
        }
    }
    
    /**
     * 保存消息并返回消息ID
     * @param sender 发送者
     * @param receiver 接收者
     * @param content 内容
     * @return 消息ID，如果保存失败则返回-1
     */
    public static int saveMessageAndGetId(String sender, String receiver, String content) {
        logger.info("尝试保存消息并获取ID: " + sender + " -> " + receiver + ": " + content);
        
        // 验证API连接是否可用
        if (!isApiAvailable()) {
            logger.warning("API服务不可用，无法保存消息");
            return -1;
        }
        
        try {
            URL url = new URL(API_BASE_URL + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "JavaClient");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 准备时间戳（使用ISO格式，确保与服务端兼容）
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 创建JSON格式的请求体
            String jsonInputString = String.format(
                    "{\"sender\":\"%s\",\"receiver\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                    sender,
                    receiver,
                    content.replace("\"", "\\\"").replace("\n", "\\n"),
                    timestamp
            );
            
            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            
            // 记录详细响应信息
            StringBuilder responseContent = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseContent.append(responseLine.trim());
                }
            }
            
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("消息保存成功，响应: " + responseContent);
                
                // 尝试从响应中提取消息ID
                String response = responseContent.toString();
                if (response.contains("\"id\":")) {
                    try {
                        int id = extractIntField(response, "id");
                        if (id > 0) {
                            logger.info("提取到消息ID: " + id);
                            return id;
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "解析消息ID时出错", e);
                    }
                }
                
                // 如果无法提取ID，返回一个正数作为临时ID
                return 1;
            } else {
                logger.severe("保存消息失败: 状态码=" + responseCode + ", 响应: " + responseContent);
                logger.severe("请求内容: " + jsonInputString);
                logger.severe("请求URL: " + url.toString());
                return -1;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "保存消息时发生异常", e);
            return -1;
        }
    }
} 