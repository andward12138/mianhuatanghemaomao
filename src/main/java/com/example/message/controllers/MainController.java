package com.example.message.controllers;

import com.example.message.core.EventBus;
import com.example.message.core.Router;
import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Optional;

public class MainController {
    
    private Router router;
    private String username;
    private boolean isServerMode = false;
    
    // UI组件
    private VBox sidebar;
    private StackPane mainContent;
    private Label connectionStatusLabel;
    
    // 子控制器
    private DiaryController diaryController;
    private ChatController chatController;
    private LogsController logsController;
    private AnniversaryController anniversaryController;
    
    public MainController() {
        initializeControllers();
        setupEventListeners();
    }
    
    private void initializeControllers() {
        diaryController = new DiaryController();
        chatController = new ChatController();
        logsController = new LogsController();
        anniversaryController = new AnniversaryController();
    }
    
    private void setupEventListeners() {
        // 监听连接状态变化
        EventBus.getInstance().subscribe(EventBus.Events.CONNECTION_STATUS_CHANGED, this::onConnectionStatusChanged);
        
        // 监听页面变化
        EventBus.getInstance().subscribe(EventBus.Events.PAGE_CHANGED, this::onPageChanged);
    }
    
    public void showMainInterface(Stage primaryStage) {
        // 请求用户名
        requestUsername();
        
        // 创建主界面
        BorderPane root = createMainLayout();
        
        // 创建场景并应用样式
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/com/example/message/styles/elegant-theme.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // 默认显示日记页面
        router.navigateTo(Router.Page.DIARY, false);
    }
    
    private BorderPane createMainLayout() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");
        
        // 创建侧边栏
        sidebar = createSidebar();
        
        // 创建主内容区域
        mainContent = new StackPane();
        mainContent.getStyleClass().add("main-content");
        mainContent.setPadding(new Insets(20));
        
        // 创建路由器
        router = new Router(mainContent);
        registerPages();
        
        // 创建菜单栏
        MenuBar menuBar = createMenuBar();
        
        // 创建中心内容区域
        VBox centerContent = new VBox();
        centerContent.getChildren().addAll(menuBar, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        
        // 组装布局
        root.setLeft(sidebar);
        root.setCenter(centerContent);
        
        return root;
    }
    
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(200);
        sidebar.setSpacing(15);
        sidebar.setPadding(new Insets(20));
        
        // 应用标题
        Label appTitle = new Label("💕 我们的小屋");
        appTitle.getStyleClass().add("app-title");
        appTitle.setAlignment(Pos.CENTER);
        appTitle.setMaxWidth(Double.MAX_VALUE);
        
        // 用户信息
        VBox userInfo = createUserInfoSection();
        
        // 导航按钮
        VBox navigation = createNavigationButtons();
        
        // 连接状态
        connectionStatusLabel = new Label("未连接");
        connectionStatusLabel.getStyleClass().add("connection-status");
        connectionStatusLabel.setAlignment(Pos.CENTER);
        connectionStatusLabel.setMaxWidth(Double.MAX_VALUE);
        
