package com.example.message.core;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Router {
    
    public enum Page {
        DIARY("diary", "心情日记"),
        ANNIVERSARY("anniversary", "纪念日"),
        CHAT("chat", "消息聊天"), 
        LOGS("logs", "聊天记录"),
        SETTINGS("settings", "设置");
        
        private final String id;
        private final String displayName;
        
        Page(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
    }
    
    private final StackPane contentContainer;
    private final Map<Page, Supplier<Node>> pageSuppliers = new HashMap<>();
    private final Map<Page, Node> pageCache = new HashMap<>();
    private Page currentPage;
    private Runnable onPageChangeCallback;
    
    public Router(StackPane contentContainer) {
        this.contentContainer = contentContainer;
    }
    
    public void registerPage(Page page, Supplier<Node> pageSupplier) {
        pageSuppliers.put(page, pageSupplier);
    }
    
    public void navigateTo(Page page) {
        navigateTo(page, true);
    }
    
    public void navigateTo(Page page, boolean withAnimation) {
        if (page == currentPage) {
            return;
        }
        
        Node pageNode = getOrCreatePage(page);
        
        if (withAnimation && !contentContainer.getChildren().isEmpty()) {
            // 淡出当前页面，淡入新页面
            Node currentNode = contentContainer.getChildren().get(0);
            
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), currentNode);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            
            fadeOut.setOnFinished(e -> {
                contentContainer.getChildren().clear();
                contentContainer.getChildren().add(pageNode);
                
                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), pageNode);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            
            fadeOut.play();
        } else {
            // 直接切换
            contentContainer.getChildren().clear();
            contentContainer.getChildren().add(pageNode);
        }
        
        Page previousPage = currentPage;
        currentPage = page;
        
        // 触发页面变化回调
        if (onPageChangeCallback != null) {
            onPageChangeCallback.run();
        }
        
        // 发布页面变化事件
        EventBus.getInstance().publish("page.changed", new PageChangeEvent(previousPage, currentPage));
        
        System.out.println("导航到页面: " + page.getDisplayName());
    }
    
    private Node getOrCreatePage(Page page) {
        // 检查缓存
        if (pageCache.containsKey(page)) {
            return pageCache.get(page);
        }
        
        // 创建新页面
        Supplier<Node> supplier = pageSuppliers.get(page);
        if (supplier == null) {
            throw new IllegalArgumentException("未注册的页面: " + page);
        }
        
        Node pageNode = supplier.get();
        
        // 某些页面不缓存（如聊天页面需要实时更新）
        if (page != Page.CHAT) {
            pageCache.put(page, pageNode);
        }
        
        return pageNode;
    }
    
    public Page getCurrentPage() {
        return currentPage;
    }
    
    public void setOnPageChangeCallback(Runnable callback) {
        this.onPageChangeCallback = callback;
    }
    
    public void clearCache() {
        pageCache.clear();
    }
    
    public void clearCache(Page page) {
        pageCache.remove(page);
    }
    
    // 页面变化事件
    public static class PageChangeEvent {
        private final Page from;
        private final Page to;
        
        public PageChangeEvent(Page from, Page to) {
            this.from = from;
            this.to = to;
        }
        
        public Page getFrom() { return from; }
        public Page getTo() { return to; }
    }
}