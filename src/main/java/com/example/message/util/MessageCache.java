package com.example.message.util;

import com.example.message.model.ChatMessage;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 消息缓存管理类
 * 使用LRU缓存策略来存储最近的聊天消息，减少数据库查询次数
 */
public class MessageCache {
    private static final Logger logger = Logger.getLogger(MessageCache.class.getName());
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 1000;      // 最大缓存消息数量
    private static final int MAX_USER_MESSAGES = 100;    // 每个用户对话最大缓存消息数
    private static final long CACHE_EXPIRE_TIME = 30 * 60 * 1000; // 缓存过期时间30分钟
    
    // 单例实例
    private static volatile MessageCache instance;
    
    // 消息缓存存储
    private final Map<String, List<ChatMessage>> userMessageCache;
    private final Map<String, Long> cacheTimestamps;
    private final LinkedHashMap<Integer, ChatMessage> messageIdCache;
    private final ReadWriteLock lock;
    
    // 缓存统计
    private long cacheHits = 0;
    private long cacheMisses = 0;
    
    private MessageCache() {
        this.userMessageCache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        this.messageIdCache = new LinkedHashMap<Integer, ChatMessage>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, ChatMessage> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
        this.lock = new ReentrantReadWriteLock();
        
        // 启动缓存清理线程
        startCacheCleanupThread();
    }
    
