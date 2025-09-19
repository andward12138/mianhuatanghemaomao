package com.example.message;

import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import com.example.message.core.EventBus;

import java.time.LocalDateTime;

/**
 * 测试聊天消息同步功能
 */
public class ChatSyncTest {
    
    public static void main(String[] args) {
        System.out.println("开始测试聊天消息同步...");
        
        // 设置消息接收回调
        ChatService.setMessageReceivedCallback(message -> {
            System.out.println("收到消息回调: " + message.getSender() + " -> " + message.getReceiver() + ": " + message.getContent());
        });
        
        // 设置EventBus监听
        EventBus.getInstance().subscribe(EventBus.Events.MESSAGE_RECEIVED, (Object messageObj) -> {
            if (messageObj instanceof ChatMessage) {
                ChatMessage message = (ChatMessage) messageObj;
                System.out.println("EventBus收到消息: " + message.getSender() + " -> " + message.getReceiver() + ": " + message.getContent());
            }
        });
        
        // 模拟发送消息
        ChatMessage testMessage = new ChatMessage(1, "用户A", "用户B", "测试消息", LocalDateTime.now().toString(), false);
        
        // 通过EventBus发布消息
        EventBus.getInstance().publish(EventBus.Events.MESSAGE_RECEIVED, testMessage);
        
        System.out.println("测试完成");
    }
}