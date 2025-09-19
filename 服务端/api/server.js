const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const app = express();
const port = 3000;

// 中间件配置
app.use(cors());
app.use(bodyParser.json());

// 数据库连接
const db = new sqlite3.Database(path.join(__dirname, '../db/messages.db'));

// 创建必要的表
db.serialize(() => {
  // 创建消息表
  db.run(`CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender TEXT,
    receiver TEXT,
    content TEXT,
    timestamp TEXT
  )`);

  // 创建日记表
  db.run(`CREATE TABLE IF NOT EXISTS diaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user TEXT NOT NULL,
    date TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    tags TEXT
  )`);

  // 创建日志表
  db.run(`CREATE TABLE IF NOT EXISTS logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    level TEXT NOT NULL,
    user TEXT NOT NULL,
    message TEXT NOT NULL
  )`);
});

// 获取所有消息或两个用户之间的消息
app.get('/api/messages', (req, res) => {
  const { user1, user2 } = req.query;
  
  // 如果有user1和user2参数，获取两个用户之间的消息
  if (user1 && user2) {
    console.log(`获取用户 ${user1} 和 ${user2} 之间的消息`);
    db.all(
      `SELECT * FROM (
         SELECT *, ROW_NUMBER() OVER (
           PARTITION BY sender, receiver, content, timestamp 
           ORDER BY id ASC
         ) as rn
         FROM chat_messages 
         WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
       ) WHERE rn = 1
       ORDER BY timestamp ASC`,
      [user1, user2, user2, user1],
      (err, rows) => {
        if (err) {
          console.error('获取用户间消息错误:', err);
          return res.status(500).json({ error: '获取消息失败' });
        }
        console.log(`找到 ${rows.length} 条消息（已去重）`);
        res.json(rows);
      }
    );
  } else {
    // 获取所有消息（去重）
    db.all(`SELECT * FROM (
              SELECT *, ROW_NUMBER() OVER (
                PARTITION BY sender, receiver, content, timestamp 
                ORDER BY id ASC
              ) as rn
              FROM chat_messages
            ) WHERE rn = 1
            ORDER BY timestamp DESC`, (err, rows) => {
      if (err) {
        console.error(err);
        return res.status(500).json({ error: '获取消息失败' });
      }
      console.log(`返回 ${rows.length} 条消息（已去重）`);
      res.json(rows);
    });
  }
});

// 获取特定用户的消息
app.get('/api/messages/:username', (req, res) => {
  const username = req.params.username;
  db.all(
    'SELECT * FROM chat_messages WHERE sender = ? OR receiver = ? OR receiver = "all" ORDER BY timestamp DESC',
    [username, username],
    (err, rows) => {
      if (err) {
        console.error(err);
        return res.status(500).json({ error: '获取消息失败' });
      }
      res.json(rows);
    }
  );
});

// 添加新消息
app.post('/api/messages', (req, res) => {
  const { sender, receiver, content, timestamp } = req.body;

  console.log('收到消息:', req.body);

  if (!sender || !content || !timestamp) {
    return res.status(400).json({ error: '消息数据不完整' });
  }

  const receiverValue = receiver || 'all';

  db.run(
    'INSERT INTO chat_messages (sender, receiver, content, timestamp) VALUES (?, ?, ?, ?)',
    [sender, receiverValue, content, timestamp],
    function (err) {
      if (err) {
        console.error('数据库插入错误:', err);
        return res.status(500).json({ error: '存储消息失败' });
      }

      console.log('消息已存储，ID:', this.lastID);
      res.json({
        id: this.lastID,
        sender,
        receiver: receiverValue,
        content,
        timestamp
      });
    }
  );
});

// ===== 日记相关API路由 =====

// 获取所有日记
app.get('/api/diaries', (req, res) => {
  db.all('SELECT * FROM diaries ORDER BY timestamp DESC', (err, rows) => {
    if (err) {
      console.error('获取日记错误:', err);
      return res.status(500).json({ error: '获取日记失败' });
    }
    res.json(rows);
  });
});

// 创建新日记
app.post('/api/diaries', (req, res) => {
  const { user, date, content, timestamp, tags } = req.body;
  
  console.log('收到日记:', req.body);

  if (!user || !date || !content || !timestamp) {
    return res.status(400).json({ error: '日记数据不完整' });
  }

  db.run(
    'INSERT INTO diaries (user, date, content, timestamp, tags) VALUES (?, ?, ?, ?, ?)',
    [user, date, content, timestamp, tags || ''],
    function (err) {
      if (err) {
        console.error('日记存储错误:', err);
        return res.status(500).json({ error: '存储日记失败' });
      }

      console.log('日记已存储，ID:', this.lastID);
      res.status(201).json({
        id: this.lastID,
        user,
        date,
        content,
        timestamp,
        tags: tags || ''
      });
    }
  );
});

