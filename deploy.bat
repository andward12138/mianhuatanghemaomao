@echo off
REM 情侣聊天应用部署脚本 (Windows版本)
REM 使用方法: deploy.bat "提交信息"

setlocal enabledelayedexpansion

set COMMIT_MSG=%~1
if "%COMMIT_MSG%"=="" set COMMIT_MSG=更新代码

set SERVER_USER=username
set SERVER_HOST=8.134.99.69
set SERVER_PATH=/opt/chatapp

echo 🚀 开始部署流程...

REM 1. 本地提交代码
echo 📝 提交本地代码...
git add .
git commit -m "%COMMIT_MSG%" 2>nul || echo 没有新的更改需要提交
git push origin main

REM 2. 服务器拉取更新
echo 🌐 服务器拉取更新...
ssh %SERVER_USER%@%SERVER_HOST% "cd /opt/chatapp && sudo git pull origin main && cd api && sudo pkill -f 'node.*server.js' || true && sleep 2 && nohup node server.js > api_server.log 2>&1 & && echo 'API服务器已重启' && cd ../chat-server && sudo pkill -9 -f 'ChatServer' || true && sleep 2 && nohup java ChatServer > chat_server.log 2>&1 & && echo '聊天服务器已重启' && echo '✅ 服务器更新完成!'"

echo 🎉 部署完成! 纪念日管理器已更新到服务器
pause
