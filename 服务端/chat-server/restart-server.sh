#!/bin/bash

echo "=========================================="
echo "重启优化后的聊天服务器"
echo "=========================================="

# 停止现有服务器
echo "停止现有服务器..."
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

# 编译服务器
echo "编译服务器..."
javac -encoding UTF-8 ChatServer.java

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"

# 启动服务器
echo "启动优化后的聊天服务器..."

# 设置JVM参数
JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"

# 启动服务器
nohup java $JVM_OPTS ChatServer > chat_server.log 2>&1 &
SERVER_PID=$!

echo $SERVER_PID > chat_server.pid
echo "✅ 服务器已启动，PID: $SERVER_PID"

# 等待启动
sleep 3

# 检查是否成功启动
if ps -p $SERVER_PID > /dev/null 2>&1; then
    echo ""
    echo "🎉 服务器启动成功！"
    echo "- 监听端口: 8888"
    echo "- 进程ID: $SERVER_PID"
    echo "- 日志文件: chat_server.log"
    echo ""
    echo "🔧 主要优化:"
    echo "- ✅ 异步消息广播（解决实时同步问题）"
    echo "- ✅ 消息队列系统（提高并发性能）"
    echo "- ✅ 连接池优化（减少资源占用）"
    echo "- ✅ 超时处理机制（防止僵死连接）"
    echo ""
    echo "📊 查看实时日志: tail -f chat_server.log"
    echo "🌐 检查端口监听: netstat -tlnp | grep 8888"
else
    echo "❌ 服务器启动失败，请检查日志:"
    cat chat_server.log
    exit 1
fi

echo "=========================================="