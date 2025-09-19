package com.example.message.controllers;

import com.example.message.model.ChatMessage;
import com.example.message.services.ChatService;
import com.example.message.services.ChatServiceExtensions;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class LogsController {
    
    private VBox logsContainer;
    private TextField searchField;
    
    public Node createLogsView() {
        VBox logsPanel = new VBox();
        logsPanel.getStyleClass().addAll("content-panel", "logs-panel");
        logsPanel.setSpacing(20);
        logsPanel.setPadding(new Insets(20));
        VBox.setVgrow(logsPanel, Priority.ALWAYS);
        
        // 标题区域
        HBox headerBox = createHeaderSection();
        
        // 搜索区域
        HBox searchBox = createSearchSection();
        
        // 聊天记录区域
        ScrollPane scrollPane = createLogsSection();
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // 操作按钮区域
        HBox buttonsBox = createButtonsSection();
        
        logsPanel.getChildren().addAll(headerBox, searchBox, scrollPane, buttonsBox);
        
        // 加载聊天记录
        loadChatLogs();
        
        return logsPanel;
    }
    
    private HBox createHeaderSection() {
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(20);
        
        Label titleLabel = new Label("📖 我们的聊天记录");
        titleLabel.getStyleClass().add("page-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label infoLabel = new Label("💕 记录我们的每一句话");
        infoLabel.getStyleClass().add("info-label");
        
        headerBox.getChildren().addAll(titleLabel, spacer, infoLabel);
        return headerBox;
    }
    
    private HBox createSearchSection() {
        HBox searchBox = new HBox();
        searchBox.getStyleClass().add("search-section");
        searchBox.setSpacing(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(15));
        
        Label searchLabel = new Label("🔍");
        searchLabel.getStyleClass().add("search-icon");
        
        searchField = new TextField();
        searchField.getStyleClass().add("search-field");
        searchField.setPromptText("搜索聊天记录...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().add("search-button");
        searchButton.setOnAction(e -> searchLogs());
        
        Button clearSearchButton = new Button("清除");
        clearSearchButton.getStyleClass().add("clear-search-button");
        clearSearchButton.setOnAction(e -> {
            searchField.clear();
            loadChatLogs();
        });
        
        searchBox.getChildren().addAll(searchLabel, searchField, searchButton, clearSearchButton);
        return searchBox;
    }
    
    private ScrollPane createLogsSection() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("logs-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        
        logsContainer = new VBox();
        logsContainer.getStyleClass().add("logs-container");
        logsContainer.setSpacing(10);
        logsContainer.setPadding(new Insets(15));
        
        scrollPane.setContent(logsContainer);
        return scrollPane;
    }
    
    private HBox createButtonsSection() {
        HBox buttonsBox = new HBox();
        buttonsBox.getStyleClass().add("buttons-section");
        buttonsBox.setSpacing(10);
        buttonsBox.setAlignment(Pos.CENTER_RIGHT);
        buttonsBox.setPadding(new Insets(10));
        
        Button refreshButton = new Button("🔄 刷新");
        refreshButton.getStyleClass().add("refresh-button");
        refreshButton.setOnAction(e -> {
            loadChatLogs();
            showAlert("刷新完成", "聊天记录已刷新 💕");
        });
        
        Button exportButton = new Button("📤 导出");
        exportButton.getStyleClass().add("export-button");
        exportButton.setOnAction(e -> exportLogs());
        
        Button clearButton = new Button("🗑️ 清空");
        clearButton.getStyleClass().add("clear-button");
        clearButton.setOnAction(e -> clearLogs());
        
        buttonsBox.getChildren().addAll(refreshButton, exportButton, clearButton);
        return buttonsBox;
    }
    
    private void loadChatLogs() {
        logsContainer.getChildren().clear();
        
        List<ChatMessage> messages = ChatServiceExtensions.getAllChatHistory();
        if (messages != null && !messages.isEmpty()) {
            for (ChatMessage message : messages) {
                Node messageNode = createLogEntry(message);
                logsContainer.getChildren().add(messageNode);
            }
        } else {
            Label noLogsLabel = new Label("还没有聊天记录 💔\n开始聊天来创建美好回忆吧 💕");
            noLogsLabel.getStyleClass().add("no-logs-label");
            noLogsLabel.setAlignment(Pos.CENTER);
            noLogsLabel.setMaxWidth(Double.MAX_VALUE);
            logsContainer.getChildren().add(noLogsLabel);
        }
    }
    
    private void searchLogs() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            loadChatLogs();
            return;
        }
        
        logsContainer.getChildren().clear();
        
        List<ChatMessage> filteredMessages = ChatServiceExtensions.searchChatHistory(keyword);
        if (filteredMessages != null && !filteredMessages.isEmpty()) {
            for (ChatMessage message : filteredMessages) {
                Node messageNode = createLogEntry(message);
                logsContainer.getChildren().add(messageNode);
            }
        } else {
            Label noResultsLabel = new Label("没有找到包含 \"" + keyword + "\" 的聊天记录 😢");
            noResultsLabel.getStyleClass().add("no-results-label");
            noResultsLabel.setAlignment(Pos.CENTER);
            noResultsLabel.setMaxWidth(Double.MAX_VALUE);
            logsContainer.getChildren().add(noResultsLabel);
        }
    }
    
    private Node createLogEntry(ChatMessage message) {
        VBox logEntry = new VBox();
        logEntry.getStyleClass().add("log-entry");
        logEntry.setSpacing(8);
        logEntry.setPadding(new Insets(12));
        
        // 消息头部信息
        HBox headerInfo = new HBox();
        headerInfo.setAlignment(Pos.CENTER_LEFT);
        headerInfo.setSpacing(15);
        
        Label senderLabel = new Label("👤 " + message.getSender());
        senderLabel.getStyleClass().add("log-sender");
        
        Label receiverLabel = new Label("→ " + message.getReceiver());
        receiverLabel.getStyleClass().add("log-receiver");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label timeLabel = new Label("🕐 " + message.getTimestamp().format(
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")));
        timeLabel.getStyleClass().add("log-time");
        
        headerInfo.getChildren().addAll(senderLabel, receiverLabel, spacer, timeLabel);
        
        // 消息内容
        Label contentLabel = new Label(message.getContent());
        contentLabel.getStyleClass().add("log-content");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(Double.MAX_VALUE);
        
        logEntry.getChildren().addAll(headerInfo, contentLabel);
        
        return logEntry;
    }
    
    private void exportLogs() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("导出聊天记录");
        alert.setHeaderText("导出功能");
        alert.setContentText("导出功能正在开发中... 💕\n敬请期待！");
        alert.showAndWait();
    }
    
    private void clearLogs() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认清空");
        alert.setHeaderText("清空聊天记录");
        alert.setContentText("确定要清空所有聊天记录吗？\n这个操作无法撤销 💔");
        
        // 美化确认对话框
        alert.getDialogPane().getStylesheets().add(
            getClass().getResource("/com/example/message/styles/elegant-theme.css").toExternalForm()
        );
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ChatServiceExtensions.clearChatHistory();
            loadChatLogs();
            showAlert("清空完成", "聊天记录已清空");
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // 美化提示对话框
        alert.getDialogPane().getStylesheets().add(
            getClass().getResource("/com/example/message/styles/elegant-theme.css").toExternalForm()
        );
        
        alert.showAndWait();
    }
}