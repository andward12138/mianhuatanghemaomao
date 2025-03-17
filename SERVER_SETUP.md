# 聊天服务器部署指南

本文档提供了在云服务器上部署聊天服务器的详细步骤，以及如何配置客户端连接到服务器。

## 服务器信息

- 服务器IP地址: 8.134.99.69
- 域名: mianhuatanghemaomao.xin
- 聊天服务端口: 8888

## 服务器端部署步骤

### 1. 连接到服务器

使用SSH连接到您的云服务器：

```bash
ssh root@8.134.99.69
```

或者使用域名：

```bash
ssh root@mianhuatanghemaomao.xin
```

### 2. 安装Java环境

```bash
# 更新软件包列表
apt update

# 安装JDK
apt install -y openjdk-11-jdk

# 验证安装
java -version
```

### 3. 创建项目目录

```bash
mkdir -p /opt/chatserver/com/example/message/server
cd /opt/chatserver
```

### 4. 上传服务器程序

将`ChatServer.java`文件上传到服务器。您可以使用以下方法之一：

#### 方法1: 使用SCP命令从本地上传

在本地计算机上执行：

```bash
scp src/main/java/com/example/message/server/ChatServer.java root@8.134.99.69:/opt/chatserver/com/example/message/server/
```

#### 方法2: 在服务器上直接创建文件

```bash
nano /opt/chatserver/com/example/message/server/ChatServer.java
```

然后粘贴`ChatServer.java`的内容，按`Ctrl+X`，然后按`Y`保存。

### 5. 编译服务器程序

```bash
cd /opt/chatserver
javac -d . com/example/message/server/ChatServer.java
```

### 6. 创建启动脚本

```bash
nano /opt/chatserver/start.sh
```

添加以下内容：

```bash
#!/bin/bash
cd /opt/chatserver
nohup java com.example.message.server.ChatServer > chatserver.log 2>&1 &
echo $! > chatserver.pid
echo "聊天服务器已启动，PID: $(cat chatserver.pid)"
```

使脚本可执行：

```bash
chmod +x /opt/chatserver/start.sh
```

### 7. 创建停止脚本

```bash
nano /opt/chatserver/stop.sh
```

添加以下内容：

```bash
#!/bin/bash
if [ -f /opt/chatserver/chatserver.pid ]; then
    PID=$(cat /opt/chatserver/chatserver.pid)
    if ps -p $PID > /dev/null; then
        echo "正在停止聊天服务器 (PID: $PID)..."
        kill $PID
        rm /opt/chatserver/chatserver.pid
        echo "聊天服务器已停止"
    else
        echo "聊天服务器未运行"
        rm /opt/chatserver/chatserver.pid
    fi
else
    echo "聊天服务器未运行"
fi
```

使脚本可执行：

```bash
chmod +x /opt/chatserver/stop.sh
```

### 8. 配置防火墙

确保服务器的防火墙开放了8888端口：

```bash
# 对于使用ufw的系统
ufw allow 8888/tcp
ufw status

# 对于使用firewalld的系统
firewall-cmd --permanent --add-port=8888/tcp
firewall-cmd --reload
firewall-cmd --list-all
```

### 9. 启动服务器

```bash
/opt/chatserver/start.sh
```

### 10. 查看日志

```bash
tail -f /opt/chatserver/chatserver.log
```

### 11. 设置开机自启动

```bash
nano /etc/systemd/system/chatserver.service
```

添加以下内容：

```
[Unit]
Description=Chat Server
After=network.target

[Service]
Type=forking
User=root
WorkingDirectory=/opt/chatserver
ExecStart=/opt/chatserver/start.sh
ExecStop=/opt/chatserver/stop.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
systemctl daemon-reload
systemctl enable chatserver
systemctl start chatserver
systemctl status chatserver
```

## 客户端配置

### 1. 修改客户端配置

在客户端程序中，修改`ChatService.java`文件中的服务器地址：

```java
// 服务器模式相关变量
private static final String SERVER_HOST = "mianhuatanghemaomao.xin"; // 使用域名连接
private static final int SERVER_PORT = 8888;
```

### 2. 编译并运行客户端

使用Maven编译项目：

```bash
mvn clean package
```

运行客户端：

```bash
java -jar target/message-1.0-SNAPSHOT.jar
```

### 3. 使用服务器模式

1. 启动应用程序后，会提示您输入用户名
2. 然后会提示您选择连接模式，选择"服务器连接"模式
3. 应用程序会自动连接到聊天服务器
4. 连接成功后，您可以在在线用户列表中看到其他用户
5. 选择一个用户开始聊天

## 故障排除

### 服务器无法启动

- 检查Java是否正确安装：`java -version`
- 检查编译是否成功：`ls -la /opt/chatserver/com/example/message/server/`
- 检查日志文件：`cat /opt/chatserver/chatserver.log`

### 客户端无法连接到服务器

- 确认服务器正在运行：`ps aux | grep ChatServer`
- 检查防火墙设置：`ufw status` 或 `firewall-cmd --list-all`
- 测试端口是否开放：`telnet 8.134.99.69 8888`
- 检查服务器日志：`tail -f /opt/chatserver/chatserver.log`

### 域名解析问题

如果使用域名连接出现问题，可以尝试使用IP地址：

```java
private static final String SERVER_HOST = "8.134.99.69"; // 使用IP地址连接
```

## 安全注意事项

当前实现是基础版本，如果要在实际环境中长期使用，建议考虑以下安全措施：

1. 添加用户认证机制
2. 加密通信内容
3. 实现消息持久化
4. 添加用户注册功能
5. 实现更完善的错误处理和恢复机制 