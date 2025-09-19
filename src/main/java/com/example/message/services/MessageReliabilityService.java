package com.example.message.services;

import com.example.message.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.function.Consumer;

/**
 * 消息可靠性服务
 * 提供消息确认、重试、离线消息等可靠性保障
 */
public class MessageReliabilityService {
    
    private static final Logger logger = Logger.getLogger(MessageReliabilityService.class.getName());
    
    // 等待确认的消息
    private static final Map<Long, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    
    // 离线消息存储
    private static final Map<String, List<ChatMessage>> offlineMessages = new ConcurrentHashMap<>();
    
    // 重试执行器
    private static final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);
    
    // 消息发送回调
    private static Consumer<ChatMessage> messageSendCallback;
    
    // 消息确认超时时间（秒）
    private static final int ACK_TIMEOUT = 30;
    
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;
    
    // 是否已启动
    private static volatile boolean started = false;
    
    /**
     * 等待确认的消息
     */
    private static class PendingMessage {
        public final ChatMessage message;
        public final AtomicInteger retryCount;
        public final long createTime;
        public final ScheduledFuture<?> timeoutTask;
        
        public PendingMessage(ChatMessage message, ScheduledFuture<?> timeoutTask) {
            this.message = message;
            this.retryCount = new AtomicInteger(0);
            this.createTime = System.currentTimeMillis();
            this.timeoutTask = timeoutTask;
        }
    }
    
    /**
     * 启动可靠性服务
     */
    public static void start(Consumer<ChatMessage> sendCallback) {
        if (started) {
            return;
        }
        
        messageSendCallback = sendCallback;
        started = true;
        
        logger.info("消息可靠性服务已启动");
        
        // 启动定期清理任务
        retryExecutor.scheduleAtFixedRate(() -> {
            cleanupExpiredMessages();
        }, 60, 60, TimeUnit.SECONDS); // 每分钟清理一次
    }
    
    /**
     * 停止可靠性服务
     */
    public static void stop() {
        started = false;
        
        // 取消所有等待中的任务
        pendingMessages.values().forEach(pending -> {
            if (pending.timeoutTask != null) {
                pending.timeoutTask.cancel(false);
            }
        });
        
        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
        }
        
        logger.info("消息可靠性服务已停止");
    }
    
    /**
     * 发送需要确认的消息
     */
    public static void sendReliableMessage(ChatMessage message) {
        if (!started) {
            logger.warning("可靠性服务未启动，直接发送消息");
            if (messageSendCallback != null) {
                messageSendCallback.accept(message);
            }
            return;
        }
        
        // 设置消息状态为发送中
        message.setStatus(ChatMessage.MessageStatus.SENDING);
        
        // 创建超时任务
        ScheduledFuture<?> timeoutTask = retryExecutor.schedule(() -> {
            handleMessageTimeout(message.getMessageId());
        }, ACK_TIMEOUT, TimeUnit.SECONDS);
        
        // 添加到等待确认列表
        PendingMessage pending = new PendingMessage(message, timeoutTask);
        pendingMessages.put(message.getMessageId(), pending);
        
        // 发送消息
        if (messageSendCallback != null) {
            messageSendCallback.accept(message);
        }
        
        logger.fine(String.format("可靠消息已发送: ID=%d, 内容=%s", 
            message.getMessageId(), message.getContent()));
    }
    
    /**
     * 确认消息已收到
     */
    public static void acknowledgeMessage(long messageId) {
        PendingMessage pending = pendingMessages.remove(messageId);
        if (pending != null) {
            // 取消超时任务
            if (pending.timeoutTask != null) {
                pending.timeoutTask.cancel(false);
            }
            
            // 更新消息状态
            pending.message.setStatus(ChatMessage.MessageStatus.DELIVERED);
            
            logger.fine(String.format("消息已确认: ID=%d", messageId));
        }
    }
    
    /**
     * 处理消息超时
     */
    private static void handleMessageTimeout(long messageId) {
        PendingMessage pending = pendingMessages.get(messageId);
        if (pending == null) {
            return; // 消息已经被确认或处理
        }
        
        int currentRetry = pending.retryCount.incrementAndGet();
        
        if (currentRetry <= MAX_RETRY_COUNT) {
            logger.warning(String.format("消息超时，开始第%d次重试: ID=%d", currentRetry, messageId));
            
            // 重新发送消息
            ChatMessage message = pending.message;
            message.setStatus(ChatMessage.MessageStatus.SENDING);
            
            // 创建新的超时任务
            ScheduledFuture<?> newTimeoutTask = retryExecutor.schedule(() -> {
                handleMessageTimeout(messageId);
            }, ACK_TIMEOUT, TimeUnit.SECONDS);
            
            // 更新超时任务
            PendingMessage newPending = new PendingMessage(message, newTimeoutTask);
            newPending.retryCount.set(currentRetry);
            pendingMessages.put(messageId, newPending);
            
            // 重新发送
            if (messageSendCallback != null) {
                messageSendCallback.accept(message);
            }
        } else {
            logger.severe(String.format("消息发送失败，超过最大重试次数: ID=%d", messageId));
            
            // 移除失败的消息
            pendingMessages.remove(messageId);
            
            // 更新消息状态为失败
            pending.message.setStatus(ChatMessage.MessageStatus.FAILED);
        }
    }
    
    /**
     * 添加离线消息
     */
    public static void addOfflineMessage(String userId, ChatMessage message) {
        List<ChatMessage> userOfflineMessages = offlineMessages.computeIfAbsent(
            userId, 
            k -> new ArrayList<>()
        );
        
        synchronized (userOfflineMessages) {
            userOfflineMessages.add(message);
            
            // 限制离线消息数量，避免内存溢出
            if (userOfflineMessages.size() > 1000) {
                userOfflineMessages.remove(0); // 移除最老的消息
            }
        }
        
        logger.info(String.format("已添加离线消息: 用户=%s, 消息ID=%d", userId, message.getMessageId()));
    }
    
    /**
     * 获取并清除用户的离线消息
     */
    public static List<ChatMessage> getAndClearOfflineMessages(String userId) {
        List<ChatMessage> messages = offlineMessages.remove(userId);
        if (messages != null && !messages.isEmpty()) {
            logger.info(String.format("获取离线消息: 用户=%s, 数量=%d", userId, messages.size()));
            return new ArrayList<>(messages);
        }
        return new ArrayList<>();
    }
    
    /**
     * 检查用户是否有离线消息
     */
    public static boolean hasOfflineMessages(String userId) {
        List<ChatMessage> messages = offlineMessages.get(userId);
        return messages != null && !messages.isEmpty();
    }
    
    /**
     * 获取离线消息数量
     */
    public static int getOfflineMessageCount(String userId) {
        List<ChatMessage> messages = offlineMessages.get(userId);
        return messages != null ? messages.size() : 0;
    }
    
    /**
     * 清理过期的消息
     */
    private static void cleanupExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 5 * 60 * 1000; // 5分钟
        
        // 清理超时的等待确认消息
        pendingMessages.entrySet().removeIf(entry -> {
            PendingMessage pending = entry.getValue();
            if (currentTime - pending.createTime > expireTime) {
                if (pending.timeoutTask != null) {
                    pending.timeoutTask.cancel(false);
                }
                logger.fine(String.format("清理过期的等待确认消息: ID=%d", entry.getKey()));
                return true;
            }
            return false;
        });
        
        // 清理过期的离线消息（超过24小时）
        long offlineExpireTime = 24 * 60 * 60 * 1000; // 24小时
        offlineMessages.entrySet().removeIf(entry -> {
            List<ChatMessage> messages = entry.getValue();
            synchronized (messages) {
                messages.removeIf(message -> {
                    long messageTime = message.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    return currentTime - messageTime > offlineExpireTime;
                });
                
                if (messages.isEmpty()) {
                    logger.fine(String.format("清理空的离线消息列表: 用户=%s", entry.getKey()));
                    return true;
                }
            }
            return false;
        });
    }
    
    /**
     * 获取统计信息
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("pendingMessages", pendingMessages.size());
        stats.put("offlineUsers", offlineMessages.size());
        
        int totalOfflineMessages = offlineMessages.values().stream()
            .mapToInt(List::size)
            .sum();
        stats.put("totalOfflineMessages", totalOfflineMessages);
        
        return stats;
    }
}
