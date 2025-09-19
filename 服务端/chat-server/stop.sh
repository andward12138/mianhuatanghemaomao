#!/bin/bash
if [ -f chat_server.pid ]; then
  PID=$(cat chat_server.pid)
  if ps -p $PID > /dev/null; then
    echo "正在停止聊天服务器 (PID: $PID)..."
    kill $PID
    rm chat_server.pid
    echo "聊天服务器已停止"
  else
    echo "聊天服务器未运行"
    rm chat_server.pid
  fi
else
  echo "找不到PID文件，聊天服务器可能未启动"
fi
