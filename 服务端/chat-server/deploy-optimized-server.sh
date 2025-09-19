#!/bin/bash

# 聊天服务器优化部署脚本
# 用于部署优化后的ChatServer，解决实时消息同步问题

echo "=========================================="
echo "聊天服务器优化部署脚本"
echo "=========================================="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 8或更高版本"
    exit 1
fi

echo "Java版本检查:"
java -version

# 停止现有服务器
echo ""
echo "停止现有聊天服务器..."
if [ -f "chat_server.pid" ]; then
    PID=$(cat chat_server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "正在停止进程 $PID..."
        kill $PID
        sleep 3
        
        # 强制停止如果还在运行
        if ps -p $PID > /dev/null 2>&1; then
            echo "强制停止进程 $PID..."
            kill -9 $PID
        fi
    fi
    rm -f chat_server.pid
fi

# 备份原始服务器
echo ""
echo "备份原始服务器文件..."
if [ -f "ChatServer.java" ]; then
    cp ChatServer.java ChatServer.java.backup.$(date +%Y%m%d_%H%M%S)
    echo "原始文件已备份"
fi

# 编译优化后的服务器
echo ""
echo "编译优化后的服务器..."
javac -encoding UTF-8 ChatServerOptimized.java

if [ $? -ne 0 ]; then
    echo "错误: 编译失败"
    exit 1
fi

echo "编译成功"

# 启动优化后的服务器
echo ""
echo "启动优化后的聊天服务器..."

# 设置JVM参数优化性能
JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8"

# 启动服务器并记录PID
nohup java $JVM_OPTS ChatServerOptimized > chat_server_optimized.log 2>&1 &
SERVER_PID=$!

echo $SERVER_PID > chat_server.pid
echo "服务器已启动，PID: $SERVER_PID"
echo "日志文件: chat_server_optimized.log"

# 等待服务器启动
echo ""
echo "等待服务器启动..."
sleep 3

# 检查服务器是否正常运行
if ps -p $SERVER_PID > /dev/null 2>&1; then
    echo "✅ 服务器启动成功！"
    echo ""
    echo "服务器信息:"
    echo "- 监听端口: 8888"
    echo "- 进程ID: $SERVER_PID"
    echo "- 日志文件: chat_server_optimized.log"
    echo ""
    echo "主要优化:"
    echo "- ✅ 异步消息广播，避免阻塞"
    echo "- ✅ 消息队列系统，提高并发性能"
    echo "- ✅ 连接池优化，减少资源占用"
    echo "- ✅ 超时处理机制，防止僵死连接"
    echo ""
    echo "查看实时日志: tail -f chat_server_optimized.log"
else
    echo "❌ 服务器启动失败，请检查日志文件"
    cat chat_server_optimized.log
    exit 1
fi

# 显示网络监听状态
echo ""
echo "网络监听状态:"
netstat -tlnp 2>/dev/null | grep :8888 || ss -tlnp 2>/dev/null | grep :8888

echo ""
echo "部署完成！"
echo "=========================================="