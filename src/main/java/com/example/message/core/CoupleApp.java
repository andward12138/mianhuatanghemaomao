package com.example.message.core;

import com.example.message.controllers.MainController;
import com.example.message.services.ApiService;
import com.example.message.services.ChatService;
import com.example.message.services.DiaryService;
import com.example.message.services.ReminderService;
import com.example.message.util.DBUtil;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CoupleApp extends Application {
    
    private static CoupleApp instance;
    private Stage primaryStage;
    private MainController mainController;
    
    public static CoupleApp getInstance() {
        return instance;
    }
    
    @Override
    public void start(Stage primaryStage) {
        instance = this;
        this.primaryStage = primaryStage;
        
        // 创建主控制器
        mainController = new MainController();
        
        // 初始化应用
        initializeApplication();
        
        // 设置主窗口
        setupPrimaryStage();
        
        // 显示主界面
        mainController.showMainInterface(primaryStage);
    }
    
    private void initializeApplication() {
        // 初始化数据库
        DBUtil.initializeDatabase();
        
        // 初始化API服务
        ApiService.initialize();
        
        // 设置日记服务使用云存储
        DiaryService.setUseCloudStorage(true);
        
        // 设置消息接收回调
        ChatService.setMessageReceivedCallback(mainController::handleReceivedMessage);
        
        // 启动提醒服务
        ReminderService.start();
        
        System.out.println("应用初始化完成");
    }
    
    private void setupPrimaryStage() {
        primaryStage.setTitle("棉花糖和猫猫的小屋 💕");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        
        // 应用关闭时清理资源
        primaryStage.setOnCloseRequest(e -> {
            ChatService.stopServer();
            ReminderService.stop();
            System.out.println("应用已关闭");
        });
    }
    
    public Stage getPrimaryStage() {
        return primaryStage;
    }
    
    public MainController getMainController() {
        return mainController;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}