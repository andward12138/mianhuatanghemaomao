package com.example.message;

import com.example.message.core.CoupleApp;

/**
 * 应用程序入口点
 * 重构后的简化版本，将所有逻辑移到了CoupleApp中
 */
public class MainApp {
    public static void main(String[] args) {
        // 启动重构后的应用
        CoupleApp.launch(CoupleApp.class, args);
    }
}