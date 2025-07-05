package com.example.message.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 数据库连接池管理类
 * 使用简单的连接池实现来复用数据库连接，提高性能
 */
public class DBConnectionPool {
    private static final Logger logger = Logger.getLogger(DBConnectionPool.class.getName());
    
    // 连接池配置
    private static final int INITIAL_POOL_SIZE = 5;      // 初始连接数
    private static final int MAX_POOL_SIZE = 10;         // 最大连接数
    private static final int CONNECTION_TIMEOUT = 30000; // 连接超时时间（毫秒）
    
    // 连接池实例
    private static volatile DBConnectionPool instance;
    private final BlockingQueue<Connection> pool;
    private final AtomicInteger connectionCount;
    private final String dbUrl;
    private volatile boolean isShutdown = false;
    
    // 私有构造函数，实现单例模式
    private DBConnectionPool(String dbUrl) {
        this.dbUrl = dbUrl;
        this.pool = new ArrayBlockingQueue<>(MAX_POOL_SIZE);
        this.connectionCount = new AtomicInteger(0);
        initializePool();
    }
    
    /**
     * 获取连接池实例
     * @param dbUrl 数据库URL
     * @return 连接池实例
     */
    public static DBConnectionPool getInstance(String dbUrl) {
        if (instance == null) {
            synchronized (DBConnectionPool.class) {
                if (instance == null) {
                    instance = new DBConnectionPool(dbUrl);
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化连接池
     */
    private void initializePool() {
        logger.info("初始化数据库连接池...");
        for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
            try {
                Connection connection = createConnection();
                if (connection != null) {
                    pool.offer(connection);
                    connectionCount.incrementAndGet();
                    logger.fine("创建连接 " + (i + 1) + "/" + INITIAL_POOL_SIZE);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "创建初始连接失败: " + e.getMessage(), e);
            }
        }
        logger.info("连接池初始化完成，初始连接数: " + connectionCount.get());
    }
    
    /**
     * 创建新的数据库连接
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
    
    /**
     * 从连接池获取连接
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    public Connection getConnection() throws SQLException {
        if (isShutdown) {
            throw new SQLException("连接池已关闭");
        }
        
        Connection connection = pool.poll();
        
        // 如果池中没有可用连接，且还没达到最大连接数，则创建新连接
        if (connection == null && connectionCount.get() < MAX_POOL_SIZE) {
            try {
                connection = createConnection();
                connectionCount.incrementAndGet();
                logger.fine("创建新连接，当前连接数: " + connectionCount.get());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "创建新连接失败: " + e.getMessage(), e);
                throw e;
            }
        }
        
        // 如果还是没有连接，则等待
        if (connection == null) {
            try {
                logger.fine("等待可用连接...");
                connection = pool.take(); // 阻塞等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("等待连接时被中断", e);
            }
        }
        
        // 验证连接有效性
        if (connection != null && !isConnectionValid(connection)) {
            logger.warning("连接无效，重新创建...");
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "关闭无效连接时出错: " + e.getMessage(), e);
            }
            connectionCount.decrementAndGet();
            return getConnection(); // 递归获取新连接
        }
        
        return connection;
    }
    
    /**
     * 将连接返回到连接池
     * @param connection 数据库连接
     */
    public void returnConnection(Connection connection) {
        if (connection == null || isShutdown) {
            return;
        }
        
        try {
            // 检查连接是否有效
            if (isConnectionValid(connection)) {
                // 重置连接状态
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                
                // 返回到池中
                if (!pool.offer(connection)) {
                    // 如果池满了，关闭连接
                    connection.close();
                    connectionCount.decrementAndGet();
                    logger.fine("连接池已满，关闭连接，当前连接数: " + connectionCount.get());
                }
            } else {
                // 连接无效，关闭并减少计数
                connection.close();
                connectionCount.decrementAndGet();
                logger.fine("连接无效，已关闭，当前连接数: " + connectionCount.get());
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "返回连接时出错: " + e.getMessage(), e);
            try {
                connection.close();
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "关闭连接时出错: " + ex.getMessage(), ex);
            }
            connectionCount.decrementAndGet();
        }
    }
    
    /**
     * 检查连接是否有效
     * @param connection 数据库连接
     * @return 连接是否有效
     */
    private boolean isConnectionValid(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            logger.log(Level.FINE, "检查连接有效性时出错: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取连接池状态信息
     * @return 连接池状态
     */
    public String getPoolStatus() {
        return String.format("连接池状态 - 总连接数: %d, 可用连接数: %d, 最大连接数: %d",
                connectionCount.get(), pool.size(), MAX_POOL_SIZE);
    }
    
    /**
     * 关闭连接池
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        
        logger.info("正在关闭连接池...");
        isShutdown = true;
        
        // 关闭所有连接
        Connection connection;
        while ((connection = pool.poll()) != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "关闭连接时出错: " + e.getMessage(), e);
            }
        }
        
        logger.info("连接池已关闭");
    }
    
    /**
     * 获取当前连接数
     * @return 当前连接数
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }
    
    /**
     * 获取可用连接数
     * @return 可用连接数
     */
    public int getAvailableConnections() {
        return pool.size();
    }
} 