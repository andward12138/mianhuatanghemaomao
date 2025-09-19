#!/bin/bash

# 消息系统完整重启脚本
# 使用方法: ./restart-all-services.sh

echo "🚀 开始重启消息系统所有服务..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否在正确的目录
if [ ! -d "/opt/chatapp" ]; then
    log_error "未找到 /opt/chatapp 目录，请确认路径正确"
    exit 1
fi

cd /opt/chatapp

# 第一步：停止所有服务
log_info "停止现有服务..."
pkill -9 -f "ChatServer" 2>/dev/null
pkill -9 -f "java.*ChatServer" 2>/dev/null
pkill -f "node.*server.js" 2>/dev/null

# 清理端口
sudo fuser -k 8888/tcp 2>/dev/null
sudo fuser -k 3001/tcp 2>/dev/null

# 清理PID文件
rm -f chat-server/chat_server.pid 2>/dev/null
rm -f api/api_server.pid 2>/dev/null

sleep 2

# 第二步：启动聊天服务器
log_info "启动聊天服务器..."
cd /opt/chatapp/chat-server

# 编译
javac -encoding UTF-8 ChatServer.java
if [ $? -ne 0 ]; then
    log_error "聊天服务器编译失败"
    exit 1
fi

# 启动聊天服务器
nohup java -Xms256m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dchat.server.id=1 ChatServer > chat_server.log 2>&1 &
echo $! > chat_server.pid

# 第三步：启动API服务器
log_info "启动API服务器..."
cd /opt/chatapp/api

nohup node server.js > api_server.log 2>&1 &
echo $! > api_server.pid

# 第四步：等待启动完成
log_info "等待服务启动完成..."
sleep 5

# 第五步：验证服务状态
log_info "验证服务状态..."

SUCCESS=true

# 检查聊天服务器
if [ -f "/opt/chatapp/chat-server/chat_server.pid" ] && ps -p $(cat /opt/chatapp/chat-server/chat_server.pid) > /dev/null 2>&1; then
    CHAT_PID=$(cat /opt/chatapp/chat-server/chat_server.pid)
    log_info "✅ 聊天服务器运行正常 (PID: $CHAT_PID)"
else
    log_error "❌ 聊天服务器启动失败"
    if [ -f "/opt/chatapp/chat-server/chat_server.log" ]; then
        log_error "最近的错误日志："
        tail -10 /opt/chatapp/chat-server/chat_server.log
    fi
    SUCCESS=false
fi

# 检查API服务器
if [ -f "/opt/chatapp/api/api_server.pid" ] && ps -p $(cat /opt/chatapp/api/api_server.pid) > /dev/null 2>&1; then
    API_PID=$(cat /opt/chatapp/api/api_server.pid)
    log_info "✅ API服务器运行正常 (PID: $API_PID)"
else
    log_error "❌ API服务器启动失败"
    if [ -f "/opt/chatapp/api/api_server.log" ]; then
        log_error "最近的错误日志："
        tail -10 /opt/chatapp/api/api_server.log
    fi
    SUCCESS=false
fi

# 检查端口监听
log_info "端口监听状态："
netstat -tlnp | grep -E "8888|3001" | while read line; do
    echo "  $line"
done

# 最终结果
if [ "$SUCCESS" = true ]; then
    log_info "🎉 所有服务启动成功！"
    log_info "聊天服务器端口: 8888"
    log_info "API服务器端口: 3001"
    echo ""
    log_info "现在可以启动客户端进行测试了！"
else
    log_error "❌ 部分服务启动失败，请检查日志"
    exit 1
fi
