package com.example.message.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DBUtil {
    private static final Logger logger = Logger.getLogger(DBUtil.class.getName());
    
    // 数据库路径配置（优先级从高到低）
    private static final String APP_DIR_DB_PATH = "./data/app.db"; // 应用运行目录下data子目录（jar包同级目录）
    private static final String CURRENT_DIR_DB_PATH = "./app.db"; // 应用当前目录（备选）
    private static final String USER_HOME_DB_PATH = System.getProperty("user.home") + "/心情通讯/data.db"; // 用户主目录
    private static final String TEMP_DIR_DB_PATH = System.getProperty("java.io.tmpdir") + "/心情通讯/data.db"; // 临时目录
    private static final String MEMORY_DB_PATH = ":memory:"; // 内存数据库（最后选项）
    
    private static String dbPath = null; // 实际使用的路径
    
    static {
        // 初始化检查，确保数据库目录存在
        initializeDatabasePath();
    }
    
    // 初始化数据库路径
    private static void initializeDatabasePath() {
        logger.info("开始初始化数据库路径...");
        
        // 尝试在应用运行目录创建数据库文件（jar包同级目录）
        File appDirDbFile = new File(APP_DIR_DB_PATH);
        File appDirDbParent = appDirDbFile.getParentFile();
        
        if (appDirDbParent != null && (appDirDbParent.exists() || appDirDbParent.mkdirs())) {
            try {
                // 获取绝对路径以便日志记录
                String absolutePath = Paths.get(APP_DIR_DB_PATH).toAbsolutePath().toString();
                dbPath = APP_DIR_DB_PATH;
                logger.info("使用应用运行目录数据库路径: " + absolutePath);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "无法使用应用运行目录: " + e.getMessage(), e);
            }
        } else {
            logger.warning("无法使用或创建应用运行目录: " + (appDirDbParent != null ? appDirDbParent.getAbsolutePath() : "null"));
        }
        
        // 尝试在当前目录直接创建数据库文件
        File currentDirDbFile = new File(CURRENT_DIR_DB_PATH);
        try {
            String absolutePath = currentDirDbFile.getAbsolutePath();
            dbPath = CURRENT_DIR_DB_PATH;
            logger.info("使用当前目录数据库路径: " + absolutePath);
            return;
        } catch (Exception e) {
            logger.log(Level.WARNING, "无法使用当前目录: " + e.getMessage(), e);
        }
        
        // 尝试从JAR文件所在目录创建数据库文件
        try {
            String jarPath = DBUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            String jarDir = jarFile.getParentFile().getAbsolutePath();
            String jarDirDbPath = jarDir + File.separator + "data" + File.separator + "app.db";
            
            File jarDirDbFile = new File(jarDirDbPath);
            File jarDirDbParent = jarDirDbFile.getParentFile();
            
            if (jarDirDbParent != null && (jarDirDbParent.exists() || jarDirDbParent.mkdirs())) {
                dbPath = jarDirDbPath;
                logger.info("使用JAR文件目录数据库路径: " + jarDirDbPath);
                return;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "无法在JAR文件目录创建数据库: " + e.getMessage(), e);
        }
        
        // 尝试在用户主目录创建数据库文件
        File userHomeDbFile = new File(USER_HOME_DB_PATH);
        File userHomeDbParent = userHomeDbFile.getParentFile();
        
        if (userHomeDbParent != null && (userHomeDbParent.exists() || userHomeDbParent.mkdirs())) {
            try {
                dbPath = USER_HOME_DB_PATH;
                logger.info("使用用户主目录数据库路径: " + dbPath);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "无法使用用户主目录: " + e.getMessage(), e);
            }
        } else {
            logger.warning("无法使用或创建用户主目录: " + (userHomeDbParent != null ? userHomeDbParent.getAbsolutePath() : "null"));
        }
        
        // 尝试在临时目录创建数据库文件
        File tempDirDbFile = new File(TEMP_DIR_DB_PATH);
        File tempDirDbParent = tempDirDbFile.getParentFile();
        
        if (tempDirDbParent != null && (tempDirDbParent.exists() || tempDirDbParent.mkdirs())) {
            try {
                dbPath = TEMP_DIR_DB_PATH;
                logger.info("使用临时目录数据库路径: " + dbPath);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "无法使用临时目录: " + e.getMessage(), e);
            }
        } else {
            logger.warning("无法使用或创建临时目录: " + (tempDirDbParent != null ? tempDirDbParent.getAbsolutePath() : "null"));
        }
        
        // 如果所有路径都失败，使用内存数据库
        logger.warning("无法创建任何数据库目录，将使用内存数据库");
        dbPath = MEMORY_DB_PATH;
    }

    // 获取数据库连接
    public static Connection getConnection() throws SQLException {
        if (dbPath == null) {
            initializeDatabasePath();
        }
        
        try {
            // 连接前确保数据库目录存在
            if (!MEMORY_DB_PATH.equals(dbPath)) {
                File dbFile = new File(dbPath);
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                    logger.info("已创建数据库目录: " + parentDir.getAbsolutePath());
                }
            }
            
            logger.fine("尝试连接数据库: " + dbPath);
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "获取数据库连接失败: " + e.getMessage() + "，路径: " + dbPath, e);
            throw e;
        }
    }

    // 初始化数据库表
    public static void initializeDatabase() {
        logger.info("初始化数据库表结构...");
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
            
            logger.info("数据库表初始化完成，路径: " + dbPath);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "初始化数据库表时出错: " + e.getMessage(), e);
        }
    }
    
    // 获取当前使用的数据库路径
    public static String getDatabasePath() {
        if (dbPath == null) {
            initializeDatabasePath();
        }
        return dbPath;
    }
    
    // 验证数据库连接并返回状态信息
    public static boolean testConnection() {
        try (Connection connection = getConnection()) {
            boolean isValid = connection != null && connection.isValid(2);
            logger.info("数据库连接测试 - " + (isValid ? "成功" : "失败") + ", 路径: " + dbPath);
            return isValid;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "数据库连接测试失败: " + e.getMessage(), e);
            return false;
        }
    }
}
