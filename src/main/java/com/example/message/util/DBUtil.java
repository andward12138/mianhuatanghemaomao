package com.example.message.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
    // 数据库路径配置
    private static final String DEFAULT_DB_PATH = "E:/sqlite/identifier.sqlite";  // 默认路径
    private static final String FALLBACK_DB_PATH = System.getProperty("user.home") + "/message_app/data.sqlite"; // 备用路径
    
    private static String dbPath = DEFAULT_DB_PATH; // 实际使用的路径
    
    static {
        // 初始化检查，确保数据库目录存在
        initializeDatabasePath();
    }
    
    // 初始化数据库路径
    private static void initializeDatabasePath() {
        // 检查默认路径是否可用
        File defaultDir = new File(DEFAULT_DB_PATH).getParentFile();
        if (defaultDir != null && (defaultDir.exists() || defaultDir.mkdirs())) {
            dbPath = DEFAULT_DB_PATH;
            System.out.println("使用默认数据库路径: " + dbPath);
            return;
        }
        
        // 使用备用路径
        File fallbackDir = new File(FALLBACK_DB_PATH).getParentFile();
        if (fallbackDir != null && (fallbackDir.exists() || fallbackDir.mkdirs())) {
            dbPath = FALLBACK_DB_PATH;
            System.out.println("使用备用数据库路径: " + dbPath);
        } else {
            System.err.println("无法创建数据库目录，将使用内存数据库");
            dbPath = ":memory:"; // 使用内存数据库作为最后的备用方案
        }
    }

    // 获取数据库连接
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    // 初始化数据库表
    public static void initializeDatabase() {
        try (Connection connection = getConnection()) {
            // 创建日记表
            String createDiaryTableSQL = "CREATE TABLE IF NOT EXISTS diary_entries ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "date TEXT, "
                    + "content TEXT, "
                    + "timestamp TEXT, "
                    + "tags TEXT)";
            connection.createStatement().execute(createDiaryTableSQL);
            
            // 创建聊天消息表
            String createChatTableSQL = "CREATE TABLE IF NOT EXISTS chat_messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "sender TEXT, "
                    + "receiver TEXT, "
                    + "content TEXT, "
                    + "timestamp TEXT, "
                    + "is_read INTEGER DEFAULT 0)";
            connection.createStatement().execute(createChatTableSQL);
            
            System.out.println("数据库表初始化完成");
        } catch (SQLException e) {
            System.err.println("初始化数据库表时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 获取当前使用的数据库路径
    public static String getDatabasePath() {
        return dbPath;
    }
}
