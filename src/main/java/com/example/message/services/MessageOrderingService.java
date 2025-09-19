package com.example.message.services;

import com.example.message.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.function.Consumer;

/**
 * 客户端消息排序服务
 * 借鉴微信的本地排序机制，确保消息按正确顺序显示
 */
public class MessageOrderingService {
    
    private static final Logger logger = Logger.getLogger(MessageOrderingService.class.getName());
    
    // 每个会话的消息缓冲区
    private static final Map<String, MessageBuffer> conversationBuffers = new ConcurrentHashMap<>();
    
    // 消息显示回调
    private static Consumer<ChatMessage> messageDisplayCallback;
    
    // 排序处理线程池
    private static final ExecutorService orderingExecutor = Executors.newCachedThreadPool();
    
    // 缓冲等待时间（毫秒）
    private static final long BUFFER_TIMEOUT = 1000;
    
    // 是否已启动
    private static volatile boolean started = false;
    
    /**
     * 消息缓冲区
     */
    private static class MessageBuffer {
        private final String conversationId;
        private final PriorityBlockingQueue<ChatMessage> messageQueue;
        private final Map<Long, ChatMessage> messageMap;
        private long expectedSequence;
        private long lastProcessTime;
        
        public MessageBuffer(String conversationId) {
            this.conversationId = conversationId;
            // 按消息ID排序的优先队列
            this.messageQueue = new PriorityBlockingQueue<>(100, 
                Comparator.comparing(ChatMessage::getMessageId));
            this.messageMap = new ConcurrentHashMap<>();
            this.expectedSequence = 1;
            this.lastProcessTime = System.currentTimeMillis();
        }
        
        public synchronized void addMessage(ChatMessage message) {
            if (!messageMap.containsKey(message.getMessageId())) {
                messageQueue.offer(message);
                messageMap.put(message.getMessageId(), message);
                lastProcessTime = System.currentTimeMillis();
                
                logger.fine(String.format("消息已加入缓冲区: %s, ID: %d, Seq: %d", 
                    conversationId, message.getMessageId(), message.getSequenceNumber()));
            }
        }
        
        public synchronized List<ChatMessage> getOrderedMessages() {
            List<ChatMessage> orderedMessages = new ArrayList<>();
            
            // 按消息ID顺序处理消息
            while (!messageQueue.isEmpty()) {
                ChatMessage message = messageQueue.peek();
                
                // 检查是否是期望的下一条消息
                if (message.getSequenceNumber() == expectedSequence) {
                    messageQueue.poll();
                    messageMap.remove(message.getMessageId());
                    orderedMessages.add(message);
                    expectedSequence++;
                } else if (message.getSequenceNumber() < expectedSequence) {
                    // 重复消息，丢弃
                    messageQueue.poll();
                    messageMap.remove(message.getMessageId());
                    logger.warning(String.format("丢弃重复消息: ID: %d, Seq: %d", 
                        message.getMessageId(), message.getSequenceNumber()));
                } else {
                    // 消息序列号大于期望值，可能是乱序，等待前面的消息
                    break;
                }
            }
            
            // 如果等待时间过长，强制输出所有消息
            if (System.currentTimeMillis() - lastProcessTime > BUFFER_TIMEOUT && !messageQueue.isEmpty()) {
                logger.warning(String.format("等待超时，强制输出乱序消息: %s", conversationId));
                
                while (!messageQueue.isEmpty()) {
                    ChatMessage message = messageQueue.poll();
                    messageMap.remove(message.getMessageId());
                    orderedMessages.add(message);
                }
                
                // 重置期望序列号
                if (!orderedMessages.isEmpty()) {
                    ChatMessage lastMessage = orderedMessages.get(orderedMessages.size() - 1);
                    expectedSequence = lastMessage.getSequenceNumber() + 1;
                }
            }
            
            return orderedMessages;
        }
        
        public boolean isEmpty() {
            return messageQueue.isEmpty();
        }
        
        public long getLastProcessTime() {
            return lastProcessTime;
        }
    }
    
    /**
     * 启动消息排序服务
     */
    public static void start(Consumer<ChatMessage> displayCallback) {
        if (started) {
            return;
        }
        
        messageDisplayCallback = displayCallback;
        started = true;
        
        // 启动定时处理任务
        orderingExecutor.submit(() -> {
            logger.info("消息排序服务已启动");
            
            while (started) {
                try {
                    processAllBuffers();
                    Thread.sleep(100); // 每100毫秒检查一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "处理消息缓冲区时出错", e);
                }
            }
            
            logger.info("消息排序服务已停止");
        });
    }
    
    /**
     * 停止消息排序服务
     */
    public static void stop() {
        started = false;
        orderingExecutor.shutdown();
        try {
            if (!orderingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                orderingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            orderingExecutor.shutdownNow();
        }
    }
    
    /**
     * 添加消息到排序缓冲区
     */
    public static void addMessage(ChatMessage message) {
        if (!started) {
            // 服务未启动，直接显示
            if (messageDisplayCallback != null) {
                messageDisplayCallback.accept(message);
            }
            return;
        }
        
        String conversationId = message.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            // 没有会话ID，直接显示
            if (messageDisplayCallback != null) {
                messageDisplayCallback.accept(message);
            }
            return;
        }
        
        // 添加到对应会话的缓冲区
        MessageBuffer buffer = conversationBuffers.computeIfAbsent(
            conversationId, 
            MessageBuffer::new
        );
        
        buffer.addMessage(message);
    }
    
    /**
     * 处理所有缓冲区
     */
    private static void processAllBuffers() {
        for (MessageBuffer buffer : conversationBuffers.values()) {
            List<ChatMessage> orderedMessages = buffer.getOrderedMessages();
            
            for (ChatMessage message : orderedMessages) {
                if (messageDisplayCallback != null) {
                    messageDisplayCallback.accept(message);
                }
            }
        }
        
        // 清理长时间未使用的缓冲区
        cleanupOldBuffers();
    }
    
    /**
     * 清理长时间未使用的缓冲区
     */
    private static void cleanupOldBuffers() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = 5 * 60 * 1000; // 5分钟
        
        conversationBuffers.entrySet().removeIf(entry -> {
            MessageBuffer buffer = entry.getValue();
            if (buffer.isEmpty() && 
                currentTime - buffer.getLastProcessTime() > cleanupThreshold) {
                logger.fine("清理未使用的消息缓冲区: " + entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 强制刷新指定会话的消息缓冲区
     */
    public static void flushConversation(String conversationId) {
        MessageBuffer buffer = conversationBuffers.get(conversationId);
        if (buffer != null) {
            List<ChatMessage> orderedMessages = buffer.getOrderedMessages();
            for (ChatMessage message : orderedMessages) {
                if (messageDisplayCallback != null) {
                    messageDisplayCallback.accept(message);
                }
            }
        }
    }
    
    /**
     * 获取缓冲区统计信息
     */
    public static Map<String, Integer> getBufferStats() {
        Map<String, Integer> stats = new HashMap<>();
        conversationBuffers.forEach((conversationId, buffer) -> {
            stats.put(conversationId, buffer.messageQueue.size());
        });
        return stats;
    }
}
