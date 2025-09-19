package com.example.message.controllers;

import com.example.message.core.CoupleApp;
import com.example.message.core.EventBus;
import com.example.message.model.Diary;
import com.example.message.services.DiaryService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class DiaryController {
    
    private TextArea diaryContentArea;
    private ComboBox<String> moodSelector;
    private ListView<Diary> diaryListView;
    private int currentDiaryId = -1;
    
    public DiaryController() {
        setupEventListeners();
    }
    
    private void setupEventListeners() {
        EventBus.getInstance().subscribe(EventBus.Events.DIARY_SAVED, this::onDiarySaved);
        EventBus.getInstance().subscribe(EventBus.Events.DIARY_UPDATED, this::onDiaryUpdated);
        EventBus.getInstance().subscribe(EventBus.Events.DIARY_DELETED, this::onDiaryDeleted);
    }
    
    public Node createDiaryView() {
        VBox diaryPanel = new VBox();
        diaryPanel.getStyleClass().addAll("content-panel", "diary-panel");
        diaryPanel.setSpacing(20);
        diaryPanel.setPadding(new Insets(20));
        VBox.setVgrow(diaryPanel, Priority.ALWAYS);
        
        // 标题区域
        HBox headerBox = createHeaderSection();
        
        // 主要内容区域 - 分割面板
        SplitPane splitPane = createMainContent();
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        
        diaryPanel.getChildren().addAll(headerBox, splitPane);
        
        // 加载日记列表
        loadDiaries();
        
        return diaryPanel;
    }
    
    private HBox createHeaderSection() {
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(20);
        
        Label titleLabel = new Label("💝 我们的心情日记");
        titleLabel.getStyleClass().add("page-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // 云同步状态
        HBox syncStatus = new HBox(10);
        syncStatus.setAlignment(Pos.CENTER_RIGHT);
        
        Label syncLabel = new Label("☁️ 云同步已启用");
        syncLabel.getStyleClass().add("sync-status");
        
        Button refreshButton = new Button("🔄 刷新");
        refreshButton.getStyleClass().add("refresh-button");
        refreshButton.setOnAction(e -> refreshFromCloud());
        
        syncStatus.getChildren().addAll(syncLabel, refreshButton);
        
        headerBox.getChildren().addAll(titleLabel, spacer, syncStatus);
        return headerBox;
    }
    
    private SplitPane createMainContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("diary-split-pane");
        
        // 左侧：日记列表
        VBox listSection = createListSection();
        
        // 右侧：日记编辑
        VBox editSection = createEditSection();
        
        splitPane.getItems().addAll(listSection, editSection);
        splitPane.setDividerPositions(0.4);
        
        return splitPane;
    }
    
    private VBox createListSection() {
        VBox listSection = new VBox();
        listSection.getStyleClass().add("diary-list-section");
        listSection.setSpacing(15);
        listSection.setPadding(new Insets(15));
        
        // 搜索区域
        VBox searchBox = createSearchSection();
        
        // 日记列表
        diaryListView = new ListView<>();
        diaryListView.getStyleClass().add("diary-list");
        VBox.setVgrow(diaryListView, Priority.ALWAYS);
        
        // 设置自定义单元格
        diaryListView.setCellFactory(createDiaryCellFactory());
        
        listSection.getChildren().addAll(searchBox, diaryListView);
        return listSection;
    }
    
    private VBox createSearchSection() {
        VBox searchBox = new VBox(10);
        searchBox.getStyleClass().add("search-section");
        
        Label searchLabel = new Label("🔍 搜索日记");
        searchLabel.getStyleClass().add("section-label");
        
        HBox searchControls = new HBox(10);
        searchControls.setAlignment(Pos.CENTER_LEFT);
        
        TextField searchField = new TextField();
        searchField.setPromptText("搜索关键词...");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("开始日期");
        startDate.getStyleClass().add("date-picker");
        
        DatePicker endDate = new DatePicker();
        endDate.setPromptText("结束日期");
        endDate.getStyleClass().add("date-picker");
        
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().add("search-button");
        searchButton.setOnAction(e -> {
            String keyword = searchField.getText();
            LocalDate start = startDate.getValue();
            LocalDate end = endDate.getValue();
            searchDiaries(keyword, start, end);
        });
        
        searchControls.getChildren().addAll(searchField, startDate, endDate, searchButton);
        searchBox.getChildren().addAll(searchLabel, searchControls);
        
        return searchBox;
    }
    
    private VBox createEditSection() {
        VBox editSection = new VBox();
        editSection.getStyleClass().add("diary-edit-section");
        editSection.setSpacing(15);
        editSection.setPadding(new Insets(15));
        
        Label editLabel = new Label("✍️ 记录今天的心情");
        editLabel.getStyleClass().add("section-label");
        
        // 心情选择器
        HBox moodBox = new HBox(10);
        moodBox.setAlignment(Pos.CENTER_LEFT);
        
        Label moodLabel = new Label("心情:");
        moodLabel.getStyleClass().add("field-label");
        
        moodSelector = new ComboBox<>();
        moodSelector.getStyleClass().add("mood-selector");
        moodSelector.getItems().addAll("😊 开心", "😢 悲伤", "😠 愤怒", "😌 平静", "😰 焦虑", "🤩 兴奋", "😴 困倦", "🥰 甜蜜");
        moodSelector.setValue("😊 开心");
        
        moodBox.getChildren().addAll(moodLabel, moodSelector);
        
        // 内容编辑区
        VBox contentEditBox = new VBox(8);
        
        diaryContentArea = new TextArea();
        diaryContentArea.getStyleClass().add("diary-content-area");
        diaryContentArea.setPromptText("写下今天发生的美好事情...\n记录我们的点点滴滴 💕");
        diaryContentArea.setPrefRowCount(12);
        VBox.setVgrow(diaryContentArea, Priority.ALWAYS);
        
        // 字数统计
        Label charCountLabel = new Label("0 字");
        charCountLabel.getStyleClass().add("char-count-label");
        charCountLabel.setAlignment(Pos.CENTER_RIGHT);
        charCountLabel.setMaxWidth(Double.MAX_VALUE);
        
        // 添加字数统计监听器
        diaryContentArea.textProperty().addListener((obs, oldText, newText) -> {
            int charCount = newText.length();
            charCountLabel.setText(charCount + " 字");
            
            // 根据字数改变颜色
            if (charCount > 1000) {
                charCountLabel.setStyle("-fx-text-fill: #f56565;"); // 红色
            } else if (charCount > 500) {
                charCountLabel.setStyle("-fx-text-fill: #ed8936;"); // 橙色
            } else {
                charCountLabel.setStyle("-fx-text-fill: #718096;"); // 灰色
            }
        });
        
        contentEditBox.getChildren().addAll(diaryContentArea, charCountLabel);
        
        // 操作按钮
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("clear-button");
        clearButton.setOnAction(e -> clearForm());
        
        Button saveButton = new Button("💾 保存日记");
        saveButton.getStyleClass().add("save-button");
        saveButton.setOnAction(e -> saveDiary());
        
        buttonBox.getChildren().addAll(clearButton, saveButton);
        
        editSection.getChildren().addAll(editLabel, moodBox, contentEditBox, buttonBox);
        return editSection;
    }
    
    private Callback<ListView<Diary>, ListCell<Diary>> createDiaryCellFactory() {
        return param -> new ListCell<Diary>() {
            private Map<Integer, Boolean> expandedStates = new HashMap<>();
            
            @Override
            protected void updateItem(Diary diary, boolean empty) {
                super.updateItem(diary, empty);
                if (empty || diary == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                
                // 获取当前日记的展开状态
                boolean isExpanded = expandedStates.getOrDefault(diary.getId(), false);
                
                VBox diaryItem = new VBox();
                diaryItem.getStyleClass().add("diary-item");
                diaryItem.setSpacing(12);
                
                // 头部信息区域
                HBox headerBox = new HBox();
                headerBox.setAlignment(Pos.CENTER_LEFT);
                headerBox.setSpacing(12);
                
                // 日期和心情
                VBox metaInfo = new VBox(4);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
                Label dateLabel = new Label(diary.getDate().format(formatter));
                dateLabel.getStyleClass().add("diary-date");
                
                Label moodLabel = new Label(diary.getMood());
                moodLabel.getStyleClass().add("diary-mood");
                
                metaInfo.getChildren().addAll(dateLabel, moodLabel);
                
                // 右侧操作区域
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                HBox actionBox = new HBox(8);
                actionBox.setAlignment(Pos.CENTER_RIGHT);
                
                Button editButton = new Button("编辑");
                editButton.getStyleClass().add("edit-button-small");
                editButton.setOnAction(event -> {
                    event.consume(); // 防止事件冒泡
                    editDiary(diary);
                });
                
                Button deleteButton = new Button("删除");
                deleteButton.getStyleClass().add("delete-button-small");
                deleteButton.setOnAction(event -> {
                    event.consume(); // 防止事件冒泡
                    deleteDiary(diary);
                });
                
                actionBox.getChildren().addAll(editButton, deleteButton);
                
                headerBox.getChildren().addAll(metaInfo, spacer, actionBox);
                
                // 内容区域 - 智能显示，支持换行
                VBox contentBox = new VBox(8);
                String content = diary.getContent();
                
                // 判断内容长度，决定显示方式
                boolean isLongContent = content.length() > 150;
                
                // 使用TextFlow来更好地处理文本格式
                TextFlow contentFlow = new TextFlow();
                contentFlow.getStyleClass().add("diary-content-flow");
                contentFlow.setMaxWidth(Double.MAX_VALUE);
                
                Text contentText = new Text();
                contentText.getStyleClass().add(isExpanded ? "diary-content-full" : "diary-content-preview");
                
                if (isLongContent) {
                    // 长内容：显示预览 + 展开按钮
                    String displayText = isExpanded ? content : content.substring(0, 150) + "...";
                    contentText.setText(displayText);
                    contentFlow.getChildren().add(contentText);
                    
                    Button expandButton = new Button(isExpanded ? "收起" : "展开全文");
                    expandButton.getStyleClass().add("expand-button");
                    expandButton.setOnAction(event -> {
                        event.consume(); // 防止事件冒泡
                        expandedStates.put(diary.getId(), !isExpanded);
                        // 刷新ListView
                        getListView().refresh();
                    });
                    
                    contentBox.getChildren().addAll(contentFlow, expandButton);
                } else {
                    // 短内容：直接显示
                    contentText.setText(content);
                    contentFlow.getChildren().add(contentText);
                    contentBox.getChildren().add(contentFlow);
                }
                
                // 添加分割线（除了最后一项）
                if (getIndex() < getListView().getItems().size() - 1) {
                    Region separator = new Region();
                    separator.getStyleClass().add("diary-separator");
                    separator.setPrefHeight(1);
                    separator.setMaxHeight(1);
                    separator.setStyle("-fx-background-color: #e2e8f0; -fx-margin: 8 0;");
                    diaryItem.getChildren().addAll(headerBox, contentBox, separator);
                } else {
                    diaryItem.getChildren().addAll(headerBox, contentBox);
                }
                
                setGraphic(diaryItem);
                setText(null);
            }
        };
    }
    
    private void loadDiaries() {
        Platform.runLater(() -> {
            diaryListView.getItems().clear();
            List<Diary> diaries = DiaryService.getAllDiaries();
            if (diaries != null && !diaries.isEmpty()) {
                diaryListView.getItems().addAll(diaries);
            }
        });
    }
    
    private void searchDiaries(String keyword, LocalDate startDate, LocalDate endDate) {
        Platform.runLater(() -> {
            diaryListView.getItems().clear();
            
            String startDateStr = startDate != null ? startDate.toString() : null;
            String endDateStr = endDate != null ? endDate.toString() : null;
            
            List<Diary> diaries = DiaryService.searchDiaries(keyword, startDateStr, endDateStr);
            if (diaries != null && !diaries.isEmpty()) {
                diaryListView.getItems().addAll(diaries);
            }
        });
    }
    
    private void saveDiary() {
        String content = diaryContentArea.getText().trim();
        String mood = moodSelector.getValue();
        
        if (content.isEmpty()) {
            showError("内容为空", "请输入日记内容 💕");
            return;
        }
        
        try {
            int result;
            if (currentDiaryId != -1) {
                // 更新现有日记
                boolean success = DiaryService.updateDiary(currentDiaryId, content, mood);
                if (success) {
                    showAlert("更新成功", "日记已更新 💕");
                    EventBus.getInstance().publish(EventBus.Events.DIARY_UPDATED, currentDiaryId);
                    result = currentDiaryId;
                } else {
                    showError("更新失败", "无法更新日记，请重试");
                    return;
                }
            } else {
                // 添加新日记
                result = DiaryService.addDiary(content, mood);
                if (result > 0) {
                    showAlert("保存成功", "日记已保存到云端 ☁️💕");
                    EventBus.getInstance().publish(EventBus.Events.DIARY_SAVED, result);
                } else {
                    showError("保存失败", "无法保存日记，请重试");
                    return;
                }
            }
            
            clearForm();
            loadDiaries();
            
        } catch (Exception e) {
            showError("操作失败", "发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void editDiary(Diary diary) {
        diaryContentArea.setText(diary.getContent());
        moodSelector.setValue(diary.getMood());
        currentDiaryId = diary.getId();
    }
    
    private void deleteDiary(Diary diary) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除日记");
        alert.setContentText("确定要删除这篇日记吗？\n删除后无法恢复 💔");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    DiaryService.deleteDiary(diary.getId());
                    showAlert("删除成功", "日记已删除");
                    EventBus.getInstance().publish(EventBus.Events.DIARY_DELETED, diary.getId());
                    loadDiaries();
                } catch (Exception e) {
                    showError("删除失败", e.getMessage());
                }
            }
        });
    }
    
    private void clearForm() {
        diaryContentArea.clear();
        moodSelector.setValue("😊 开心");
        currentDiaryId = -1;
    }
    
    private void refreshFromCloud() {
        DiaryService.refreshFromCloud();
        loadDiaries();
        showAlert("刷新成功", "已从云端获取最新日记 ☁️💕");
    }
    
    private void onDiarySaved(Object diaryId) {
        System.out.println("日记保存事件: " + diaryId);
    }
    
    private void onDiaryUpdated(Object diaryId) {
        System.out.println("日记更新事件: " + diaryId);
    }
    
    private void onDiaryDeleted(Object diaryId) {
        System.out.println("日记删除事件: " + diaryId);
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
}