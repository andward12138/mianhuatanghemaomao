#!/bin/bash

# 情侣聊天应用部署脚本
# 使用方法: ./deploy.sh [commit-message]

set -e

COMMIT_MSG=${1:-"更新代码"}
SERVER_USER="username"  # 替换为你的服务器用户名
SERVER_HOST="8.134.99.69"
SERVER_PATH="/opt/chatapp"

echo "🚀 开始部署流程..."

# 1. 本地提交代码
echo "📝 提交本地代码..."
git add .
git commit -m "$COMMIT_MSG" || echo "没有新的更改需要提交"
git push origin main

# 2. 服务器拉取更新
echo "🌐 服务器拉取更新..."
ssh $SERVER_USER@$SERVER_HOST << 'EOF'
cd /opt/chatapp
sudo git pull origin main

echo "🔄 重启API服务器..."
cd api
sudo pkill -f "node.*server.js" || true
sleep 2
nohup node server.js > api_server.log 2>&1 &
echo "API服务器已重启"

echo "🔄 重启聊天服务器..."
cd ../chat-server
sudo pkill -9 -f "ChatServer" || true
sleep 2
nohup java ChatServer > chat_server.log 2>&1 &
echo "聊天服务器已重启"

echo "✅ 服务器更新完成!"
EOF

echo "🎉 部署完成! 纪念日管理器已更新到服务器"
