package com.example.message.core;

import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
    
    private static EventBus instance;
    private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
    
    private EventBus() {}
    
    public static EventBus getInstance() {
        if (instance == null) {
            synchronized (EventBus.class) {
                if (instance == null) {
                    instance = new EventBus();
                }
            }
        }
        return instance;
    }
    
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String eventType, Consumer<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
                  .add((Consumer<Object>) handler);
    }
    
    public void unsubscribe(String eventType, Consumer<?> handler) {
        List<Consumer<Object>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                subscribers.remove(eventType);
            }
        }
    }
    
    public void publish(String eventType, Object data) {
        List<Consumer<Object>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            // 在JavaFX应用线程中执行UI相关的事件处理
            if (Platform.isFxApplicationThread()) {
                handlers.forEach(handler -> {
                    try {
                        handler.accept(data);
                    } catch (Exception e) {
                        System.err.println("事件处理器执行失败: " + eventType);
                        e.printStackTrace();
                    }
                });
            } else {
                Platform.runLater(() -> {
                    handlers.forEach(handler -> {
                        try {
                            handler.accept(data);
                        } catch (Exception e) {
                            System.err.println("事件处理器执行失败: " + eventType);
                            e.printStackTrace();
                        }
                    });
                });
            }
        }
    }
    
    public void publishAsync(String eventType, Object data) {
        new Thread(() -> publish(eventType, data)).start();
    }
    
    public void clear() {
        subscribers.clear();
    }
    
    // 预定义的事件类型
    public static class Events {
        public static final String MESSAGE_RECEIVED = "message.received";
        public static final String MESSAGE_SENT = "message.sent";
        public static final String USER_ONLINE = "user.online";
        public static final String USER_OFFLINE = "user.offline";
        public static final String DIARY_SAVED = "diary.saved";
        public static final String DIARY_UPDATED = "diary.updated";
        public static final String DIARY_DELETED = "diary.deleted";
        public static final String ANNIVERSARY_SAVED = "anniversary.saved";
        public static final String ANNIVERSARY_UPDATED = "anniversary.updated";
        public static final String ANNIVERSARY_DELETED = "anniversary.deleted";
        public static final String ANNIVERSARY_REMINDER = "anniversary.reminder";
        public static final String THEME_CHANGED = "theme.changed";
        public static final String PAGE_CHANGED = "page.changed";
        public static final String CONNECTION_STATUS_CHANGED = "connection.status.changed";
    }
}