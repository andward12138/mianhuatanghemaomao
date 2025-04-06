package com.example.message.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessage {
    private int id;
    private String sender;
    private String receiver;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;

    // 构造函数（完整版）
    public ChatMessage(int id, String sender, String receiver, String content, LocalDateTime timestamp, boolean isRead) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }
    
    // 简化版构造函数（用于API返回的消息）
    public ChatMessage(String sender, String content, LocalDateTime timestamp) {
        this.id = 0; // 默认ID为0
        this.sender = sender;
        this.receiver = null; // 接收者可能未知
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = true; // 默认为已读
    }
    
    // 从旧格式兼容的构造函数
    public ChatMessage(int id, String sender, String receiver, String content, String timestamp, boolean isRead) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.isRead = isRead;
        
        // 尝试解析时间戳，支持多种格式
        this.timestamp = parseTimestamp(timestamp);
    }

    // 解析时间戳的辅助方法，支持多种格式
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now();
        }
        
        // 尝试多种日期格式解析
        try {
            // 尝试ISO格式 (带T的格式 2023-04-04T12:30:20)
            if (timestamp.contains("T")) {
                return LocalDateTime.parse(timestamp);
            }
            
            // 尝试标准格式 (yyyy-MM-dd HH:mm:ss)
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e1) {
            try {
                // 尝试日期+时间格式 (yyyy-MM-dd HH:mm)
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e2) {
                try {
                    // 尝试短日期+时间格式 (yyyy/MM/dd HH:mm:ss)
                    return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
                } catch (Exception e3) {
                    // 所有格式都失败，使用当前时间
                    System.out.println("无法解析时间戳: " + timestamp + "，使用当前时间");
                    return LocalDateTime.now();
                }
            }
        }
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    // 获取格式化的时间字符串
    public String getFormattedTimestamp() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", isRead=" + isRead +
                '}';
    }
} 