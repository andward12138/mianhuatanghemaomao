module.exports = {
  apps: [{
    name: 'chatapp-api',
    script: 'server.js',
    cwd: '/opt/chatapp/服务端/api',
    instances: 1,
    autorestart: true,
    watch: false,
    max_memory_restart: '1G',
    env: {
      NODE_ENV: 'production',
      PORT: 3001
    },
    // 自动重启配置
    max_restarts: 10,
    min_uptime: '10s',
    // 错误日志配置
    error_file: '/opt/chatapp/服务端/api/logs/err.log',
    out_file: '/opt/chatapp/服务端/api/logs/out.log',
    log_file: '/opt/chatapp/服务端/api/logs/combined.log',
    time: true,
    // 重启延迟配置
    restart_delay: 4000,
    // 监听文件变化（生产环境建议关闭）
    ignore_watch: ['node_modules', 'logs', '*.log'],
    // 集群配置（单实例）
    exec_mode: 'fork'
  }]
};
