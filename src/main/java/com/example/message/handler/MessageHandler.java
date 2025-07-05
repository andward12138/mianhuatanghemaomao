package com.example.message.handler;

import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 消息处理器 - 负责处理消息的接收、显示和管理
 */
public class MessageHandler {
    
    private final VBox chatMessages;
    private final ScrollPane chatScrollPane;
    private final String currentUsername;
    private final Set<Integer> displayedMessageIds = new HashSet<>();
    private Consumer<String> unreadBadgeHandler;
    
    public MessageHandler(VBox chatMessages, ScrollPane chatScrollPane, String currentUsername) {
        this.chatMessages = chatMessages;
        this.chatScrollPane = chatScrollPane;
        this.currentUsername = currentUsername;
    }
    
    /**
     * 初始化组件（用于延迟初始化）
     */
    public void initializeComponents(VBox chatMessages, ScrollPane chatScrollPane) {
        // 这个方法用于ChatController中的延迟初始化
        // 因为构造函数中可能组件还没有创建完成
    }
    
    /**
     * 设置未读消息标记处理器
     */
    public void setUnreadBadgeHandler(Consumer<String> handler) {
        this.unreadBadgeHandler = handler;
    }
    
    /**
     * 处理接收到的消息
     */
    public void handleReceivedMessage(ChatMessage message) {
        System.out.println("接收到消息: " + message.getContent() + " 来自: " + message.getSender());
        
        Platform.runLater(() -> {
            try {
                String currentChatPeer = ChatService.getCurrentChatPeer();
                
                // 如果是当前聊天对象发送的消息，立即显示
                if (message.getSender().equals(currentChatPeer)) {
                    System.out.println("立即显示来自当前聊天对象的消息");
                    addNewMessageToChat(message);
                } else {
                    // 不是当前聊天对象的消息，添加未读标记
                    System.out.println("添加未读标记给用户: " + message.getSender());
                    if (unreadBadgeHandler != null) {
                        unreadBadgeHandler.accept(message.getSender());
                    }
                }
                
            } catch (Exception e) {
                System.err.println("处理接收消息时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 加载聊天历史
     */
    public void loadChatHistory(String username) {
        try {
            System.out.println("开始加载与用户 " + username + " 的完整聊天历史");
            
            // 获取完整聊天历史
            List<ChatMessage> history = ChatService.getChatHistory(username);
            
            Platform.runLater(() -> {
                // 清空现有消息
                chatMessages.getChildren().clear();
                displayedMessageIds.clear();
                
                if (history != null && !history.isEmpty()) {
                    System.out.println("加载到 " + history.size() + " 条历史消息");
                    
                    // 按时间排序
                    history.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
                    
                    // 逐个添加消息
                    for (ChatMessage msg : history) {
                        if (isMessageRelevant(msg, username)) {
                            HBox messageBox = createMessageBox(msg);
                            chatMessages.getChildren().add(messageBox);
                            displayedMessageIds.add(msg.getId());
                        }
                    }
                    
                    System.out.println("历史消息加载完成，显示消息数: " + chatMessages.getChildren().size());
                } else {
                    // 显示无历史消息提示
                    Label noMessagesLabel = new Label("还没有与该用户的聊天记录，发送消息开始对话吧");
                    noMessagesLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px; -fx-alignment: center;");
                    noMessagesLabel.setAlignment(Pos.CENTER);
                    noMessagesLabel.setMaxWidth(Double.MAX_VALUE);
                    chatMessages.getChildren().add(noMessagesLabel);
                }
                
                // 滚动到最新消息
                scrollToBottom();
            });
            
        } catch (Exception e) {
            System.err.println("加载聊天历史时出错: " + e.getMessage());
            e.printStackTrace();
            
            Platform.runLater(() -> {
                chatMessages.getChildren().clear();
                Label errorLabel = new Label("加载聊天历史失败，请重试");
                errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px; -fx-alignment: center;");
                errorLabel.setAlignment(Pos.CENTER);
                errorLabel.setMaxWidth(Double.MAX_VALUE);
                chatMessages.getChildren().add(errorLabel);
            });
        }
    }
    
    /**
     * 添加新消息到聊天区域
     */
    public void addNewMessageToChat(ChatMessage message) {
        try {
            System.out.println("添加新消息到聊天区域: " + message.getContent());
            
            // 检查是否重复消息
            if (isDuplicateMessage(message)) {
                System.out.println("检测到重复消息，忽略");
                return;
            }
            
            // 移除加载提示
            removeLoadingMessages();
            
            // 创建消息显示组件
            HBox messageBox = createMessageBox(message);
            
            // 添加到聊天区域
            chatMessages.getChildren().add(messageBox);
            displayedMessageIds.add(message.getId());
            
            // 滚动到最新消息
            scrollToBottom();
            
            System.out.println("新消息已添加并滚动到底部");
            
        } catch (Exception e) {
            System.err.println("添加新消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建消息显示组件
     */
    private HBox createMessageBox(ChatMessage message) {
        HBox messageBox = new HBox();
        messageBox.setSpacing(10);
        messageBox.setPadding(new Insets(5));
        messageBox.setId("msg-" + message.getId());
        messageBox.setUserData(message.getTimestamp());
        messageBox.setMinHeight(60);
        messageBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        
        // 判断消息方向
        boolean isOutgoing = message.getSender().equals(currentUsername);
        messageBox.setAlignment(isOutgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        // 创建消息内容框
        VBox messageContentBox = new VBox();
        messageContentBox.setSpacing(5);
        messageContentBox.setMaxWidth(300);
        messageContentBox.setMinHeight(50);
        messageContentBox.setPadding(new Insets(8));
        
        // 根据消息方向设置文字颜色
        String senderColor = isOutgoing ? "white" : "#333";
        String contentColor = isOutgoing ? "white" : "black";
        String timeColor = isOutgoing ? "#E0E0E0" : "#999";
        
        // 发送者标签
        Label senderLabel = new Label(message.getSender());
        senderLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + senderColor + "; -fx-font-size: 11px;");
        
        // 消息内容
        Label contentLabel = new Label(message.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + contentColor + ";");
        
        // 时间标签
        String timeText = message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Label timeLabel = new Label(timeText);
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + timeColor + ";");
        
        messageContentBox.getChildren().addAll(senderLabel, contentLabel, timeLabel);
        
        // 创建头像
        Circle avatar = new Circle(20);
        avatar.setFill(isOutgoing ? Color.LIGHTBLUE : Color.LIGHTGREEN);
        String initial = message.getSender().isEmpty() ? "?" : 
                        message.getSender().substring(0, 1).toUpperCase();
        Label initials = new Label(initial);
        initials.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        StackPane avatarPane = new StackPane(avatar, initials);
        
        // 根据消息方向排列组件
        if (isOutgoing) {
            messageBox.getChildren().addAll(messageContentBox, avatarPane);
        } else {
            messageBox.getChildren().addAll(avatarPane, messageContentBox);
        }
        
        // 设置消息样式
        if (isOutgoing) {
            messageBox.setStyle("-fx-background-radius: 10; -fx-padding: 5;");
            messageContentBox.setStyle("-fx-background-color: #007AFF; -fx-background-radius: 15; -fx-padding: 10;");
        } else {
            messageBox.setStyle("-fx-background-radius: 10; -fx-padding: 5;");
            messageContentBox.setStyle("-fx-background-color: #E5E5EA; -fx-background-radius: 15; -fx-padding: 10;");
        }
        
        return messageBox;
    }
    
    /**
     * 检查消息是否与当前聊天相关
     */
    private boolean isMessageRelevant(ChatMessage msg, String username) {
        // 自己发送给对方的消息
        if (msg.getSender().equals(currentUsername) && msg.getReceiver().equals(username)) {
            return true;
        }
        
        // 对方发送给自己的消息
        if (msg.getSender().equals(username) && msg.getReceiver().equals(currentUsername)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否重复消息
     */
    private boolean isDuplicateMessage(ChatMessage message) {
        return displayedMessageIds.contains(message.getId());
    }
    
    /**
     * 移除加载提示消息
     */
    private void removeLoadingMessages() {
        ObservableList<Node> children = chatMessages.getChildren();
        children.removeIf(node -> node instanceof Label && 
                (((Label) node).getText().contains("正在加载") || 
                 ((Label) node).getText().contains("还没有与该用户")));
    }
    
    /**
     * 滚动到底部
     */
    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (chatScrollPane != null) {
                chatScrollPane.setVvalue(1.0);
                
                // 再次延迟滚动，确保渲染完成
                Platform.runLater(() -> {
                    chatScrollPane.setVvalue(1.0);
                });
            }
        });
    }
    
    /**
     * 清空聊天消息
     */
    public void clearMessages() {
        Platform.runLater(() -> {
            chatMessages.getChildren().clear();
            displayedMessageIds.clear();
        });
    }
} 