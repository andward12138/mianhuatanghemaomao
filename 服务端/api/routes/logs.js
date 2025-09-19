const express = require('express');
const router = express.Router();
const db = require('../db');

// 保存日志
router.post('/logs', (req, res) => {
  // 支持批量日志上传，检查请求是否为数组
  const logs = Array.isArray(req.body) ? req.body : [req.body];
  
  if (logs.length === 0) {
    return res.status(400).json({ error: '没有提供日志数据' });
  }
  
  // 准备批量插入
  const placeholders = logs.map(() => '(?, ?, ?, ?)').join(', ');
  const values = [];
  
  // 添加参数
  logs.forEach(log => {
    const { timestamp, level, user, message } = log;
    if (!timestamp || !level || !user || !message) {
      return res.status(400).json({ error: '日志缺少必要字段' });
    }
    
    values.push(timestamp, level, user, message);
  });
  
  const sql = `INSERT INTO logs (timestamp, level, user, message) VALUES ${placeholders}`;
  
  db.run(sql, values, function(err) {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    
    res.status(201).json({
      saved: logs.length,
      success: true
    });
  });
});

// 获取日志
router.get('/logs', (req, res) => {
  const limit = req.query.limit ? parseInt(req.query.limit) : 100;
  
  const sql = `SELECT * FROM logs ORDER BY timestamp DESC LIMIT ?`;
  
  db.all(sql, [limit], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    res.json(rows);
  });
});

module.exports = router;
