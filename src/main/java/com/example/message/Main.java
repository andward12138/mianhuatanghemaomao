package com.example.message;

import com.example.message.model.ChatMessage;
import com.example.message.model.Diary;
import com.example.message.services.ChatService;
import com.example.message.services.DiaryService;
import com.example.message.util.DBUtil;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.scene.Node;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {

    private boolean isSidebarExpanded = true;  // 控制侧边栏的展开与收缩
    private String username; // 当前用户名
    private VBox chatMessagesBox; // 聊天消息显示区域
    private TextField messageField; // 消息输入框
    private TextField peerIpField; // 对方IP输入框
    private Button connectButton; // 连接按钮
    private Label connectionStatusLabel; // 连接状态标签
    private ScrollPane chatScrollPane; // 聊天滚动面板
    private String currentPeer; // 当前连接的对方IP
    private ListView<String> onlineUsersListView; // 在线用户列表
    private boolean isServerMode = false; // 是否使用服务器模式
    private ObservableList<String> onlineUsers = FXCollections.observableArrayList(); // 在线用户列表数据
    private StackPane mainContent; // 主内容区
    private VBox chatMessages; // 聊天消息容器
    private ChatService chatClient; // 聊天客户端服务
    private ChatService chatServer; // 聊天服务器
    private String connectionStatus = "未连接"; // 连接状态
    private int currentDiaryId = -1; // 当前编辑的日记ID
    private TextArea diaryContentArea; // 日记内容文本区域
    private ComboBox<String> moodSelector; // 心情选择器
    private Map<String, Runnable> messageCallbacks = new HashMap<>();
    private Timeline chatRefreshTimer;
    private Set<Integer> displayedMessageIds = new HashSet<>();

    @Override
    public void start(Stage primaryStage) {
        // 初始化数据库
        DBUtil.initializeDatabase();
        
        // 显示数据库位置信息，方便排查问题
        String dbPath = DBUtil.getDatabasePath();
        System.out.println("应用程序使用的数据库路径: " + dbPath);
        boolean isConnected = DBUtil.testConnection();
        System.out.println("数据库连接状态: " + (isConnected ? "成功" : "失败"));
        
        // 在应用启动时弹出数据库路径提示
        if (!":memory:".equals(dbPath)) {
            Alert dbInfoAlert = new Alert(Alert.AlertType.INFORMATION);
            dbInfoAlert.setTitle("数据库信息");
            dbInfoAlert.setHeaderText("应用数据存储位置");
            
            VBox content = new VBox(5);
            content.setPadding(new Insets(10));
            
            Label pathLabel = new Label("您的聊天数据存储在:");
            Label locationLabel = new Label(dbPath);
            locationLabel.setStyle("-fx-font-weight: bold;");
            
            Label statusLabel = new Label("数据库连接状态: " + (isConnected ? "正常" : "异常"));
            statusLabel.setStyle("-fx-text-fill: " + (isConnected ? "green" : "red") + "; -fx-font-weight: bold;");
            
            Label infoLabel = new Label("如需备份，请复制此文件或整个data目录。");
            infoLabel.setWrapText(true);
            
            content.getChildren().addAll(pathLabel, locationLabel, statusLabel, infoLabel);
            
            dbInfoAlert.getDialogPane().setContent(content);
            dbInfoAlert.show();
        }
        
        // 初始化API服务
        com.example.message.services.ApiService.initialize();
        
        // 请求用户输入用户名
        requestUsername();
        
        // 设置消息接收回调
        ChatService.setMessageReceivedCallback(this::handleReceivedMessage);
        
        // 设置日记服务使用云存储
        DiaryService.setUseCloudStorage(true);
        
        // 主窗口设置
        primaryStage.setTitle("棉花糖和猫猫的小屋");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        
        // 创建主布局（使用BorderPane更适合响应式布局）
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");
        
        // 创建菜单栏
        MenuBar menuBar = new MenuBar();
        
        // 创建设置菜单
        Menu settingsMenu = new Menu("设置");
        
        // 添加数据库信息菜单项
        addDatabaseInfoMenuItem(settingsMenu);
        
        // 添加菜单到菜单栏
        menuBar.getMenus().add(settingsMenu);
        
        // 创建侧边栏
        VBox sidebar = createSidebar();
        
        // 创建主内容区域
        mainContent = new StackPane();
        mainContent.getStyleClass().add("main-content");
        mainContent.setPadding(new Insets(15));
        
        // 创建垂直布局以容纳菜单栏和主内容
        VBox centerContent = new VBox();
        centerContent.getChildren().addAll(menuBar, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        
        // 添加侧边栏和主内容区到根布局
        root.setLeft(sidebar);
        root.setCenter(centerContent);
        
        // 设置初始内容为心情日记界面
        openDiary();
        
        // 设置新消息通知回调
        ChatService.setNewMessageNotificationCallback(sender -> {
            System.out.println("收到来自 " + sender + " 的新消息通知");
            // 查找并触发该用户的未读消息回调
            if (messageCallbacks.containsKey(sender)) {
                messageCallbacks.get(sender).run();
            }
        });
        
        // 创建场景
        Scene scene = new Scene(root, 1200, 800);
        
        // 加载CSS样式
        scene.getStylesheets().add(getClass().getResource("/com/example/message/static/app.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // 在应用关闭时停止聊天服务
        primaryStage.setOnCloseRequest(e -> {
            ChatService.stopServer();
        });
    }
    
    // 处理接收到的消息
    private void handleReceivedMessage(ChatMessage message) {
        System.out.println("接收到消息回调: ID=" + message.getId() + 
                          ", 发送者=" + message.getSender() + 
                          ", 接收者=" + message.getReceiver() + 
                          ", 时间=" + message.getTimestamp());
        
        // 先判断消息ID是否处理过，避免重复显示
        if (message.getId() > 0 && displayedMessageIds.contains(message.getId())) {
            System.out.println("消息已显示过，跳过处理: ID=" + message.getId());
            return;
        }
        
        // 如果消息ID大于0，记录为已处理
        if (message.getId() > 0) {
            displayedMessageIds.add(message.getId());
            System.out.println("记录消息ID为已处理: " + message.getId());
        }
        
        // 直接将消息转发到addMessageToChat处理
        // addMessageToChat中会进行详细的过滤和处理逻辑
        Platform.runLater(() -> addMessageToChat(message));
    }
    
    // 请求用户输入用户名
    private void requestUsername() {
        TextInputDialog dialog = new TextInputDialog("用户");
        dialog.setTitle("用户名");
        dialog.setHeaderText("请输入您的用户名");
        dialog.setContentText("用户名:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            username = name;
            // 启动聊天服务器
            ChatService.startServer(username);
        });
        
        // 如果用户没有输入用户名，使用默认值
        if (username == null || username.isEmpty()) {
            username = "用户";
            ChatService.startServer(username);
        }
        
        // 选择聊天模式
        chooseChatMode();
    }
    
    // 选择聊天模式
    private void chooseChatMode() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("选择连接模式");
        alert.setHeaderText("请选择聊天连接模式");
        alert.setContentText("如果您和聊天对象在同一局域网内，请选择\"直接连接\"模式。\n如果您和聊天对象在不同网络环境，请选择\"服务器连接\"模式。");
        
        ButtonType directModeButton = new ButtonType("直接连接");
        ButtonType serverModeButton = new ButtonType("服务器连接");
        
        alert.getButtonTypes().setAll(directModeButton, serverModeButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == serverModeButton) {
                System.out.println("用户选择了服务器连接模式");
                
                // 显示连接中对话框
                Alert connectingAlert = new Alert(Alert.AlertType.INFORMATION);
                connectingAlert.setTitle("连接中");
                connectingAlert.setHeaderText("正在连接到聊天服务器...");
                connectingAlert.setContentText("请稍候，正在尝试连接到聊天服务器。");
                
                // 不阻塞UI线程
                Platform.runLater(() -> {
                    // 设置服务器模式
                    isServerMode = true;
                    ChatService.setUseServerMode(true);
                    
                    // 连接到服务器
                    boolean connected = ChatService.connectToServer(username);
                    
                    // 关闭连接中对话框
                    connectingAlert.close();
                    
                    if (connected) {
                        // 连接成功
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("连接成功");
                        successAlert.setHeaderText("已成功连接到聊天服务器");
                        successAlert.setContentText("您现在可以与其他在线用户聊天了。");
                        successAlert.showAndWait();
                        
                        // 刷新用户列表
                        refreshOnlineUsers();
                    } else {
                        // 连接失败
                        Alert failureAlert = new Alert(Alert.AlertType.ERROR);
                        failureAlert.setTitle("连接失败");
                        failureAlert.setHeaderText("无法连接到聊天服务器");
                        failureAlert.setContentText("连接服务器失败，将使用直接连接模式。\n请检查网络连接或稍后再试。");
                        failureAlert.showAndWait();
                        
                        // 回退到直接连接模式
                        isServerMode = false;
                        ChatService.setUseServerMode(false);
                    }
                });
                
                // 显示连接中对话框，但不阻塞后续代码执行
                new Thread(() -> {
                    try {
                        Platform.runLater(() -> connectingAlert.show());
                        // 等待一段时间后自动关闭（以防连接过程中出现问题）
                        Thread.sleep(10000);
                        Platform.runLater(() -> {
                            if (connectingAlert.isShowing()) {
                                connectingAlert.close();
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } else {
                System.out.println("用户选择了直接连接模式");
                isServerMode = false;
                ChatService.setUseServerMode(false);
            }
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(180);
        sidebar.setMinWidth(80);
        sidebar.setSpacing(20);
        sidebar.setPadding(new Insets(30, 15, 15, 15));
        
        // 添加应用标题
        Label appTitle = new Label("心情通信");
        appTitle.getStyleClass().add("title-label");
        appTitle.setAlignment(Pos.CENTER);
        appTitle.setMaxWidth(Double.MAX_VALUE);
        
        // 创建侧边栏按钮
        Button diaryButton = new Button("心情日记");
        diaryButton.getStyleClass().add("sidebar-button");
        diaryButton.setMaxWidth(Double.MAX_VALUE);
        diaryButton.setOnAction(e -> openDiary());
        
        Button chatButton = new Button("消息聊天");
        chatButton.getStyleClass().add("sidebar-button");
        chatButton.setMaxWidth(Double.MAX_VALUE);
        chatButton.setOnAction(e -> openChat());
        
        Button logsButton = new Button("聊天记录");
        logsButton.getStyleClass().add("sidebar-button");
        logsButton.setMaxWidth(Double.MAX_VALUE);
        logsButton.setOnAction(e -> openLogs());
        
        // 创建收缩/展开按钮
        ToggleButton toggleSidebar = new ToggleButton("收起");
        toggleSidebar.getStyleClass().add("sidebar-button");
        toggleSidebar.setMaxWidth(Double.MAX_VALUE);
        
        toggleSidebar.setOnAction(e -> {
            if (toggleSidebar.isSelected()) {
                toggleSidebar.setText("展开");
                sidebar.setPrefWidth(80);
                diaryButton.setText("");
                chatButton.setText("");
                logsButton.setText("");
                // 添加图标样式
                diaryButton.getStyleClass().add("icon-only");
                chatButton.getStyleClass().add("icon-only");
                logsButton.getStyleClass().add("icon-only");
                appTitle.setVisible(false);
            } else {
                toggleSidebar.setText("收起");
                sidebar.setPrefWidth(180);
                diaryButton.setText("心情日记");
                chatButton.setText("消息聊天");
                logsButton.setText("聊天记录");
                // 移除图标样式
                diaryButton.getStyleClass().remove("icon-only");
                chatButton.getStyleClass().remove("icon-only");
                logsButton.getStyleClass().remove("icon-only");
                appTitle.setVisible(true);
            }
        });
        
        Region spacer = new Region();
        spacer.getStyleClass().add("grow-y");
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        sidebar.getChildren().addAll(appTitle, diaryButton, chatButton, logsButton, spacer, toggleSidebar);
        return sidebar;
    }

    private void openDiary() {
        mainContent.getChildren().clear();
        
        VBox diaryPanel = new VBox();
        diaryPanel.getStyleClass().addAll("content-box", "responsive-container");
        diaryPanel.setSpacing(15);
        diaryPanel.setPadding(new Insets(15));
        VBox.setVgrow(diaryPanel, Priority.ALWAYS);
        HBox.setHgrow(diaryPanel, Priority.ALWAYS);
        
        Label titleLabel = new Label("心情日记");
        titleLabel.getStyleClass().add("title-label");
        
        HBox infoBox = new HBox();
        infoBox.getStyleClass().add("info-box");
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setPadding(new Insets(5, 10, 5, 10));
        
        HBox cloudButtons = new HBox();
        cloudButtons.setSpacing(10);
        cloudButtons.setAlignment(Pos.CENTER_RIGHT);
        
        // 创建一个分割面板，左侧是日记列表，右侧是日记编辑区
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("responsive-container");
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        
        // 创建左侧的日记列表
        VBox listSection = new VBox();
        listSection.setSpacing(10);
        listSection.getStyleClass().add("content-box");
        listSection.setPadding(new Insets(10));
        
        // 搜索区域
        HBox searchBox = new HBox();
        searchBox.setSpacing(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        TextField searchField = new TextField();
        searchField.setPromptText("搜索关键词");
        searchField.setPrefWidth(150);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("开始日期");
        startDate.setPrefWidth(130);
        
        DatePicker endDate = new DatePicker();
        endDate.setPromptText("结束日期");
        endDate.setPrefWidth(130);
        
        // 日记列表
        ListView<Diary> diaryListView = new ListView<>();
        VBox.setVgrow(diaryListView, Priority.ALWAYS);
        
        Button searchButton = new Button("搜索");
        searchButton.setOnAction(e -> {
            String keyword = searchField.getText();
            LocalDate start = startDate.getValue();
            LocalDate end = endDate.getValue();
            loadFilteredDiaries(diaryListView, keyword, start, end);
        });
        
        searchBox.getChildren().addAll(searchField, startDate, endDate, searchButton);
        
        if (DiaryService.isCloudStorageEnabled()) {
            Label infoLabel = new Label("日记已启用云同步，所有人都能看到你的心情");
            infoBox.getChildren().add(infoLabel);
            
            Button refreshButton = new Button("从云端刷新");
            refreshButton.getStyleClass().add("sync-button");
            refreshButton.setOnAction(e -> {
                DiaryService.refreshFromCloud();
                loadDiaries(diaryListView);
                showAlert("刷新成功", "已从云端获取最新日记");
            });
            
            cloudButtons.getChildren().add(refreshButton);
        }
        
        HBox headerBox = new HBox();
        headerBox.setSpacing(15);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.getChildren().addAll(titleLabel, infoBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        headerBox.getChildren().add(cloudButtons);
        
        diaryListView.setCellFactory(param -> new ListCell<Diary>() {
            @Override
            protected void updateItem(Diary diary, boolean empty) {
                super.updateItem(diary, empty);
                if (empty || diary == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                
                VBox diaryItem = new VBox();
                diaryItem.getStyleClass().add("diary-item");
                diaryItem.setSpacing(5);
                
                // 确保使用日记的原始时间，而不是当前时间
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
                String formattedDate = diary.getDate().format(formatter);
                Label dateLabel = new Label(formattedDate);
                dateLabel.getStyleClass().add("diary-date");
                
                Label moodLabel = new Label("#" + diary.getMood());
                moodLabel.getStyleClass().add("diary-tags");
                
                HBox metaInfo = new HBox(10);
                metaInfo.getChildren().addAll(dateLabel, moodLabel);
                
                Label contentLabel = new Label(diary.getContent());
                contentLabel.setWrapText(true);
                contentLabel.setMaxWidth(Double.MAX_VALUE);
                contentLabel.getStyleClass().add("diary-content");
                
                HBox buttonsBox = new HBox();
                buttonsBox.setSpacing(10);
                buttonsBox.setAlignment(Pos.CENTER_RIGHT);
                
                Button deleteButton = new Button("删除");
                deleteButton.getStyleClass().add("delete-button");
                deleteButton.setOnAction(event -> {
                    try {
                        DiaryService.deleteDiary(diary.getId());
                        loadDiaries(diaryListView);
                        showAlert("删除成功", "日记已删除");
                    } catch (Exception ex) {
                        showError("删除失败", ex.getMessage());
                    }
                });
                
                Button editButton = new Button("编辑");
                editButton.getStyleClass().add("edit-button");
                editButton.setOnAction(event -> {
                    diaryContentArea.setText(diary.getContent());
                    moodSelector.setValue(diary.getMood());
                    currentDiaryId = diary.getId();
                });
                
                buttonsBox.getChildren().addAll(editButton, deleteButton);
                
                diaryItem.getChildren().addAll(metaInfo, contentLabel, buttonsBox);
                setGraphic(diaryItem);
                setText(null);
            }
        });
        
        listSection.getChildren().addAll(searchBox, diaryListView);
        
        // 创建右侧的日记编辑区
        VBox editSection = new VBox();
        editSection.setSpacing(15);
        editSection.getStyleClass().add("content-box");
        editSection.setPadding(new Insets(15));
        
        Label contentLabel = new Label("今天的心情：");
        
        diaryContentArea = new TextArea();
        diaryContentArea.setPromptText("写下你今天的心情...");
        diaryContentArea.setPrefRowCount(10);
        VBox.setVgrow(diaryContentArea, Priority.ALWAYS);
        
        HBox moodBox = new HBox();
        moodBox.setAlignment(Pos.CENTER_LEFT);
        moodBox.setSpacing(10);
        
        Label moodLabel = new Label("选择心情：");
        moodSelector = new ComboBox<>();
        moodSelector.getItems().addAll("开心", "悲伤", "愤怒", "平静", "焦虑", "兴奋", "困倦");
        moodSelector.setValue("开心");
        
        moodBox.getChildren().addAll(moodLabel, moodSelector);
        
        Button saveButton = new Button("保存日记");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(e -> {
            try {
                String content = diaryContentArea.getText();
                String mood = moodSelector.getValue();
                
                if (content.isEmpty()) {
                    showError("内容为空", "请输入日记内容");
                    return;
                }
                
                saveButton.setDisable(true); // 禁用按钮防止重复提交
                
                int result;
                if (currentDiaryId != -1) {
                    // 更新现有日记
                    boolean success = DiaryService.updateDiary(currentDiaryId, content, mood);
                    if (success) {
                        showAlert("更新成功", "日记已更新到本地数据库，并尝试同步到云端");
                        result = currentDiaryId;
                    } else {
                        showError("更新失败", "无法更新日记，请检查日志了解详情");
                        saveButton.setDisable(false);
                        return;
                    }
                } else {
                    // 添加新日记
                    result = DiaryService.addDiary(content, mood);
                    if (result > 0) {
                        showAlert("保存成功", "日记已保存到本地数据库，并尝试同步到云端");
                    } else {
                        showError("保存失败", "无法保存日记，请检查日志了解详情");
                        saveButton.setDisable(false);
                        return;
                    }
                }
                
                // 重置表单
                diaryContentArea.clear();
                moodSelector.setValue("开心");
                currentDiaryId = -1;
                
                // 刷新日记列表
                loadDiaries(diaryListView);
                
                // 重新启用保存按钮
                saveButton.setDisable(false);
            } catch (Exception ex) {
                showError("操作失败", "发生错误: " + ex.getMessage());
                ex.printStackTrace();
                saveButton.setDisable(false);
            }
        });
        
        editSection.getChildren().addAll(contentLabel, diaryContentArea, moodBox, saveButton);
        
        // 将左右两部分添加到分割面板
        splitPane.getItems().addAll(listSection, editSection);
        splitPane.setDividerPositions(0.4);
        
        // 添加所有元素到日记面板
        diaryPanel.getChildren().addAll(headerBox, splitPane);
        
        // 将日记面板添加到主内容区
        mainContent.getChildren().add(diaryPanel);
        
        // 加载日记
        loadDiaries(diaryListView);
    }

    private void openChat() {
        mainContent.getChildren().clear();
        
        // 创建并启动聊天刷新定时器
        if (chatRefreshTimer != null) {
            chatRefreshTimer.stop();
        }
        
        chatRefreshTimer = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> {  // 1秒刷新一次
                String currentPeer = ChatService.getCurrentChatPeer();
                if (currentPeer != null && !currentPeer.isEmpty()) {
                    // 静默刷新与当前用户的聊天历史
                    refreshChatWithUser(currentPeer, false);
                }
            })
        );
        chatRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        chatRefreshTimer.play();
        
        VBox chatPanel = new VBox();
        chatPanel.getStyleClass().addAll("content-box", "responsive-container");
        chatPanel.setSpacing(15);
        chatPanel.setPadding(new Insets(15));
        VBox.setVgrow(chatPanel, Priority.ALWAYS);
        HBox.setHgrow(chatPanel, Priority.ALWAYS);
        
        // 当聊天窗口关闭时停止刷新定时器
        chatPanel.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                if (chatRefreshTimer != null) {
                    chatRefreshTimer.stop();
                }
            }
        });
        
        Label titleLabel = new Label("消息聊天");
        titleLabel.getStyleClass().add("title-label");
        
        HBox topBox = new HBox();
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setSpacing(15);
        
        HBox connectionBox = new HBox();
        connectionBox.setAlignment(Pos.CENTER_RIGHT);
        connectionBox.setSpacing(10);
        HBox.setHgrow(connectionBox, Priority.ALWAYS);
        
        // 当前聊天对象显示
        Label currentChatPeerLabel = new Label("当前未选择聊天对象");
        currentChatPeerLabel.getStyleClass().add("info-box");
        currentChatPeerLabel.setPadding(new Insets(5, 10, 5, 10));
        topBox.getChildren().addAll(titleLabel, currentChatPeerLabel);
        
        if (isServerMode) {
            Label serverStatusLabel = new Label("服务器模式");
            serverStatusLabel.getStyleClass().add("server-status");
            serverStatusLabel.setPadding(new Insets(5, 10, 5, 10));
            topBox.getChildren().add(serverStatusLabel);
            
            // 创建左右分割的布局
            SplitPane chatSplitPane = new SplitPane();
            VBox.setVgrow(chatSplitPane, Priority.ALWAYS);
            
            // 在线用户显示区域（左侧）
            VBox usersBox = new VBox();
            usersBox.getStyleClass().add("content-box");
            usersBox.setSpacing(10);
            usersBox.setPadding(new Insets(10));
            usersBox.setMinWidth(200);
            usersBox.setMaxWidth(250);
            
            Label usersLabel = new Label("在线用户");
            usersLabel.getStyleClass().add("section-label");
            
            onlineUsersListView = new ListView<>();
            VBox.setVgrow(onlineUsersListView, Priority.ALWAYS);
            
            // 设置在线用户列表单元格工厂，支持消息提醒和状态显示
            onlineUsersListView.setCellFactory(param -> new ListCell<String>() {
                @Override
                protected void updateItem(String user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    
                    HBox userItem = new HBox();
                    userItem.setSpacing(10);
                    userItem.setAlignment(Pos.CENTER_LEFT);
                    
                    // 用户头像（简单圆形）
                    Circle userAvatar = new Circle(15);
                    userAvatar.setFill(Color.LIGHTBLUE);
                    Text userInitial = new Text(user.substring(0, 1).toUpperCase());
                    StackPane avatarPane = new StackPane(userAvatar, userInitial);
                    
                    // 用户名称和状态区域
                    VBox userInfo = new VBox();
                    userInfo.setSpacing(3);
                    Label userName = new Label(user);
                    userName.getStyleClass().add("user-name");
                    
                    userInfo.getChildren().add(userName);
                    
                    // 未读消息标记
                    Circle unreadBadge = new Circle(8, Color.RED);
                    unreadBadge.setVisible(false); // 默认不可见
                    
                    userItem.getChildren().addAll(avatarPane, userInfo);
                    HBox.setHgrow(userInfo, Priority.ALWAYS);
                    userItem.getChildren().add(unreadBadge);
                    
                    // 如果是当前选中用户，设置样式
                    if (user.equals(ChatService.getCurrentChatPeer())) {
                        getStyleClass().add("selected-user");
                        unreadBadge.setVisible(false);
                    }
                    
                    // 设置处理新消息的样式
                    setOnNewMessage(user, () -> {
                        // 如果不是当前选中的用户，显示未读标记
                        if (!user.equals(ChatService.getCurrentChatPeer())) {
                            Platform.runLater(() -> {
                                unreadBadge.setVisible(true);
                            });
                        }
                    });
                    
                    setGraphic(userItem);
                    setText(null);
                }
            });
            
            // 先更新现有的用户列表
            List<String> currentUsers = ChatService.getOnlineUsers();
            if (currentUsers != null && !currentUsers.isEmpty()) {
                Platform.runLater(() -> {
                    onlineUsersListView.getItems().clear();
                    onlineUsersListView.getItems().addAll(currentUsers);
                });
            }
            
            // 设置用户列表更新回调
            ChatService.setOnUserListUpdate(users -> {
                if (users == null || users.isEmpty()) {
                    return;
                }
                
                Platform.runLater(() -> {
                    try {
                        // 保存当前选中项
                        String currentSelectedUser = onlineUsersListView.getSelectionModel().getSelectedItem();
                        
                        onlineUsersListView.getItems().clear();
                        onlineUsersListView.getItems().addAll(users);
                        
                        // 恢复选中项
                        if (currentSelectedUser != null) {
                            for (int i = 0; i < onlineUsersListView.getItems().size(); i++) {
                                if (onlineUsersListView.getItems().get(i).equals(currentSelectedUser)) {
                                    onlineUsersListView.getSelectionModel().select(i);
                                    break;
                                }
                            }
                        }
                        
                        System.out.println("已更新UI中的用户列表，用户数: " + users.size());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
            
            // 设置用户列表点击事件
            onlineUsersListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && !newValue.equals(oldValue)) {
                        System.out.println("已选择聊天用户: " + newValue);
                        
                        // 设置当前聊天对象
                        ChatService.setCurrentChatPeer(newValue);
                        
                        // 更新当前聊天对象标签
                        Platform.runLater(() -> {
                            currentChatPeerLabel.setText("正在与 " + newValue + " 聊天");
                        });
                        
                        // 清空当前消息显示和已显示消息ID集合
                        chatMessages.getChildren().clear();
                        displayedMessageIds.clear();
                        
                        // 加载并显示与该用户的历史消息
                        showChatWithUser(newValue);
                        
                        // 清除未读标记
                        clearUnreadBadge(newValue);
                        
                        // 刷新ListView使样式生效
                        onlineUsersListView.refresh();
                    }
                }
            );
            
            usersBox.getChildren().addAll(usersLabel, onlineUsersListView);
            
            // 创建消息显示区域（右侧）
            VBox messagesBox = new VBox();
            messagesBox.getStyleClass().add("content-box");
            messagesBox.setSpacing(10);
            VBox.setVgrow(messagesBox, Priority.ALWAYS);
            
            // 消息展示区域
            chatScrollPane = new ScrollPane();
            chatScrollPane.setFitToWidth(true);
            chatScrollPane.setFitToHeight(true);
            VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
            
            VBox messageContainer = new VBox();
            messageContainer.setSpacing(10);
            messageContainer.setPadding(new Insets(10));
            VBox.setVgrow(messageContainer, Priority.ALWAYS);
            chatMessages = messageContainer;
            
            chatScrollPane.setContent(messageContainer);
            
            // 创建发送消息区域
            HBox sendMessageBox = new HBox();
            sendMessageBox.setSpacing(10);
            sendMessageBox.setAlignment(Pos.CENTER);
            
            TextField messageInput = new TextField();
            messageInput.setPromptText("输入消息...");
            HBox.setHgrow(messageInput, Priority.ALWAYS);
            
            Button sendButton = new Button("发送");
            sendButton.getStyleClass().add("send-button");
            sendButton.setDisable(true); // 初始禁用，直到选择聊天对象
            
            sendButton.setOnAction(e -> {
                String message = messageInput.getText().trim();
                if (!message.isEmpty()) {
                    String currentPeer = ChatService.getCurrentChatPeer();
                    if (currentPeer != null && !currentPeer.isEmpty()) {
                        // 禁用发送按钮防止重复发送
                        sendButton.setDisable(true);
                        
                        // 发送消息给当前选中的用户
                        ChatService.sendPrivateMessage(currentPeer, message);
                        messageInput.clear();
                        
                        // 自动滚动到最新消息
                        Platform.runLater(() -> {
                            chatScrollPane.setVvalue(1.0);
                            // 短暂延迟后重新启用发送按钮
                            new Thread(() -> {
                                try {
                                    Thread.sleep(300);
                                    Platform.runLater(() -> sendButton.setDisable(false));
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    Platform.runLater(() -> sendButton.setDisable(false));
                                }
                            }).start();
                        });
                    } else {
                        showError("未选择聊天对象", "请先从在线用户列表中选择一个聊天对象");
                    }
                }
            });
            
            // 当选择了聊天对象时启用发送按钮
            onlineUsersListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    sendButton.setDisable(newValue == null);
                }
            );
            
            messageInput.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER && !sendButton.isDisable()) {
                    sendButton.fire();
                }
            });
            
            sendMessageBox.getChildren().addAll(messageInput, sendButton);
            
            messagesBox.getChildren().addAll(chatScrollPane, sendMessageBox);
            
            // 添加分割面板
            chatSplitPane.getItems().addAll(usersBox, messagesBox);
            chatSplitPane.setDividerPositions(0.25);
            SplitPane.setResizableWithParent(usersBox, false);
            
            chatPanel.getChildren().addAll(topBox, chatSplitPane);
        } else {
            // 直连模式布局...保持原有代码
            Label clientStatusLabel = new Label(connectionStatus);
            clientStatusLabel.getStyleClass().add("info-box");
            clientStatusLabel.setPadding(new Insets(5, 10, 5, 10));
            
            Button connectButton = new Button("连接服务器");
            connectButton.setOnAction(e -> requestServerInfo());
            
            connectionBox.getChildren().add(connectButton);
            topBox.getChildren().addAll(titleLabel, clientStatusLabel, connectionBox);
            
            // 创建消息区域
            VBox messagesBox = new VBox();
            messagesBox.getStyleClass().add("content-box");
            messagesBox.setSpacing(10);
            VBox.setVgrow(messagesBox, Priority.ALWAYS);
            
            Label messagesLabel = new Label("聊天消息");
            
            chatScrollPane = new ScrollPane();
            chatScrollPane.setFitToWidth(true);
            chatScrollPane.setFitToHeight(true);
            VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
            
            VBox messageContainer = new VBox();
            messageContainer.setSpacing(10);
            messageContainer.setPadding(new Insets(10));
            VBox.setVgrow(messageContainer, Priority.ALWAYS);
            chatMessages = messageContainer;
            
            chatScrollPane.setContent(messageContainer);
            messagesBox.getChildren().addAll(messagesLabel, chatScrollPane);
            
            // 创建发送消息区域
            HBox sendMessageBox = new HBox();
            sendMessageBox.setSpacing(10);
            sendMessageBox.setPadding(new Insets(10));
            sendMessageBox.getStyleClass().add("content-box");
            
            TextField messageInput = new TextField();
            messageInput.setPromptText("输入消息...");
            HBox.setHgrow(messageInput, Priority.ALWAYS);
            
            Button sendButton = new Button("发送");
            sendButton.getStyleClass().add("button");
            sendButton.setOnAction(e -> {
                if (chatClient == null || !chatClient.isConnected()) {
                    showError("未连接", "请先连接到服务器");
                    return;
                }
                
                String message = messageInput.getText().trim();
                if (!message.isEmpty()) {
                    chatClient.sendMessage(message);
                    messageInput.clear();
                }
            });
            
            messageInput.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    sendButton.fire();
                }
            });
            
            sendMessageBox.getChildren().addAll(messageInput, sendButton);
            
            // 添加所有元素到聊天面板
            chatPanel.getChildren().addAll(topBox, messagesBox, sendMessageBox);
        }
        
        // 将聊天面板添加到主内容区
        mainContent.getChildren().add(chatPanel);
    }

    private void addMessageToChat(ChatMessage message) {
        // 确保在JavaFX主线程中执行
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> addMessageToChat(message));
            return;
        }
        
        // 先检查chatMessages是否为null
        if (chatMessages == null) {
            System.err.println("错误: chatMessages为空，无法添加消息");
            return;
        }
        
        System.out.println("尝试添加消息: ID=" + message.getId() + ", 发送者=" + message.getSender() + 
                          ", 接收者=" + message.getReceiver() + ", 内容=" + message.getContent());
        
        // 先检查消息ID，如果已显示过则跳过
        if (message.getId() > 0 && displayedMessageIds.contains(message.getId())) {
            System.out.println("在addMessageToChat中跳过已显示的消息: ID=" + message.getId() + ", 内容=" + message.getContent());
            return;
        }
        
        // 检查消息是否应该显示在当前聊天窗口
        String currentPeer = ChatService.getCurrentChatPeer();
        String messageSender = message.getSender();
        String messageReceiver = message.getReceiver();
        
        // 调试日志
        System.out.println("处理消息显示: 发送者=" + messageSender + ", 接收者=" + messageReceiver + 
                           ", 当前聊天对象=" + currentPeer + ", 当前用户=" + username + ", 消息ID=" + message.getId());
        
        // 简化消息显示条件，确保消息能够正确显示
        boolean shouldDisplay = false;
        
        // 如果是在聊天窗口(当前有选择的聊天对象)
        if (currentPeer != null && !currentPeer.isEmpty()) {
            // 1. 当前用户是发送者，且当前聊天对象是接收者
            if (username.equals(messageSender) && currentPeer.equals(messageReceiver)) {
                shouldDisplay = true;
                System.out.println("显示消息 - 当前用户发送给当前聊天对象的消息");
            }
            // 2. 当前用户是接收者，且当前聊天对象是发送者
            else if (username.equals(messageReceiver) && currentPeer.equals(messageSender)) {
                shouldDisplay = true;
                System.out.println("显示消息 - 当前聊天对象发送给当前用户的消息");
            }
        }
        // 如果是在聊天记录页面(无当前聊天对象)
        else if (currentPeer == null && chatRefreshTimer == null) {
            shouldDisplay = true;
            System.out.println("显示消息 - 在聊天记录页面显示所有消息");
        }
        
        // 如果不应该显示，直接返回
        if (!shouldDisplay) {
            System.out.println("跳过不应该在当前窗口显示的消息");
            return;
        }
        
        // 记录消息ID，防止重复显示
        if (message.getId() > 0) {
            displayedMessageIds.add(message.getId());
            System.out.println("记录消息ID: " + message.getId() + " 为已显示");
        }
        
        // 创建消息显示组件
        HBox messageBox = new HBox();
        messageBox.setSpacing(10);
        messageBox.setPadding(new Insets(5));
        messageBox.setId("msg-" + message.getId()); // 添加ID以便后续查找
        
        // 存储时间戳，但确保可以正确比较
        try {
            messageBox.setUserData(message.getTimestamp());
        } catch (Exception e) {
            System.err.println("无法设置消息时间戳作为userData: " + e.getMessage());
        }
        
        // 判断消息的方向（发送或接收）
        boolean isOutgoing = messageSender.equals(username);
        if (isOutgoing) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getStyleClass().add("outgoing-message");
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
        
        // 创建消息内容框
        VBox messageContentBox = new VBox();
        messageContentBox.setSpacing(5);
        messageContentBox.getStyleClass().add("chat-bubble");
        messageContentBox.setPrefWidth(Control.USE_COMPUTED_SIZE);
        messageContentBox.setMaxWidth(300);
        
        // 添加发送者标签
        Label senderLabel = new Label(messageSender);
        senderLabel.getStyleClass().add("chat-sender");
        
        // 添加消息内容
        Label contentLabel = new Label(message.getContent());
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("chat-content");
        
        // 添加时间标签
        Label timeLabel = new Label(message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        timeLabel.getStyleClass().add("chat-time");
        
        // 组装消息内容框
        messageContentBox.getChildren().addAll(senderLabel, contentLabel, timeLabel);
        
        // 添加头像
        if (isOutgoing) {
            Circle avatar = new Circle(20);
            avatar.getStyleClass().add("user-avatar");
            Label initials = new Label(messageSender.substring(0, 1).toUpperCase());
            StackPane avatarPane = new StackPane(avatar, initials);
            
            messageBox.getChildren().addAll(messageContentBox, avatarPane);
        } else {
            Circle avatar = new Circle(20);
            avatar.getStyleClass().add("user-avatar");
            Label initials = new Label(messageSender.substring(0, 1).toUpperCase());
            StackPane avatarPane = new StackPane(avatar, initials);
            
            messageBox.getChildren().addAll(avatarPane, messageContentBox);
        }
        
        // 先直接添加消息到聊天区域，不尝试排序
        chatMessages.getChildren().add(messageBox);
        System.out.println("成功添加消息到聊天区域: ID=" + message.getId() + ", 内容=" + message.getContent());
        
        // 自动滚动到最新消息
        chatScrollPane.setVvalue(1.0);
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

    private void loadDiaries(ListView<Diary> diaryListView) {
        // 清空日记列表
        diaryListView.getItems().clear();
        
        // 获取所有日记
        List<Diary> diaries = DiaryService.getAllDiaries();
        
        // 添加到列表
        if (diaries != null && !diaries.isEmpty()) {
            diaryListView.getItems().addAll(diaries);
        }
    }

    private void loadFilteredDiaries(ListView<Diary> diaryListView, String keyword, LocalDate startDate, LocalDate endDate) {
        // 清空日记列表
        diaryListView.getItems().clear();
        
        // 准备查询参数
        String startDateStr = startDate != null ? startDate.toString() : null;
        String endDateStr = endDate != null ? endDate.toString() : null;
        
        // 获取筛选后的日记
        List<Diary> diaries = DiaryService.searchDiaries(keyword, startDateStr, endDateStr);
        
        // 添加到列表
        if (diaries != null && !diaries.isEmpty()) {
            diaryListView.getItems().addAll(diaries);
        }
    }

    private void refreshOnlineUsers() {
        if (isServerMode) {
            List<String> users = ChatService.getOnlineUsers();
            Platform.runLater(() -> {
                onlineUsers.clear();
                onlineUsers.addAll(users);
            });
        }
    }

    private void requestServerInfo() {
        // Implementation of requestServerInfo method
    }

    private void openLogs() {
        mainContent.getChildren().clear();
        
        VBox logsPanel = new VBox();
        logsPanel.getStyleClass().addAll("content-box", "responsive-container");
        logsPanel.setSpacing(15);
        logsPanel.setPadding(new Insets(15));
        VBox.setVgrow(logsPanel, Priority.ALWAYS);
        HBox.setHgrow(logsPanel, Priority.ALWAYS);
        
        Label titleLabel = new Label("聊天记录");
        titleLabel.getStyleClass().add("title-label");
        
        // 创建搜索区域
        HBox searchBox = new HBox();
        searchBox.setSpacing(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("content-box");
        searchBox.setPadding(new Insets(10));
        
        TextField searchField = new TextField();
        searchField.setPromptText("搜索关键词...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().add("button");
        
        searchBox.getChildren().addAll(searchField, searchButton);
        
        // 创建聊天记录区域
        VBox logsArea = new VBox();
        logsArea.setSpacing(10);
        logsArea.getStyleClass().add("content-box");
        logsArea.setPadding(new Insets(10));
        VBox.setVgrow(logsArea, Priority.ALWAYS);
        
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        VBox logsContainer = new VBox();
        logsContainer.setSpacing(10);
        logsContainer.setPadding(new Insets(10));
        
        scrollPane.setContent(logsContainer);
        logsArea.getChildren().add(scrollPane);
        
        // 加载聊天记录
        List<ChatMessage> messages = ChatService.getChatHistory();
        if (messages != null && !messages.isEmpty()) {
            for (ChatMessage message : messages) {
                HBox messageBox = new HBox();
                messageBox.setSpacing(10);
                messageBox.setPadding(new Insets(5));
                messageBox.setAlignment(Pos.CENTER_LEFT);
                
                VBox messageContentBox = new VBox();
                messageContentBox.setSpacing(5);
                messageContentBox.getStyleClass().add("chat-bubble");
                messageContentBox.setPrefWidth(Control.USE_COMPUTED_SIZE);
                messageContentBox.setMaxWidth(500);
                
                Label senderLabel = new Label(message.getSender());
                senderLabel.getStyleClass().add("chat-sender");
                
                Label contentLabel = new Label(message.getContent());
                contentLabel.setWrapText(true);
                contentLabel.getStyleClass().add("chat-content");
                
                Label timeLabel = new Label(message.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                timeLabel.getStyleClass().add("chat-time");
                
                messageContentBox.getChildren().addAll(senderLabel, contentLabel, timeLabel);
                messageBox.getChildren().add(messageContentBox);
                
                logsContainer.getChildren().add(messageBox);
            }
        } else {
            Label noLogsLabel = new Label("没有聊天记录");
            noLogsLabel.setAlignment(Pos.CENTER);
            noLogsLabel.setMaxWidth(Double.MAX_VALUE);
            logsContainer.getChildren().add(noLogsLabel);
        }
        
        // 创建操作按钮区域
        HBox buttonsBox = new HBox();
        buttonsBox.setSpacing(10);
        buttonsBox.setAlignment(Pos.CENTER_RIGHT);
        buttonsBox.setPadding(new Insets(10));
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("sync-button");
        refreshButton.setOnAction(e -> {
            // 重新加载聊天记录
            openLogs();
        });
        
        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("delete-button");
        clearButton.setOnAction(e -> {
            // 确认清空
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认清空");
            alert.setHeaderText("您确定要清空聊天记录吗？");
            alert.setContentText("此操作不可撤销。");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // 清空聊天记录
                ChatService.clearChatHistory();
                openLogs();
            }
        });
        
        buttonsBox.getChildren().addAll(refreshButton, clearButton);
        
        // 添加所有组件到主面板
        logsPanel.getChildren().addAll(titleLabel, searchBox, logsArea, buttonsBox);
        
        // 将主面板添加到主内容区
        mainContent.getChildren().add(logsPanel);
        
        // 搜索功能
        searchButton.setOnAction(e -> {
            String keyword = searchField.getText().trim();
            if (!keyword.isEmpty()) {
                List<ChatMessage> filteredMessages = ChatService.searchChatHistory(keyword);
                
                // 清空当前显示的消息
                logsContainer.getChildren().clear();
                
                if (filteredMessages != null && !filteredMessages.isEmpty()) {
                    for (ChatMessage message : filteredMessages) {
                        HBox messageBox = new HBox();
                        messageBox.setSpacing(10);
                        messageBox.setPadding(new Insets(5));
                        messageBox.setAlignment(Pos.CENTER_LEFT);
                        
                        VBox messageContentBox = new VBox();
                        messageContentBox.setSpacing(5);
                        messageContentBox.getStyleClass().add("chat-bubble");
                        messageContentBox.setPrefWidth(Control.USE_COMPUTED_SIZE);
                        messageContentBox.setMaxWidth(500);
                        
                        Label senderLabel = new Label(message.getSender());
                        senderLabel.getStyleClass().add("chat-sender");
                        
                        Label contentLabel = new Label(message.getContent());
                        contentLabel.setWrapText(true);
                        contentLabel.getStyleClass().add("chat-content");
                        
                        Label timeLabel = new Label(message.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        timeLabel.getStyleClass().add("chat-time");
                        
                        messageContentBox.getChildren().addAll(senderLabel, contentLabel, timeLabel);
                        messageBox.getChildren().add(messageContentBox);
                        
                        logsContainer.getChildren().add(messageBox);
                    }
                } else {
                    Label noLogsLabel = new Label("没有找到匹配的聊天记录");
                    noLogsLabel.setAlignment(Pos.CENTER);
                    noLogsLabel.setMaxWidth(Double.MAX_VALUE);
                    logsContainer.getChildren().add(noLogsLabel);
                }
            } else {
                // 如果搜索关键词为空，显示所有聊天记录
                openLogs();
            }
        });
    }

    private void showChatWithUser(String username) {
        System.out.println("加载与用户 " + username + " 的聊天历史");
        
        // 在UI线程中执行更新
        Platform.runLater(() -> {
            try {
                // 更新当前聊天对象
                ChatService.setCurrentChatPeer(username);
                System.out.println("已设置当前聊天对象为: " + username);
                
                // 清空历史消息显示区
                if (chatMessages != null) {
                    chatMessages.getChildren().clear();
                    System.out.println("已清空聊天消息显示区");
                } else {
                    System.err.println("错误: chatMessages为null");
                }
                
                // 清空已显示消息ID集合，确保能加载所有历史消息
                displayedMessageIds.clear();
                System.out.println("已清空已显示消息ID集合");
                
                // 提示正在加载
                Label loadingLabel = new Label("正在加载聊天历史...");
                loadingLabel.getStyleClass().add("info-label");
                loadingLabel.setAlignment(Pos.CENTER);
                loadingLabel.setMaxWidth(Double.MAX_VALUE);
                chatMessages.getChildren().add(loadingLabel);
                
                // 使用新的刷新方法，加载所有历史
                new Thread(() -> refreshChatWithUser(username, true)).start();
                
                // 清除该用户的未读标记
                clearUnreadBadge(username);
                
                // 刷新界面
                onlineUsersListView.refresh();
            } catch (Exception e) {
                System.err.println("加载聊天历史时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // 设置用户接收新消息的回调
    private void setOnNewMessage(String user, Runnable callback) {
        // 存储回调以在接收到消息时调用
        if (!messageCallbacks.containsKey(user)) {
            messageCallbacks.put(user, callback);
        }
    }

    // 清除用户的未读标记
    private void clearUnreadBadge(String user) {
        // 在UI中刷新用户列表项，移除未读标记
        Platform.runLater(() -> {
            onlineUsersListView.refresh();
            
            // 使用safer方式处理UI元素
            for (int i = 0; i < onlineUsersListView.getItems().size(); i++) {
                if (onlineUsersListView.getItems().get(i).equals(user)) {
                    // 简单地刷新整个列表而不是尝试直接修改标记
                    // 因为对单元格的直接访问和修改是不可靠的
                    break;
                }
            }
        });
    }

    // 修改refreshChatWithUser方法确保显示完整历史
    private void refreshChatWithUser(String username, boolean clearHistory) {
        System.out.println("刷新与用户 " + username + " 的聊天历史" + (clearHistory ? "(加载全部)" : "(增量更新)"));
        
        try {
            if (chatMessages == null) {
                System.err.println("错误: chatMessages为null，无法刷新聊天");
                return;
            }
            
            // 如果需要清空历史，则清空消息显示区
            if (clearHistory) {
                Platform.runLater(() -> {
                    chatMessages.getChildren().clear();
                    System.out.println("已清空聊天消息显示区");
                });
            }
            
            // 加载与该用户的聊天历史
            List<ChatMessage> history;
            if (clearHistory) {
                // 清空历史时获取所有消息
                history = ChatService.getChatHistory(username);
                System.out.println("加载全部历史消息: " + (history != null ? history.size() : 0) + " 条");
            } else {
                // 只获取最近15秒的消息避免重复，用于实时更新
                history = ChatService.getNewChatHistory(username, LocalDateTime.now().minusSeconds(15));
                System.out.println("加载增量消息: " + (history != null ? history.size() : 0) + " 条");
            }
            
            if (history != null && !history.isEmpty()) {
                System.out.println("找到 " + history.size() + " 条历史消息");
                
                // 确保消息按照时间戳排序
                history.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
                
                // 如果是清除历史模式，先清除"正在加载"标签
                if (clearHistory) {
                    Platform.runLater(() -> {
                        chatMessages.getChildren().clear();
                    });
                }
                
                // 处理消息
                for (ChatMessage msg : history) {
                    // 只有增量更新时才跳过已显示的消息
                    if (!clearHistory && msg.getId() > 0 && displayedMessageIds.contains(msg.getId())) {
                        System.out.println("跳过已显示的消息ID: " + msg.getId() + ", 内容: " + msg.getContent());
                        continue;
                    }
                    
                    // 确保只显示与当前用户和选定聊天对象相关的消息
                    if ((msg.getSender().equals(this.username) && msg.getReceiver().equals(username)) ||
                        (msg.getSender().equals(username) && msg.getReceiver().equals(this.username))) {
                        
                        // 显示消息
                        Platform.runLater(() -> addMessageToChat(msg));
                    }
                }
            } else if (clearHistory) {
                // 显示无历史消息提示
                Platform.runLater(() -> {
                    chatMessages.getChildren().clear();
                    Label noMessagesLabel = new Label("还没有与该用户的聊天记录，发送消息开始对话吧");
                    noMessagesLabel.getStyleClass().add("info-label");
                    noMessagesLabel.setAlignment(Pos.CENTER);
                    noMessagesLabel.setMaxWidth(Double.MAX_VALUE);
                    chatMessages.getChildren().add(noMessagesLabel);
                });
            }
        } catch (Exception e) {
            System.err.println("刷新聊天历史时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 添加一个菜单项显示数据库信息
    private void addDatabaseInfoMenuItem(Menu settingsMenu) {
        MenuItem dbInfoItem = new MenuItem("数据库信息");
        dbInfoItem.setOnAction(e -> {
            String dbPath = DBUtil.getDatabasePath();
            boolean isConnected = DBUtil.testConnection();
            
            Alert dbInfoAlert = new Alert(Alert.AlertType.INFORMATION);
            dbInfoAlert.setTitle("数据库信息");
            dbInfoAlert.setHeaderText("应用数据存储位置");
            
            VBox content = new VBox(5);
            content.setPadding(new Insets(10));
            
            Label pathLabel = new Label("您的聊天数据存储在:");
            Label locationLabel = new Label(dbPath);
            locationLabel.setStyle("-fx-font-weight: bold;");
            
            Label statusLabel = new Label("数据库连接状态: " + (isConnected ? "正常" : "异常"));
            statusLabel.setStyle("-fx-text-fill: " + (isConnected ? "green" : "red") + "; -fx-font-weight: bold;");
            
            HBox actionsBox = new HBox(10);
            actionsBox.setAlignment(Pos.CENTER);
            
            Button openFolderButton = new Button("打开数据目录");
            openFolderButton.setOnAction(event -> {
                try {
                    File dbFile = new File(dbPath);
                    File parentDir = dbFile.getParentFile();
                    if (parentDir != null && parentDir.exists()) {
                        Desktop.getDesktop().open(parentDir);
                    } else {
                        showError("错误", "数据目录不存在: " + (parentDir != null ? parentDir.getAbsolutePath() : "未知"));
                    }
                } catch (Exception ex) {
                    showError("错误", "无法打开数据目录: " + ex.getMessage());
                }
            });
            
            Button backupButton = new Button("备份数据库");
            backupButton.setOnAction(event -> {
                try {
                    // 创建备份文件名
                    String timestamp = LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    File dbFile = new File(dbPath);
                    String backupPath = dbFile.getParent() + File.separator + "backup_" + timestamp + "_app.db";
                    
                    // 复制文件
                    Files.copy(dbFile.toPath(), new File(backupPath).toPath());
                    
                    showInfo("备份成功", "已创建数据库备份:\n" + backupPath);
                } catch (Exception ex) {
                    showError("备份失败", "无法创建数据库备份: " + ex.getMessage());
                }
            });
            
            actionsBox.getChildren().addAll(openFolderButton, backupButton);
            
            content.getChildren().addAll(pathLabel, locationLabel, statusLabel, new Separator(), actionsBox);
            
            dbInfoAlert.getDialogPane().setContent(content);
            dbInfoAlert.showAndWait();
        });
        
        settingsMenu.getItems().add(dbInfoItem);
    }

    // 显示信息对话框
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
