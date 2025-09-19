const express = require('express');
const router = express.Router();
const db = require('../db');

// 获取所有日记
router.get('/diaries', (req, res) => {
  const sql = `SELECT * FROM diaries ORDER BY timestamp DESC`;
  
  db.all(sql, [], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    res.json(rows);
  });
});

// 创建新日记
router.post('/diaries', (req, res) => {
  const { user, date, content, timestamp, tags } = req.body;
  
  if (!user || !date || !content || !timestamp) {
    return res.status(400).json({ error: '缺少必要字段' });
  }
  
  const sql = `INSERT INTO diaries (user, date, content, timestamp, tags) 
               VALUES (?, ?, ?, ?, ?)`;
  
  db.run(sql, [user, date, content, timestamp, tags], function(err) {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    
    res.status(201).json({
      id: this.lastID,
      user,
      date,
      content,
      timestamp,
      tags
    });
  });
});

// 搜索日记
router.get('/diaries/search', (req, res) => {
  const { keyword, startDate, endDate, user } = req.query;
  
  let sql = `SELECT * FROM diaries WHERE 1=1`;
  const params = [];
  
  if (keyword) {
    sql += ` AND (content LIKE ? OR tags LIKE ?)`;
    params.push(`%${keyword}%`, `%${keyword}%`);
  }
  
  if (startDate) {
    sql += ` AND date >= ?`;
    params.push(startDate);
  }
  
  if (endDate) {
    sql += ` AND date <= ?`;
    params.push(endDate);
  }
  
  if (user) {
    sql += ` AND user = ?`;
    params.push(user);
  }
  
  sql += ` ORDER BY timestamp DESC`;
  
  db.all(sql, params, (err, rows) => {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    res.json(rows);
  });
});

// 更新日记
router.put('/diaries/:id', (req, res) => {
  const { content, tags } = req.body;
  const { id } = req.params;
  
  if (!content) {
    return res.status(400).json({ error: '缺少必要字段' });
  }
  
  const sql = `UPDATE diaries SET content = ?, tags = ? WHERE id = ?`;
  
  db.run(sql, [content, tags, id], function(err) {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    
    if (this.changes === 0) {
      return res.status(404).json({ error: '未找到指定ID的日记' });
    }
    
    res.json({ id, content, tags, changes: this.changes });
  });
});

// 删除日记
router.delete('/diaries/:id', (req, res) => {
  const { id } = req.params;
  
  const sql = `DELETE FROM diaries WHERE id = ?`;
  
  db.run(sql, [id], function(err) {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    
    if (this.changes === 0) {
      return res.status(404).json({ error: '未找到指定ID的日记' });
    }
    
    res.json({ deleted: true, changes: this.changes });
  });
});

module.exports = router;
