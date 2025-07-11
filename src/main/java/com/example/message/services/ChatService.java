package com.example.message.services;

import com.example.message.model.ChatMessage;
import com.example.message.util.DBUtil;
import com.example.message.util.MessageCache;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.logging.Level;
import javafx.application.Platform;

public class ChatService {
    private static int PORT = 9999; // 改为可变端口
    private static ServerSocket serverSocket;
    private static ExecutorService executorService;
    private static boolean isServerRunning = false;
    private static String currentUser;
    private static String connectedPeer;
    private static Socket clientSocket;
    private static Consumer<ChatMessage> messageReceivedCallback;

    // 服务器模式相关变量
    // 注意：要使用服务器模式，请将SERVER_HOST修改为您的服务器IP地址或域名
    // 如果您没有公网服务器，可以使用内网穿透工具（如ngrok）将本地服务器暴露到公网
    private static final String SERVER_HOST = "8.134.99.69"; // 服务器IP地址
    private static final int SERVER_PORT = 8888;
    private static Socket serverConnection;
    private static PrintWriter serverWriter;
    private static BufferedReader serverReader;
    private static boolean isConnectedToServer = false;
    private static boolean isUsingServerMode = false; // 是否使用服务器模式
    private static String currentChatPeer; // 当前聊天对象的用户名

    // 在线用户列表缓存
    private static final List<String> onlineUsers = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(ChatService.class.getName());
    
    // 消息缓存实例
    private static final MessageCache messageCache = MessageCache.getInstance();

    // 设置是否使用服务器模式
    public static void setUseServerMode(boolean useServerMode) {
        isUsingServerMode = useServerMode;
        
        // 如果切换到服务器模式，测试API连接
        if (useServerMode) {
            System.out.println("已切换到服务器模式");
            
            // 初始化API服务
            if (!ApiService.isApiAvailable()) {
                ApiService.initialize();
            }
            
            boolean apiConnected = ApiService.isApiAvailable();
            if (apiConnected) {
                System.out.println("API连接测试成功！数据将存储在云服务器上。");
            } else {
                System.out.println("API连接测试失败，将使用本地数据库作为备份。数据将在API可用时自动同步。");
            }
        } else {
            System.out.println("已切换到直接连接模式，将使用本地数据库。");
        }
    }

    // 设置消息接收回调
    public static void setMessageReceivedCallback(Consumer<ChatMessage> callback) {
        messageReceivedCallback = callback;
    }

