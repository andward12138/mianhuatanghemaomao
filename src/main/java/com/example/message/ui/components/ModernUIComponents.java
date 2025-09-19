package com.example.message.ui.components;

// import atlantafx.base.theme.PrimerLight;
// import atlantafx.base.theme.PrimerDark;
import io.github.palexdev.materialfx.controls.*;
import io.github.palexdev.materialfx.enums.FloatMode;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
// import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView; // 暂时禁用

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * 现代化UI组件工具类
 * 整合了多个UI库的组件，提供统一的接口
 */
public class ModernUIComponents {
    
    // 主题管理
    public static class ThemeManager {
        private static boolean isDarkMode = false;
        
        public static void applyTheme(Application app) {
            // AtlantaFX theme application will be implemented when dependency is resolved
            // For now, use the elegant-theme.css
            if (isDarkMode) {
                // app.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                System.out.println("Dark mode activated - using elegant-theme.css");
            } else {
                // app.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                System.out.println("Light mode activated - using elegant-theme.css");
            }
        }
        
        public static void toggleTheme(Application app) {
            isDarkMode = !isDarkMode;
            applyTheme(app);
        }
        
        public static boolean isDarkMode() {
            return isDarkMode;
        }
    }
    
    // 创建Material Design风格的按钮
    public static MFXButton createMaterialButton(String text, FontAwesomeIcon icon) {
        MFXButton button = new MFXButton(text);
        button.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        button.setPrefHeight(40);
        button.setPrefWidth(120);
        
        // 暂时移除FontAwesome图标以避免兼容性问题
        // if (icon != null) { ... }
        
        // 添加悬停效果
        button.setOnMouseEntered(e -> {
            button.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white;");
        });
        button.setOnMouseExited(e -> {
            button.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        });
        
        return button;
    }
    
    // 创建Material Design风格的文本框
    public static MFXTextField createMaterialTextField(String promptText) {
        MFXTextField textField = new MFXTextField();
        textField.setPromptText(promptText);
        textField.setFloatMode(FloatMode.ABOVE);
        textField.setPrefHeight(45);
        textField.setStyle("-fx-border-color: #2196F3; -fx-border-width: 0 0 2 0;");
        return textField;
    }
    
    // 创建搜索文本框
    public static CustomTextField createSearchTextField(String promptText) {
        CustomTextField searchField = (CustomTextField) TextFields.createClearableTextField();
        searchField.setPromptText(promptText);
        searchField.setPrefHeight(35);
        
        // 添加搜索提示（移除图标以避免兼容性问题）
        // FontAwesome图标暂时禁用
        
        return searchField;
    }
    
    // 创建现代化的卡片容器
    public static VBox createCard(String title, Node content) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 8;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
            -fx-border-radius: 8;
        """);
        
        if (title != null && !title.isEmpty()) {
            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
            titleLabel.setTextFill(Color.web("#333333"));
            card.getChildren().add(titleLabel);
        }
        
        if (content != null) {
            card.getChildren().add(content);
        }
        
        return card;
    }
    
    // 创建用户头像
    public static Region createAvatar(String initials, Color backgroundColor) {
        Region avatar = new Region();
        avatar.setPrefSize(40, 40);
        avatar.setMaxSize(40, 40);
        avatar.setMinSize(40, 40);
        avatar.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 20; -fx-border-radius: 20;",
            toHexString(backgroundColor)
        ));
        
        // 添加文字标签
        Label label = new Label(initials);
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setAlignment(Pos.CENTER);
        
        StackPane container = new StackPane(avatar, label);
        return container;
    }
    
    // 显示通知
    public static void showNotification(String title, String message, Duration duration) {
        Notifications.create()
            .title(title)
            .text(message)
            .hideAfter(duration)
            .position(Pos.TOP_RIGHT)
            .showInformation();
    }
    
    // 显示成功通知
    public static void showSuccessNotification(String message) {
        Notifications.create()
            .title("成功")
            .text(message)
            .hideAfter(Duration.seconds(3))
            .position(Pos.TOP_RIGHT)
            .showConfirm();
    }
    
    // 显示错误通知
    public static void showErrorNotification(String message) {
        Notifications.create()
            .title("错误")
            .text(message)
            .hideAfter(Duration.seconds(5))
            .position(Pos.TOP_RIGHT)
            .showError();
    }
    
    // 创建弹出窗口
    public static PopOver createPopOver(String title, Node content) {
        PopOver popOver = new PopOver();
        popOver.setTitle(title);
        popOver.setContentNode(content);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        popOver.setDetachable(false);
        return popOver;
    }
    
    // 创建Material Design风格的进度条
    public static MFXProgressBar createMaterialProgressBar() {
        MFXProgressBar progressBar = new MFXProgressBar();
        progressBar.setPrefHeight(6);
        progressBar.setStyle("-fx-accent: #2196F3;");
        return progressBar;
    }
    
    // 创建Material Design风格的滑块
    public static MFXSlider createMaterialSlider(double min, double max, double value) {
        MFXSlider slider = new MFXSlider();
        slider.setMin(min);
        slider.setMax(max);
        slider.setValue(value);
        slider.setStyle("-fx-accent: #2196F3;");
        return slider;
    }
    
    // 创建现代化的分隔线
    public static Separator createStyledSeparator() {
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #E0E0E0;");
        return separator;
    }
    
    // 创建图标按钮（简化版，不使用FontAwesome）
    public static Button createIconButton(FontAwesomeIcon icon, String tooltip) {
        Button button = new Button("⚙"); // 使用Unicode字符替代图标
        button.setStyle("-fx-font-size: 16px;");
        
        button.setStyle("""
            -fx-background-color: transparent;
            -fx-border-color: transparent;
            -fx-padding: 8;
            -fx-background-radius: 4;
        """);
        
        // 悬停效果
        button.setOnMouseEntered(e -> {
            button.setStyle("""
                -fx-background-color: rgba(0,0,0,0.05);
                -fx-border-color: transparent;
                -fx-padding: 8;
                -fx-background-radius: 4;
            """);
        });
        button.setOnMouseExited(e -> {
            button.setStyle("""
                -fx-background-color: transparent;
                -fx-border-color: transparent;
                -fx-padding: 8;
                -fx-background-radius: 4;
            """);
        });
        
        if (tooltip != null) {
            button.setTooltip(new Tooltip(tooltip));
        }
        
        return button;
    }
    
    // 辅助方法：将Color转换为十六进制字符串
    private static String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
    
    // 创建现代化的标签页
    public static TabPane createStyledTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("""
            -fx-tab-min-height: 40;
            -fx-tab-max-height: 40;
        """);
        return tabPane;
    }
    
    // 创建现代化的菜单栏
    public static MenuBar createStyledMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle("""
            -fx-background-color: white;
            -fx-border-width: 0 0 1 0;
            -fx-border-color: #E0E0E0;
        """);
        return menuBar;
    }
}