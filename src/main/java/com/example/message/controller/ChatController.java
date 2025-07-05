package com.example.message.controller;

import com.example.message.handler.MessageHandler;
import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天控制器 - 负责处理聊天UI相关逻辑
 */
public class ChatController {
    
    private final String currentUsername;
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private MessageHandler messageHandler;
    
    // UI 组件
    private ListView<String> onlineUsersListView;
    private VBox chatMessages;
    private ScrollPane chatScrollPane;
    private TextField messageField;
    private Button sendButton;
    private Label connectionStatusLabel;
    private StackPane mainContent;
    
    public ChatController(String currentUsername) {
        this.currentUsername = currentUsername;
        // MessageHandler将在UI组件创建后初始化
    }
    
    /**
     * 创建聊天界面
     */
    public StackPane createChatView() {
        mainContent = new StackPane();
        mainContent.setStyle("-fx-background-color: #232a38;");
        
        // 主布局
        HBox mainLayout = new HBox();
        mainLayout.setSpacing(15);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setStyle("-fx-background-color: transparent;");
        
        // 左侧用户列表
        VBox leftPanel = createUserListPanel();
        leftPanel.setPrefWidth(250);
        leftPanel.setMinWidth(250);
        
        // 右侧聊天区域
        VBox rightPanel = createChatPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        
        mainLayout.getChildren().addAll(leftPanel, rightPanel);
        mainContent.getChildren().add(mainLayout);
        
        return mainContent;
    }
    
