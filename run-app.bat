@echo off
title 棉花糖和猫猫的小屋 💕
echo 正在启动聊天应用...
echo.

REM 检查Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 未找到Java，请先安装JDK 21
    echo 下载地址：https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

REM 检查Maven
mvn -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 未找到Maven，请先安装Maven
    echo 下载地址：https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo ✅ 环境检查通过，正在启动应用...
echo 请稍等，首次运行可能需要下载依赖...
echo.

REM 运行应用
mvn javafx:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 启动失败，可能原因：
    echo 1. 网络连接问题（无法下载依赖）
    echo 2. Java或Maven版本不匹配
    echo 3. 项目文件不完整
    echo.
)

pause

