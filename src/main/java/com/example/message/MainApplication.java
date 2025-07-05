package com.example.message;

import com.example.message.controller.ChatController;
import com.example.message.controller.DiaryController;
import com.example.message.services.ChatService;
import com.example.message.util.DBUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

/**
 * 主应用程序入口
 */
public class MainApplication extends Application {
    
    private String currentUsername;
    private ChatController chatController;
    private DiaryController diaryController;
    private TabPane mainTabPane;
    private Stage primaryStage;
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        // 设置应用图标
        try {
            Image icon = new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/icon.ico")));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("无法加载应用图标: " + e.getMessage());
        }
        
        // 初始化数据库
        initializeDatabase();
        
        // 请求用户名
        if (!requestUsername()) {
            Platform.exit();
            return;
        }
        
        // 选择聊天模式
        if (!chooseChatMode()) {
            Platform.exit();
            return;
        }
        
        // 初始化控制器
        initializeControllers();
        
        // 创建主界面
        Scene scene = createMainScene();
        
        // 设置窗口 - 显示实例信息以区分多个运行实例
        String instanceId = String.valueOf(System.currentTimeMillis() % 10000);
        String port = "端口:" + ChatService.getCurrentPort();
        primaryStage.setTitle("棉花糖和猫猫的小屋 - " + currentUsername + " (实例:" + instanceId + ", " + port + ")");
        primaryStage.setScene(scene);
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        
        // 设置关闭事件
        primaryStage.setOnCloseRequest(event -> {
            try {
                ChatService.disconnect();
                DBUtil.closeAllConnections();
                Platform.exit();
                System.exit(0);
            } catch (Exception e) {
                System.err.println("应用关闭时出错: " + e.getMessage());
            }
        });
        
        primaryStage.show();
        
