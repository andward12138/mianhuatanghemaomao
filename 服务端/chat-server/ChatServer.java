import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8888;
    private static ServerSocket serverSocket;
    private static final ConcurrentHashMap<String, Socket> clients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static final ExecutorService messagePool = Executors.newFixedThreadPool(10); // 专用消息发送线程池
    private static boolean running = true;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // 消息队列系统 - 关键优化
    private static final BlockingQueue<MessageTask> messageQueue = new LinkedBlockingQueue<>();
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // 强制使用IPv4
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv4Addresses", "true");
        try {
            // 明确绑定到IPv4地址
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("[" + getTime() + "] 服务器绑定到IPv4地址: " + serverSocket.getInetAddress().getHostAddress());
            System.out.println("[" + getTime() + "] 服务器已启动，监听端口: " + PORT);
            
            // 启动消息处理器 - 关键优化
            startMessageProcessor();
            
            // 启动心跳检测线程
            startHeartbeatChecker();
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[" + getTime() + "] 接受客户端连接异常: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[" + getTime() + "] 服务器启动异常: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private static void handleClient(Socket clientSocket) {
        String clientInfo = "";
        String username = "";
        
        try {
            clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            System.out.println("[" + getTime() + "] 新客户端连接: " + clientInfo);
            
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            
            // 等待客户端发送登录信息
            String loginMsg = in.readLine();
            if (loginMsg != null && loginMsg.startsWith("LOGIN:")) {
                username = loginMsg.substring(6);
                
                // 检查用户名是否已存在
                if (clients.containsKey(username)) {
                    out.println("ERROR:用户名已存在");
                    clientSocket.close();
                    System.out.println("[" + getTime() + "] 拒绝连接，用户名已存在: " + username);
                    return;
                }
                
                // 记录客户端信息
                clients.put(username, clientSocket);
                clientWriters.put(username, out); // 缓存PrintWriter，提高性能
                lastHeartbeats.put(username, System.currentTimeMillis());
                
                // 发送成功消息
                out.println("LOGIN_SUCCESS");
                System.out.println("[" + getTime() + "] 用户登录成功: " + username);
                
                // 广播用户上线消息
                broadcastUserList();
                
                // 处理客户端消息
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equals("HEARTBEAT")) {
                        lastHeartbeats.put(username, System.currentTimeMillis());
                        out.println("HEARTBEAT_ACK");
                        continue;
                    }
                    
                    if (message.startsWith("MSG:")) {
                        System.out.println("[" + getTime() + "] 收到消息 从 " + username + ": " + message);
                        broadcastMessage(username, message.substring(4));
                    } else if (message.startsWith("PRIVATE:")) {
                        // 处理私聊消息: PRIVATE:接收者:内容 (简化格式)
                        String[] parts = message.substring(8).split(":", 2);
                        if (parts.length == 2) {
                            String receiver = parts[0];
                            String content = parts[1];
                            
                            System.out.println(String.format("[%s] 私聊消息 从 %s 到 %s: %s", 
                                getTime(), username, receiver, content));
                            
                            sendPrivateMessage(username, receiver, content);
                        } else {
                            System.err.println("[" + getTime() + "] 无效的私聊消息格式: " + message);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (e instanceof SocketException && e.getMessage().contains("Connection reset")) {
                System.out.println("[" + getTime() + "] 客户端断开连接: " + (username.isEmpty() ? clientInfo : username));
            } else {
                System.err.println("[" + getTime() + "] 处理客户端异常: " + e.getMessage());
            }
        } finally {
            if (!username.isEmpty()) {
                clients.remove(username);
                clientWriters.remove(username); // 清理缓存的PrintWriter
                lastHeartbeats.remove(username);
                try {
                    broadcastUserList();
                } catch (Exception e) {
                    System.err.println("[" + getTime() + "] 广播用户列表异常: " + e.getMessage());
                }
                System.out.println("[" + getTime() + "] 用户离线: " + username);
            }
            
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("[" + getTime() + "] 关闭客户端连接异常: " + e.getMessage());
            }
        }
    }
    
    // 异步消息广播 - 关键优化，解决实时同步问题
    private static void broadcastMessage(String sender, String content) {
        String timestamp = getTime();
        String message = "MSG:" + sender + ":" + content + ":" + timestamp;
        
        // 将消息加入队列，由专门的线程处理，避免阻塞
        messageQueue.offer(new MessageTask(MessageTask.Type.BROADCAST, message, null));
        
        System.out.println("[" + timestamp + "] 消息已加入广播队列: " + message);
    }
    
    // 发送私聊消息
    private static void sendPrivateMessage(String sender, String receiver, String content) {
        String timestamp = getTime();
        
        // 构建消息格式：MSG:sender:receiver:content:timestamp
        String message = String.format("MSG:%s:%s:%s:%s", sender, receiver, content, timestamp);
        
        // 将私聊消息加入队列
        messageQueue.offer(new MessageTask(MessageTask.Type.PRIVATE, message, receiver));
        
        System.out.println(String.format("[%s] 私聊消息已加入队列: %s -> %s", 
            timestamp, sender, receiver));
    }
    
    // 生成会话ID
    private static String generateConversationId(String user1, String user2) {
        if (user1.compareTo(user2) < 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }
    
    // 消息ID生成器（简化版）
    private static final Map<String, Long> conversationCounters = new ConcurrentHashMap<>();
    
    private static long generateMessageId(String conversationId) {
        long timestamp = System.currentTimeMillis();
        long sequence = conversationCounters.computeIfAbsent(conversationId, k -> 0L);
        conversationCounters.put(conversationId, sequence + 1);
        
        // 简化的ID生成：时间戳 + 序列号
        return (timestamp << 10) | (sequence % 1024);
    }
    
    // 获取下一个序列号
    private static final Map<String, Long> sequenceCounters = new ConcurrentHashMap<>();
    
    private static long getNextSequenceNumber(String conversationId) {
        return sequenceCounters.computeIfAbsent(conversationId, k -> 0L) + 1;
    }
    
    // 异步用户列表广播
    private static void broadcastUserList() {
        StringBuilder userListMsg = new StringBuilder("USERS:");
        for (String user : clients.keySet()) {
            userListMsg.append(user).append(",");
        }
        
        if (userListMsg.length() > 6 && userListMsg.charAt(userListMsg.length() - 1) == ',') {
            userListMsg.deleteCharAt(userListMsg.length() - 1);
        }
        
        // 将用户列表更新加入队列，异步处理
        messageQueue.offer(new MessageTask(MessageTask.Type.USER_LIST, userListMsg.toString(), null));
    }
    
    // 消息处理器 - 专门处理消息发送的线程，解决实时同步问题的核心
    private static void startMessageProcessor() {
        messagePool.execute(() -> {
            while (running) {
                try {
                    MessageTask task = messageQueue.take(); // 阻塞等待消息
                    
                    if (task.type == MessageTask.Type.BROADCAST) {
                        // 并行发送给所有客户端，避免单点阻塞
                        List<CompletableFuture<Void>> futures = new ArrayList<>();
                        
                        for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                try {
                                    PrintWriter writer = entry.getValue();
                                    if (writer != null && !writer.checkError()) {
                                        writer.println(task.message);
                                        writer.flush();
                                    }
                                } catch (Exception e) {
                                    System.err.println("[" + getTime() + "] 向用户 " + entry.getKey() + " 发送消息失败: " + e.getMessage());
                                    // 标记该连接为有问题，后续清理
                                    markClientForCleanup(entry.getKey());
                                }
                            }, messagePool);
                            
                            futures.add(future);
                        }
                        
                        // 等待所有发送完成，但设置超时防止僵死
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .orTimeout(2, TimeUnit.SECONDS) // 2秒超时
                            .exceptionally(throwable -> {
                                System.err.println("[" + getTime() + "] 消息广播超时或失败: " + throwable.getMessage());
                                return null;
                            });
                            
                    } else if (task.type == MessageTask.Type.USER_LIST) {
                        // 发送用户列表更新
                        for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                            try {
                                PrintWriter writer = entry.getValue();
                                if (writer != null && !writer.checkError()) {
                                    writer.println(task.message);
                                    writer.flush();
                                }
                            } catch (Exception e) {
                                System.err.println("[" + getTime() + "] 向用户 " + entry.getKey() + " 发送用户列表失败: " + e.getMessage());
                            }
                        }
                    } else if (task.type == MessageTask.Type.PRIVATE) {
                        // 发送私聊消息给指定用户（不再向发送者回传，避免重复保存）
                        // 消息格式：MSG:sender:receiver:content:timestamp
                        String[] msgParts = task.message.split(":", 5);
                        if (msgParts.length >= 4) {
                            String sender = msgParts[1];
                            String receiver = msgParts[2];
                            String content = msgParts[3];
                            String timestamp = msgParts.length > 4 ? msgParts[4] : getTime();
                            
                            // 发送给接收者
                            PrintWriter targetWriter = clientWriters.get(task.targetUser);
                            if (targetWriter != null && !targetWriter.checkError()) {
                                try {
                                    targetWriter.println(task.message);
                                    targetWriter.flush();
                                    System.out.println("[" + getTime() + "] 私聊消息已发送给接收者: " + task.targetUser);
                                } catch (Exception e) {
                                    System.err.println("[" + getTime() + "] 向用户 " + task.targetUser + " 发送私聊消息失败: " + e.getMessage());
                                }
                            } else {
                                System.err.println("[" + getTime() + "] 用户 " + task.targetUser + " 不在线，无法发送私聊消息");
                            }
                            
                            // 不再向发送者回传消息，避免重复保存
                            // 发送者已在客户端发送时保存消息，无需服务器回传
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[" + getTime() + "] 消息处理器异常: " + e.getMessage());
                }
            }
        });
    }
    
    // 标记需要清理的客户端
    private static final Set<String> clientsToCleanup = ConcurrentHashMap.newKeySet();
    
    private static void markClientForCleanup(String username) {
        clientsToCleanup.add(username);
    }
    
    private static void startHeartbeatChecker() {
        threadPool.execute(() -> {
            while (running) {
                try {
                    long now = System.currentTimeMillis();
                    List<String> timeoutUsers = new ArrayList<>();
                    
                    // 检查心跳超时
                    for (Map.Entry<String, Long> entry : lastHeartbeats.entrySet()) {
                        if (now - entry.getValue() > 30000) { // 30秒没有心跳
                            timeoutUsers.add(entry.getKey());
                        }
                    }
                    
                    // 添加标记为需要清理的客户端
                    timeoutUsers.addAll(clientsToCleanup);
                    clientsToCleanup.clear();
                    
                    for (String username : timeoutUsers) {
                        Socket socket = clients.get(username);
                        if (socket != null) {
                            System.out.println("[" + getTime() + "] 用户 " + username + " 连接异常，断开连接");
                            socket.close();
                            clients.remove(username);
                            clientWriters.remove(username); // 清理PrintWriter缓存
                            lastHeartbeats.remove(username);
                        }
                    }
                    
                    if (!timeoutUsers.isEmpty()) {
                        broadcastUserList();
                    }
                    
                    Thread.sleep(5000); // 每5秒检查一次
                } catch (Exception e) {
                    System.err.println("[" + getTime() + "] 心跳检测异常: " + e.getMessage());
                }
            }
        });
    }
    
    private static String getTime() {
        return sdf.format(new Date());
    }
    
    // 安全解析long值的辅助方法
    private static long parseLong(String str, long defaultValue) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public static void shutdown() {
        running = false;
        
        for (Socket socket : clients.values()) {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // 忽略关闭时的异常
            }
        }
        
        clients.clear();
        clientWriters.clear(); // 清理PrintWriter缓存
        lastHeartbeats.clear();
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // 忽略关闭时的异常
        }
        
        threadPool.shutdown();
        messagePool.shutdown(); // 关闭消息处理线程池
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            if (!messagePool.awaitTermination(5, TimeUnit.SECONDS)) {
                messagePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            messagePool.shutdownNow();
        }
        
        System.out.println("[" + getTime() + "] 服务器已关闭");
    }
    
    // 消息任务类 - 支持异步消息处理
    static class MessageTask {
        enum Type {
            BROADCAST, USER_LIST, PRIVATE
        }
        
        final Type type;
        final String message;
        final String targetUser;
        final long messageId;
        final long sequenceNumber;
        
        // 旧版本构造函数（兼容性）
        MessageTask(Type type, String message, String targetUser) {
            this.type = type;
            this.message = message;
            this.targetUser = targetUser;
            this.messageId = 0;
            this.sequenceNumber = 0;
        }
        
        // 新版本构造函数（支持消息ID和序列号）
        MessageTask(Type type, String message, String targetUser, long messageId, long sequenceNumber) {
            this.type = type;
            this.message = message;
            this.targetUser = targetUser;
            this.messageId = messageId;
            this.sequenceNumber = sequenceNumber;
        }
    }
}