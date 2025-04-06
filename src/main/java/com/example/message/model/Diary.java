package com.example.message.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Diary {
    private int id;
    private String content;
    private String mood;
    private LocalDateTime date;

    // 构造函数
    public Diary(int id, String content, String mood, LocalDateTime date) {
        this.id = id;
        this.content = content;
        this.mood = mood;
        this.date = date;
    }

    // 从旧格式创建的辅助构造函数
    public Diary(int id, String date, String content, String timestamp, String tags) {
        this.id = id;
        this.content = content;
        this.mood = tags; // 旧版本中的tags作为mood
        
        // 尝试解析日期和时间
        try {
            // 首先尝试解析标准格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            this.date = LocalDateTime.parse(timestamp, formatter);
        } catch (Exception e) {
            try {
                // 如果失败，尝试解析ISO格式
                if (timestamp.contains("T")) {
                    // ISO格式: 2023-06-05T15:30:00
                    if (timestamp.contains(".")) {
                        // 含微秒的ISO格式: 2023-06-05T15:30:00.123456789
                        this.date = LocalDateTime.parse(timestamp);
                    } else {
                        // 不含微秒的ISO格式: 2023-06-05T15:30:00
                        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                        this.date = LocalDateTime.parse(timestamp, isoFormatter);
                    }
                } else {
                    // 尝试解析日期部分
                    try {
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        this.date = LocalDateTime.of(LocalDateTime.parse(date, dateFormatter).toLocalDate(), java.time.LocalTime.NOON);
                    } catch (Exception ex) {
                        System.err.println("无法解析时间戳和日期，使用当前时间: " + timestamp + ", " + date);
                        ex.printStackTrace();
                        this.date = LocalDateTime.now();
                    }
                }
            } catch (Exception ex) {
                System.err.println("所有时间戳解析方法都失败，使用当前时间: " + timestamp);
                ex.printStackTrace();
                this.date = LocalDateTime.now();
            }
        }
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getContent() {
        return content;
    }

    public String getMood() {
        return mood;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setMood(String mood) {
        this.mood = mood;
    }
    
    @Override
    public String toString() {
        return "Diary{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", mood='" + mood + '\'' +
                ", date=" + date +
                '}';
    }
}