    /**
     * 获取缓存实例
     * @return 缓存实例
     */
    public static MessageCache getInstance() {
        if (instance == null) {
            synchronized (MessageCache.class) {
                if (instance == null) {
                    instance = new MessageCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * 缓存用户之间的消息列表
     * @param user1 用户1
     * @param user2 用户2
     * @param messages 消息列表
     */
    public void cacheUserMessages(String user1, String user2, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        String cacheKey = generateCacheKey(user1, user2);
        
        lock.writeLock().lock();
        try {
            // 限制缓存大小
            List<ChatMessage> cachedMessages = messages.stream()
                    .sorted((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()))
                    .limit(MAX_USER_MESSAGES)
                    .collect(Collectors.toList());
            
            userMessageCache.put(cacheKey, new ArrayList<>(cachedMessages));
            cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
            // 同时缓存单个消息
            for (ChatMessage message : cachedMessages) {
                if (message.getId() > 0) {
                    messageIdCache.put(message.getId(), message);
                }
            }
            
            logger.fine("缓存用户消息: " + cacheKey + ", 消息数: " + cachedMessages.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取用户之间的缓存消息
     * @param user1 用户1
     * @param user2 用户2
     * @return 缓存的消息列表，如果没有缓存则返回null
     */
    public List<ChatMessage> getUserMessages(String user1, String user2) {
        String cacheKey = generateCacheKey(user1, user2);
        
        lock.readLock().lock();
        try {
            Long timestamp = cacheTimestamps.get(cacheKey);
            if (timestamp == null || System.currentTimeMillis() - timestamp > CACHE_EXPIRE_TIME) {
                cacheMisses++;
                return null; // 缓存过期或不存在
            }
            
            List<ChatMessage> messages = userMessageCache.get(cacheKey);
            if (messages != null) {
                cacheHits++;
                logger.fine("缓存命中: " + cacheKey + ", 消息数: " + messages.size());
                return new ArrayList<>(messages); // 返回副本
            }
            
            cacheMisses++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 添加新消息到缓存
     * @param message 新消息
     */
    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            // 缓存单个消息
            if (message.getId() > 0) {
                messageIdCache.put(message.getId(), message);
            }
            
            // 更新用户消息缓存
            String cacheKey = generateCacheKey(message.getSender(), message.getReceiver());
            List<ChatMessage> messages = userMessageCache.get(cacheKey);
            
            if (messages != null) {
                // 添加到现有缓存
                messages.add(0, message); // 添加到列表开头
                
                // 限制缓存大小
                if (messages.size() > MAX_USER_MESSAGES) {
                    messages.remove(messages.size() - 1); // 移除最老的消息
                }
                
                // 更新时间戳
                cacheTimestamps.put(cacheKey, System.currentTimeMillis());
                
                logger.fine("添加消息到缓存: " + cacheKey + ", 消息ID: " + message.getId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 根据消息ID获取消息
     * @param messageId 消息ID
     * @return 消息对象，如果没有缓存则返回null
     */
    public ChatMessage getMessageById(int messageId) {
        lock.readLock().lock();
        try {
            ChatMessage message = messageIdCache.get(messageId);
            if (message != null) {
                cacheHits++;
                logger.fine("消息ID缓存命中: " + messageId);
                return message;
            }
            
            cacheMisses++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 更新消息状态（如已读状态）
     * @param messageId 消息ID
     * @param isRead 是否已读
     */
    public void updateMessageStatus(int messageId, boolean isRead) {
        lock.writeLock().lock();
        try {
            ChatMessage message = messageIdCache.get(messageId);
            if (message != null) {
                message.setRead(isRead);
                logger.fine("更新消息状态: ID=" + messageId + ", 已读=" + isRead);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取最近的消息（用于增量更新）
     * @param user1 用户1
     * @param user2 用户2
     * @param since 起始时间
     * @return 指定时间之后的消息列表
     */
    public List<ChatMessage> getRecentMessages(String user1, String user2, LocalDateTime since) {
        if (since == null) {
            return getUserMessages(user1, user2);
        }
        
        String cacheKey = generateCacheKey(user1, user2);
        
        lock.readLock().lock();
        try {
            List<ChatMessage> messages = userMessageCache.get(cacheKey);
            if (messages != null) {
                List<ChatMessage> recentMessages = messages.stream()
                        .filter(msg -> msg.getTimestamp().isAfter(since))
                        .collect(Collectors.toList());
                
                if (!recentMessages.isEmpty()) {
                    cacheHits++;
                    logger.fine("获取最近消息: " + cacheKey + ", 数量: " + recentMessages.size());
                    return recentMessages;
                }
            }
            
            cacheMisses++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 清除指定用户的缓存
     * @param user1 用户1
     * @param user2 用户2
     */
    public void clearUserCache(String user1, String user2) {
        String cacheKey = generateCacheKey(user1, user2);
        
        lock.writeLock().lock();
        try {
            userMessageCache.remove(cacheKey);
            cacheTimestamps.remove(cacheKey);
            logger.fine("清除用户缓存: " + cacheKey);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        lock.writeLock().lock();
        try {
            userMessageCache.clear();
            cacheTimestamps.clear();
            messageIdCache.clear();
            logger.info("清除所有缓存");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        lock.readLock().lock();
        try {
            long totalRequests = cacheHits + cacheMisses;
            double hitRate = totalRequests > 0 ? (double) cacheHits / totalRequests * 100 : 0;
            
            return String.format("缓存统计 - 命中率: %.2f%% (命中: %d, 未命中: %d), " +
                    "用户对话缓存: %d, 消息ID缓存: %d",
                    hitRate, cacheHits, cacheMisses, 
                    userMessageCache.size(), messageIdCache.size());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 生成缓存键
     * @param user1 用户1
     * @param user2 用户2
     * @return 缓存键
     */
    private String generateCacheKey(String user1, String user2) {
        if (user1 == null || user2 == null) {
            return "";
        }
        
        // 确保缓存键的一致性，不管用户顺序如何
        return user1.compareTo(user2) < 0 ? 
                user1 + ":" + user2 : 
                user2 + ":" + user1;
    }
    
    /**
     * 启动缓存清理线程
     */
    private void startCacheCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10 * 60 * 1000); // 每10分钟运行一次
                    cleanupExpiredCache();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        cleanupThread.setDaemon(true);
        cleanupThread.setName("Message-Cache-Cleanup");
        cleanupThread.start();
        
        logger.info("缓存清理线程已启动");
    }
    
    /**
     * 清理过期的缓存
     */
    private void cleanupExpiredCache() {
        lock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            List<String> expiredKeys = new ArrayList<>();
            
            for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
                if (currentTime - entry.getValue() > CACHE_EXPIRE_TIME) {
                    expiredKeys.add(entry.getKey());
                }
            }
            
            for (String key : expiredKeys) {
                userMessageCache.remove(key);
                cacheTimestamps.remove(key);
            }
            
            if (!expiredKeys.isEmpty()) {
                logger.info("清理过期缓存: " + expiredKeys.size() + " 个用户对话");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
} 