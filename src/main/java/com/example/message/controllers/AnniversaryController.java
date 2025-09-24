package com.example.message.controllers;

import com.example.message.core.EventBus;
import com.example.message.model.Anniversary;
import com.example.message.services.AnniversaryService;
import com.example.message.services.ApiService;
import com.example.message.ui.components.ModernUIComponents;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * 纪念日控制器
 * 负责纪念日管理界面的创建和交互
 */
public class AnniversaryController {
    private static final Logger logger = Logger.getLogger(AnniversaryController.class.getName());
    
    private VBox anniversaryListView;
    private TextField titleField;
    private DatePicker datePicker;
    private TextArea descriptionArea;
    private ComboBox<String> categorySelector;
    private CheckBox recurringCheckBox;
    private Spinner<Integer> reminderDaysSpinner;
    private Button saveButton;
    private Button clearButton;
    
    private int currentAnniversaryId = -1; // 当前编辑的纪念日ID
    private Timeline refreshTimer; // 定时刷新倒计时
    
    public AnniversaryController() {
        setupEventListeners();
        startRefreshTimer();
    }
    
    private void setupEventListeners() {
        EventBus.getInstance().subscribe(EventBus.Events.ANNIVERSARY_SAVED, this::onAnniversarySaved);
        EventBus.getInstance().subscribe(EventBus.Events.ANNIVERSARY_UPDATED, this::onAnniversaryUpdated);
        EventBus.getInstance().subscribe(EventBus.Events.ANNIVERSARY_DELETED, this::onAnniversaryDeleted);
    }
    
    private void startRefreshTimer() {
        // 每分钟刷新一次倒计时显示
        refreshTimer = new Timeline(
            new KeyFrame(Duration.minutes(1), event -> refreshCountdowns())
        );
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }
    
    private void refreshCountdowns() {
        Platform.runLater(() -> {
            if (anniversaryListView != null) {
                loadAnniversaries();
            }
        });
    }
    
    public Node createAnniversaryView() {
        VBox anniversaryPanel = new VBox();
        anniversaryPanel.getStyleClass().addAll("content-panel", "anniversary-panel");
        anniversaryPanel.setSpacing(20);
        anniversaryPanel.setPadding(new Insets(20));
        VBox.setVgrow(anniversaryPanel, Priority.ALWAYS);
        
        // 标题区域
        HBox headerBox = createHeaderSection();
        
        // 主要内容区域
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("anniversary-split-pane");
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        
        // 左侧：纪念日列表
        VBox listSection = createListSection();
        
        // 右侧：编辑区域
        VBox editSection = createEditSection();
        
        splitPane.getItems().addAll(listSection, editSection);
        splitPane.setDividerPositions(0.6);
        SplitPane.setResizableWithParent(editSection, false);
        
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        anniversaryPanel.getChildren().addAll(headerBox, splitPane);
        
        // 初始加载纪念日
        loadAnniversaries();
        
        return anniversaryPanel;
    }
    
