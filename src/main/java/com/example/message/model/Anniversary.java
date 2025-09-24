package com.example.message.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 纪念日模型类
 * 用于管理情侣之间的重要纪念日
 */
public class Anniversary {
    private int id;
    private String title;           // 纪念日名称
    private LocalDate date;         // 纪念日期
    private String description;     // 描述
    private String photos;          // 照片路径（多张用逗号分隔）
    private boolean isRecurring;    // 是否每年重复
    private int reminderDays;       // 提前几天提醒
    private String category;        // 分类：恋爱、生日、节日等
    private String createdBy;       // 创建者
    private LocalDateTime createTime; // 创建时间
    
    // 构造函数
    public Anniversary() {
        this.createTime = LocalDateTime.now();
        this.reminderDays = 1; // 默认提前1天提醒
        this.isRecurring = false;
    }
    
    public Anniversary(String title, LocalDate date, String description) {
        this();
        this.title = title;
        this.date = date;
        this.description = description;
    }
    
    public Anniversary(int id, String title, LocalDate date, String description, 
                      String photos, boolean isRecurring, int reminderDays, 
                      String category, String createdBy, LocalDateTime createTime) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.description = description;
        this.photos = photos;
        this.isRecurring = isRecurring;
        this.reminderDays = reminderDays;
        this.category = category;
        this.createdBy = createdBy;
        this.createTime = createTime != null ? createTime : LocalDateTime.now();
    }
    
    // 计算距离纪念日的天数
    public long getDaysUntil() {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = date;
        
        // 如果是每年重复的纪念日
        if (isRecurring) {
            targetDate = date.withYear(today.getYear());
            // 如果今年的日期已过，计算到明年的天数
            if (targetDate.isBefore(today) || targetDate.isEqual(today)) {
                targetDate = targetDate.withYear(today.getYear() + 1);
            }
        }
        
        return ChronoUnit.DAYS.between(today, targetDate);
    }
    
    // 检查是否需要提醒
    public boolean shouldRemind() {
        long daysUntil = getDaysUntil();
        return daysUntil >= 0 && daysUntil <= reminderDays;
    }
    
    // 检查是否是今天
    public boolean isToday() {
        return getDaysUntil() == 0;
    }
    
    // 获取格式化的日期字符串
    public String getFormattedDate() {
        return date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
    }
    
    // 获取格式化的创建时间
    public String getFormattedCreateTime() {
        return createTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    // 获取倒计时显示文本
    public String getCountdownText() {
        long days = getDaysUntil();
        if (days == 0) {
            return "🎉 就是今天！";
        } else if (days == 1) {
            return "💕 还有1天";
        } else if (days > 0) {
            return String.format("💝 还有%d天", days);
        } else {
            // 对于非重复的已过去的纪念日
            if (!isRecurring) {
                return String.format("📅 已过去%d天", Math.abs(days));
            }
            return "计算错误";
        }
    }
    
    // 获取纪念日类型的表情符号
    public String getCategoryEmoji() {
        if (category == null) return "💕";
        
        switch (category.toLowerCase()) {
            case "恋爱": case "love": return "💕";
            case "生日": case "birthday": return "🎂";
            case "节日": case "holiday": return "🎄";
            case "旅行": case "travel": return "✈️";
            case "纪念": case "memorial": return "🌟";
            case "约会": case "date": return "💐";
            default: return "💝";
        }
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public void setDate(LocalDate date) {
        this.date = date;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getPhotos() {
        return photos;
    }
    
    public void setPhotos(String photos) {
        this.photos = photos;
    }
    
    public boolean isRecurring() {
        return isRecurring;
    }
    
    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }
    
    public int getReminderDays() {
        return reminderDays;
    }
    
    public void setReminderDays(int reminderDays) {
        this.reminderDays = reminderDays;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    @Override
    public String toString() {
        return String.format("Anniversary{id=%d, title='%s', date=%s, daysUntil=%d}", 
                           id, title, date, getDaysUntil());
    }
}
