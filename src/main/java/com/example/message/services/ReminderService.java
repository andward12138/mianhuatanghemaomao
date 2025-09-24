package com.example.message.services;

import com.example.message.core.EventBus;
import com.example.message.model.Anniversary;
import com.example.message.ui.components.ModernUIComponents;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 提醒服务
 * 负责检查和发送纪念日提醒
 */
public class ReminderService {
    private static final Logger logger = Logger.getLogger(ReminderService.class.getName());
    
    private static ScheduledExecutorService scheduler;
    private static boolean isRunning = false;
    
    /**
     * 启动提醒服务
     */
    public static void start() {
        if (isRunning) {
            logger.info("提醒服务已经在运行中");
            return;
        }
        
        scheduler = Executors.newScheduledThreadPool(1);
        isRunning = true;
        
        // 每小时检查一次提醒
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkReminders();
            } catch (Exception e) {
                logger.severe("检查提醒时发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.HOURS);
        
        // 启动时立即检查一次
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 等待应用完全启动
                    checkReminders();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
        
        logger.info("提醒服务已启动，每小时检查一次纪念日提醒");
    }
    
    /**
     * 停止提醒服务
     */
    public static void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        isRunning = false;
        logger.info("提醒服务已停止");
    }
    
    /**
     * 检查需要提醒的纪念日
     */
    private static void checkReminders() {
        logger.info("开始检查纪念日提醒...");
        
        Task<List<Anniversary>> task = new Task<List<Anniversary>>() {
            @Override
            protected List<Anniversary> call() throws Exception {
                return AnniversaryService.getRemindableAnniversaries();
            }
        };
        
        task.setOnSucceeded(e -> {
            List<Anniversary> remindableAnniversaries = task.getValue();
            
            if (remindableAnniversaries != null && !remindableAnniversaries.isEmpty()) {
                logger.info("发现 " + remindableAnniversaries.size() + " 个需要提醒的纪念日");
                
                for (Anniversary anniversary : remindableAnniversaries) {
                    showReminderNotification(anniversary);
                    
                    // 发布提醒事件
                    EventBus.getInstance().publish(EventBus.Events.ANNIVERSARY_REMINDER, anniversary);
                }
            } else {
                logger.info("没有需要提醒的纪念日");
            }
        });
        
        task.setOnFailed(e -> {
            Throwable exception = task.getException();
            logger.severe("检查提醒失败: " + (exception != null ? exception.getMessage() : "未知错误"));
        });
        
        new Thread(task).start();
    }
    
    /**
     * 显示提醒通知
     * @param anniversary 纪念日
     */
    private static void showReminderNotification(Anniversary anniversary) {
        Platform.runLater(() -> {
            String title = "纪念日提醒 💕";
            String message;
            
            if (anniversary.isToday()) {
                message = String.format("🎉 今天是「%s」！\n别忘了和TA一起庆祝这个特殊的日子 💕", 
                    anniversary.getTitle());
            } else {
                long daysUntil = anniversary.getDaysUntil();
                message = String.format("💝 还有%d天就是「%s」了！\n记得准备惊喜哦 💕", 
                    daysUntil, anniversary.getTitle());
            }
            
            // 使用现代化通知
            if (anniversary.isToday()) {
                ModernUIComponents.showSpecialNotification(message);
            } else {
                ModernUIComponents.showInfoNotification(message);
            }
            
            // 同时显示详细对话框
            showDetailedReminderDialog(anniversary);
            
            logger.info("已显示纪念日提醒: " + anniversary.getTitle());
        });
    }
    
    /**
     * 显示详细的提醒对话框
     * @param anniversary 纪念日
     */
    private static void showDetailedReminderDialog(Anniversary anniversary) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("纪念日提醒 💕");
        
        if (anniversary.isToday()) {
            alert.setHeaderText("🎉 特殊的日子到了！");
        } else {
            alert.setHeaderText("💝 即将到来的纪念日");
        }
        
        StringBuilder content = new StringBuilder();
        content.append("纪念日：").append(anniversary.getTitle()).append("\n");
        content.append("日期：").append(anniversary.getFormattedDate()).append("\n");
        content.append("倒计时：").append(anniversary.getCountdownText()).append("\n");
        
        if (anniversary.getDescription() != null && !anniversary.getDescription().isEmpty()) {
            content.append("\n描述：").append(anniversary.getDescription());
        }
        
        if (anniversary.isToday()) {
            content.append("\n\n💕 今天是你们的特殊日子，记得和TA一起庆祝！");
        } else {
            content.append("\n\n💡 别忘了为这个特殊的日子做准备哦～");
        }
        
        alert.setContentText(content.toString());
        
        // 添加按钮
        ButtonType viewAllButton = new ButtonType("查看所有纪念日");
        ButtonType okButton = ButtonType.OK;
        alert.getButtonTypes().setAll(viewAllButton, okButton);
        
        // 美化对话框
        try {
            alert.getDialogPane().getStylesheets().add(
                ReminderService.class.getResource("/com/example/message/styles/couple-theme.css").toExternalForm()
            );
        } catch (Exception e) {
            logger.warning("无法加载样式表: " + e.getMessage());
        }
        
        alert.showAndWait().ifPresent(response -> {
            if (response == viewAllButton) {
                // 导航到纪念日页面
                EventBus.getInstance().publish(EventBus.Events.PAGE_CHANGED, "anniversary");
            }
        });
    }
    
    /**
     * 立即检查提醒（用于测试或手动触发）
     */
    public static void checkNow() {
        logger.info("手动触发纪念日提醒检查");
        new Thread(() -> checkReminders()).start();
    }
    
    /**
     * 检查服务是否运行中
     * @return 是否运行中
     */
    public static boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取下一次检查时间（用于显示）
     * @return 格式化的时间字符串
     */
    public static String getNextCheckTime() {
        if (!isRunning) {
            return "服务未启动";
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCheck = now.plusHours(1).withMinute(0).withSecond(0);
        
        return nextCheck.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    
    /**
     * 创建测试纪念日提醒（用于开发测试）
     */
    public static void createTestReminder() {
        Platform.runLater(() -> {
            Anniversary testAnniversary = new Anniversary();
            testAnniversary.setId(-1);
            testAnniversary.setTitle("测试纪念日");
            testAnniversary.setDescription("这是一个测试提醒");
            
            showReminderNotification(testAnniversary);
        });
    }
}
