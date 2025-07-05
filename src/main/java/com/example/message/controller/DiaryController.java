package com.example.message.controller;

import com.example.message.model.Diary;
import com.example.message.services.DiaryService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 日记控制器 - 负责处理日记UI相关逻辑
 */
public class DiaryController {
    
    private final ObservableList<Diary> diaries = FXCollections.observableArrayList();
    private int currentDiaryId = -1;
    
    // UI 组件
    private ListView<Diary> diaryListView;
    private TextArea diaryContentArea;
    private ComboBox<String> moodSelector;
    private TextField titleField;
    private TextField tagField;
    private Button saveButton;
    private Button newDiaryButton;
    private Button deleteButton;
    private TextField searchField;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Button searchButton;
    private Button clearSearchButton;
    
    /**
     * 创建日记界面
     */
    public VBox createDiaryView() {
        VBox diaryView = new VBox();
        diaryView.setSpacing(15);
        diaryView.setPadding(new Insets(20));
        diaryView.setStyle("-fx-background-color: #232a38;");
        
        // 主布局
        HBox mainLayout = new HBox();
        mainLayout.setSpacing(15);
        
        // 左侧日记列表
        VBox leftPanel = createDiaryListPanel();
        leftPanel.setPrefWidth(350);
        leftPanel.setMinWidth(350);
        
        // 右侧日记编辑区域
        VBox rightPanel = createDiaryEditPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        
        mainLayout.getChildren().addAll(leftPanel, rightPanel);
        
        diaryView.getChildren().add(mainLayout);
        
        // 初始化数据
        loadDiaries();
        
        return diaryView;
    }
    
