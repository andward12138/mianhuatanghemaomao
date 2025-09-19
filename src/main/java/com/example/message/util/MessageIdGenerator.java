package com.example.message.util;

import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 分布式消息ID生成器
 * 借鉴微信的号段分配机制，确保消息ID全局有序
 */
public class MessageIdGenerator {
    
    // 服务器节点ID (1-1023，10位)
    private static final long SERVER_ID;
    
    // 每个会话的序列号计数器
    private static final Map<String, AtomicLong> conversationCounters = new ConcurrentHashMap<>();
    
    // ID组成: 时间戳(42位) + 服务器ID(10位) + 会话序列号(12位)
    // 总共64位，可以用long存储
    
    static {
        // 从环境变量或配置文件获取服务器ID，默认为1
        String serverIdStr = System.getProperty("chat.server.id", "1");
        SERVER_ID = Long.parseLong(serverIdStr);
        
        if (SERVER_ID < 1 || SERVER_ID > 1023) {
            throw new IllegalArgumentException("服务器ID必须在1-1023之间");
        }
    }
    
    /**
     * 为指定会话生成全局有序的消息ID
     * @param conversationId 会话ID（单聊为两个用户ID组合，群聊为群ID）
     * @return 全局有序的消息ID
     */
    public static long generateMessageId(String conversationId) {
        // 获取当前时间戳（毫秒）
        long timestamp = Instant.now().toEpochMilli();
        
        // 获取或创建会话的序列号计数器
        AtomicLong counter = conversationCounters.computeIfAbsent(
            conversationId, 
            k -> new AtomicLong(0)
        );
        
        // 获取当前会话的序列号（0-4095，12位）
        long sequence = counter.getAndIncrement() % 4096;
        
        // 组装消息ID: 时间戳(42位) + 服务器ID(10位) + 序列号(12位)
        long messageId = ((timestamp & 0x3FFFFFFFFFFL) << 22) |
                        ((SERVER_ID & 0x3FFL) << 12) |
                        (sequence & 0xFFFL);
        
        return messageId;
    }
    
    /**
     * 从消息ID中提取时间戳
     */
    public static long extractTimestamp(long messageId) {
        return messageId >> 22;
    }
    
    /**
     * 从消息ID中提取服务器ID
     */
    public static long extractServerId(long messageId) {
        return (messageId >> 12) & 0x3FF;
    }
    
    /**
     * 从消息ID中提取序列号
     */
    public static long extractSequence(long messageId) {
        return messageId & 0xFFF;
    }
    
    /**
     * 生成会话ID
     * 单聊：较小的用户ID + "_" + 较大的用户ID
     * 群聊：直接使用群ID
     */
    public static String generateConversationId(String user1, String user2) {
        if (user1.compareTo(user2) < 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }
    
    /**
     * 清理长时间未使用的会话计数器（定期调用）
     */
    public static void cleanupOldCounters() {
        // 可以根据需要实现清理逻辑
        // 例如：清理超过24小时未使用的计数器
    }
}