    private HBox createHeaderSection() {
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(20);
        
        Label titleLabel = new Label("💝 我们的纪念日");
        titleLabel.getStyleClass().add("page-title");
        
        // 添加今日纪念日提示
        Label todayLabel = new Label();
        todayLabel.getStyleClass().add("today-anniversary-label");
        updateTodayLabel(todayLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button addButton = ModernUIComponents.createIconButton(
            de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.PLUS, 
            "添加纪念日"
        );
        addButton.setText("✨ 添加纪念日");
        addButton.getStyleClass().addAll("add-button", "mfx-button");
        addButton.setOnAction(e -> clearEditForm());
        
        headerBox.getChildren().addAll(titleLabel, todayLabel, spacer, addButton);
        return headerBox;
    }
    
    private void updateTodayLabel(Label todayLabel) {
        List<Anniversary> todayAnniversaries = AnniversaryService.getTodayAnniversaries();
        if (!todayAnniversaries.isEmpty()) {
            Anniversary anniversary = todayAnniversaries.get(0);
            todayLabel.setText("🎉 今天是：" + anniversary.getTitle() + "！");
            todayLabel.getStyleClass().add("today-special");
            
            // 添加闪烁动画
            FadeTransition fadeTransition = new FadeTransition(Duration.seconds(1), todayLabel);
            fadeTransition.setFromValue(0.3);
            fadeTransition.setToValue(1.0);
            fadeTransition.setCycleCount(Timeline.INDEFINITE);
            fadeTransition.setAutoReverse(true);
            fadeTransition.play();
        } else {
            todayLabel.setText("");
        }
    }
    
    private VBox createListSection() {
        VBox listSection = new VBox();
        listSection.getStyleClass().add("anniversary-list-section");
        listSection.setSpacing(10);
        listSection.setPadding(new Insets(15));
        listSection.setMinWidth(400);
        listSection.setMaxWidth(500);
        
        Label sectionLabel = new Label("📅 纪念日列表");
        sectionLabel.getStyleClass().add("section-label");
        
        // 创建滚动面板
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("anniversary-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        anniversaryListView = new VBox();
        anniversaryListView.getStyleClass().add("anniversary-list");
        anniversaryListView.setSpacing(10);
        anniversaryListView.setPadding(new Insets(10));
        
        scrollPane.setContent(anniversaryListView);
        
        listSection.getChildren().addAll(sectionLabel, scrollPane);
        return listSection;
    }
    
    private VBox createEditSection() {
        VBox editSection = new VBox();
        editSection.getStyleClass().add("anniversary-edit-section");
        editSection.setSpacing(15);
        editSection.setPadding(new Insets(15));
        editSection.setMinWidth(300);
        editSection.setMaxWidth(400);
        
        Label editLabel = new Label("✏️ 编辑纪念日");
        editLabel.getStyleClass().add("section-label");
        
        // 标题输入
        Label titleLabel = new Label("纪念日标题 *");
        titleLabel.getStyleClass().add("field-label");
        titleField = new TextField();
        titleField.getStyleClass().add("anniversary-field");
        titleField.setPromptText("例如：第一次见面、在一起一周年");
        
        // 日期选择
        Label dateLabel = new Label("纪念日期 *");
        dateLabel.getStyleClass().add("field-label");
        datePicker = new DatePicker();
        datePicker.getStyleClass().add("anniversary-date-picker");
        datePicker.setValue(LocalDate.now());
        
        // 分类选择
        Label categoryLabel = new Label("分类");
        categoryLabel.getStyleClass().add("field-label");
        categorySelector = new ComboBox<>();
        categorySelector.getStyleClass().add("anniversary-category");
        categorySelector.getItems().addAll("恋爱", "生日", "节日", "旅行", "纪念", "约会");
        categorySelector.setValue("恋爱");
        
        // 描述输入
        Label descLabel = new Label("描述");
        descLabel.getStyleClass().add("field-label");
        descriptionArea = new TextArea();
        descriptionArea.getStyleClass().add("anniversary-description");
        descriptionArea.setPromptText("记录这个特殊日子的美好回忆...");
        descriptionArea.setPrefRowCount(3);
        
        // 重复选项
        recurringCheckBox = new CheckBox("每年重复");
        recurringCheckBox.getStyleClass().add("anniversary-checkbox");
        recurringCheckBox.setSelected(true);
        
        // 提醒设置
        HBox reminderBox = new HBox(10);
        reminderBox.setAlignment(Pos.CENTER_LEFT);
        Label reminderLabel = new Label("提前提醒：");
        reminderLabel.getStyleClass().add("field-label");
        reminderDaysSpinner = new Spinner<>(0, 30, 1);
        reminderDaysSpinner.getStyleClass().add("anniversary-spinner");
        reminderDaysSpinner.setEditable(true);
        Label daysLabel = new Label("天");
        reminderBox.getChildren().addAll(reminderLabel, reminderDaysSpinner, daysLabel);
        
        // 按钮区域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        saveButton = ModernUIComponents.createIconButton(
            de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.SAVE, 
            "保存纪念日"
        );
        saveButton.setText("💾 保存");
        saveButton.getStyleClass().addAll("save-button", "mfx-button");
        saveButton.setOnAction(e -> saveAnniversary());
        
        clearButton = ModernUIComponents.createIconButton(
            de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.REFRESH, 
            "清空表单"
        );
        clearButton.setText("🔄 清空");
        clearButton.getStyleClass().addAll("clear-button", "mfx-button");
        clearButton.setOnAction(e -> clearEditForm());
        
        buttonBox.getChildren().addAll(saveButton, clearButton);
        
        editSection.getChildren().addAll(
            editLabel, 
            titleLabel, titleField,
            dateLabel, datePicker,
            categoryLabel, categorySelector,
            descLabel, descriptionArea,
            recurringCheckBox,
            reminderBox,
            buttonBox
        );
        
        return editSection;
    }
    
    private void loadAnniversaries() {
        new Thread(() -> {
            List<Anniversary> anniversaries = AnniversaryService.getAllAnniversaries();
            
            Platform.runLater(() -> {
                anniversaryListView.getChildren().clear();
                
                if (anniversaries.isEmpty()) {
                    Label emptyLabel = new Label("还没有添加纪念日\n点击右上角按钮添加第一个纪念日吧 💕");
                    emptyLabel.getStyleClass().add("empty-anniversary-label");
                    emptyLabel.setAlignment(Pos.CENTER);
                    anniversaryListView.getChildren().add(emptyLabel);
                } else {
                    for (Anniversary anniversary : anniversaries) {
                        Node anniversaryCard = createAnniversaryCard(anniversary);
                        anniversaryListView.getChildren().add(anniversaryCard);
                    }
                }
            });
        }).start();
    }
    
    private Node createAnniversaryCard(Anniversary anniversary) {
        VBox card = new VBox();
        card.getStyleClass().add("anniversary-card");
        card.setSpacing(8);
        card.setPadding(new Insets(15));
        card.setOnMouseClicked(e -> editAnniversary(anniversary));
        
        // 头部：标题和倒计时
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        
        Text emoji = new Text(anniversary.getCategoryEmoji());
        emoji.setFont(Font.font(20));
        
        Label titleLabel = new Label(anniversary.getTitle());
        titleLabel.getStyleClass().add("anniversary-card-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label countdownLabel = new Label(anniversary.getCountdownText());
        countdownLabel.getStyleClass().add("anniversary-countdown");
        
        // 如果是今天，添加特殊样式
        if (anniversary.isToday()) {
            countdownLabel.getStyleClass().add("anniversary-today");
            card.getStyleClass().add("anniversary-card-today");
        }
        
        header.getChildren().addAll(emoji, titleLabel, spacer, countdownLabel);
        
        // 详细信息
        VBox details = new VBox(5);
        
        Label dateLabel = new Label("📅 " + anniversary.getFormattedDate() + 
            (anniversary.isRecurring() ? " (每年重复)" : ""));
        dateLabel.getStyleClass().add("anniversary-card-date");
        
        if (anniversary.getDescription() != null && !anniversary.getDescription().isEmpty()) {
            Label descLabel = new Label("💭 " + anniversary.getDescription());
            descLabel.getStyleClass().add("anniversary-card-description");
            descLabel.setWrapText(true);
            details.getChildren().add(descLabel);
        }
        
        details.getChildren().add(dateLabel);
        
        // 操作按钮
        HBox actionBox = new HBox(5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button editBtn = new Button("✏️");
        editBtn.getStyleClass().add("anniversary-action-btn");
        editBtn.setTooltip(new Tooltip("编辑"));
        editBtn.setOnAction(e -> {
            e.consume();
            editAnniversary(anniversary);
        });
        
        Button deleteBtn = new Button("🗑️");
        deleteBtn.getStyleClass().add("anniversary-action-btn");
        deleteBtn.setTooltip(new Tooltip("删除"));
        deleteBtn.setOnAction(e -> {
            e.consume();
            deleteAnniversary(anniversary);
        });
        
        actionBox.getChildren().addAll(editBtn, deleteBtn);
        
        card.getChildren().addAll(header, details, actionBox);
        
        return card;
    }
    
    private void saveAnniversary() {
        String title = titleField.getText().trim();
        LocalDate date = datePicker.getValue();
        
        if (title.isEmpty()) {
            showError("输入错误", "请输入纪念日标题");
            return;
        }
        
        if (date == null) {
            showError("输入错误", "请选择纪念日期");
            return;
        }
        
        try {
            Anniversary anniversary = new Anniversary();
            anniversary.setTitle(title);
            anniversary.setDate(date);
            anniversary.setDescription(descriptionArea.getText().trim());
            anniversary.setCategory(categorySelector.getValue());
            anniversary.setRecurring(recurringCheckBox.isSelected());
            anniversary.setReminderDays(reminderDaysSpinner.getValue());
            anniversary.setCreatedBy(ApiService.getCurrentUser());
            
            boolean success;
            if (currentAnniversaryId == -1) {
                // 添加新纪念日
                success = AnniversaryService.addAnniversary(anniversary);
                if (success) {
                    showSuccess("添加成功", "纪念日已添加 💕");
                    EventBus.getInstance().publish(EventBus.Events.ANNIVERSARY_SAVED, anniversary);
                }
            } else {
                // 更新现有纪念日
                anniversary.setId(currentAnniversaryId);
                success = AnniversaryService.updateAnniversary(anniversary);
                if (success) {
                    showSuccess("更新成功", "纪念日已更新 💕");
                    EventBus.getInstance().publish(EventBus.Events.ANNIVERSARY_UPDATED, anniversary);
                }
            }
            
            if (success) {
                clearEditForm();
                loadAnniversaries();
            } else {
                showError("操作失败", "无法保存纪念日，请重试");
            }
            
        } catch (Exception e) {
            showError("操作失败", "发生错误: " + e.getMessage());
            logger.severe("保存纪念日时发生异常: " + e.getMessage());
        }
    }
    
    private void editAnniversary(Anniversary anniversary) {
        currentAnniversaryId = anniversary.getId();
        titleField.setText(anniversary.getTitle());
        datePicker.setValue(anniversary.getDate());
        descriptionArea.setText(anniversary.getDescription() != null ? anniversary.getDescription() : "");
        categorySelector.setValue(anniversary.getCategory() != null ? anniversary.getCategory() : "恋爱");
        recurringCheckBox.setSelected(anniversary.isRecurring());
        reminderDaysSpinner.getValueFactory().setValue(anniversary.getReminderDays());
    }
    
    private void deleteAnniversary(Anniversary anniversary) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除纪念日");
        alert.setContentText("确定要删除「" + anniversary.getTitle() + "」这个纪念日吗？");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = AnniversaryService.deleteAnniversary(anniversary.getId());
            if (success) {
                showSuccess("删除成功", "纪念日已删除");
                EventBus.getInstance().publish(EventBus.Events.ANNIVERSARY_DELETED, anniversary.getId());
                loadAnniversaries();
                clearEditForm();
            } else {
                showError("删除失败", "无法删除纪念日，请重试");
            }
        }
    }
    
    private void clearEditForm() {
        currentAnniversaryId = -1;
        titleField.clear();
        datePicker.setValue(LocalDate.now());
        descriptionArea.clear();
        categorySelector.setValue("恋爱");
        recurringCheckBox.setSelected(true);
        reminderDaysSpinner.getValueFactory().setValue(1);
    }
    
    private void onAnniversarySaved(Object anniversary) {
        logger.info("纪念日已保存: " + anniversary);
    }
    
    private void onAnniversaryUpdated(Object anniversary) {
        logger.info("纪念日已更新: " + anniversary);
    }
    
    private void onAnniversaryDeleted(Object anniversaryId) {
        logger.info("纪念日已删除: ID=" + anniversaryId);
    }
    
    private void showSuccess(String title, String message) {
        ModernUIComponents.showSuccessNotification(message);
    }
    
    private void showError(String title, String message) {
        ModernUIComponents.showErrorNotification(message);
    }
    
    public void cleanup() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