        // 弹性空间
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        sidebar.getChildren().addAll(appTitle, userInfo, navigation, spacer, connectionStatusLabel);
        return sidebar;
    }
    
    private VBox createUserInfoSection() {
        VBox userInfo = new VBox(5);
        userInfo.getStyleClass().add("user-info");
        userInfo.setAlignment(Pos.CENTER);
        userInfo.setPadding(new Insets(10));
        
        Label usernameLabel = new Label(username != null ? username : "用户");
        usernameLabel.getStyleClass().add("username-label");
        
        Label statusLabel = new Label("在线");
        statusLabel.getStyleClass().add("status-label");
        
        userInfo.getChildren().addAll(usernameLabel, statusLabel);
        return userInfo;
    }
    
    private VBox createNavigationButtons() {
        VBox navigation = new VBox(10);
        navigation.setAlignment(Pos.CENTER);
        
        Button diaryButton = createNavButton("💝 心情日记", Router.Page.DIARY);
        Button anniversaryButton = createNavButton("💕 纪念日", Router.Page.ANNIVERSARY);
        Button chatButton = createNavButton("💬 消息聊天", Router.Page.CHAT);
        Button logsButton = createNavButton("📖 聊天记录", Router.Page.LOGS);
        
        navigation.getChildren().addAll(diaryButton, anniversaryButton, chatButton, logsButton);
        return navigation;
    }
    
    private Button createNavButton(String text, Router.Page page) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> router.navigateTo(page));
        return button;
    }
    
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        Menu settingsMenu = new Menu("设置");
        
        MenuItem themeItem = new MenuItem("主题设置");
        themeItem.setOnAction(e -> showThemeSettings());
        
        MenuItem apiInfoItem = new MenuItem("服务器信息");
        apiInfoItem.setOnAction(e -> showApiInfo());
        
        settingsMenu.getItems().addAll(themeItem, apiInfoItem);
        menuBar.getMenus().add(settingsMenu);
        
        return menuBar;
    }
    
    private void registerPages() {
        router.registerPage(Router.Page.DIARY, () -> diaryController.createDiaryView());
        router.registerPage(Router.Page.ANNIVERSARY, () -> anniversaryController.createAnniversaryView());
        router.registerPage(Router.Page.CHAT, () -> chatController.createChatView());
        router.registerPage(Router.Page.LOGS, () -> logsController.createLogsView());
    }
    
    private void requestUsername() {
        TextInputDialog dialog = new TextInputDialog("小甜心");
        dialog.setTitle("欢迎来到我们的小屋 💕");
        dialog.setHeaderText("请输入您的昵称");
        dialog.setContentText("昵称:");
        
        // 美化对话框
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("/com/example/message/styles/elegant-theme.css").toExternalForm()
        );
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            username = name;
            ChatService.startServer(username);
        });
        
        if (username == null || username.isEmpty()) {
            username = "小甜心";
            ChatService.startServer(username);
        }
        
        // 选择聊天模式
        chooseChatMode();
    }
    
    private void chooseChatMode() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("选择连接模式");
        alert.setHeaderText("请选择聊天连接模式 💕");
        alert.setContentText("如果你们在同一网络，选择\"直接连接\"。\n如果在不同地方，选择\"服务器连接\"。");
        
        ButtonType directModeButton = new ButtonType("💻 直接连接");
        ButtonType serverModeButton = new ButtonType("🌐 服务器连接");
        
        alert.getButtonTypes().setAll(directModeButton, serverModeButton);
        
        // 美化对话框
        alert.getDialogPane().getStylesheets().add(
            getClass().getResource("/com/example/message/styles/elegant-theme.css").toExternalForm()
        );
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == serverModeButton) {
                isServerMode = true;
                ChatService.setUseServerMode(true);
                connectToServer();
            } else {
                isServerMode = false;
                ChatService.setUseServerMode(false);
                updateConnectionStatus("直接连接模式");
            }
        }
    }
    
    private void connectToServer() {
        Platform.runLater(() -> {
            updateConnectionStatus("连接中...");
            
            new Thread(() -> {
                boolean connected = ChatService.connectToServer(username);
                
                Platform.runLater(() -> {
                    if (connected) {
                        updateConnectionStatus("已连接到服务器 💕");
                        showAlert("连接成功", "已成功连接到聊天服务器！\n现在可以和TA聊天了 💕");
                    } else {
                        updateConnectionStatus("连接失败");
                        showError("连接失败", "无法连接到服务器，将使用直接连接模式");
                        isServerMode = false;
                        ChatService.setUseServerMode(false);
                    }
                });
            }).start();
        });
    }
    
    private void updateConnectionStatus(String status) {
        Platform.runLater(() -> {
            connectionStatusLabel.setText(status);
            EventBus.getInstance().publish(EventBus.Events.CONNECTION_STATUS_CHANGED, status);
        });
    }
    
    public void handleReceivedMessage(ChatMessage message) {
        // 通过事件总线分发消息
        EventBus.getInstance().publish(EventBus.Events.MESSAGE_RECEIVED, message);
    }
    
    private void onConnectionStatusChanged(Object status) {
        // 处理连接状态变化
        System.out.println("连接状态变化: " + status);
    }
    
    private void onPageChanged(Object event) {
        if (event instanceof Router.PageChangeEvent) {
            Router.PageChangeEvent pageEvent = (Router.PageChangeEvent) event;
            System.out.println("页面切换: " + 
                (pageEvent.getFrom() != null ? pageEvent.getFrom().getDisplayName() : "无") + 
                " -> " + pageEvent.getTo().getDisplayName());
        }
    }
    
    private void showThemeSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("主题设置");
        alert.setHeaderText("主题功能即将推出 💕");
        alert.setContentText("敬请期待更多浪漫主题！");
        alert.showAndWait();
    }
    
    private void showApiInfo() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("服务器信息");
        alert.setHeaderText("云存储信息 ☁️");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label urlLabel = new Label("服务器地址: " + com.example.message.services.ApiService.getApiUrl());
        Label statusLabel = new Label("状态: " + 
            (com.example.message.services.ApiService.isApiAvailable() ? "在线 💚" : "离线 💔"));
        Label infoLabel = new Label("所有数据都安全存储在云端，随时随地访问你们的回忆 💕");
        infoLabel.setWrapText(true);
        
        content.getChildren().addAll(urlLabel, statusLabel, infoLabel);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Getters
    public String getUsername() { return username; }
    public boolean isServerMode() { return isServerMode; }
    public Router getRouter() { return router; }
}