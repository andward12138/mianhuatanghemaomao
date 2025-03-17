package com.example.message.services;

import com.example.message.model.ChatMessage;
// 替换外部依赖为Java内置类
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 与云服务器上的REST API进行通信的服务类
 */
public class ApiService {
    private static final String API_BASE_URL = "http://8.134.99.69:3000/api";
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 30000; // 30秒
    private static final int MAX_RETRIES = 3; // 最大重试次数
    private static boolean isApiAvailable = false; // API是否可用
    private static long lastApiCheckTime = 0; // 上次API检查时间
    private static final long API_CHECK_INTERVAL = 60000; // API检查间隔（毫秒）
    private static String currentUser = null; // 当前用户
    
    /**
     * 设置当前用户
     * @param username 用户名
     */
    public static void setCurrentUser(String username) {
        currentUser = username;
        System.out.println("API服务当前用户设置为: " + username);
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
        // 启动API可用性检查线程
        Thread apiCheckThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                isApiAvailable = testConnection();
                System.out.println("API可用性检查结果: " + (isApiAvailable ? "可用" : "不可用"));
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
        
        // 立即执行一次检查
        isApiAvailable = testConnection();
        lastApiCheckTime = System.currentTimeMillis();
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
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                String timestamp = LocalDateTime.now().toString();
                
                // 构建JSON字符串
                String jsonBody = String.format(
                    "{\"sender\":\"%s\",\"receiver\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                    sender, receiver, content, timestamp
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
                    System.out.println("消息已成功保存到云服务器: " + sender + " -> " + receiver);
                    return true;
                } else {
                    System.out.println("保存消息失败: " + responseCode + " - " + conn.getResponseMessage());
                    // 如果是服务器错误，尝试重试
                    if (responseCode >= 500 && retries < MAX_RETRIES - 1) {
                        retries++;
                        System.out.println("正在进行第 " + retries + " 次重试...");
                        Thread.sleep(1000 * retries); // 指数退避
                        continue;
                    }
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                System.out.println("保存消息时发生异常: " + e.getMessage());
                e.printStackTrace();
                // 如果是网络错误，尝试重试
                if (retries < MAX_RETRIES - 1) {
                    retries++;
                    System.out.println("发生异常，正在进行第 " + retries + " 次重试...");
                    try {
                        Thread.sleep(1000 * retries); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
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
                System.out.println("从云服务器获取到聊天记录: " + jsonStr);
                
                // 简单解析JSON数组
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    String[] items = jsonStr.substring(1, jsonStr.length() - 1).split("\\},\\{");
                    System.out.println("解析到 " + items.length + " 条聊天记录");
                    
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i];
                        if (i == 0) item = item.startsWith("{") ? item : "{" + item;
                        if (i == items.length - 1) item = item.endsWith("}") ? item : item + "}";
                        else item = "{" + item + "}";
                        
                        // 提取字段
                        int id = extractIntField(item, "id");
                        String sender = extractStringField(item, "sender");
                        String receiver = extractStringField(item, "receiver");
                        String content = extractStringField(item, "content");
                        String timestamp = extractStringField(item, "timestamp");
                        boolean isRead = extractIntField(item, "is_read") == 1;
                        
                        messages.add(new ChatMessage(id, sender, receiver, content, timestamp, isRead));
                    }
                }
            } else {
                System.out.println("获取聊天历史失败: " + responseCode + " - " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            System.out.println("获取聊天历史时发生异常: " + e.getMessage());
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
                System.out.println("消息 " + messageId + " 已标记为已读");
                return true;
            } else {
                System.out.println("标记消息为已读失败: " + responseCode + " - " + conn.getResponseMessage());
                return false;
            }
        } catch (Exception e) {
            System.out.println("标记消息为已读时发生异常: " + e.getMessage());
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
            System.out.println("API连接测试失败: " + e.getMessage());
            return false;
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
} 