package com.example.message.services;

import com.example.message.model.ChatMessage;
import com.example.message.util.DBUtil;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ChatService {
    private static final int PORT = 9999;
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
        
        executorService = Executors.newCachedThreadPool();
        
        try {
            System.out.println("正在连接到服务器: " + SERVER_HOST + ":" + SERVER_PORT);
            
            // 首先测试API连接
            System.out.println("测试API连接...");
            boolean apiConnected = ApiService.testConnection();
            if (apiConnected) {
                System.out.println("API连接测试成功！");
            } else {
                System.out.println("API连接测试失败，但仍将尝试连接聊天服务器");
            }
            
            // 使用Socket.connect()方法，设置超时时间
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 10000); // 10秒超时
            serverConnection = socket;
            
            System.out.println("成功连接到服务器！");
            System.out.println("本地地址: " + socket.getLocalAddress() + ":" + socket.getLocalPort());
            System.out.println("远程地址: " + socket.getRemoteSocketAddress());
            
            // 创建输入输出流
            serverWriter = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(serverConnection.getOutputStream(), "UTF-8")), true);
            serverReader = new BufferedReader(
                new InputStreamReader(serverConnection.getInputStream(), "UTF-8"));
            
            System.out.println("输入输出流创建成功");
            
            // 发送登录消息
            String loginMessage = "LOGIN|" + username;
            System.out.println("发送登录消息: " + loginMessage);
            serverWriter.println(loginMessage);
            
            // 读取服务器响应
            String response = serverReader.readLine();
            System.out.println("服务器响应: " + response);
            
            if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                isConnectedToServer = true;
                
                // 启动线程接收服务器消息
                executorService.submit(() -> {
                    try {
                        String line;
                        while ((line = serverReader.readLine()) != null) {
                            processServerMessage(line);
                        }
                    } catch (IOException e) {
                        System.out.println("与服务器的连接断开: " + e.getMessage());
                        isConnectedToServer = false;
                    }
                });
                
                // 启动心跳检测线程
                executorService.submit(() -> {
                    while (isConnectedToServer) {
                        try {
                            Thread.sleep(30000); // 每30秒发送一次心跳
                            if (isConnectedToServer) {
                                System.out.println("发送心跳检测...");
                                serverWriter.println("PING");
                                // 检查连接是否仍然有效
                                if (serverWriter.checkError()) {
                                    System.out.println("心跳检测失败，服务器连接可能已断开");
                                    isConnectedToServer = false;
                                    break;
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                
                System.out.println("已连接到聊天服务器");
                
                // 请求在线用户列表
                serverWriter.println("GET_USERS");
                
                return true;
            } else {
                System.out.println("登录失败: " + (response != null ? response : "无响应"));
                socket.close();
                return false;
            }
        } catch (UnknownHostException e) {
            System.out.println("无法解析服务器地址: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SocketTimeoutException e) {
            System.out.println("连接服务器超时: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (ConnectException e) {
            System.out.println("连接被拒绝: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.out.println("连接服务器失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("连接过程中发生未知错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // 处理从服务器接收的消息
    private static void processServerMessage(String message) {
        System.out.println("收到服务器消息: " + message);
        
        String[] parts = message.split("\\|");
        if (parts.length < 1) return;
        
        String command = parts[0];
        
        switch (command) {
            case "LOGIN_SUCCESS":
                isConnectedToServer = true;
                System.out.println("登录成功: " + (parts.length > 1 ? parts[1] : ""));
                break;
                
            case "MESSAGE":
                // 消息格式: MESSAGE|发送者|接收者|内容
                if (parts.length >= 4) {
                    String sender = parts[1];
                    String receiver = parts[2];
                    String content = parts[3];
                    
                    // 保存消息到本地数据库
                    String timestamp = LocalDateTime.now().toString();
                    saveMessage(sender, receiver, content);
                    
                    // 如果设置了回调，通知UI更新
                    if (messageReceivedCallback != null) {
                        ChatMessage chatMessage = new ChatMessage(0, sender, receiver, content, timestamp, false);
                        messageReceivedCallback.accept(chatMessage);
                    }
                }
                break;
                
            case "USERS":
                // 用户列表格式: USERS|用户1|用户2|...
                List<String> users = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    users.add(parts[i]);
                }
                synchronized (onlineUsers) {
                    onlineUsers.clear();
                    onlineUsers.addAll(users);
                }
                System.out.println("在线用户列表已更新: " + String.join(", ", users));
                break;
                
            case "ERROR":
                // 错误消息格式: ERROR|错误信息
                System.out.println("服务器错误: " + (parts.length > 1 ? parts[1] : "未知错误"));
                break;
                
            case "PONG":
                // 心跳响应
                System.out.println("收到心跳响应");
                break;
                
            case "MESSAGE_SENT":
                // 消息发送成功确认
                System.out.println("消息已发送给: " + (parts.length > 1 ? parts[1] : ""));
                break;
                
            default:
                System.out.println("未知服务器命令: " + command);
                break;
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
                    serverWriter.println("LOGOUT|" + currentUser);
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
                String message = "MESSAGE|" + currentUser + "|" + currentChatPeer + "|" + content;
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
            // 向服务器请求在线用户列表
            System.out.println("请求在线用户列表");
            serverWriter.println("GET_USERS");
            
            // 返回当前缓存的用户列表
            synchronized (onlineUsers) {
                users.addAll(onlineUsers);
            }
            
            System.out.println("当前在线用户: " + String.join(", ", users));
        } else {
            System.out.println("未连接到服务器，无法获取在线用户列表");
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
        // 如果使用服务器模式，则通过ApiService获取聊天历史
        if (isUsingServerMode) {
            try {
                // 尝试通过API获取聊天历史
                List<ChatMessage> messages = ApiService.getChatHistory(currentUser, otherUser);
                if (messages != null && !messages.isEmpty()) {
                    return messages;
                } else {
                    System.out.println("通过API获取聊天历史失败或为空，尝试从本地数据库获取");
                    return getChatHistoryFromLocalDB(otherUser);
                }
            } catch (Exception e) {
                System.out.println("通过API获取聊天历史时出错: " + e.getMessage());
                return getChatHistoryFromLocalDB(otherUser);
            }
        } else {
            // 使用本地数据库
            return getChatHistoryFromLocalDB(otherUser);
        }
    }
    
    // 从本地数据库获取聊天历史（作为备份方法）
    private static List<ChatMessage> getChatHistoryFromLocalDB(String otherUser) {
        List<ChatMessage> messages = new ArrayList<>();
        String selectSQL = "SELECT * FROM chat_messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER BY timestamp ASC";
        
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            
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
            
            System.out.println("已从本地数据库加载与 " + otherUser + " 的聊天历史: " + messages.size() + " 条消息");
        } catch (SQLException e) {
            System.out.println("从本地数据库获取聊天历史时出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        return messages;
    }
    
    // 标记消息为已读
    public static void markAsRead(int messageId) {
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
        
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {
            
            preparedStatement.setInt(1, messageId);
            preparedStatement.executeUpdate();
            System.out.println("消息已在本地数据库中标记为已读: " + messageId);
        } catch (SQLException e) {
            System.out.println("在本地数据库中标记消息为已读时出错: " + e.getMessage());
            e.printStackTrace();
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
}