    /**
     * 创建日记列表面板
     */
    private VBox createDiaryListPanel() {
        VBox listPanel = new VBox();
        listPanel.setSpacing(15);
        listPanel.setPadding(new Insets(20));
        listPanel.getStyleClass().add("content-box");
        listPanel.setStyle("-fx-background-color: #2f3747; -fx-background-radius: 12px; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 12px;");
        
        // 标题
        Label titleLabel = new Label("📝 我的日记");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e9edf5;");
        
        // 搜索区域
        VBox searchArea = createSearchArea();
        
        // 日记列表
        diaryListView = new ListView<>(diaries);
        diaryListView.setPrefHeight(450);
        diaryListView.getStyleClass().add("list-view");
        diaryListView.setStyle("-fx-background-color: #343e54; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px;");
        diaryListView.setCellFactory(listView -> new DiaryListCell());
        diaryListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Diary selectedDiary = diaryListView.getSelectionModel().getSelectedItem();
                if (selectedDiary != null) {
                    loadDiaryForEdit(selectedDiary);
                }
            }
        });
        VBox.setVgrow(diaryListView, Priority.ALWAYS);
        
        // 按钮区域
        HBox buttonArea = createListButtonArea();
        
        listPanel.getChildren().addAll(titleLabel, searchArea, diaryListView, buttonArea);
        
        return listPanel;
    }
    
    /**
     * 创建搜索区域
     */
    private VBox createSearchArea() {
        VBox searchArea = new VBox();
        searchArea.setSpacing(5);
        
        // 关键字搜索
        searchField = new TextField();
        searchField.setPromptText("搜索日记内容...");
        
        // 日期范围选择
        HBox dateRange = new HBox();
        dateRange.setSpacing(5);
        dateRange.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("日期范围:");
        dateLabel.setStyle("-fx-font-size: 12px;");
        
        startDatePicker = new DatePicker();
        startDatePicker.setPromptText("开始日期");
        
        Label toLabel = new Label("至");
        toLabel.setStyle("-fx-font-size: 12px;");
        
        endDatePicker = new DatePicker();
        endDatePicker.setPromptText("结束日期");
        
        dateRange.getChildren().addAll(dateLabel, startDatePicker, toLabel, endDatePicker);
        
        // 搜索按钮
        HBox searchButtons = new HBox();
        searchButtons.setSpacing(5);
        
        searchButton = new Button("搜索");
        searchButton.setOnAction(e -> performSearch());
        
        clearSearchButton = new Button("清除");
        clearSearchButton.setOnAction(e -> clearSearch());
        
        searchButtons.getChildren().addAll(searchButton, clearSearchButton);
        
        searchArea.getChildren().addAll(searchField, dateRange, searchButtons);
        
        return searchArea;
    }
    
    /**
     * 创建列表按钮区域
     */
    private HBox createListButtonArea() {
        HBox buttonArea = new HBox();
        buttonArea.setSpacing(10);
        buttonArea.setAlignment(Pos.CENTER);
        
        newDiaryButton = new Button("新建日记");
        newDiaryButton.setOnAction(e -> createNewDiary());
        newDiaryButton.setStyle("-fx-background-color: #007AFF; -fx-text-fill: white;");
        
        deleteButton = new Button("删除日记");
        deleteButton.setOnAction(e -> deleteDiary());
        deleteButton.setStyle("-fx-background-color: #FF3B30; -fx-text-fill: white;");
        
        buttonArea.getChildren().addAll(newDiaryButton, deleteButton);
        
        return buttonArea;
    }
    
    /**
     * 创建日记编辑面板
     */
    private VBox createDiaryEditPanel() {
        VBox editPanel = new VBox();
        editPanel.setSpacing(15);
        editPanel.setPadding(new Insets(20));
        editPanel.getStyleClass().add("content-box");
        editPanel.setStyle("-fx-background-color: #2f3747; -fx-background-radius: 12px; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 12px;");
        
        // 编辑标题
        Label editTitle = new Label("✏️ 编辑日记");
        editTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-padding: 0 0 10 0;");
        
        // 标题输入
        HBox titleBox = new HBox();
        titleBox.setSpacing(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("标题:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-min-width: 60px;");
        
        titleField = new TextField();
        titleField.setPromptText("请输入日记标题...");
        titleField.getStyleClass().add("text-field");
        titleField.setStyle("-fx-background-color: #343e54; -fx-text-fill: #e9edf5; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px;");
        HBox.setHgrow(titleField, Priority.ALWAYS);
        
        titleBox.getChildren().addAll(titleLabel, titleField);
        
        // 心情和标签
        HBox moodTagBox = new HBox();
        moodTagBox.setSpacing(15);
        moodTagBox.setAlignment(Pos.CENTER_LEFT);
        
        Label moodLabel = new Label("心情:");
        moodLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-min-width: 60px;");
        
        moodSelector = new ComboBox<>();
        moodSelector.getItems().addAll("😊开心", "😢难过", "😡生气", "😴疲惫", "😍兴奋", "😐平静", "😨焦虑", "🤔思考");
        moodSelector.setPromptText("选择心情");
        moodSelector.getStyleClass().add("combo-box");
        moodSelector.setStyle("-fx-background-color: #343e54; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        Label tagLabel = new Label("标签:");
        tagLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e9edf5; -fx-min-width: 60px;");
        
        tagField = new TextField();
        tagField.setPromptText("标签用逗号分隔");
        tagField.getStyleClass().add("text-field");
        tagField.setStyle("-fx-background-color: #343e54; -fx-text-fill: #e9edf5; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px;");
        HBox.setHgrow(tagField, Priority.ALWAYS);
        
        moodTagBox.getChildren().addAll(moodLabel, moodSelector, tagLabel, tagField);
        
        // 内容编辑区
        Label contentLabel = new Label("内容:");
        contentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e9edf5;");
        
        diaryContentArea = new TextArea();
        diaryContentArea.setPromptText("今天发生了什么...");
        diaryContentArea.setWrapText(true);
        diaryContentArea.getStyleClass().add("text-area");
        diaryContentArea.setStyle("-fx-background-color: #343e54; -fx-text-fill: #e9edf5; -fx-border-color: #464e63; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 12px;");
        VBox.setVgrow(diaryContentArea, Priority.ALWAYS);
        
        // 保存按钮
        saveButton = new Button("💾 保存日记");
        saveButton.setOnAction(e -> saveDiary());
        saveButton.getStyleClass().add("button");
        saveButton.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px 20px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        
        editPanel.getChildren().addAll(editTitle, titleBox, moodTagBox, contentLabel, diaryContentArea, saveButton);
        
        return editPanel;
    }
    
    /**
     * 加载日记列表
     */
    private void loadDiaries() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Diary> diaryList = DiaryService.getAllDiaries();
                Platform.runLater(() -> {
                    diaries.clear();
                    if (diaryList != null) {
                        diaries.addAll(diaryList);
                    }
                    System.out.println("日记列表加载完成，共 " + diaries.size() + " 条记录");
                });
            } catch (Exception e) {
                System.err.println("加载日记列表时出错: " + e.getMessage());
                Platform.runLater(() -> {
                    showAlert("错误", "加载日记列表失败: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * 执行搜索
     */
    private void performSearch() {
        String keyword = searchField.getText().trim();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        
        CompletableFuture.runAsync(() -> {
            try {
                List<Diary> searchResults = DiaryService.searchDiaries(keyword, startDate, endDate);
                Platform.runLater(() -> {
                    diaries.clear();
                    if (searchResults != null) {
                        diaries.addAll(searchResults);
                    }
                    System.out.println("搜索完成，找到 " + diaries.size() + " 条记录");
                });
            } catch (Exception e) {
                System.err.println("搜索日记时出错: " + e.getMessage());
                Platform.runLater(() -> {
                    showAlert("错误", "搜索日记失败: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * 清除搜索
     */
    private void clearSearch() {
        searchField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        loadDiaries();
    }
    
    /**
     * 创建新日记
     */
    private void createNewDiary() {
        currentDiaryId = -1;
        titleField.clear();
        diaryContentArea.clear();
        moodSelector.getSelectionModel().clearSelection();
        tagField.clear();
        titleField.requestFocus();
    }
    
    /**
     * 加载日记进行编辑
     */
    private void loadDiaryForEdit(Diary diary) {
        currentDiaryId = diary.getId();
        
        // 解析内容，分离标题和内容
        String content = diary.getContent();
        String title = "";
        String bodyContent = content;
        
        // 如果内容包含换行符，第一行作为标题
        if (content.contains("\n\n")) {
            String[] parts = content.split("\n\n", 2);
            title = parts[0];
            bodyContent = parts[1];
        } else if (content.length() > 50) {
            // 如果内容较长，取前50个字符作为标题
            title = content.substring(0, 50) + "...";
            bodyContent = content;
        } else {
            title = content;
            bodyContent = "";
        }
        
        titleField.setText(title);
        diaryContentArea.setText(bodyContent);
        
        // 解析心情和标签
        String mood = diary.getMood();
        String tags = "";
        String selectedMood = "";
        
        if (mood != null && mood.contains(",")) {
            String[] moodParts = mood.split(",", 2);
            selectedMood = moodParts[0];
            tags = moodParts[1];
        } else {
            selectedMood = mood;
        }
        
        moodSelector.getSelectionModel().select(selectedMood);
        tagField.setText(tags);
    }
    
    /**
     * 保存日记
     */
    private void saveDiary() {
        String title = titleField.getText().trim();
        String content = diaryContentArea.getText().trim();
        String mood = moodSelector.getSelectionModel().getSelectedItem();
        String tags = tagField.getText().trim();
        
        if (title.isEmpty() || content.isEmpty()) {
            showAlert("提示", "标题和内容不能为空");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean success;
                if (currentDiaryId == -1) {
                    // 新建日记
                    success = DiaryService.createDiary(title, content, mood, tags);
                } else {
                    // 更新日记
                    success = DiaryService.updateDiary(currentDiaryId, title, content, mood, tags);
                }
                
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("成功", "日记保存成功");
                        loadDiaries();
                        createNewDiary();
                    } else {
                        showAlert("错误", "日记保存失败");
                    }
                });
                
            } catch (Exception e) {
                System.err.println("保存日记时出错: " + e.getMessage());
                Platform.runLater(() -> {
                    showAlert("错误", "保存日记失败: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * 删除日记
     */
    private void deleteDiary() {
        Diary selectedDiary = diaryListView.getSelectionModel().getSelectedItem();
        if (selectedDiary == null) {
            showAlert("提示", "请先选择要删除的日记");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText("删除日记");
        confirmAlert.setContentText("确定要删除这条日记吗？此操作无法撤销。");
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                CompletableFuture.runAsync(() -> {
                    try {
                        boolean success = DiaryService.deleteDiary(selectedDiary.getId());
                        Platform.runLater(() -> {
                            if (success) {
                                showAlert("成功", "日记删除成功");
                                loadDiaries();
                                createNewDiary();
                            } else {
                                showAlert("错误", "日记删除失败");
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("删除日记时出错: " + e.getMessage());
                        Platform.runLater(() -> {
                            showAlert("错误", "删除日记失败: " + e.getMessage());
                        });
                    }
                });
            }
        });
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
     * 日记列表单元格
     */
    private class DiaryListCell extends ListCell<Diary> {
        @Override
        protected void updateItem(Diary diary, boolean empty) {
            super.updateItem(diary, empty);
            
            if (empty || diary == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            
            VBox cellContent = new VBox(3);
            cellContent.setPadding(new Insets(5));
            
            // 标题 - 从内容中提取
            String content = diary.getContent();
            String title = "";
            if (content.contains("\n\n")) {
                title = content.split("\n\n", 2)[0];
            } else if (content.length() > 30) {
                title = content.substring(0, 30) + "...";
            } else {
                title = content;
            }
            
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            
            // 日期和心情
            String dateStr = diary.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String moodStr = diary.getMood() != null ? diary.getMood() : "";
            // 只显示心情的第一部分（如果有逗号分隔）
            if (moodStr.contains(",")) {
                moodStr = moodStr.split(",")[0];
            }
            Label infoLabel = new Label(dateStr + " " + moodStr);
            infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
            
            // 内容预览
            String preview = diary.getContent();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            Label previewLabel = new Label(preview);
            previewLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999;");
            previewLabel.setWrapText(true);
            
            cellContent.getChildren().addAll(titleLabel, infoLabel, previewLabel);
            setGraphic(cellContent);
        }
    }
    
    // Getters
    public ObservableList<Diary> getDiaries() {
        return diaries;
    }
} 