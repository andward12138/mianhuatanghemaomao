package com.example.message.controllers;

import com.example.message.core.CoupleApp;
import com.example.message.core.EventBus;
import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import com.example.message.services.ChatServiceExtensions;
import com.example.message.ui.components.ModernUIComponents;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatController {
    
    private VBox chatMessages;
    private ScrollPane chatScrollPane;
    private TextField messageInput;
    private Button sendButton;
    private ListView<String> onlineUsersListView;
    private Label currentChatPeerLabel;
    private Set<Integer> displayedMessageIds = new HashSet<>();
    
    public ChatController() {
        setupEventListeners();
    }
    
    private void setupEventListeners() {
        EventBus.getInstance().subscribe(EventBus.Events.MESSAGE_RECEIVED, this::onMessageReceived);
        EventBus.getInstance().subscribe(EventBus.Events.USER_ONLINE, this::onUserOnline);
        EventBus.getInstance().subscribe(EventBus.Events.USER_OFFLINE, this::onUserOffline);
    }
    
    private Node cachedChatView = null;
    
    public Node createChatView() {
        // 如果已经创建了聊天视图，直接返回缓存的版本
        if (cachedChatView != null) {
            return cachedChatView;
        }
        
        VBox chatPanel = new VBox();
        chatPanel.getStyleClass().addAll("content-panel", "chat-panel");
        chatPanel.setSpacing(20);
        chatPanel.setPadding(new Insets(20));
        VBox.setVgrow(chatPanel, Priority.ALWAYS);
        
        // 标题区域
        HBox headerBox = createHeaderSection();
        
        // 主要内容区域
        Node mainContent = createMainContent();
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        
        chatPanel.getChildren().addAll(headerBox, mainContent);
        
        // 缓存创建的视图
        cachedChatView = chatPanel;
        
        return chatPanel;
    }
    
    private HBox createHeaderSection() {
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(20);
        
        Label titleLabel = new Label("💬 我们的聊天");
        titleLabel.getStyleClass().add("page-title");
        
        currentChatPeerLabel = new Label("选择一个人开始聊天 💕");
        currentChatPeerLabel.getStyleClass().add("chat-peer-label");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // 连接状态
        Label connectionStatus = new Label("🌐 服务器模式");
        connectionStatus.getStyleClass().add("connection-indicator");
        
        headerBox.getChildren().addAll(titleLabel, currentChatPeerLabel, spacer, connectionStatus);
        return headerBox;
    }
    
    private Node createMainContent() {
        MainController mainController = CoupleApp.getInstance().getMainController();
        
        if (mainController.isServerMode()) {
            return createServerModeLayout();
        } else {
            return createDirectModeLayout();
        }
    }
    
    private Node createServerModeLayout() {
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("chat-split-pane");
        
        // 左侧：在线用户列表
        VBox usersSection = createUsersSection();
        
        // 右侧：聊天区域
        VBox chatSection = createChatSection();
        
        splitPane.getItems().addAll(usersSection, chatSection);
        splitPane.setDividerPositions(0.25);
        SplitPane.setResizableWithParent(usersSection, false);
        
        return splitPane;
    }
    
    private Node createDirectModeLayout() {
        VBox directLayout = new VBox(15);
        
        // 连接控制区域
        HBox connectionBox = createConnectionControls();
        
        // 聊天区域
        VBox chatSection = createChatSection();
        VBox.setVgrow(chatSection, Priority.ALWAYS);
        
        directLayout.getChildren().addAll(connectionBox, chatSection);
        return directLayout;
    }
    
    private VBox createUsersSection() {
        VBox usersSection = new VBox();
        usersSection.getStyleClass().add("users-section");
        usersSection.setSpacing(10);
        usersSection.setPadding(new Insets(15));
        usersSection.setMinWidth(200);
        usersSection.setMaxWidth(250);
        
        Label usersLabel = new Label("💕 在线的TA");
        usersLabel.getStyleClass().add("section-label");
        
        onlineUsersListView = new ListView<>();
        onlineUsersListView.getStyleClass().add("users-list");
        VBox.setVgrow(onlineUsersListView, Priority.ALWAYS);
        
        // 设置用户列表单元格
        onlineUsersListView.setCellFactory(param -> createUserCell());
        
        // 设置选择监听器
        onlineUsersListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null && !newValue.equals(oldValue)) {
                    selectChatPeer(newValue);
                }
            }
        );
        
        // 设置用户列表更新回调
        ChatServiceExtensions.setOnUserListUpdate(users -> {
            Platform.runLater(() -> {
                String currentSelected = onlineUsersListView.getSelectionModel().getSelectedItem();
                onlineUsersListView.getItems().clear();
                onlineUsersListView.getItems().addAll(users);
                
                // 恢复选中项
                if (currentSelected != null && users.contains(currentSelected)) {
                    onlineUsersListView.getSelectionModel().select(currentSelected);
                }
            });
        });
        
        // 加载当前在线用户
        List<String> currentUsers = ChatServiceExtensions.getOnlineUsers();
        if (currentUsers != null) {
            onlineUsersListView.getItems().addAll(currentUsers);
        }
        
        usersSection.getChildren().addAll(usersLabel, onlineUsersListView);
        return usersSection;
    }
    
    private ListCell<String> createUserCell() {
        return new ListCell<String>() {
            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                
                HBox userItem = new HBox();
                userItem.getStyleClass().add("user-item");
                userItem.setSpacing(10);
                userItem.setAlignment(Pos.CENTER_LEFT);
                userItem.setPadding(new Insets(8));
                
                // 用户头像
                Circle avatar = new Circle(20);
                avatar.getStyleClass().add("user-avatar");
                avatar.setFill(Color.web("#FFB6C1"));
                
                Text initial = new Text(user.substring(0, 1).toUpperCase());
                initial.getStyleClass().add("avatar-text");
                StackPane avatarPane = new StackPane(avatar, initial);
                
                // 用户信息
                VBox userInfo = new VBox(2);
                Label userName = new Label(user);
                userName.getStyleClass().add("user-name");
                
                Label statusLabel = new Label("💚 在线");
                statusLabel.getStyleClass().add("user-status");
                
                userInfo.getChildren().addAll(userName, statusLabel);
                HBox.setHgrow(userInfo, Priority.ALWAYS);
                
                // 未读消息指示器
                Circle unreadBadge = new Circle(6, Color.web("#FF69B4"));
                unreadBadge.setVisible(false);
                
                userItem.getChildren().addAll(avatarPane, userInfo, unreadBadge);
                
                // 如果是当前选中用户，添加选中样式
                if (user.equals(ChatServiceExtensions.getCurrentChatPeer())) {
                    userItem.getStyleClass().add("selected-user");
                }
                
                setGraphic(userItem);
                setText(null);
            }
        };
    }
    
    private HBox createConnectionControls() {
        HBox connectionBox = new HBox(10);
        connectionBox.getStyleClass().add("connection-controls");
        connectionBox.setAlignment(Pos.CENTER_LEFT);
        connectionBox.setPadding(new Insets(10));
        
        Label statusLabel = new Label("💻 直接连接模式");
        statusLabel.getStyleClass().add("connection-status");
        
        Button connectButton = new Button("🔗 连接到对方");
        connectButton.getStyleClass().add("connect-button");
        connectButton.setOnAction(e -> showConnectionDialog());
        
        connectionBox.getChildren().addAll(statusLabel, connectButton);
        return connectionBox;
    }
    
    private VBox createChatSection() {
        VBox chatSection = new VBox();
        chatSection.getStyleClass().add("chat-section");
        chatSection.setSpacing(10);
        VBox.setVgrow(chatSection, Priority.ALWAYS);
        
        // 聊天消息区域
        chatScrollPane = new ScrollPane();
        chatScrollPane.getStyleClass().add("chat-scroll-pane");
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setFitToHeight(true);
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        chatMessages = new VBox();
        chatMessages.getStyleClass().add("chat-messages");
        chatMessages.setSpacing(10);
        chatMessages.setPadding(new Insets(15));
        
        chatScrollPane.setContent(chatMessages);
        
        // 消息输入区域
        HBox inputSection = createInputSection();
        
        chatSection.getChildren().addAll(chatScrollPane, inputSection);
        return chatSection;
    }
    
    private HBox createInputSection() {
        HBox inputSection = new HBox();
        inputSection.getStyleClass().add("message-input-section");
        inputSection.setSpacing(10);
        inputSection.setPadding(new Insets(10));
        inputSection.setAlignment(Pos.CENTER);
        
        // 使用现代化UI组件创建消息输入框
        messageInput = new TextField();
        messageInput.getStyleClass().addAll("message-input", "custom-text-field");
        messageInput.setPromptText("输入消息... 💕");
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        
        // 使用现代化UI组件创建发送按钮
        sendButton = ModernUIComponents.createIconButton(
            de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.PAPER_PLANE, 
            "发送消息 (Enter)"
        );
        sendButton.setText("💌 发送");
        sendButton.getStyleClass().addAll("send-button", "mfx-button");
        sendButton.setDisable(true);
        
        // 发送消息事件
        sendButton.setOnAction(e -> sendMessage());
        messageInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !sendButton.isDisable()) {
                sendMessage();
            }
        });
        
        // 根据是否选择了聊天对象来启用/禁用发送按钮
        updateSendButtonState();
        
        inputSection.getChildren().addAll(messageInput, sendButton);
        return inputSection;
    }
    
    private void selectChatPeer(String username) {
        ChatServiceExtensions.setCurrentChatPeer(username);
        currentChatPeerLabel.setText("正在与 " + username + " 聊天 💕");
        
        // 清空当前消息显示
        chatMessages.getChildren().clear();
        displayedMessageIds.clear();
        
        // 加载历史消息
        loadChatHistory(username);
        
        // 启用发送按钮
        updateSendButtonState();
        
        // 刷新用户列表样式
        onlineUsersListView.refresh();
        
        System.out.println("选择聊天对象: " + username);
    }
    
    private void loadChatHistory(String username) {
        new Thread(() -> {
            List<ChatMessage> history = ChatServiceExtensions.getChatHistory(username);
            if (history != null && !history.isEmpty()) {
                Platform.runLater(() -> {
                    for (ChatMessage message : history) {
                        addMessageToChat(message, false);
                    }
                    scrollToBottom();
                });
            }
        }).start();
    }
    
    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (message.isEmpty()) return;
        
        String currentPeer = ChatServiceExtensions.getCurrentChatPeer();
        if (currentPeer == null || currentPeer.isEmpty()) {
            showError("未选择聊天对象", "请先选择一个聊天对象");
            return;
        }
        
        // 防止重复调用
        if (sendButton.isDisabled()) {
            System.out.println("ChatController.sendMessage: 发送按钮已禁用，跳过重复调用");
            return;
        }
        
        System.out.println("ChatController.sendMessage: 开始发送消息 - " + message);
        
        // 禁用发送按钮防止重复发送
        sendButton.setDisable(true);
        
        // 发送消息（ChatService内部会处理立即显示）
        ChatServiceExtensions.sendPrivateMessage(currentPeer, message);
        messageInput.clear();
        
        // 显示发送成功通知
        ModernUIComponents.showSuccessNotification("消息发送成功 💕");
        
        // 发布消息发送事件
        EventBus.getInstance().publish(EventBus.Events.MESSAGE_SENT, message);
        
        // 重新启用发送按钮
        Platform.runLater(() -> {
            sendButton.setDisable(false);
            messageInput.requestFocus();
        });
        
        System.out.println("ChatController.sendMessage: 消息发送完成");
    }
    
    private void addMessageToChat(ChatMessage message, boolean withAnimation) {
        if (message == null || chatMessages == null) {
            System.out.println("addMessageToChat: message或chatMessages为null");
            return;
        }
        
        // 检查是否已显示过
        if (message.getId() > 0 && displayedMessageIds.contains(message.getId())) {
            System.out.println("addMessageToChat: 消息已显示过，ID=" + message.getId());
            return;
        }
        
        // 对于ID为0的消息（临时消息），检查是否有相同内容和时间的消息
        if (message.getId() == 0 && message.getSender() != null && message.getReceiver() != null && message.getContent() != null) {
            String messageKey = message.getSender() + "|" + message.getReceiver() + "|" + message.getContent() + "|" + message.getTimestamp();
            if (displayedMessageIds.contains(messageKey.hashCode())) {
                System.out.println("addMessageToChat: 临时消息已显示过");
                return;
            }
            displayedMessageIds.add(messageKey.hashCode());
        }
        
        // 检查消息是否应该显示在当前聊天窗口
        String currentPeer = ChatService.getCurrentChatPeer();
        String username = CoupleApp.getInstance().getMainController().getUsername();
        System.out.println("addMessageToChat: currentPeer=" + currentPeer + ", username=" + username + 
                          ", sender=" + message.getSender() + ", receiver=" + message.getReceiver());
        
        boolean shouldDisplay = false;
        if (currentPeer != null && message.getSender() != null && message.getReceiver() != null) {
            if ((message.getSender().equals(username) && message.getReceiver().equals(currentPeer)) ||
                (message.getSender().equals(currentPeer) && message.getReceiver().equals(username))) {
                shouldDisplay = true;
            }
        }
        
        if (!shouldDisplay) {
            System.out.println("addMessageToChat: 消息不应该在当前窗口显示");
            return;
        }
        
        System.out.println("addMessageToChat: 准备显示消息到chatMessages，当前子节点数=" + chatMessages.getChildren().size());
        
        // 记录消息ID
        if (message.getId() > 0) {
            displayedMessageIds.add(message.getId());
        }
        
        // 创建消息气泡
        Node messageNode = createMessageBubble(message);
        System.out.println("addMessageToChat: 消息气泡创建完成，准备添加到界面");
        
        if (withAnimation) {
            // 添加动画效果
            messageNode.setOpacity(0);
            messageNode.setScaleX(0.8);
            messageNode.setScaleY(0.8);
            
            chatMessages.getChildren().add(messageNode);
            
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), messageNode);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), messageNode);
            scaleIn.setFromX(0.8);
            scaleIn.setFromY(0.8);
            scaleIn.setToX(1.0);
            scaleIn.setToY(1.0);
            
            fadeIn.play();
            scaleIn.play();
        } else {
            chatMessages.getChildren().add(messageNode);
        }
        
        // 强制刷新UI布局和重绘
        forceRefreshChatUI();
        
        System.out.println("addMessageToChat: 消息已添加到界面，新的子节点数=" + chatMessages.getChildren().size());
        
        // 确保滚动到底部
        scrollToBottom();
    }
    
    private Node createMessageBubble(ChatMessage message) {
        // 验证消息有效性
        if (message == null || message.getSender() == null || message.getContent() == null) {
            System.out.println("createMessageBubble: 无效的消息对象");
            return new Label("无效消息");
        }
        
        String username = CoupleApp.getInstance().getMainController().getUsername();
        boolean isOutgoing = message.getSender().equals(username);
        
        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");
        messageBox.setSpacing(12);
        messageBox.setPadding(new Insets(8));
        
        if (isOutgoing) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getStyleClass().add("outgoing-message");
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
            messageBox.getStyleClass().add("incoming-message");
        }
        
        // 头像（现代化设计）
        Circle avatar = new Circle(20);
        avatar.getStyleClass().add("message-avatar");
        if (isOutgoing) {
            avatar.setFill(Color.web("#4a90e2"));
        } else {
            avatar.setFill(Color.web("#38b2ac"));
        }
        
        Text initial = new Text(message.getSender().substring(0, 1).toUpperCase());
        initial.getStyleClass().add("avatar-text-small");
        StackPane avatarPane = new StackPane(avatar, initial);
        
        // 消息内容区域
        VBox messageContent = new VBox();
        messageContent.setSpacing(4);
        messageContent.setMaxWidth(320);
        
        // 发送者名称和时间（在一行显示）
        HBox metaBox = new HBox();
        metaBox.setSpacing(8);
        metaBox.setAlignment(Pos.CENTER_LEFT);
        
        if (!isOutgoing) {
            Label senderLabel = new Label(message.getSender());
            senderLabel.getStyleClass().add("sender-name");
            metaBox.getChildren().add(senderLabel);
        }
        
        Label timeLabel = new Label(message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.getStyleClass().add("message-time");
        
        if (isOutgoing) {
            metaBox.setAlignment(Pos.CENTER_RIGHT);
        }
        metaBox.getChildren().add(timeLabel);
        
        // 消息气泡
        VBox bubble = new VBox();
        bubble.getStyleClass().add("message-bubble");
        if (isOutgoing) {
            bubble.getStyleClass().add("my-bubble");
        } else {
            bubble.getStyleClass().add("their-bubble");
        }
        bubble.setSpacing(2);
        
        // 消息内容
        Label contentLabel = new Label(message.getContent());
        contentLabel.getStyleClass().add("message-content");
        contentLabel.setWrapText(true);
        
        bubble.getChildren().add(contentLabel);
        
        // 组装消息内容
        if (isOutgoing) {
            messageContent.setAlignment(Pos.CENTER_RIGHT);
            messageContent.getChildren().addAll(metaBox, bubble);
            messageBox.getChildren().addAll(messageContent, avatarPane);
        } else {
            messageContent.setAlignment(Pos.CENTER_LEFT);
            messageContent.getChildren().addAll(metaBox, bubble);
            messageBox.getChildren().addAll(avatarPane, messageContent);
        }
        
        return messageBox;
    }
    
    private void forceRefreshChatUI() {
        // 强制刷新聊天界面的布局和显示
        if (chatMessages != null) {
            chatMessages.requestLayout();
            chatMessages.autosize();
        }
        if (chatScrollPane != null) {
            chatScrollPane.requestLayout();
            chatScrollPane.autosize();
        }
        
        // 触发场景重绘
        Platform.runLater(() -> {
            if (chatMessages != null && chatMessages.getScene() != null) {
                chatMessages.getScene().getWindow().requestFocus();
            }
        });
    }
    
    private void scrollToBottom() {
        // 使用双重Platform.runLater确保在布局计算完成后滚动
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                if (chatScrollPane != null) {
                    chatScrollPane.setVvalue(1.0);
                }
            });
        });
    }
    
    private void updateSendButtonState() {
        String currentPeer = ChatService.getCurrentChatPeer();
        if (sendButton != null) {
            sendButton.setDisable(currentPeer == null || currentPeer.isEmpty());
        }
    }
    
    private void showConnectionDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("连接到对方");
        dialog.setHeaderText("输入对方的IP地址");
        dialog.setContentText("IP地址:");
        
        dialog.showAndWait().ifPresent(ip -> {
            // 实现直接连接逻辑
            System.out.println("尝试连接到: " + ip);
        });
    }
    
    private void onMessageReceived(Object messageObj) {
        if (messageObj instanceof ChatMessage) {
            ChatMessage message = (ChatMessage) messageObj;
            System.out.println("ChatController收到消息: " + message.getSender() + " -> " + message.getReceiver() + ": " + message.getContent());
            
            // 检查是否已经在JavaFX应用线程中
            if (Platform.isFxApplicationThread()) {
                addMessageToChat(message, true);
                // 触发窗口闪烁提示（如果不是当前活动窗口）
                notifyNewMessage();
            } else {
                Platform.runLater(() -> {
                    addMessageToChat(message, true);
                    notifyNewMessage();
                });
            }
        }
    }
    
    private void notifyNewMessage() {
        // 简单的视觉提示：让聊天框稍微闪烁一下
        if (chatMessages != null && chatMessages.getScene() != null) {
            chatMessages.getScene().getWindow().requestFocus();
        }
    }
    
    private void onUserOnline(Object user) {
        System.out.println("用户上线: " + user);
    }
    
    private void onUserOffline(Object user) {
        System.out.println("用户下线: " + user);
    }
    
    private void showError(String title, String message) {
        // 使用现代化通知替代传统弹窗
        ModernUIComponents.showErrorNotification(message);
        
        // 保留原有的弹窗作为备选
        // Alert alert = new Alert(Alert.AlertType.ERROR);
        // alert.setTitle(title);
        // alert.setHeaderText(null);
        // alert.setContentText(message);
        // alert.showAndWait();
    }
}