// 搜索日记
app.get('/api/diaries/search', (req, res) => {
  const { keyword, startDate, endDate, user } = req.query;
  
  let sql = 'SELECT * FROM diaries WHERE 1=1';
  const params = [];
  
  if (keyword) {
    sql += ' AND (content LIKE ? OR tags LIKE ?)';
    params.push(`%${keyword}%`, `%${keyword}%`);
  }
  
  if (startDate) {
    sql += ' AND date >= ?';
    params.push(startDate);
  }
  
  if (endDate) {
    sql += ' AND date <= ?';
    params.push(endDate);
  }
  
  if (user) {
    sql += ' AND user = ?';
    params.push(user);
  }
  
  sql += ' ORDER BY timestamp DESC';
  
  db.all(sql, params, (err, rows) => {
    if (err) {
      console.error('搜索日记错误:', err);
      return res.status(500).json({ error: '搜索日记失败' });
    }
    res.json(rows);
  });
});

// 更新日记
app.put('/api/diaries/:id', (req, res) => {
  const { content, tags } = req.body;
  const { id } = req.params;
  
  if (!content) {
    return res.status(400).json({ error: '日记内容不能为空' });
  }
  
  db.run(
    'UPDATE diaries SET content = ?, tags = ? WHERE id = ?',
    [content, tags || '', id],
    function (err) {
      if (err) {
        console.error('更新日记错误:', err);
        return res.status(500).json({ error: '更新日记失败' });
      }
      
      if (this.changes === 0) {
        return res.status(404).json({ error: '未找到指定ID的日记' });
      }
      
      console.log('日记已更新, ID:', id);
      res.json({
        id: parseInt(id),
        content,
        tags: tags || '',
        changes: this.changes
      });
    }
  );
});

// 删除日记
app.delete('/api/diaries/:id', (req, res) => {
  const { id } = req.params;
  
  db.run('DELETE FROM diaries WHERE id = ?', [id], function (err) {
    if (err) {
      console.error('删除日记错误:', err);
      return res.status(500).json({ error: '删除日记失败' });
    }
    
    if (this.changes === 0) {
      return res.status(404).json({ error: '未找到指定ID的日记' });
    }
    
    console.log('日记已删除, ID:', id);
    res.json({ deleted: true, changes: this.changes });
  });
});

// ===== 日志相关API路由 =====

// 保存日志
app.post('/api/logs', (req, res) => {
  // 支持批量日志上传
  const logs = Array.isArray(req.body) ? req.body : [req.body];
  
  if (logs.length === 0) {
    return res.status(400).json({ error: '没有提供日志数据' });
  }
  
  // 使用事务处理批量插入
  db.serialize(() => {
    db.run('BEGIN TRANSACTION');
    
    const stmt = db.prepare('INSERT INTO logs (timestamp, level, user, message) VALUES (?, ?, ?, ?)');
    let success = true;
    
    logs.forEach(log => {
      const { timestamp, level, user, message } = log;
      if (!timestamp || !level || !user || !message) {
        success = false;
        return;
      }
      
      stmt.run([timestamp, level, user, message], err => {
        if (err) {
          console.error('日志插入错误:', err);
          success = false;
        }
      });
    });
    
    stmt.finalize();
    
    if (success) {
      db.run('COMMIT', err => {
        if (err) {
          console.error('日志事务提交错误:', err);
          return res.status(500).json({ error: '存储日志失败' });
        }
        console.log(`成功保存 ${logs.length} 条日志`);
        res.status(201).json({ saved: logs.length, success: true });
      });
    } else {
      db.run('ROLLBACK');
      res.status(500).json({ error: '存储日志失败' });
    }
  });
});

// 获取日志
app.get('/api/logs', (req, res) => {
  const limit = req.query.limit ? parseInt(req.query.limit) : 100;
  
  db.all('SELECT * FROM logs ORDER BY timestamp DESC LIMIT ?', [limit], (err, rows) => {
    if (err) {
      console.error('获取日志错误:', err);
      return res.status(500).json({ error: '获取日志失败' });
    }
    res.json(rows);
  });
});

// 启动服务器
app.listen(port, '0.0.0.0', () => {
  console.log(`API服务器运行在 http://0.0.0.0:${port}`);
});

// 优雅关闭
process.on('SIGINT', () => {
  db.close();
  process.exit(0);
});
