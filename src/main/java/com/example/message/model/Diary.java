package com.example.message.model;
public class Diary {
    private int id;
    private String date;
    private String content;
    private String timestamp;
    private String tags;

    // Constructor
    public Diary(int id, String date, String content, String timestamp, String tags) {
        this.id = id;
        this.date = date;
        this.content = content;
        this.timestamp = timestamp;
        this.tags = tags;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getContent() {
        return content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getTags() {
        return tags;
    }
}