    /**
     * 创建用户列表面板
     */
    private VBox createUserListPanel() {
        VBox userPanel = new VBox();
        userPanel.setSpacing(15);
        userPanel.setPadding(new Insets(20));
        userPanel.getStyleClass().add("content-box");
        userPanel.setStyle("-fx-background-color: #2f3747; -fx-background-radius: 12px; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 12px;");
        
        // 标题
        Label titleLabel = new Label("在线用户");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e9edf5;");
        
        // 连接状态
        connectionStatusLabel = new Label("未连接");
        connectionStatusLabel.setStyle("-fx-text-fill: #a0a8b8; -fx-font-size: 12px;");
        
        // 用户列表
        onlineUsersListView = new ListView<>(onlineUsers);
        onlineUsersListView.setPrefHeight(350);
        onlineUsersListView.getStyleClass().add("list-view");
        onlineUsersListView.setStyle("-fx-background-color: #343e54; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px;");
        onlineUsersListView.setCellFactory(listView -> new UserListCell());
        onlineUsersListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedUser = onlineUsersListView.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals(currentUsername)) {
                    openChatWithUser(selectedUser);
                }
            }
        });
        VBox.setVgrow(onlineUsersListView, Priority.ALWAYS);
        
        // 刷新按钮
        Button refreshButton = new Button("🔄 刷新用户列表");
        refreshButton.getStyleClass().add("button");
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setOnAction(e -> refreshOnlineUsers());
        
        userPanel.getChildren().addAll(titleLabel, connectionStatusLabel, onlineUsersListView, refreshButton);
        
        return userPanel;
    }
    
    /**
     * 创建聊天面板
     */
    private VBox createChatPanel() {
        VBox chatPanel = new VBox();
        chatPanel.setSpacing(15);
        chatPanel.setPadding(new Insets(20));
        chatPanel.getStyleClass().add("content-box");
        chatPanel.setStyle("-fx-background-color: #2f3747; -fx-background-radius: 12px; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 12px;");
        
        // 聊天标题
        Label chatTitle = new Label("💬 聊天区域");
        chatTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-padding: 0 0 10 0;");
        
        // 聊天消息区域
        chatMessages = new VBox();
        chatMessages.setSpacing(8);
        chatMessages.setPadding(new Insets(15));
        chatMessages.setStyle("-fx-background-color: #343e54; -fx-background-radius: 10px;");
        
        chatScrollPane = new ScrollPane(chatMessages);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.getStyleClass().add("scroll-pane");
        chatScrollPane.setStyle("-fx-background-color: #343e54; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 10px;");
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        // 初始化消息处理器
        messageHandler = new MessageHandler(chatMessages, chatScrollPane, currentUsername);
        messageHandler.setUnreadBadgeHandler(this::setUnreadBadge);
        
        // 设置消息接收回调
        ChatService.setMessageCallback(messageHandler::handleReceivedMessage);
        
        // 消息输入区域
        HBox messageInputBox = createMessageInputBox();
        
        chatPanel.getChildren().addAll(chatTitle, chatScrollPane, messageInputBox);
        
        return chatPanel;
    }
    
    /**
     * 创建消息输入框
     */
    private HBox createMessageInputBox() {
        HBox inputBox = new HBox();
        inputBox.setSpacing(12);
        inputBox.setPadding(new Insets(15, 0, 0, 0));
        inputBox.setAlignment(Pos.CENTER_LEFT);
        
        messageField = new TextField();
        messageField.setPromptText("输入消息...");
        messageField.getStyleClass().add("text-field");
        messageField.setStyle("-fx-background-color: #343e54; -fx-text-fill: #e9edf5; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 12px; -fx-font-size: 14px;");
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendMessage();
            }
        });
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        sendButton = new Button("📤 发送");
        sendButton.getStyleClass().add("button");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px 20px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        
        inputBox.getChildren().addAll(messageField, sendButton);
        
        return inputBox;
    }
    
    /**
     * 发送消息
     */
    private void sendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty()) {
            return;
        }
        
        String currentPeer = ChatService.getCurrentChatPeer();
        if (currentPeer == null || currentPeer.isEmpty()) {
            showAlert("提示", "请先选择一个用户开始聊天");
            return;
        }
        
        // 异步发送消息
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = ChatService.sendPrivateMessageWithResult(currentPeer, content);
                
                Platform.runLater(() -> {
                    if (success) {
                        messageField.clear();
                        System.out.println("消息发送成功");
                    } else {
                        showAlert("错误", "消息发送失败，请检查网络连接");
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("错误", "发送消息时出错: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * 打开与指定用户的聊天
     */
    private void openChatWithUser(String username) {
        System.out.println("打开与用户 " + username + " 的聊天");
        
        // 设置当前聊天对象
        ChatService.setCurrentChatPeer(username);
        
        // 显示加载提示
        showLoadingMessage();
        
        // 异步加载聊天历史
        CompletableFuture.runAsync(() -> {
            messageHandler.loadChatHistory(username);
        });
        
        // 清除未读标记
        clearUnreadBadge(username);
        
        // 刷新用户列表
        onlineUsersListView.refresh();
    }
    
    /**
     * 显示加载提示
     */
    private void showLoadingMessage() {
        Platform.runLater(() -> {
            chatMessages.getChildren().clear();
            Label loadingLabel = new Label("正在加载聊天历史...");
            loadingLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px; -fx-alignment: center;");
            loadingLabel.setAlignment(Pos.CENTER);
            loadingLabel.setMaxWidth(Double.MAX_VALUE);
            chatMessages.getChildren().add(loadingLabel);
        });
    }
    
    /**
     * 刷新在线用户列表
     */
    public void refreshOnlineUsers() {
        CompletableFuture.runAsync(() -> {
            try {
                var users = ChatService.getOnlineUsers();
                Platform.runLater(() -> {
                    onlineUsers.clear();
                    if (users != null) {
                        onlineUsers.addAll(users);
                    }
                    System.out.println("在线用户列表已刷新，用户数: " + onlineUsers.size());
                });
            } catch (Exception e) {
                System.err.println("刷新在线用户列表时出错: " + e.getMessage());
            }
        });
    }
    
    /**
     * 设置未读消息标记
     */
    private void setUnreadBadge(String username) {
        unreadCounts.put(username, unreadCounts.getOrDefault(username, 0) + 1);
        onlineUsersListView.refresh();
    }
    
    /**
     * 清除未读消息标记
     */
    private void clearUnreadBadge(String username) {
        unreadCounts.remove(username);
        onlineUsersListView.refresh();
    }
    
    /**
     * 更新连接状态
     */
    public void updateConnectionStatus(String status) {
        Platform.runLater(() -> {
            connectionStatusLabel.setText(status);
        });
    }
    
    /**
     * 显示提示对话框
     */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * 用户列表单元格
     */
    private class UserListCell extends ListCell<String> {
        @Override
        protected void updateItem(String user, boolean empty) {
            super.updateItem(user, empty);
            
            if (empty || user == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }
            
            // 显示用户名和未读消息数
            String displayText = "👤 " + user;
            Integer unreadCount = unreadCounts.get(user);
            if (unreadCount != null && unreadCount > 0) {
                displayText += " 🔴(" + unreadCount + ")";
                setStyle("-fx-font-weight: bold; -fx-text-fill: #8a7cfa; -fx-background-color: rgba(138, 124, 250, 0.1); -fx-padding: 8px; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            } else {
                setStyle("-fx-font-weight: normal; -fx-text-fill: #e9edf5; -fx-background-color: transparent; -fx-padding: 8px;");
            }
            
            setText(displayText);
        }
    }
    
    // Getters
    public ObservableList<String> getOnlineUsers() {
        return onlineUsers;
    }
    
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }
} 