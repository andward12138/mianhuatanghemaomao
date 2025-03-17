package com.example.message.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

/**
 * 聊天服务器
 * 用于中转不同网络环境下的用户消息
 * 
 * 使用方法：
 * 1. 将此服务器部署在具有公网IP的服务器上
 * 2. 修改ChatService.java中的SERVER_HOST为服务器的IP地址或域名
 * 3. 启动服务器
 */
public class ChatServer {
    private static final int PORT = 8888;
    private static ServerSocket serverSocket;
    private static ExecutorService executorService;
    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            executorService = Executors.newCachedThreadPool();
            
            log("聊天服务器已启动，监听端口: " + PORT);
            log("服务器IP地址: " + InetAddress.getLocalHost().getHostAddress());
            
            // 添加关闭钩子，确保服务器正常关闭
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log("服务器正在关闭...");
                shutdown();
                log("服务器已关闭");
            }));
            
            // 定期打印在线用户数量
            executorService.submit(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Thread.sleep(60000); // 每分钟
                        log("当前在线用户数: " + clients.size() + ", 用户列表: " + String.join(", ", clients.keySet()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    log("新客户端连接: " + clientAddress);
                    
                    ClientHandler handler = new ClientHandler(clientSocket);
                    executorService.submit(handler);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        log("接受客户端连接时出错: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }
    
    /**
     * 关闭服务器
     */
    private static void shutdown() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // 通知所有客户端服务器关闭
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage("ERROR|服务器已关闭");
            handler.close();
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 向所有客户端广播在线用户列表
     */
    private static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USERS");
        for (String username : clients.keySet()) {
            userList.append("|").append(username);
        }
        
        String message = userList.toString();
        log("广播用户列表: " + message);
        
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(message);
        }
    }
    
    /**
     * 记录日志
     */
    private static void log(String message) {
        System.out.println("[" + dateFormat.format(new Date()) + "] " + message);
    }
    
    /**
     * 客户端处理器
     */
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String username;
        private boolean isRunning = true;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true);
                this.reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            } catch (IOException e) {
                log("创建客户端处理器时出错: " + e.getMessage());
                isRunning = false;
            }
        }
        
        @Override
        public void run() {
            try {
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    processMessage(line);
                }
            } catch (IOException e) {
                log("读取客户端消息时出错: " + e.getMessage());
            } finally {
                close();
                if (username != null) {
                    clients.remove(username);
                    log("用户 " + username + " 已断开连接");
                    broadcastUserList();
                }
            }
        }
        
        /**
         * 处理客户端消息
         */
        private void processMessage(String message) {
            log("收到消息: " + message);
            
            String[] parts = message.split("\\|");
            if (parts.length < 1) return;
            
            String command = parts[0];
            
            try {
                switch (command) {
                    case "LOGIN":
                        if (parts.length >= 2) {
                            username = parts[1];
                            // 检查用户名是否已存在
                            if (clients.containsKey(username)) {
                                sendMessage("ERROR|用户名 " + username + " 已被使用，请选择其他用户名");
                                username = null;
                            } else {
                                clients.put(username, this);
                                log("用户 " + username + " 已登录");
                                sendMessage("LOGIN_SUCCESS|" + username);
                                broadcastUserList();
                            }
                        }
                        break;
                        
                    case "LOGOUT":
                        if (parts.length >= 2) {
                            String user = parts[1];
                            if (user.equals(username)) {
                                clients.remove(user);
                                log("用户 " + user + " 已登出");
                                broadcastUserList();
                            }
                        }
                        break;
                        
                    case "MESSAGE":
                        if (parts.length >= 4) {
                            String sender = parts[1];
                            String receiver = parts[2];
                            String content = parts[3];
                            
                            // 验证发送者身份
                            if (!sender.equals(username)) {
                                log("消息发送者身份验证失败: " + sender + " != " + username);
                                sendMessage("ERROR|发送者身份验证失败");
                                return;
                            }
                            
                            log("转发消息: " + sender + " -> " + receiver + ": " + content);
                            
                            // 转发消息给接收者
                            ClientHandler receiverHandler = clients.get(receiver);
                            if (receiverHandler != null) {
                                receiverHandler.sendMessage("MESSAGE|" + sender + "|" + receiver + "|" + content);
                                // 发送确认消息给发送者
                                sendMessage("MESSAGE_SENT|" + receiver);
                            } else {
                                // 接收者不在线，发送错误消息给发送者
                                log("接收者 " + receiver + " 不在线");
                                sendMessage("ERROR|用户 " + receiver + " 不在线");
                            }
                        }
                        break;
                        
                    case "GET_USERS":
                        // 发送在线用户列表
                        StringBuilder userList = new StringBuilder("USERS");
                        for (String user : clients.keySet()) {
                            if (!user.equals(username)) {  // 不包括自己
                                userList.append("|").append(user);
                            }
                        }
                        sendMessage(userList.toString());
                        break;
                        
                    case "PING":
                        // 心跳检测
                        sendMessage("PONG");
                        break;
                        
                    default:
                        log("未知命令: " + command);
                        sendMessage("ERROR|未知命令: " + command);
                        break;
                }
            } catch (Exception e) {
                log("处理消息时出错: " + e.getMessage());
                e.printStackTrace();
                sendMessage("ERROR|服务器内部错误");
            }
        }
        
        /**
         * 发送消息给客户端
         */
        public void sendMessage(String message) {
            if (writer != null && isRunning) {
                writer.println(message);
                if (writer.checkError()) {
                    log("发送消息失败: " + message);
                    isRunning = false;
                }
            }
        }
        
        /**
         * 关闭连接
         */
        public void close() {
            isRunning = false;
            try {
                if (writer != null) {
                    writer.close();
                }
                if (reader != null) {
                    reader.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                log("关闭客户端连接时出错: " + e.getMessage());
            }
        }
    }
} 