// 客户端优化补丁 - 针对 ChatService.java 的关键修改

/**
 * 优化1: 改进 getOnlineUsers() 方法，减少频繁的API调用
 * 
 * 原问题：每次调用都会发送GET_USERS请求，造成网络拥堵
 * 解决方案：使用缓存机制，定期更新而不是每次请求
 */
public class ClientOptimizationPatch {
    
    // 用户列表缓存优化
    private static long lastUserListUpdate = 0;
    private static final long USER_LIST_CACHE_DURATION = 5000; // 5秒缓存
    
    /**
     * 优化后的获取在线用户列表方法
     * 替换原 ChatService.java 中的 getOnlineUsers() 方法 (第588-635行)
     */
    public static List<String> getOnlineUsersOptimized() {
        List<String> users = new ArrayList<>();
        
        if (isConnectedToServer) {
            long currentTime = System.currentTimeMillis();
            
            // 返回当前缓存的用户列表
            synchronized (onlineUsers) {
                users.addAll(onlineUsers);
                logger.info("返回缓存的在线用户列表: " + users.size() + " 用户");
            }
            
            // 只有当缓存过期且用户列表为空时才主动请求
            if (users.isEmpty() || (currentTime - lastUserListUpdate > USER_LIST_CACHE_DURATION)) {
                logger.info("用户列表缓存过期或为空，请求更新");
                if (serverWriter != null) {
                    try {
                        serverWriter.println("GET_USERS");
                        serverWriter.flush();
                        lastUserListUpdate = currentTime;
                        
                        // 减少等待时间，避免阻塞UI
                        Thread.sleep(200); // 从500ms减少到200ms
                        
                        // 再次检查用户列表
                        synchronized (onlineUsers) {
                            users.clear();
                            users.addAll(onlineUsers);
                            logger.info("更新后的用户列表: " + users.size() + " 用户");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "请求用户列表时出错", e);
                    }
                }
            }
        }
        
        return users;
    }
    
    /**
     * 优化2: 改进消息处理流程，避免重复保存
     * 
     * 原问题：消息在多个地方被保存，造成重复和延迟
     * 解决方案：统一消息处理流程，避免重复操作
     */
    
    /**
     * 优化后的消息发送方法
     * 替换原 ChatService.java 中的 sendPrivateMessage() 方法 (第881-940行)
     */
    public static void sendPrivateMessageOptimized(String receiver, String content) {
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
            final int tempId = (int)(System.currentTimeMillis() % 100000) + (int)(Math.random() * 1000);
            
            logger.info("发送消息，临时ID: " + tempId + " 给 " + receiver);
            
            // 立即显示在UI上（乐观更新）
            if (messageReceivedCallback != null) {
                final ChatMessage chatMessage = new ChatMessage(
                    tempId, currentUser, receiver, content, timestampStr, false);
                
                // 立即更新UI，不等待服务器响应
                Platform.runLater(() -> {
                    logger.info("乐观更新UI: " + chatMessage.getContent());
                    messageReceivedCallback.accept(chatMessage);
                });
            }
            
            // 异步保存到数据库和发送到服务器
            CompletableFuture.runAsync(() -> {
                try {
                    // 保存到数据库
                    int savedId = saveMessageAndGetId(currentUser, receiver, content, String.valueOf(tempId));
                    logger.info("消息已保存到数据库，ID: " + savedId);
                    
                    // 发送到服务器
                    String messageToSend = "PRIVATE:" + receiver + ":" + content;
                    logger.info("发送到服务器: " + messageToSend);
                    serverWriter.println(messageToSend);
                    serverWriter.flush();
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "异步处理消息时出错", e);
                    
                    // 如果失败，可以考虑回滚UI更新或显示错误状态
                    Platform.runLater(() -> {
                        // 这里可以添加错误处理逻辑，比如显示消息发送失败的状态
                        logger.warning("消息发送失败，可以在UI上显示重试选项");
                    });
                }
            });
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "发送私人消息时出错", e);
        }
    }
    
    /**
     * 优化3: 改进消息接收处理，避免重复显示
     * 
     * 原问题：消息可能被重复显示，特别是发送者自己的消息
     * 解决方案：添加消息去重机制
     */
    
    // 消息去重缓存
    private static final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private static final long MESSAGE_CACHE_DURATION = 60000; // 1分钟缓存
    private static long lastCacheClear = System.currentTimeMillis();
    
    /**
     * 优化后的服务器消息处理方法
     * 替换原 ChatService.java 中 processServerMessage() 方法的消息处理部分 (第291-348行)
     */
    public static void processServerMessageOptimized(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        logger.info("处理服务器消息: " + message);
        
        // 定期清理消息缓存
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheClear > MESSAGE_CACHE_DURATION) {
            processedMessages.clear();
            lastCacheClear = currentTime;
            logger.fine("清理消息去重缓存");
        }

        if (message.startsWith("MSG:")) {
            String[] parts = message.substring("MSG:".length()).split(":", 3);
            String sender, receiver, content;
            
            if (parts.length >= 3) {
                sender = parts[0];
                receiver = parts[1];
                content = parts[2];
            } else if (parts.length == 2) {
                sender = parts[0];
                receiver = "all";
                content = parts[1];
            } else {
                logger.warning("消息格式错误: " + message);
                return;
            }
            
            // 生成消息唯一标识用于去重
            String messageKey = sender + ":" + receiver + ":" + content + ":" + (currentTime / 1000); // 按秒去重
            
            // 检查是否已处理过此消息
            if (processedMessages.contains(messageKey)) {
                logger.fine("消息已处理过，跳过: " + messageKey);
                return;
            }
            
            processedMessages.add(messageKey);
            
            // 如果是自己发送的消息，检查是否需要显示
            if (sender.equals(currentUser)) {
                logger.info("收到自己发送的消息回显，检查是否为重复消息");
                // 可以选择不显示自己的消息回显，因为已经通过乐观更新显示了
                return;
            }
            
            // 保存消息到本地数据库
            int msgId = saveMessageAndGetId(sender, receiver, content, null);
            
            // 通知UI更新
            if (messageReceivedCallback != null) {
                final ChatMessage chatMessage = new ChatMessage(
                    msgId, sender, receiver, content, LocalDateTime.now().toString(), false);
                
                Platform.runLater(() -> {
                    messageReceivedCallback.accept(chatMessage);
                });
            }
        }
    }
    
    /**
     * 优化4: 连接状态监控和自动重连优化
     */
    
    private static volatile boolean isReconnecting = false;
    
    /**
     * 优化后的重连机制
     * 替换原 ChatService.java 中的 tryReconnect() 方法 (第1016-1035行)
     */
    public static void tryReconnectOptimized() {
        if (isReconnecting || !isUsingServerMode || currentUser == null) {
            return;
        }
        
        isReconnecting = true;
        logger.info("开始重连流程...");
        
        CompletableFuture.runAsync(() -> {
            int maxRetries = 3;
            int retryDelay = 2000; // 2秒
            
            for (int i = 0; i < maxRetries; i++) {
                try {
                    logger.info("重连尝试 " + (i + 1) + "/" + maxRetries);
                    
                    Thread.sleep(retryDelay);
                    
                    boolean reconnected = connectToServer(currentUser);
                    if (reconnected) {
                        logger.info("重连成功");
                        isReconnecting = false;
                        return;
                    }
                    
                    retryDelay *= 2; // 指数退避
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "重连尝试失败: " + e.getMessage(), e);
                }
            }
            
            logger.warning("重连失败，已达到最大重试次数");
            isReconnecting = false;
        });
    }
}