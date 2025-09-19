#!/bin/bash

# API服务器调试和修复脚本
echo "🔧 开始调试API服务器..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

# 进入API目录
cd /opt/chatapp/api || {
    log_error "无法进入API目录"
    exit 1
}

log_info "当前目录: $(pwd)"

# 1. 停止现有API服务器
log_info "停止现有API服务器..."
pkill -f "node.*server.js" 2>/dev/null
rm -f api_server.pid 2>/dev/null
sleep 2

# 2. 检查数据库目录和文件
log_info "检查数据库状态..."
DB_DIR="/opt/chatapp/db"
DB_FILE="$DB_DIR/messages.db"

if [ ! -d "$DB_DIR" ]; then
    log_warn "数据库目录不存在，创建: $DB_DIR"
    mkdir -p "$DB_DIR"
fi

if [ ! -f "$DB_FILE" ]; then
    log_warn "数据库文件不存在: $DB_FILE"
    log_info "将在首次运行时自动创建"
else
    log_info "数据库文件存在: $DB_FILE"
    log_debug "文件大小: $(ls -lh $DB_FILE | awk '{print $5}')"
    log_debug "文件权限: $(ls -l $DB_FILE | awk '{print $1}')"
fi

# 3. 检查Node.js和依赖
log_info "检查Node.js环境..."
if ! command -v node &> /dev/null; then
    log_error "Node.js 未安装"
    exit 1
fi

NODE_VERSION=$(node --version)
log_info "Node.js版本: $NODE_VERSION"

if [ ! -f "package.json" ]; then
    log_error "package.json 不存在"
    exit 1
fi

if [ ! -d "node_modules" ]; then
    log_warn "node_modules 不存在，正在安装依赖..."
    npm install
fi

# 4. 运行数据库调试脚本
if [ -f "debug-database.js" ]; then
    log_info "运行数据库调试脚本..."
    node debug-database.js
    echo ""
fi

# 5. 启动API服务器并显示详细日志
log_info "启动API服务器..."
echo "========================= API服务器日志 ========================="

# 启动服务器并实时显示日志
node server.js &
API_PID=$!
echo $API_PID > api_server.pid

# 等待服务器启动
sleep 3

# 检查服务器是否正在运行
if ps -p $API_PID > /dev/null 2>&1; then
    log_info "✅ API服务器启动成功 (PID: $API_PID)"
    
    # 检查端口监听
    if netstat -tlnp | grep -q ":3001.*LISTEN"; then
        log_info "✅ 端口3001监听正常"
    else
        log_error "❌ 端口3001未监听"
    fi
    
    # 测试API连接
    log_info "测试API连接..."
    sleep 2
    
    # 测试获取消息接口
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/api/messages 2>/dev/null)
    if [ "$HTTP_STATUS" = "200" ]; then
        log_info "✅ API接口响应正常"
    else
        log_error "❌ API接口响应异常 (状态码: $HTTP_STATUS)"
    fi
    
    # 测试消息保存接口
    log_info "测试消息保存接口..."
    TEST_RESPONSE=$(curl -s -X POST http://localhost:3001/api/messages \
        -H "Content-Type: application/json" \
        -d '{"sender":"test_debug","receiver":"test_debug2","content":"debug test message","timestamp":"2025-09-19T16:00:00"}' \
        2>/dev/null)
    
    if echo "$TEST_RESPONSE" | grep -q '"id"'; then
        log_info "✅ 消息保存测试成功"
        log_debug "响应: $TEST_RESPONSE"
    else
        log_error "❌ 消息保存测试失败"
        log_error "响应: $TEST_RESPONSE"
    fi
    
else
    log_error "❌ API服务器启动失败"
    if [ -f "api_server.log" ]; then
        log_error "错误日志："
        tail -10 api_server.log
    fi
    exit 1
fi

echo ""
log_info "调试完成！如果发现问题，请检查上面的输出信息。"
log_info "API服务器正在运行，PID: $API_PID"
log_info "要查看实时日志，运行: tail -f /opt/chatapp/api/api_server.log"
log_info "要停止服务器，运行: kill $API_PID"
