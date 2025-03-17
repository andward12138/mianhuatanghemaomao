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

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

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

    @Override
    public void start(Stage primaryStage) {
        // 初始化数据库
        DBUtil.initializeDatabase();
        
        // 初始化API服务
        com.example.message.services.ApiService.initialize();
        
        // 请求用户输入用户名
        requestUsername();
        
        // 设置消息接收回调
        ChatService.setMessageReceivedCallback(this::handleReceivedMessage);
        
        // 创建可收缩侧边栏
        VBox sidebar = createSidebar();

        // 创建右侧内容区域（聊天或心情日记）
        StackPane content = new StackPane();

        // 设置初始内容为心情日记界面
        openDiary(content);

        // 主界面布局（侧边栏 + 内容区域）
        HBox root = new HBox();
        root.getChildren().addAll(sidebar, content);

        // 创建场景
        Scene scene = new Scene(root, 800, 600);

        // 加载CSS样式
        scene.getStylesheets().add(getClass().getResource("/com/example/message/static/app.css").toExternalForm());

        // 设置标题并显示窗口
        primaryStage.setTitle("棉花糖和猫猫的小屋 - " + username);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // 在应用关闭时停止聊天服务
        primaryStage.setOnCloseRequest(e -> {
            ChatService.stopServer();
        });
    }
    
    // 处理接收到的消息
    private void handleReceivedMessage(ChatMessage message) {
        // 在JavaFX应用程序线程中更新UI
        Platform.runLater(() -> {
            // 添加消息到聊天界面
            addMessageToChat(message);
            
            // 滚动到底部
            chatScrollPane.setVvalue(1.0);
            
            // 如果需要，可以播放提示音或显示通知
        });
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
        // 创建主侧边栏容器
        VBox sidebarContainer = new VBox();
        
        // 创建侧边栏内容
        VBox sidebar = new VBox(20);  // 设置按钮之间的间距
        sidebar.setStyle("-fx-background-color: #2C3E50; -fx-padding: 10;"); // 设置背景色和内边距
        sidebar.setPrefWidth(200);
        sidebar.setMinWidth(200);

        // 创建按钮
        Button diaryButton = new Button("心情日记");
        Button chatButton = new Button("聊天");

        // 样式应用到按钮
        diaryButton.getStyleClass().add("sidebar-button");
        diaryButton.setPrefWidth(180);
        
        chatButton.getStyleClass().add("sidebar-button");
        chatButton.setPrefWidth(180);

        // 设置按钮点击事件
        diaryButton.setOnAction(e -> {
            StackPane content = (StackPane) ((HBox) diaryButton.getScene().getRoot()).getChildren().get(1);
            openDiary(content);
        });
        
        chatButton.setOnAction(e -> {
            StackPane content = (StackPane) ((HBox) chatButton.getScene().getRoot()).getChildren().get(1);
            openChat(content);
        });

        // 将按钮添加到侧边栏
        sidebar.getChildren().addAll(diaryButton, chatButton);
        
        // 创建一个始终可见的小按钮用于展开/收缩
        StackPane toggleButtonContainer = new StackPane();
        toggleButtonContainer.setMinWidth(30);
        toggleButtonContainer.setPrefWidth(30);
        toggleButtonContainer.setStyle("-fx-background-color: #1ABC9C;");
        
        Button toggleButton = new Button("≡");
        toggleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 5 0 5 0;");
        toggleButton.setOnAction(e -> toggleSidebar(sidebar));
        
        toggleButtonContainer.getChildren().add(toggleButton);
        
        // 创建水平布局，包含侧边栏和切换按钮
        HBox sidebarLayout = new HBox();
        sidebarLayout.getChildren().addAll(sidebar, toggleButtonContainer);
        
        sidebarContainer.getChildren().add(sidebarLayout);
        
        return sidebarContainer;
    }

    private void openDiary(StackPane content) {
        // 创建主布局
        VBox mainLayout = new VBox(15);
        mainLayout.setPadding(new Insets(15));
        mainLayout.setFillWidth(true);
        mainLayout.setAlignment(Pos.TOP_CENTER);
        
        // 创建标题
        Label titleLabel = new Label("心情日记");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);
        
        // 创建搜索区域
        VBox searchBox = new VBox(10);
        searchBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        searchBox.setMaxWidth(Double.MAX_VALUE);
        
        // 创建搜索框
        TextField searchTextField = new TextField();
        searchTextField.setPromptText("输入关键字搜索");
        searchTextField.setMaxWidth(Double.MAX_VALUE);
        
        // 创建日期选择器区域
        HBox datePickerBox = new HBox(10);
        datePickerBox.setAlignment(Pos.CENTER);
        
        // 创建日期选择器
        DatePicker startDatePicker = new DatePicker();
        startDatePicker.setPromptText("开始日期");
        startDatePicker.setPrefWidth(150);
        
        Label toLabel = new Label("至");
        toLabel.setTextFill(Color.WHITE);
        
        DatePicker endDatePicker = new DatePicker();
        endDatePicker.setPromptText("结束日期");
        endDatePicker.setPrefWidth(150);
        
        datePickerBox.getChildren().addAll(startDatePicker, toLabel, endDatePicker);
        
        // 创建操作按钮区域
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER);
        
        // 创建搜索按钮
        Button searchButton = new Button("搜索");
        searchButton.setPrefWidth(100);
        
        // 创建批量删除按钮
        Button batchDeleteButton = new Button("批量删除");
        batchDeleteButton.getStyleClass().add("delete-button");
        batchDeleteButton.setPrefWidth(100);
        
        actionButtons.getChildren().addAll(searchButton, batchDeleteButton);
        
        // 添加到搜索区域
        searchBox.getChildren().addAll(searchTextField, datePickerBox, actionButtons);
        
        // 创建日记显示区域
        ListView<HBox> diaryListView = new ListView<>();
        diaryListView.setStyle("-fx-background-color: transparent;");
        diaryListView.setFixedCellSize(-1); // 允许单元格自动调整高度
        
        // 创建日记列表滚动面板
        ScrollPane diaryScrollPane = new ScrollPane(diaryListView);
        diaryScrollPane.setFitToWidth(true);
        diaryScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        diaryScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        diaryScrollPane.setPrefHeight(300);
        diaryScrollPane.setMaxHeight(Double.MAX_VALUE);
        diaryScrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(diaryScrollPane, Priority.ALWAYS);
        
        // 加载所有日记
        loadDiaries(diaryListView, null, null, null);
        
        // 创建新日记区域
        VBox newDiaryBox = new VBox(10);
        newDiaryBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        newDiaryBox.setMaxWidth(Double.MAX_VALUE);
        
        Label newDiaryLabel = new Label("写新日记");
        newDiaryLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        newDiaryLabel.setTextFill(Color.WHITE);
        
        // 创建心情日记文本框
        TextArea diaryTextArea = new TextArea();
        diaryTextArea.setPromptText("写下今天的心情...");
        diaryTextArea.setWrapText(true);  // 自动换行，方便输入较长的文本
        diaryTextArea.setPrefHeight(100);
        
        // 创建标签输入框
        TextField tagsTextField = new TextField();
        tagsTextField.setPromptText("输入标签（例如：#开心, #总结）");
        
        // 创建保存按钮
        Button saveButton = new Button("保存");
        saveButton.setPrefWidth(100);
        saveButton.setAlignment(Pos.CENTER);
        
        // 添加到新日记区域
        newDiaryBox.getChildren().addAll(newDiaryLabel, diaryTextArea, tagsTextField, saveButton);
        
        // 设置按钮点击事件
        searchButton.setOnAction(e -> {
            String keyword = searchTextField.getText();
            String startDate = startDatePicker.getValue() != null ? startDatePicker.getValue().toString() : "";
            String endDate = endDatePicker.getValue() != null ? endDatePicker.getValue().toString() : "";
            loadDiaries(diaryListView, keyword, startDate, endDate);
        });
        
        batchDeleteButton.setOnAction(e -> {
            openBatchDeleteDialog(diaryListView, searchTextField.getText(), 
                    startDatePicker.getValue() != null ? startDatePicker.getValue().toString() : null,
                    endDatePicker.getValue() != null ? endDatePicker.getValue().toString() : null);
        });
        
        saveButton.setOnAction(e -> {
            String contentText = diaryTextArea.getText();
            String tagsText = tagsTextField.getText();  // 获取用户输入的标签
            if (!contentText.isEmpty()) {
                DiaryService.saveDiary(contentText, tagsText);  // 保存日记及标签
                diaryTextArea.clear();  // 清空文本框
                tagsTextField.clear();  // 清空标签框
                loadDiaries(diaryListView, null, null, null); // 更新日记列表
            }
        });
        
        // 添加所有组件到主布局
        mainLayout.getChildren().addAll(titleLabel, searchBox, diaryScrollPane, newDiaryBox);
        
        // 更新内容区域
        content.getChildren().clear();
        content.getChildren().add(mainLayout);
    }

    // 加载日记列表
    private void loadDiaries(ListView<HBox> diaryListView, String keyword, String startDate, String endDate) {
        diaryListView.getItems().clear();
        
        // 设置列表视图自动调整宽度
        diaryListView.setPrefWidth(Region.USE_COMPUTED_SIZE);
        diaryListView.setMinWidth(Region.USE_COMPUTED_SIZE);
        diaryListView.setMaxWidth(Double.MAX_VALUE);
        
        // 设置列表视图自动调整高度
        diaryListView.setPrefHeight(300);
        diaryListView.setMinHeight(200);
        diaryListView.setMaxHeight(Double.MAX_VALUE);
        
        List<Diary> diaries;
        if (keyword != null || startDate != null || endDate != null) {
            diaries = DiaryService.searchDiaries(keyword, startDate, endDate);
        } else {
            diaries = DiaryService.getAllDiaries();
        }
        
        for (Diary diary : diaries) {
            // 创建日记项布局
            HBox diaryItem = new HBox(10);
            diaryItem.setPadding(new Insets(10));
            diaryItem.setAlignment(Pos.CENTER_LEFT);
            diaryItem.getStyleClass().add("diary-item");
            diaryItem.setMaxWidth(Double.MAX_VALUE);
            
            // 创建日记内容显示区域
            VBox diaryContent = new VBox(5);
            diaryContent.setPrefWidth(Region.USE_COMPUTED_SIZE);
            diaryContent.setMaxWidth(Double.MAX_VALUE);
            diaryContent.setMinWidth(100);
            
            // 日期和标签
            HBox metaInfo = new HBox(10);
            Label dateLabel = new Label("日期: " + diary.getDate());
            dateLabel.getStyleClass().add("diary-date");
            
            Label tagsLabel = new Label("标签: " + diary.getTags());
            tagsLabel.getStyleClass().add("diary-tags");
            
            metaInfo.getChildren().addAll(dateLabel, tagsLabel);
            
            // 内容
            Label contentLabel = new Label(diary.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(Double.MAX_VALUE);
            contentLabel.getStyleClass().add("diary-content");
            
            diaryContent.getChildren().addAll(metaInfo, contentLabel);
            HBox.setHgrow(diaryContent, Priority.ALWAYS);
            
            // 创建操作按钮区域
            VBox buttonBox = new VBox(5);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.setMinWidth(80);
            
            // 创建编辑按钮
            Button editButton = new Button("编辑");
            editButton.getStyleClass().add("edit-button");
            editButton.setMaxWidth(Double.MAX_VALUE);
            editButton.setOnAction(e -> {
                openEditDiaryDialog(diary, diaryListView, keyword, startDate, endDate);
            });
            
            // 创建删除按钮
            Button deleteButton = new Button("删除");
            deleteButton.getStyleClass().add("delete-button");
            deleteButton.setMaxWidth(Double.MAX_VALUE);
            deleteButton.setOnAction(e -> {
                // 确认删除
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("确认删除");
                alert.setHeaderText("您确定要删除这条日记吗？");
                alert.setContentText("此操作不可撤销。");
                
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    // 执行删除
                    DiaryService.deleteDiary(diary.getId());
                    // 刷新列表
                    loadDiaries(diaryListView, keyword, startDate, endDate);
                }
            });
            
            buttonBox.getChildren().addAll(editButton, deleteButton);
            
            diaryItem.getChildren().addAll(diaryContent, buttonBox);
            
            // 添加到列表
            diaryListView.getItems().add(diaryItem);
        }
    }

    // 打开编辑日记对话框
    private void openEditDiaryDialog(Diary diary, ListView<HBox> diaryListView, String keyword, String startDate, String endDate) {
        // 创建对话框
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("编辑日记");
        dialog.setHeaderText("编辑您的日记内容和标签");
        
        // 设置按钮
        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // 创建内容区域
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // 创建文本区域和标签输入框
        TextArea contentTextArea = new TextArea(diary.getContent());
        contentTextArea.setWrapText(true);
        contentTextArea.setPrefHeight(200);
        contentTextArea.getStyleClass().add("diary-content");
        
        TextField tagsTextField = new TextField(diary.getTags());
        tagsTextField.getStyleClass().add("diary-tags");
        
        // 添加标签
        Label contentLabel = new Label("内容:");
        contentLabel.getStyleClass().add("dialog-label");
        
        Label tagsLabel = new Label("标签:");
        tagsLabel.getStyleClass().add("dialog-label");
        
        // 添加到网格
        grid.add(contentLabel, 0, 0);
        grid.add(contentTextArea, 1, 0);
        grid.add(tagsLabel, 0, 1);
        grid.add(tagsTextField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        // 请求焦点
        Platform.runLater(() -> contentTextArea.requestFocus());
        
        // 处理结果
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            String newContent = contentTextArea.getText();
            String newTags = tagsTextField.getText();
            
            // 更新日记
            DiaryService.updateDiary(diary.getId(), newContent, newTags);
            
            // 刷新列表
            loadDiaries(diaryListView, keyword, startDate, endDate);
        }
    }

    private void openChat(StackPane content) {
        if (isServerMode) {
            openServerModeChat(content);
        } else {
            openDirectModeChat(content);
        }
    }
    
    // 服务器模式的聊天界面
    private void openServerModeChat(StackPane content) {
        // 创建主布局
        VBox chatLayout = new VBox(15);
        chatLayout.setPadding(new Insets(15));
        chatLayout.setFillWidth(true);
        chatLayout.setAlignment(Pos.TOP_CENTER);
        
        // 创建标题
        Label titleLabel = new Label("服务器聊天模式");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);
        
        // 创建连接状态显示
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        statusBox.setMaxWidth(Double.MAX_VALUE);
        
        Label statusLabel = new Label("服务器模式: ");
        statusLabel.setTextFill(Color.WHITE);
        
        connectionStatusLabel = new Label(ChatService.isConnectedToServer() ? "已连接" : "未连接");
        connectionStatusLabel.setTextFill(ChatService.isConnectedToServer() ? Color.GREEN : Color.RED);
        
        Button refreshButton = new Button("刷新用户列表");
        refreshButton.setOnAction(e -> refreshOnlineUsers());
        
        statusBox.getChildren().addAll(statusLabel, connectionStatusLabel, refreshButton);
        
        // 创建聊天内容区域
        HBox contentSplit = new HBox(15);
        contentSplit.setMaxWidth(Double.MAX_VALUE);
        contentSplit.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(contentSplit, Priority.ALWAYS);
        
        // 创建在线用户列表
        VBox userListBox = new VBox(10);
        userListBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        userListBox.setPrefWidth(200);
        userListBox.setMaxHeight(Double.MAX_VALUE);
        
        Label usersLabel = new Label("在线用户");
        usersLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        usersLabel.setTextFill(Color.WHITE);
        
        onlineUsersListView = new ListView<>(onlineUsers);
        onlineUsersListView.setPrefHeight(350);
        onlineUsersListView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(onlineUsersListView, Priority.ALWAYS);
        
        onlineUsersListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentPeer = newVal;
                ChatService.setCurrentChatPeer(newVal);
                loadChatHistory(newVal);
            }
        });
        
        userListBox.getChildren().addAll(usersLabel, onlineUsersListView);
        
        // 创建聊天区域
        VBox chatBox = new VBox(10);
        chatBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        chatBox.setPrefWidth(450);
        chatBox.setMaxWidth(Double.MAX_VALUE);
        chatBox.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(chatBox, Priority.ALWAYS);
        
        // 创建聊天标题
        Label chatTitleLabel = new Label("聊天消息");
        chatTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        chatTitleLabel.setTextFill(Color.WHITE);
        
        // 创建聊天消息显示区域
        chatMessagesBox = new VBox(10);
        chatMessagesBox.setPadding(new Insets(10));
        chatMessagesBox.setMaxWidth(Double.MAX_VALUE);
        chatMessagesBox.setMaxHeight(Double.MAX_VALUE);
        
        chatScrollPane = new ScrollPane(chatMessagesBox);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.setPrefHeight(300);
        chatScrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        // 创建消息输入区域
        HBox messageInputBox = new HBox(10);
        messageInputBox.setAlignment(Pos.CENTER);
        
        messageField = new TextField();
        messageField.setPromptText("输入消息...");
        messageField.setPrefWidth(500);
        messageField.setOnAction(e -> sendMessage()); // 按Enter发送消息
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        Button sendButton = new Button("发送");
        sendButton.setOnAction(e -> sendMessage());
        
        messageInputBox.getChildren().addAll(messageField, sendButton);
        
        // 将组件添加到聊天区域
        chatBox.getChildren().addAll(chatTitleLabel, chatScrollPane, messageInputBox);
        
        // 将用户列表和聊天区域添加到分割布局
        contentSplit.getChildren().addAll(userListBox, chatBox);
        
        // 将所有组件添加到主布局
        chatLayout.getChildren().addAll(titleLabel, statusBox, contentSplit);
        
        // 更新内容区域
        content.getChildren().clear();
        content.getChildren().add(chatLayout);
        
        // 刷新在线用户列表
        refreshOnlineUsers();
    }
    
    // 刷新在线用户列表
    private void refreshOnlineUsers() {
        if (isServerMode) {
            List<String> users = ChatService.getOnlineUsers();
            Platform.runLater(() -> {
                onlineUsers.clear();
                onlineUsers.addAll(users);
            });
        }
    }
    
    // 直接连接模式的聊天界面
    private void openDirectModeChat(StackPane content) {
        // 创建主布局
        VBox chatLayout = new VBox(15);
        chatLayout.setPadding(new Insets(15));
        chatLayout.setFillWidth(true);
        chatLayout.setAlignment(Pos.TOP_CENTER);
        
        // 创建标题
        Label titleLabel = new Label("实时聊天");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);
        
        // 创建连接部分
        HBox connectionBox = new HBox(10);
        connectionBox.setAlignment(Pos.CENTER_LEFT);
        connectionBox.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        connectionBox.setMaxWidth(Double.MAX_VALUE);
        
        Label myIpLabel = new Label("我的IP: " + ChatService.getLocalIpAddress());
        myIpLabel.setTextFill(Color.WHITE);
        
        Label peerIpLabel = new Label("对方IP:");
        peerIpLabel.setTextFill(Color.WHITE);
        
        peerIpField = new TextField();
        peerIpField.setPromptText("输入对方IP地址");
        peerIpField.setPrefWidth(200);
        HBox.setHgrow(peerIpField, Priority.ALWAYS);
        
        connectButton = new Button("连接");
        connectButton.setOnAction(e -> connectToPeer());
        
        connectionStatusLabel = new Label("未连接");
        connectionStatusLabel.setTextFill(Color.RED);
        
        connectionBox.getChildren().addAll(myIpLabel, peerIpLabel, peerIpField, connectButton, connectionStatusLabel);
        
        // 创建聊天消息区域
        VBox chatContainer = new VBox(10);
        chatContainer.setStyle("-fx-background-color: #34495E; -fx-padding: 15; -fx-background-radius: 5;");
        chatContainer.setMaxWidth(Double.MAX_VALUE);
        chatContainer.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(chatContainer, Priority.ALWAYS);
        
        // 创建聊天消息显示区域
        chatMessagesBox = new VBox(10);
        chatMessagesBox.setPadding(new Insets(10));
        chatMessagesBox.setMaxWidth(Double.MAX_VALUE);
        chatMessagesBox.setMaxHeight(Double.MAX_VALUE);
        
        chatScrollPane = new ScrollPane(chatMessagesBox);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.setPrefHeight(350);
        chatScrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        // 创建消息输入区域
        HBox messageInputBox = new HBox(10);
        messageInputBox.setAlignment(Pos.CENTER);
        
        messageField = new TextField();
        messageField.setPromptText("输入消息...");
        messageField.setPrefWidth(500);
        messageField.setOnAction(e -> sendMessage()); // 按Enter发送消息
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        Button sendButton = new Button("发送");
        sendButton.setOnAction(e -> sendMessage());
        
        messageInputBox.getChildren().addAll(messageField, sendButton);
        
        // 将所有组件添加到聊天容器
        chatContainer.getChildren().addAll(chatScrollPane, messageInputBox);
        
        // 将所有组件添加到主布局
        chatLayout.getChildren().addAll(titleLabel, connectionBox, chatContainer);
        
        // 更新内容区域
        content.getChildren().clear();
        content.getChildren().add(chatLayout);
        
        // 如果已经连接过，加载聊天历史
        if (currentPeer != null) {
            loadChatHistory(currentPeer);
        }
    }
    
    // 连接到对方
    private void connectToPeer() {
        String peerIp = peerIpField.getText().trim();
        if (peerIp.isEmpty()) {
            showAlert("错误", "请输入对方的IP地址");
            return;
        }
        
        boolean connected = ChatService.connectToPeer(peerIp, username);
        if (connected) {
            connectionStatusLabel.setText("已连接");
            connectionStatusLabel.setTextFill(Color.GREEN);
            connectButton.setDisable(true);
            currentPeer = peerIp;
            
            // 加载聊天历史
            loadChatHistory(peerIp);
        } else {
            connectionStatusLabel.setText("连接失败");
            connectionStatusLabel.setTextFill(Color.RED);
        }
    }
    
    // 加载聊天历史
    private void loadChatHistory(String peerIp) {
        chatMessagesBox.getChildren().clear();
        
        List<ChatMessage> messages = ChatService.getChatHistory(peerIp);
        for (ChatMessage message : messages) {
            addMessageToChat(message);
        }
        
        // 滚动到底部
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }
    
    // 发送消息
    private void sendMessage() {
        String messageContent = messageField.getText().trim();
        if (messageContent.isEmpty()) {
            return;
        }
        
        boolean sent = ChatService.sendMessage(messageContent);
        if (sent) {
            messageField.clear();
            
            // 刷新聊天历史
            loadChatHistory(currentPeer);
        } else {
            showAlert("错误", "发送消息失败，请确保已连接到对方");
        }
    }
    
    // 添加消息到聊天界面
    private void addMessageToChat(ChatMessage message) {
        HBox messageBox = new HBox(10);
        messageBox.setPadding(new Insets(5));
        messageBox.setMaxWidth(Double.MAX_VALUE);
        
        VBox messageContent = new VBox(5);
        messageContent.setPadding(new Insets(10));
        messageContent.setMaxWidth(500);
        
        Label senderLabel = new Label(message.getSender() + ":");
        senderLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label contentLabel = new Label(message.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(Double.MAX_VALUE);
        
        Label timeLabel = new Label(message.getTimestamp().substring(0, 16)); // 只显示日期和时间
        timeLabel.setFont(Font.font("System", FontWeight.LIGHT, 10));
        timeLabel.setTextFill(Color.GRAY);
        
        messageContent.getChildren().addAll(senderLabel, contentLabel, timeLabel);
        
        // 根据发送者调整消息的对齐方式
        if (message.getSender().equals(username)) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            messageContent.setStyle("-fx-background-color: #DCF8C6; -fx-padding: 10; -fx-background-radius: 10 0 10 10;");
            messageBox.getChildren().add(messageContent);
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
            messageContent.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 10; -fx-background-radius: 0 10 10 10;");
            messageBox.getChildren().add(messageContent);
        }
        
        chatMessagesBox.getChildren().add(messageBox);
        
        // 滚动到底部
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }
    
    // 显示警告对话框
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // 控制侧边栏展开/收缩
    private void toggleSidebar(VBox sidebar) {
        double targetWidth = isSidebarExpanded ? 0 : 200;
        
        // 使用动画平滑过渡
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(sidebar.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH);
        KeyFrame kf = new KeyFrame(Duration.millis(300), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
        
        // 更改展开状态
        isSidebarExpanded = !isSidebarExpanded;
    }

    // 打开批量删除对话框
    private void openBatchDeleteDialog(ListView<HBox> diaryListView, String keyword, String startDate, String endDate) {
        // 创建对话框
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("批量删除日记");
        dialog.setHeaderText("选择要删除的日记");
        
        // 设置按钮
        ButtonType deleteButtonType = new ButtonType("删除选中", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);
        
        // 创建内容区域
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        
        // 获取日记列表
        List<Diary> diaries;
        if (keyword != null || startDate != null || endDate != null) {
            diaries = DiaryService.searchDiaries(keyword, startDate, endDate);
        } else {
            diaries = DiaryService.getAllDiaries();
        }
        
        // 创建复选框列表
        VBox checkboxList = new VBox(5);
        List<CheckBox> checkBoxes = new ArrayList<>();
        
        for (Diary diary : diaries) {
            CheckBox checkBox = new CheckBox();
            
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setPadding(new Insets(5));
            item.getStyleClass().add("diary-item");
            
            VBox diaryInfo = new VBox(3);
            Label dateLabel = new Label("日期: " + diary.getDate());
            dateLabel.getStyleClass().add("diary-date");
            
            Label contentLabel = new Label(diary.getContent().length() > 50 ? 
                    diary.getContent().substring(0, 50) + "..." : diary.getContent());
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("diary-content");
            
            diaryInfo.getChildren().addAll(dateLabel, contentLabel);
            
            item.getChildren().addAll(checkBox, diaryInfo);
            
            checkBoxes.add(checkBox);
            checkboxList.getChildren().add(item);
        }
        
        // 创建全选/取消全选复选框
        CheckBox selectAllCheckBox = new CheckBox("全选/取消全选");
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            for (CheckBox cb : checkBoxes) {
                cb.setSelected(selected);
            }
        });
        
        // 添加到内容区域
        content.getChildren().addAll(selectAllCheckBox, new Separator(), checkboxList);
        
        // 创建滚动面板
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        
        dialog.getDialogPane().setContent(scrollPane);
        
        // 处理结果
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == deleteButtonType) {
            // 确认删除
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("确认删除");
            
            // 计算选中的数量
            int selectedCount = 0;
            for (CheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    selectedCount++;
                }
            }
            
            confirmAlert.setHeaderText("您确定要删除选中的 " + selectedCount + " 条日记吗？");
            confirmAlert.setContentText("此操作不可撤销。");
            
            Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                // 执行删除
                for (int i = 0; i < checkBoxes.size(); i++) {
                    if (checkBoxes.get(i).isSelected()) {
                        DiaryService.deleteDiary(diaries.get(i).getId());
                    }
                }
                
                // 刷新列表
                loadDiaries(diaryListView, keyword, startDate, endDate);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