        // 启动后的初始化
        Platform.runLater(() -> {
            chatController.refreshOnlineUsers();
            
            // 在控制台显示实例信息
            System.out.println("=== 应用实例信息 ===");
            System.out.println("用户名: " + currentUsername);
            System.out.println("实例ID: " + instanceId);
            System.out.println("监听端口: " + ChatService.getCurrentPort());
            System.out.println("数据库路径: " + DBUtil.getDatabasePath());
            System.out.println("==================");
        });
    }
    
    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            DBUtil.initializeDatabase();
            System.out.println("数据库初始化完成");
        } catch (Exception e) {
            System.err.println("数据库初始化失败: " + e.getMessage());
            showAlert("错误", "数据库初始化失败，应用可能无法正常运行: " + e.getMessage());
        }
    }
    
    /**
     * 请求用户名
     */
    private boolean requestUsername() {
        TextInputDialog dialog = new TextInputDialog("用户");
        dialog.setTitle("欢迎来到棉花糖和猫猫的小屋");
        dialog.setHeaderText("请输入您的用户名");
        dialog.setContentText("用户名:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            currentUsername = result.get().trim();
            return true;
        }
        return false;
    }
    
    /**
     * 选择聊天模式
     */
    private boolean chooseChatMode() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("选择聊天模式");
        alert.setHeaderText("请选择聊天模式");
        alert.setContentText("您希望使用哪种聊天模式？");
        
        ButtonType serverMode = new ButtonType("服务器模式");
        ButtonType directMode = new ButtonType("直连模式");
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(serverMode, directMode, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == serverMode) {
                return connectToServer();
            } else if (result.get() == directMode) {
                return setupDirectConnection();
            }
        }
        return false;
    }
    
    /**
     * 连接到服务器
     */
    private boolean connectToServer() {
        try {
            boolean connected = ChatService.connectToServer(currentUsername);
            if (connected) {
                // 检查用户名是否被修改
                String actualUsername = ChatService.getCurrentUser();
                if (!actualUsername.equals(currentUsername)) {
                    currentUsername = actualUsername;
                    showAlert("提示", "用户名已存在，已自动修改为: " + currentUsername);
                }
                
                showAlert("成功", "已连接到服务器");
                return true;
            } else {
                showAlert("错误", "无法连接到服务器");
                return false;
            }
        } catch (Exception e) {
            showAlert("错误", "连接服务器失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 设置直连模式
     */
    private boolean setupDirectConnection() {
        TextInputDialog dialog = new TextInputDialog("127.0.0.1:9999");
        dialog.setTitle("直连模式");
        dialog.setHeaderText("请输入对方的IP地址和端口");
        dialog.setContentText("格式: IP:端口 (例如: 192.168.1.100:9999)");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String peerAddress = result.get().trim();
            String peerIP = peerAddress;
            
            // 解析IP和端口
            if (peerAddress.contains(":")) {
                String[] parts = peerAddress.split(":");
                peerIP = parts[0];
                // 端口信息将在ChatService中处理
            }
            
            try {
                boolean connected = ChatService.connectDirect(currentUsername, peerIP);
                if (connected) {
                    showAlert("成功", "已连接到 " + peerAddress);
                    return true;
                } else {
                    showAlert("错误", "无法连接到 " + peerAddress + "\n请确保对方已启动服务器并检查端口是否正确");
                    return false;
                }
            } catch (Exception e) {
                showAlert("错误", "连接失败: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * 初始化控制器
     */
    private void initializeControllers() {
        chatController = new ChatController(currentUsername);
        diaryController = new DiaryController();
    }
    
    /**
     * 创建主界面
     */
    private Scene createMainScene() {
        // 创建主容器
        HBox mainContainer = new HBox();
        mainContainer.setStyle("-fx-background-color: #232a38;");
        
        // 创建侧边栏
        VBox sidebar = createSidebar();
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(250);
        sidebar.setMaxWidth(250);
        
        // 创建主内容区域
        StackPane mainContent = new StackPane();
        mainContent.getStyleClass().add("main-content");
        
        // 初始显示聊天界面
        showChatView(mainContent);
        
        mainContainer.getChildren().addAll(sidebar, mainContent);
        HBox.setHgrow(mainContent, Priority.ALWAYS);
        
        Scene scene = new Scene(mainContainer);
        
        // 应用CSS样式
        try {
            scene.getStylesheets().add(
                getClass().getResource("/com/example/message/static/app.css").toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("无法加载CSS样式: " + e.getMessage());
        }
        
        return scene;
    }
    
    /**
     * 创建侧边栏
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setStyle("-fx-background-color: #2f3747; -fx-padding: 20; -fx-spacing: 10;");
        
        // 标题
        Label titleLabel = new Label("棉花糖和猫猫的小屋");
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-padding: 0 0 20 0;");
        
        // 用户信息
        Label userLabel = new Label("用户: " + currentUsername);
        userLabel.setStyle("-fx-text-fill: #a0a8b8; -fx-font-size: 12px; -fx-padding: 0 0 20 0;");
        
        // 聊天按钮
        Button chatButton = new Button("💬 聊天");
        chatButton.getStyleClass().add("sidebar-button");
        chatButton.setMaxWidth(Double.MAX_VALUE);
        chatButton.setOnAction(e -> showChatView((StackPane) ((HBox) chatButton.getScene().getRoot()).getChildren().get(1)));
        
        // 日记按钮
        Button diaryButton = new Button("📝 日记");
        diaryButton.getStyleClass().add("sidebar-button");
        diaryButton.setMaxWidth(Double.MAX_VALUE);
        diaryButton.setOnAction(e -> showDiaryView((StackPane) ((HBox) diaryButton.getScene().getRoot()).getChildren().get(1)));
        
        // 设置按钮
        Button settingsButton = new Button("⚙️ 设置");
        settingsButton.getStyleClass().add("sidebar-button");
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.setOnAction(e -> showSettingsDialog());
        
        // 关于按钮
        Button aboutButton = new Button("ℹ️ 关于");
        aboutButton.getStyleClass().add("sidebar-button");
        aboutButton.setMaxWidth(Double.MAX_VALUE);
        aboutButton.setOnAction(e -> showAbout());
        
        // 分隔符
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        // 退出按钮
        Button exitButton = new Button("🚪 退出");
        exitButton.getStyleClass().add("sidebar-button");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.setStyle("-fx-text-fill: #ff6b8a;");
        exitButton.setOnAction(e -> {
            primaryStage.fireEvent(new javafx.stage.WindowEvent(
                primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        
        sidebar.getChildren().addAll(
            titleLabel, userLabel, chatButton, diaryButton, 
            settingsButton, aboutButton, spacer, exitButton
        );
        
        return sidebar;
    }
    
    /**
     * 显示聊天界面
     */
    private void showChatView(StackPane mainContent) {
        mainContent.getChildren().clear();
        mainContent.getChildren().add(chatController.createChatView());
    }
    
    /**
     * 显示日记界面
     */
    private void showDiaryView(StackPane mainContent) {
        mainContent.getChildren().clear();
        mainContent.getChildren().add(diaryController.createDiaryView());
    }
    
    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("设置");
        alert.setHeaderText("连接设置");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Button reconnectButton = new Button("重新连接");
        reconnectButton.setOnAction(e -> {
            alert.close();
            if (chooseChatMode()) {
                chatController.refreshOnlineUsers();
            }
        });
        
        content.getChildren().add(reconnectButton);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }
    
    /**
     * 创建菜单栏
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        // 文件菜单
        Menu fileMenu = new Menu("文件");
        
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> {
            primaryStage.fireEvent(new javafx.stage.WindowEvent(
                primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        
        fileMenu.getItems().addAll(exitItem);
        
        // 设置菜单
        Menu settingsMenu = new Menu("设置");
        
        MenuItem reconnectItem = new MenuItem("重新连接");
        reconnectItem.setOnAction(e -> {
            if (chooseChatMode()) {
                chatController.refreshOnlineUsers();
            }
        });
        
        MenuItem aboutItem = new MenuItem("关于");
        aboutItem.setOnAction(e -> showAbout());
        
        settingsMenu.getItems().addAll(reconnectItem, new SeparatorMenuItem(), aboutItem);
        
        menuBar.getMenus().addAll(fileMenu, settingsMenu);
        
        return menuBar;
    }
    
    /**
     * 显示关于对话框
     */
    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("棉花糖和猫猫的小屋");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label appLabel = new Label("应用版本: 2.0");
        Label authorLabel = new Label("作者: 棉花糖和猫猫");
        Label descLabel = new Label("一个简单的聊天和日记应用");
        
        content.getChildren().addAll(appLabel, authorLabel, descLabel);
        alert.getDialogPane().setContent(content);
        
        alert.showAndWait();
    }
    
    /**
     * 显示提示对话框
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 应用程序入口点
     */
    public static void main(String[] args) {
        launch(args);
    }
} 