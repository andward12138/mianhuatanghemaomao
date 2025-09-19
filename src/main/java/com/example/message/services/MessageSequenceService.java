package com.example.message.services;

import com.example.message.model.ChatMessage;
import com.example.message.util.MessageIdGenerator;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 消息序列化中心服务
 * 借鉴微信的单点序列化思路，为每个会话维护有序的消息序列
 */
public class MessageSequenceService {
    
    private static final Logger logger = Logger.getLogger(MessageSequenceService.class.getName());
    
    // 每个会话的序列号计数器
    private static final Map<String, AtomicLong> conversationSequences = new ConcurrentHashMap<>();
    
    // 待处理的消息队列
    private static final BlockingQueue<MessageSequenceTask> sequenceQueue = new LinkedBlockingQueue<>();
    
    // 序列化处理线程池
    private static final ExecutorService sequenceExecutor = Executors.newSingleThreadExecutor();
    
    // 消息确认回调
    private static MessageSequenceCallback sequenceCallback;
    
    // 是否已启动
    private static volatile boolean started = false;
    
    /**
     * 消息序列化任务
     */
    public static class MessageSequenceTask {
        public final ChatMessage message;
        public final String conversationId;
        public final LocalDateTime localTimestamp;
        
        public MessageSequenceTask(ChatMessage message, String conversationId, LocalDateTime localTimestamp) {
            this.message = message;
            this.conversationId = conversationId;
            this.localTimestamp = localTimestamp;
        }
    }
    
    /**
     * 序列化回调接口
     */
    public interface MessageSequenceCallback {
        void onMessageSequenced(ChatMessage message);
        void onSequenceError(ChatMessage message, Exception error);
    }
    
    /**
     * 启动序列化服务
     */
    public static void start(MessageSequenceCallback callback) {
        if (started) {
            return;
        }
        
        sequenceCallback = callback;
        started = true;
        
        // 启动序列化处理线程
        sequenceExecutor.submit(() -> {
            logger.info("消息序列化服务已启动");
            
            while (started) {
                try {
                    MessageSequenceTask task = sequenceQueue.take();
                    processSequenceTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "处理消息序列化任务时出错", e);
                }
            }
            
            logger.info("消息序列化服务已停止");
        });
    }
    
    /**
     * 停止序列化服务
     */
    public static void stop() {
        started = false;
        sequenceExecutor.shutdown();
    }
    
    /**
     * 提交消息进行序列化
     */
    public static void sequenceMessage(String sender, String receiver, String content, LocalDateTime localTimestamp) {
        if (!started) {
            logger.warning("序列化服务未启动，无法处理消息");
            return;
        }
        
        // 生成会话ID
        String conversationId = MessageIdGenerator.generateConversationId(sender, receiver);
        
        // 生成全局有序的消息ID
        long messageId = MessageIdGenerator.generateMessageId(conversationId);
        
        // 创建消息对象
        ChatMessage message = new ChatMessage(messageId, sender, receiver, content, localTimestamp, conversationId);
        
        // 提交到序列化队列
        MessageSequenceTask task = new MessageSequenceTask(message, conversationId, localTimestamp);
        
        if (!sequenceQueue.offer(task)) {
            logger.warning("序列化队列已满，消息可能丢失: " + content);
        }
    }
    
    /**
     * 处理序列化任务
     */
    private static void processSequenceTask(MessageSequenceTask task) {
        try {
            ChatMessage message = task.message;
            String conversationId = task.conversationId;
            
            // 获取或创建会话序列号
            AtomicLong sequenceCounter = conversationSequences.computeIfAbsent(
                conversationId, 
                k -> new AtomicLong(0)
            );
            
            // 分配序列号
            long sequenceNumber = sequenceCounter.getAndIncrement();
            message.setSequenceNumber(sequenceNumber);
            
            // 设置服务器时间戳
            message.setLocalTimestamp(task.localTimestamp);
            
            // 设置消息状态为已发送
            message.setStatus(ChatMessage.MessageStatus.SENT);
            
            logger.info(String.format("消息已序列化 - ID: %d, 会话: %s, 序列号: %d", 
                message.getMessageId(), conversationId, sequenceNumber));
            
            // 回调通知
            if (sequenceCallback != null) {
                sequenceCallback.onMessageSequenced(message);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "序列化消息时出错", e);
            
            if (sequenceCallback != null) {
                sequenceCallback.onSequenceError(task.message, e);
            }
        }
    }
    
    /**
     * 获取会话的当前序列号
     */
    public static long getCurrentSequence(String conversationId) {
        AtomicLong counter = conversationSequences.get(conversationId);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 重置会话的序列号（谨慎使用）
     */
    public static void resetSequence(String conversationId) {
        conversationSequences.remove(conversationId);
        logger.info("已重置会话序列号: " + conversationId);
    }
    
    /**
     * 获取所有活跃会话的统计信息
     */
    public static Map<String, Long> getSequenceStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        conversationSequences.forEach((conversationId, counter) -> {
            stats.put(conversationId, counter.get());
        });
        return stats;
    }
    
    /**
     * 清理长时间未使用的会话序列号
     */
    public static void cleanupOldSequences() {
        // 可以根据需要实现清理逻辑
        // 例如：清理超过一定时间未使用的会话序列号
        logger.info("执行序列号清理任务");
    }
}
