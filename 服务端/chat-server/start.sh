#!/bin/bash
cd /opt/chatapp/chat-server
nohup java ChatServer > chat_server.log 2>&1 &
echo $! > chat_server.pid
echo "聊天服务器已启动，PID: $(cat chat_server.pid)"