    // 启动聊天服务器（直接连接模式）
    public static void startServer(String username) {
        if (isServerRunning) return;
        
        currentUser = username;
        executorService = Executors.newCachedThreadPool();
        
        try {
            // 动态分配可用端口，避免多实例冲突
            PORT = findAvailablePort(PORT);
            serverSocket = new ServerSocket(PORT);
            isServerRunning = true;
            
            // 在后台线程中接受连接
            executorService.submit(() -> {
                while (isServerRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        handleClientConnection(socket);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            
            System.out.println("聊天服务器已启动，监听端口: " + PORT);
            System.out.println("提示：其他实例可通过IP地址和端口 " + PORT + " 连接到此服务器");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // 连接到中央服务器（服务器模式）
    public static boolean connectToServer(String username) {
        if (isConnectedToServer) return true;
        
        currentUser = username;
        // 同步设置API服务的当前用户
        ApiService.setCurrentUser(username);
        
        // 创建新的线程池
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newCachedThreadPool();
        }
        
        // 设置连接状态为尝试连接中
        boolean connectionSuccessful = false;
        Socket socket = null;
        
        try {
            logger.info("正在连接到服务器: " + SERVER_HOST + ":" + SERVER_PORT);
            
            // 首先测试API连接
            logger.info("测试API连接...");
            boolean apiConnected = ApiService.testConnection();
            if (apiConnected) {
                logger.info("API连接测试成功！");
            } else {
                logger.warning("API连接测试失败，但仍将尝试连接聊天服务器");
            }
            
            // 使用Socket.connect()方法，设置超时时间
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 10000); // 10秒超时
            serverConnection = socket;
            
            logger.info("成功连接到服务器！");
            logger.info("本地地址: " + socket.getLocalAddress() + ":" + socket.getLocalPort());
            logger.info("远程地址: " + socket.getRemoteSocketAddress());
            
            // 创建输入输出流
            serverWriter = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(serverConnection.getOutputStream(), "UTF-8")), true);
            serverReader = new BufferedReader(
                new InputStreamReader(serverConnection.getInputStream(), "UTF-8"));
            
            logger.info("输入输出流创建成功");
            
            // 发送登录消息，如果用户名冲突则自动生成新用户名
            String attemptUsername = username;
            String response = null;
            int attemptCount = 0;
            final int maxAttempts = 5;
            
            while (attemptCount < maxAttempts) {
                String loginMessage = "LOGIN:" + attemptUsername;
                logger.info("发送登录消息: " + loginMessage + " (尝试 " + (attemptCount + 1) + "/" + maxAttempts + ")");
                serverWriter.println(loginMessage);
                
                // 读取服务器响应
                response = serverReader.readLine();
                logger.info("服务器响应: " + response);
                
                if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                    // 登录成功，更新当前用户名
                    currentUser = attemptUsername;
                    ApiService.setCurrentUser(attemptUsername);
                    logger.info("登录成功，用户名: " + attemptUsername);
                    break;
                } else if (response != null && response.contains("用户名已存在")) {
                    // 用户名冲突，生成新的用户名
                    attemptCount++;
                    if (attemptCount < maxAttempts) {
                        attemptUsername = username + "_" + System.currentTimeMillis() % 10000;
                        logger.info("用户名冲突，尝试新用户名: " + attemptUsername);
                        continue;
                    } else {
                        logger.warning("多次尝试后仍无法找到可用用户名");
                        break;
                    }
                } else {
                    // 其他错误
                    logger.warning("登录失败: " + (response != null ? response : "无响应"));
                    break;
                }
            }
            
            if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                isConnectedToServer = true;
                connectionSuccessful = true;
                
                // 启动线程接收服务器消息
                executorService.submit(() -> {
                    try {
                        String line;
                        while ((line = serverReader.readLine()) != null) {
                            processServerMessage(line);
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "与服务器的连接断开: " + e.getMessage(), e);
                        isConnectedToServer = false;
                        // 尝试自动重连
                        tryReconnect();
                    }
                });
                
                // 启动心跳检测线程
                executorService.submit(() -> {
                    while (isConnectedToServer) {
                        try {
                            Thread.sleep(30000); // 每30秒发送一次心跳
                            if (isConnectedToServer) {
                                logger.fine("发送心跳检测...");
                                serverWriter.println("HEARTBEAT");
                                // 检查连接是否仍然有效
                                if (serverWriter.checkError()) {
                                    logger.warning("心跳检测失败，服务器连接可能已断开");
                                    isConnectedToServer = false;
                                    // 尝试自动重连
                                    tryReconnect();
                                    break;
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                
                logger.info("已连接到聊天服务器");
                
                // 请求在线用户列表
                serverWriter.println("GET_USERS");
                serverWriter.flush();
                
                return true;
            } else {
                logger.warning("登录失败: " + (response != null ? response : "无响应"));
                cleanupConnection(socket);
                return false;
            }
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "无法解析服务器地址: " + e.getMessage(), e);
            cleanupConnection(socket);
            return false;
        } catch (SocketTimeoutException e) {
            logger.log(Level.SEVERE, "连接服务器超时: " + e.getMessage(), e);
            cleanupConnection(socket);
            return false;
        } catch (ConnectException e) {
            logger.log(Level.SEVERE, "连接被拒绝: " + e.getMessage(), e);
            cleanupConnection(socket);
            return false;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "连接服务器失败: " + e.getMessage(), e);
            cleanupConnection(socket);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "连接过程中发生未知错误: " + e.getMessage(), e);
            cleanupConnection(socket);
            return false;
        }
    }
    
    /**
     * 处理从服务器接收到的消息
     * @param message 服务器发送的消息
     */
    private static void processServerMessage(String message) {
        if (message == null || message.isEmpty()) {
            logger.info("收到空消息，忽略");
            return;
        }

        logger.info("处理服务器消息: " + message);

        if (message.startsWith("USERLIST:") || message.startsWith("USERS:")) {
            // 处理用户列表更新
            String userListStr = message.startsWith("USERLIST:") ? 
                message.substring("USERLIST:".length()) : 
                message.substring("USERS:".length());
                
            String[] users = userListStr.split(",");
            
            synchronized (onlineUsers) {
                onlineUsers.clear();
                
                // 添加在线用户列表，排除自己
                for (String user : users) {
                    if (!user.equals(currentUser) && !user.trim().isEmpty()) {
                        onlineUsers.add(user);
                    }
                }
            }
            
            logger.info("更新在线用户列表: " + onlineUsers);
            
            // 确保在JavaFX应用线程上更新UI
            if (userListUpdateCallback != null) {
                final List<String> usersCopy = new ArrayList<>(onlineUsers);
                Platform.runLater(() -> {
                    try {
                        userListUpdateCallback.accept(usersCopy);
                        logger.info("成功调用用户列表更新回调");
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "执行用户列表更新回调时出错", e);
                    }
                });
            } else {
                logger.info("用户列表更新回调未设置");
            }
        } else if (message.startsWith("MSG:")) {
            // 处理收到的消息，格式: MSG:发送者:接收者:内容
            // 或直接是 MSG:发送者:内容 (如果是广播消息)
            String[] parts = message.substring("MSG:".length()).split(":", 3);
            String sender, receiver, content;
            
            if (parts.length >= 3) {
                // 私人消息: MSG:发送者:接收者:内容
                sender = parts[0];
                receiver = parts[1];
                content = parts[2];
                logger.info("收到私人消息 - 发送者: " + sender + 
                           ", 接收者: " + receiver + ", 内容: " + content);
            } else if (parts.length == 2) {
                // 广播消息: MSG:发送者:内容
                sender = parts[0];
                receiver = "all";
                content = parts[1];
                logger.info("收到广播消息 - 发送者: " + sender + ", 内容: " + content);
            } else {
                logger.warning("消息格式错误: " + message);
                return;
            }
            
            // 如果这条消息是当前用户发送的，检查是否需要处理
            if (sender.equals(currentUser)) {
                logger.info("收到自己发送的消息回显，检查是否需要处理");
            }
            
            // 保存消息到本地数据库
            logger.info("保存消息到数据库 - 发送者: " + sender + ", 接收者: " + receiver + ", 内容: " + content);
            int msgId = saveMessageAndGetId(sender, receiver, content, null);
            
            // 通知UI更新，显示新消息
            if (messageReceivedCallback != null) {
                // 创建消息对象
                final ChatMessage chatMessage = new ChatMessage(
                    msgId, sender, receiver, content, LocalDateTime.now().toString(), false);
                
                // 判断是否应该显示此消息
                boolean shouldDisplay = false;
                
                // 检查消息是否应该在当前聊天窗口显示
                if (currentChatPeer != null) {
                    // 当前用户是接收者，且发送者是当前聊天对象
                    if (receiver.equals(currentUser) && sender.equals(currentChatPeer)) {
                        shouldDisplay = true;
                        logger.info("要显示的消息: 当前用户是接收者，发送者是当前聊天对象");
                    }
                    // 当前用户是发送者，且接收者是当前聊天对象
                    else if (sender.equals(currentUser) && receiver.equals(currentChatPeer)) {
                        shouldDisplay = true;
                        logger.info("要显示的消息: 当前用户是发送者，接收者是当前聊天对象");
                    }
                }
                
                if (shouldDisplay) {
                    logger.info("消息将显示在当前聊天窗口");
                    Platform.runLater(() -> {
                        messageReceivedCallback.accept(chatMessage);
                    });
                } else {
                    // 如果当前用户是接收者，但不是当前聊天窗口，触发通知
                    if (receiver.equals(currentUser) && !sender.equals(currentChatPeer)) {
                        logger.info("触发来自 " + sender + " 的新消息通知");
                        if (newMessageNotificationCallback != null) {
                            Platform.runLater(() -> {
                                newMessageNotificationCallback.accept(sender);
                            });
                        }
                    }
                }
            } else {
                logger.info("消息接收回调未设置");
            }
        } else if (message.equals("PING")) {
            // 心跳消息，直接回复
            try {
                serverWriter.println("PONG");
                serverWriter.flush();
                logger.fine("响应心跳检测");
            } catch (Exception e) {
                logger.log(Level.WARNING, "发送PONG响应时出错", e);
            }
        } else {
            logger.warning("未知消息类型: " + message);
        }
    }
    
    // 停止聊天服务
    public static void stopServer() {
        isServerRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // 断开与服务器的连接
        if (isConnectedToServer) {
            try {
                if (serverWriter != null) {
                    serverWriter.println("LOGOUT:" + currentUser);
                }
                
                if (serverConnection != null && !serverConnection.isClosed()) {
                    serverConnection.close();
                }
                
                isConnectedToServer = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        System.out.println("聊天服务已停止");
    }
    
    // 处理客户端连接（直接连接模式）
    private static void handleClientConnection(Socket socket) {
        executorService.submit(() -> {
            try {
                // 获取对方的IP地址作为标识
                String peerAddress = socket.getInetAddress().getHostAddress();
                connectedPeer = peerAddress;
                
                // 创建输入流读取消息
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 解析消息格式: sender|content
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) {
                        String sender = parts[0];
                        String content = parts[1];
                        
                        // 保存接收到的消息
                        String timestamp = LocalDateTime.now().toString();
                        saveMessage(sender, currentUser, content);
                        
                        // 如果设置了回调，通知UI更新
                        if (messageReceivedCallback != null) {
                            ChatMessage message = new ChatMessage(0, sender, currentUser, content, timestamp, false);
                            messageReceivedCallback.accept(message);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("客户端断开连接: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    // 连接到对方（直接连接模式）
    public static boolean connectToPeer(String peerIp, String username) {
        try {
            System.out.println("正在连接到对方: " + peerIp + ":" + PORT);
            
            // 使用Socket.connect()方法，设置超时时间
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(peerIp, PORT), 5000); // 5秒超时
            clientSocket = socket;
            
            System.out.println("成功连接到对方！");
            connectedPeer = peerIp;
            currentUser = username;
            
            // 启动一个线程来接收消息
            executorService.submit(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 解析消息格式: sender|content
                        String[] parts = line.split("\\|", 2);
                        if (parts.length == 2) {
                            String sender = parts[0];
                            String content = parts[1];
                            
                            // 保存接收到的消息
                            String timestamp = LocalDateTime.now().toString();
                            saveMessage(sender, currentUser, content);
                            
                            // 如果设置了回调，通知UI更新
                            if (messageReceivedCallback != null) {
                                ChatMessage message = new ChatMessage(0, sender, currentUser, content, timestamp, false);
                                messageReceivedCallback.accept(message);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("与对方的连接断开: " + e.getMessage());
                }
            });
            
            return true;
        } catch (UnknownHostException e) {
            System.out.println("无法解析对方地址: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SocketTimeoutException e) {
            System.out.println("连接对方超时: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (ConnectException e) {
            System.out.println("连接被拒绝: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.out.println("连接对方失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("连接过程中发生未知错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // 发送消息
    public static boolean sendMessage(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        if (isUsingServerMode) {
            // 服务器模式发送消息
            if (!isConnectedToServer || currentChatPeer == null) {
                System.out.println("无法发送消息：未连接到服务器或未选择聊天对象");
                return false;
            }
            
            try {
                // 保存消息到本地数据库
                saveMessage(currentUser, currentChatPeer, content);
                
                // 通过服务器发送消息
                String message = "MSG:" + content;
                System.out.println("发送消息: " + message);
                serverWriter.println(message);
                
                // 检查是否发送成功
                if (serverWriter.checkError()) {
                    System.out.println("发送消息失败");
                    return false;
                }
                
                return true;
            } catch (Exception e) {
                System.out.println("发送消息时出错: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            // 直接连接模式发送消息
            if (connectedPeer == null || clientSocket == null || clientSocket.isClosed()) {
                System.out.println("无法发送消息：未连接到对方");
                return false;
            }
            
            try {
                // 保存消息到本地数据库
                saveMessage(currentUser, connectedPeer, content);
                
                // 发送消息到对方
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                String message = currentUser + "|" + content;
                System.out.println("发送消息: " + message);
                writer.println(message);
                
                // 检查是否发送成功
                if (writer.checkError()) {
                    System.out.println("发送消息失败");
                    return false;
                }
                
                return true;
            } catch (IOException e) {
                System.out.println("发送消息时出错: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }
    
    // 设置当前聊天对象（服务器模式）
    public static void setCurrentChatPeer(String peerUsername) {
        currentChatPeer = peerUsername;
        System.out.println("当前聊天对象已设置为: " + peerUsername);
    }
    
    // 获取在线用户列表（服务器模式）
    public static List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>();
        
        if (isConnectedToServer) {
            // 返回当前缓存的用户列表
            synchronized (onlineUsers) {
                users.addAll(onlineUsers);
                logger.info("返回缓存的在线用户列表: " + users.size() + " 用户");
            }
            
            // 只有当用户列表为空时才主动请求，避免频繁请求
            if (users.isEmpty()) {
                // 向服务器请求在线用户列表
                logger.info("用户列表为空，主动请求在线用户列表");
                if (serverWriter != null) {
                    try {
                        serverWriter.println("GET_USERS");
                        serverWriter.flush();
                        
                        // 等待一小段时间以获取响应
                        Thread.sleep(500);
                        
                        // 再次检查用户列表
                        synchronized (onlineUsers) {
                            users.clear();
                            users.addAll(onlineUsers);
                            logger.info("等待后重新获取用户列表: " + users.size() + " 用户");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "请求用户列表时出错", e);
                    }
                } else {
                    logger.warning("serverWriter为null，无法发送GET_USERS请求");
                }
            }
            
            // 打印用户列表信息
            if (!users.isEmpty()) {
                logger.info("当前在线用户: " + String.join(", ", users));
            } else {
                logger.warning("在线用户列表为空");
            }
        } else {
            logger.warning("未连接到服务器，无法获取在线用户列表");
        }
        
        return users;
    }
    
    // 保存消息到数据库
    public static void saveMessage(String sender, String receiver, String content) {
        // 如果使用服务器模式，则通过ApiService保存消息
        if (isUsingServerMode) {
            // 尝试通过API保存消息
            boolean success = ApiService.saveMessage(sender, receiver, content);
            if (!success) {
                System.out.println("通过API保存消息失败，尝试保存到本地数据库");
                saveMessageToLocalDB(sender, receiver, content);
            }
        } else {
            // 使用本地数据库
            saveMessageToLocalDB(sender, receiver, content);
        }
    }
    
    // 保存消息到本地数据库（作为备份方法）
    private static void saveMessageToLocalDB(String sender, String receiver, String content) {
        String timestamp = LocalDateTime.now().toString();
        String insertSQL = "INSERT INTO chat_messages (sender, receiver, content, timestamp, is_read) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
            
            preparedStatement.setString(1, sender);
            preparedStatement.setString(2, receiver);
            preparedStatement.setString(3, content);
            preparedStatement.setString(4, timestamp);
            preparedStatement.setInt(5, 0); // 未读
            
            preparedStatement.executeUpdate();
            System.out.println("消息已保存到本地数据库: " + sender + " -> " + receiver + ": " + content);
        } catch (SQLException e) {
            System.out.println("保存消息到本地数据库时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 获取与特定用户的聊天历史
    public static List<ChatMessage> getChatHistory(String otherUser) {
        logger.info("获取与用户 " + otherUser + " 的完整聊天历史");
        
        // 首先尝试从缓存获取
        List<ChatMessage> cachedMessages = messageCache.getUserMessages(currentUser, otherUser);
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            logger.info("从缓存获取到 " + cachedMessages.size() + " 条聊天历史");
            return cachedMessages;
        }
        
        // 缓存未命中，从数据源获取
        List<ChatMessage> messages = null;
        
        // 如果使用服务器模式，则通过ApiService获取聊天历史
        if (isUsingServerMode) {
            try {
                // 尝试通过API获取聊天历史
                messages = ApiService.getChatHistory(currentUser, otherUser);
                
                if (messages != null && !messages.isEmpty()) {
                    logger.info("成功从API获取 " + messages.size() + " 条聊天历史");
                    
                    // 确保所有消息都有正确的ID，避免重复显示问题
                    for (ChatMessage msg : messages) {
                        if (msg.getId() <= 0) {
                            logger.warning("API返回的消息没有有效ID: " + msg.getContent());
                        }
                    }
                    
                    // 缓存消息
                    messageCache.cacheUserMessages(currentUser, otherUser, messages);
                    return messages;
                } else {
                    logger.warning("通过API获取聊天历史失败或为空，尝试从本地数据库获取");
                    messages = getChatHistoryFromLocalDB(otherUser);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "通过API获取聊天历史时出错: " + e.getMessage(), e);
                messages = getChatHistoryFromLocalDB(otherUser);
            }
        } else {
            // 使用本地数据库
            messages = getChatHistoryFromLocalDB(otherUser);
        }
        
        // 缓存从数据库获取的消息
        if (messages != null && !messages.isEmpty()) {
            messageCache.cacheUserMessages(currentUser, otherUser, messages);
        }
        
        return messages;
    }
    
    // 从本地数据库获取聊天历史（作为备份方法）
    private static List<ChatMessage> getChatHistoryFromLocalDB(String otherUser) {
        List<ChatMessage> messages = new ArrayList<>();
        String selectSQL = "SELECT * FROM chat_messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER BY timestamp ASC";
        
        Connection connection = null;
        try {
            connection = DBUtil.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(selectSQL);
            
            logger.info("从本地数据库获取与用户 " + otherUser + " 的聊天历史");
            preparedStatement.setString(1, currentUser);
            preparedStatement.setString(2, otherUser);
            preparedStatement.setString(3, otherUser);
            preparedStatement.setString(4, currentUser);
            
            ResultSet resultSet = preparedStatement.executeQuery();
            
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String sender = resultSet.getString("sender");
                String receiver = resultSet.getString("receiver");
                String content = resultSet.getString("content");
                String timestamp = resultSet.getString("timestamp");
                boolean isRead = resultSet.getInt("is_read") == 1;
                
                messages.add(new ChatMessage(id, sender, receiver, content, timestamp, isRead));
            }
            
            logger.info("已从本地数据库加载与 " + otherUser + " 的聊天历史: " + messages.size() + " 条消息");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "从本地数据库获取聊天历史时出错: " + e.getMessage(), e);
        } finally {
            DBUtil.returnConnection(connection);
        }
        
        return messages;
    }
    
    // 标记消息为已读
    public static void markAsRead(int messageId) {
        // 先更新缓存中的消息状态
        messageCache.updateMessageStatus(messageId, true);
        
        // 如果使用服务器模式，则通过ApiService标记消息为已读
        if (isUsingServerMode) {
            // 尝试通过API标记消息为已读
            boolean success = ApiService.markAsRead(messageId);
            if (!success) {
                System.out.println("通过API标记消息为已读失败，尝试在本地数据库中标记");
                markAsReadInLocalDB(messageId);
            }
        } else {
            // 使用本地数据库
            markAsReadInLocalDB(messageId);
        }
    }
    
    // 在本地数据库中标记消息为已读（作为备份方法）
    private static void markAsReadInLocalDB(int messageId) {
        String updateSQL = "UPDATE chat_messages SET is_read = 1 WHERE id = ?";
        
        Connection connection = null;
        try {
            connection = DBUtil.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateSQL);
            
            preparedStatement.setInt(1, messageId);
            preparedStatement.executeUpdate();
            System.out.println("消息已在本地数据库中标记为已读: " + messageId);
        } catch (SQLException e) {
            System.out.println("在本地数据库中标记消息为已读时出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.returnConnection(connection);
        }
    }
    
    // 获取本机IP地址
    public static String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.out.println("获取本机IP地址时出错: " + e.getMessage());
            e.printStackTrace();
            return "未知";
        }
    }
    
    // 判断是否为公网IP地址
    public static boolean isPublicIpAddress(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            return !(address.isSiteLocalAddress() || 
                    address.isLoopbackAddress() || 
                    address.isLinkLocalAddress() || 
                    address.isMulticastAddress());
        } catch (Exception e) {
            System.out.println("判断IP地址类型时出错: " + e.getMessage());
            return false;
        }
    }
    
    // 获取公网IP地址
    public static String getPublicIpAddress() {
        try {
            URL url = new URL("https://api.ipify.org");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String ip = reader.readLine();
            return ip;
        } catch (Exception e) {
            System.out.println("获取公网IP地址时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    // 检查是否已连接到服务器
    public static boolean isConnectedToServer() {
        return isConnectedToServer;
    }

    /**
     * 获取聊天历史记录
     * @return 消息列表
     */
    public static List<ChatMessage> getChatHistory() {
        if (isUsingServerMode) {
            // 如果使用服务器模式，从API获取消息
            return ApiService.getMessages();
        } else {
            // 如果使用直连模式，从本地数据库获取
            return getLocalChatHistory();
        }
    }

    /**
     * 搜索聊天历史记录
     * @param keyword 关键词
     * @return 匹配的消息列表
     */
    public static List<ChatMessage> searchChatHistory(String keyword) {
        List<ChatMessage> allMessages = getChatHistory();
        List<ChatMessage> matchedMessages = new ArrayList<>();
        
        if (allMessages != null && !allMessages.isEmpty() && keyword != null && !keyword.isEmpty()) {
            for (ChatMessage message : allMessages) {
                // 在发送者或内容中查找关键词
                if (message.getSender().toLowerCase().contains(keyword.toLowerCase()) ||
                    message.getContent().toLowerCase().contains(keyword.toLowerCase())) {
                    matchedMessages.add(message);
                }
            }
        }
        
        return matchedMessages;
    }

    /**
     * 清空聊天历史记录
     * @return 是否成功
     */
    public static boolean clearChatHistory() {
        if (isUsingServerMode) {
            // 当前API不支持清空所有消息，返回失败
            return false;
        } else {
            // 清空缓存
            messageCache.clearAllCache();
            
            // 清空本地数据库中的聊天记录
            Connection conn = null;
            try {
                conn = DBUtil.getConnection();
                Statement stmt = conn.createStatement();
                int rows = stmt.executeUpdate("DELETE FROM chat_messages");
                return rows >= 0; // 即使没有记录被删除，也视为成功
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            } finally {
                DBUtil.returnConnection(conn);
            }
        }
    }

    /**
     * 从本地数据库获取聊天历史记录
     * @return 消息列表
     */
    private static List<ChatMessage> getLocalChatHistory() {
        List<ChatMessage> messages = new ArrayList<>();
        
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM chat_messages ORDER BY timestamp ASC")) {
            
            while (rs.next()) {
                String sender = rs.getString("sender");
                String receiver = rs.getString("receiver");
                String content = rs.getString("content");
                String timestampStr = rs.getString("timestamp");
                int id = rs.getInt("id");
                boolean isRead = rs.getInt("is_read") == 1;
                
                // 使用ChatMessage的构造函数，它内部会处理多种时间戳格式
                ChatMessage message = new ChatMessage(id, sender, receiver, content, timestampStr, isRead);
                messages.add(message);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "从本地数据库获取聊天历史时出错", e);
        }
        
        logger.info("从本地数据库获取了 " + messages.size() + " 条聊天记录");
        return messages;
    }

    /**
     * 广播消息到所有客户端
     * @param sender 发送者
     * @param message 消息内容
     */
    public static void broadcastMessage(String sender, String message) {
        // 在服务器模式下，先判断是否有选择的聊天对象
        if (isUsingServerMode && isConnectedToServer) {
            // 如果有选择的聊天对象，发送私聊消息
            if (currentChatPeer != null && !currentChatPeer.isEmpty()) {
                sendPrivateMessage(currentChatPeer, message);
                return;
            }
            
            // 否则发送广播消息
            try {
                String formattedMessage = "BROADCAST|" + sender + "|" + message;
                serverWriter.println(formattedMessage);
                serverWriter.flush();
                logger.info("向服务器广播消息: " + formattedMessage);
                
                // 同时也保存到本地聊天记录
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                saveMessageToLocalDB(sender, "all", message);
                
                // 回显自己的消息
                if (messageReceivedCallback != null) {
                    ChatMessage chatMessage = new ChatMessage(sender, message, LocalDateTime.now());
                    messageReceivedCallback.accept(chatMessage);
                }
            } catch (Exception e) {
                logger.severe("向服务器广播消息时出错: " + e.getMessage());
            }
        } else {
            logger.warning("未连接到服务器，无法广播消息");
        }
    }

    /**
     * 检查是否已连接
     * @return 是否已连接
     */
    public static boolean isConnected() {
        return isConnectedToServer || clientSocket != null;
    }

    /**
     * 设置用户列表更新回调
     * @param callback 回调函数
     */
    public static void setOnUserListUpdate(Consumer<List<String>> callback) {
        userListUpdateCallback = callback;
    }

    // 用户列表更新回调
    private static Consumer<List<String>> userListUpdateCallback;

    /**
     * 发送私人消息给指定用户
     * @param receiver 接收者用户名
     * @param content 消息内容
     */
    public static void sendPrivateMessage(String receiver, String content) {
        if (receiver == null || receiver.isEmpty() || content == null || content.isEmpty()) {
            logger.warning("无法发送消息: 接收者或内容为空");
            return;
        }
        
        // 如果没有设置当前聊天对象，则设置为接收者
        if (currentChatPeer == null || currentChatPeer.isEmpty()) {
            logger.info("设置当前聊天对象为: " + receiver);
            setCurrentChatPeer(receiver);
        }
        
        try {
            // 生成时间戳
            LocalDateTime currentTime = LocalDateTime.now();
            String timestampStr = currentTime.toString();
            
            // 生成临时消息ID
            final long tempTime = System.currentTimeMillis();
            final int tempRandom = (int)(Math.random() * 10000);
            final int tempId = (int)(tempTime % 100000) + tempRandom; // 生成一个唯一性较高的临时ID
            
            logger.info("生成临时消息ID: " + tempId + " 用于发送给 " + receiver + " 的消息");
            
            // 先保存消息到数据库，获取真实ID
            int savedId = saveMessageAndGetId(currentUser, receiver, content, String.valueOf(tempId));
            logger.info("保存消息到数据库，获取到ID: " + savedId);
            
            // 立即回显消息到聊天界面
            final int finalMsgId = savedId > 0 ? savedId : tempId;
            final String finalContent = content;
            if (messageReceivedCallback != null) {
                final ChatMessage chatMessage = new ChatMessage(
                    finalMsgId, currentUser, receiver, finalContent, timestampStr, false);
                
                Platform.runLater(() -> {
                    logger.info("回显消息到UI: ID=" + chatMessage.getId() + ", 内容=" + chatMessage.getContent());
                    messageReceivedCallback.accept(chatMessage);
                });
            }
            
            // 发送到服务器
            String messageToSend = "PRIVATE:" + receiver + ":" + content;
            logger.info("发送私人消息到服务器: " + messageToSend + " (ID=" + finalMsgId + ")");
            serverWriter.println(messageToSend);
            serverWriter.flush();
            
            // 请求刷新在线用户列表
            if (Math.random() < 0.1) { // 降低请求频率
                serverWriter.println("GET_USERS");
                serverWriter.flush();
                logger.fine("已请求更新在线用户列表");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "发送私人消息时出错", e);
        }
    }
    
    // 新方法：保存消息并返回生成的ID
    private static int saveMessageAndGetId(String sender, String receiver, String content, String tempId) {
        // 如果使用服务器模式，则通过ApiService保存消息
        if (isUsingServerMode) {
            // 尝试通过API保存消息并获取ID
            int messageId = ApiService.saveMessageAndGetId(sender, receiver, content);
            if (messageId > 0) {
                return messageId;
            } else {
                logger.warning("通过API保存消息失败，尝试保存到本地数据库");
                return saveMessageToLocalDBAndGetId(sender, receiver, content);
            }
        } else {
            // 使用本地数据库
            return saveMessageToLocalDBAndGetId(sender, receiver, content);
        }
    }
    
    // 保存消息到本地数据库并返回生成的ID
    private static int saveMessageToLocalDBAndGetId(String sender, String receiver, String content) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String insertSQL = "INSERT INTO chat_messages (sender, receiver, content, timestamp, is_read) VALUES (?, ?, ?, ?, ?)";
        
        Connection connection = null;
        try {
            connection = DBUtil.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            
            preparedStatement.setString(1, sender);
            preparedStatement.setString(2, receiver);
            preparedStatement.setString(3, content);
            preparedStatement.setString(4, timestamp);
            preparedStatement.setInt(5, 0); // 未读
            
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        logger.info("消息已保存到本地数据库并获取ID: " + id);
                        
                        // 创建ChatMessage对象并添加到缓存
                        ChatMessage message = new ChatMessage(id, sender, receiver, content, now, false);
                        messageCache.addMessage(message);
                        
                        // 验证ID是否已经存在于数据库中，以确保唯一性
                        try (PreparedStatement checkStmt = connection.prepareStatement(
                                "SELECT COUNT(*) FROM chat_messages WHERE id = ? AND id != LAST_INSERT_ROWID()")) {
                            checkStmt.setInt(1, id);
                            ResultSet rs = checkStmt.executeQuery();
                            if (rs.next() && rs.getInt(1) > 0) {
                                logger.warning("检测到ID冲突: " + id + "，尝试使用替代ID");
                                // 如果ID冲突，生成一个更大的ID
                                try (PreparedStatement maxIdStmt = connection.prepareStatement(
                                        "SELECT MAX(id) + 1 FROM chat_messages")) {
                                    ResultSet maxRs = maxIdStmt.executeQuery();
                                    if (maxRs.next()) {
                                        id = Math.max(id + 1, maxRs.getInt(1));
                                        // 更新消息记录为新ID
                                        try (PreparedStatement updateStmt = connection.prepareStatement(
                                                "UPDATE chat_messages SET id = ? WHERE ROWID = LAST_INSERT_ROWID()")) {
                                            updateStmt.setInt(1, id);
                                            updateStmt.executeUpdate();
                                            logger.info("已解决ID冲突，新ID: " + id);
                                        }
                                    }
                                }
                            }
                        }
                        
                        return id;
                    }
                }
            }
            
            logger.warning("保存消息到本地数据库成功，但未获取到ID");
            // 如果无法获取自动生成的ID，则尝试获取最后插入的记录ID
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("使用last_insert_rowid()获取到ID: " + id);
                    
                    // 创建ChatMessage对象并添加到缓存
                    ChatMessage message = new ChatMessage(id, sender, receiver, content, now, false);
                    messageCache.addMessage(message);
                    
                    return id;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "无法获取last_insert_rowid()", e);
            }
            
            // 生成一个随机的大ID，避免与已有ID冲突
            int randomId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            logger.info("无法获取数据库生成的ID，使用替代ID: " + randomId);
            
            // 创建ChatMessage对象并添加到缓存
            ChatMessage message = new ChatMessage(randomId, sender, receiver, content, now, false);
            messageCache.addMessage(message);
            
            return randomId;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "保存消息到本地数据库时出错", e);
            // 生成一个临时ID，以便UI可以显示消息
            int tempId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            logger.info("由于数据库错误，使用临时ID: " + tempId);
            
            // 创建ChatMessage对象并添加到缓存
            ChatMessage message = new ChatMessage(tempId, sender, receiver, content, now, false);
            messageCache.addMessage(message);
            
            return tempId;
        } finally {
            DBUtil.returnConnection(connection);
        }
    }

    // 获取当前聊天对象
    public static String getCurrentChatPeer() {
        return currentChatPeer;
    }

    // 获取与特定用户的聊天历史，只返回特定时间后的消息
    public static List<ChatMessage> getNewChatHistory(String otherUser, LocalDateTime since) {
        // 首先尝试从缓存获取最近的消息
        List<ChatMessage> recentMessages = messageCache.getRecentMessages(currentUser, otherUser, since);
        if (recentMessages != null) {
            logger.info("从缓存获取到 " + recentMessages.size() + " 条最近消息");
            return recentMessages;
        }
        
        // 缓存未命中，先获取全部聊天历史
        List<ChatMessage> allMessages = getChatHistory(otherUser);
        
        // 筛选出指定时间之后的消息
        List<ChatMessage> newMessages = new ArrayList<>();
        if (allMessages != null && !allMessages.isEmpty()) {
            // 如果since为当前时间，则返回所有消息（因为当前使用时机是为了显示所有历史消息）
            if (since.isEqual(LocalDateTime.now()) || 
                since.isAfter(LocalDateTime.now().minusSeconds(1))) {
                return allMessages;
            }
            
            // 否则筛选出since时间之后的消息
            for (ChatMessage message : allMessages) {
                if (message.getTimestamp().isAfter(since)) {
                    newMessages.add(message);
                    logger.fine("找到新消息: ID=" + message.getId() + ", " + message.getSender() + 
                               " -> " + message.getReceiver() + ": " + message.getContent());
                }
            }
            
            if (!newMessages.isEmpty()) {
                logger.info("找到 " + newMessages.size() + " 条自 " + 
                           since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " 之后的新消息");
            }
        }
        
        return newMessages;
    }

    // 清理连接资源
    private static void cleanupConnection(Socket socket) {
        // 关闭Socket
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "关闭Socket时出错: " + e.getMessage(), e);
            }
        }
        
        // 清空网络相关资源
        serverConnection = null;
        serverWriter = null;
        serverReader = null;
        isConnectedToServer = false;
    }

    // 尝试重新连接到服务器
    private static void tryReconnect() {
        if (isUsingServerMode && currentUser != null) {
            logger.info("尝试重新连接到服务器...");
            
            // 延迟5秒后尝试重连
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    boolean reconnected = connectToServer(currentUser);
                    if (reconnected) {
                        logger.info("重新连接成功");
                    } else {
                        logger.warning("重新连接失败");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    // 新增方法，用于通知新消息
    private static Consumer<String> newMessageNotificationCallback;

    public static void setNewMessageNotificationCallback(Consumer<String> callback) {
        newMessageNotificationCallback = callback;
    }
    
    // 获取当前用户名
    public static String getCurrentUser() {
        return currentUser;
    }
    
    // 查找可用端口
    private static int findAvailablePort(int startPort) {
        for (int port = startPort; port <= startPort + 100; port++) {
            try (ServerSocket testSocket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // 端口被占用，继续尝试下一个
            }
        }
        // 如果都被占用，返回随机端口
        try (ServerSocket testSocket = new ServerSocket(0)) {
            return testSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("无法找到可用端口", e);
        }
    }
    
    // 获取当前使用的端口
    public static int getCurrentPort() {
        return PORT;
    }
    
    // 断开连接
    public static void disconnect() {
        try {
            if (isConnectedToServer && serverConnection != null) {
                serverWriter.println("LOGOUT:" + currentUser);
                serverConnection.close();
                isConnectedToServer = false;
                logger.info("已断开服务器连接");
            }
            
            if (isServerRunning) {
                stopServer();
            }
            
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            
            if (executorService != null) {
                executorService.shutdown();
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "断开连接时出错", e);
        }
    }
    
    // 直接连接到指定IP
    public static boolean connectDirect(String username, String peerIp) {
        try {
            currentUser = username;
            return connectToPeer(peerIp, username);
        } catch (Exception e) {
            logger.log(Level.WARNING, "直接连接失败", e);
            return false;
        }
    }
    
    // 设置消息回调
    public static void setMessageCallback(Consumer<ChatMessage> callback) {
        messageReceivedCallback = callback;
    }
    
    // 发送私聊消息 - 返回boolean版本
    public static boolean sendPrivateMessageWithResult(String receiver, String content) {
        try {
            sendPrivateMessage(receiver, content);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "发送消息失败", e);
            return false;
        }
    }
}
