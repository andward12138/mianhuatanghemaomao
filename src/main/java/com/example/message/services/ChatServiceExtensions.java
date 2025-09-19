package com.example.message.services;

import com.example.message.model.ChatMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ChatService的扩展方法
 * 为了避免修改原有的ChatService文件，将新增的方法放在这里
 */
public class ChatServiceExtensions {
    
    // 用户列表更新回调
    private static Consumer<List<String>> userListUpdateCallback;
    
    public static void setOnUserListUpdate(Consumer<List<String>> callback) {
        // 直接调用ChatService的方法
        ChatService.setOnUserListUpdate(callback);
    }
    
    public static Consumer<List<String>> getUserListUpdateCallback() {
        return userListUpdateCallback;
    }
    
    // 获取在线用户列表
    public static List<String> getOnlineUsers() {
        // 直接调用ChatService的方法
        return ChatService.getOnlineUsers();
    }
    
    // 获取当前聊天对象
    public static String getCurrentChatPeer() {
        // 直接调用ChatService的方法
        return ChatService.getCurrentChatPeer();
    }
    
    // 设置当前聊天对象
    public static void setCurrentChatPeer(String peer) {
        // 直接调用ChatService的方法
        ChatService.setCurrentChatPeer(peer);
    }
    
    // 发送私人消息
    public static void sendPrivateMessage(String receiver, String message) {
        // 直接调用ChatService的方法
        ChatService.sendPrivateMessage(receiver, message);
    }
    
    // 获取与特定用户的聊天历史
    public static List<ChatMessage> getChatHistory(String username) {
        // 直接调用ChatService的方法
        return ChatService.getChatHistory(username);
    }
    
    // 获取新的聊天历史（用于实时更新）
    public static List<ChatMessage> getNewChatHistory(String username, LocalDateTime since) {
        // 直接调用ChatService的方法
        return ChatService.getNewChatHistory(username, since);
    }
    
    // 获取所有聊天历史
    public static List<ChatMessage> getAllChatHistory() {
        // 直接调用ChatService的方法
        return ChatService.getChatHistory();
    }
    
    // 搜索聊天历史
    public static List<ChatMessage> searchChatHistory(String keyword) {
        // 直接调用ChatService的方法
        return ChatService.searchChatHistory(keyword);
    }
    
    // 清空聊天历史
    public static void clearChatHistory() {
        // 直接调用ChatService的方法
        ChatService.clearChatHistory();
    }
}