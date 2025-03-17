package com.example.message.model;

public class ChatMessage {
    private int id;
    private String sender;
    private String receiver;
    private String content;
    private String timestamp;
    private boolean isRead;

    // 构造函数
    public ChatMessage(int id, String sender, String receiver, String content, String timestamp, boolean isRead) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
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

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
} 