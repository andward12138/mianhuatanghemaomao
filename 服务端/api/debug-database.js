const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// 数据库路径
const dbPath = path.join(__dirname, '../db/messages.db');
console.log('数据库路径:', dbPath);

// 检查数据库连接
const db = new sqlite3.Database(dbPath, (err) => {
  if (err) {
    console.error('数据库连接失败:', err.message);
    return;
  }
  console.log('✅ 数据库连接成功');
});

// 检查表是否存在
db.all("SELECT name FROM sqlite_master WHERE type='table'", (err, tables) => {
  if (err) {
    console.error('查询表失败:', err);
    return;
  }
  console.log('现有表:', tables);
});

// 检查chat_messages表结构
db.all("PRAGMA table_info(chat_messages)", (err, columns) => {
  if (err) {
    console.error('查询表结构失败:', err);
    return;
  }
  console.log('chat_messages表结构:', columns);
});

// 测试插入一条消息
const testMessage = {
  sender: 'test_user',
  receiver: 'test_receiver',
  content: 'test message',
  timestamp: new Date().toISOString()
};

console.log('测试插入消息:', testMessage);

db.run(
  'INSERT INTO chat_messages (sender, receiver, content, timestamp) VALUES (?, ?, ?, ?)',
  [testMessage.sender, testMessage.receiver, testMessage.content, testMessage.timestamp],
  function (err) {
    if (err) {
      console.error('❌ 测试插入失败:', err);
    } else {
      console.log('✅ 测试插入成功，ID:', this.lastID);
      
      // 查询刚插入的消息
      db.get('SELECT * FROM chat_messages WHERE id = ?', [this.lastID], (err, row) => {
        if (err) {
          console.error('查询插入的消息失败:', err);
        } else {
          console.log('插入的消息:', row);
        }
        
        // 删除测试消息
        db.run('DELETE FROM chat_messages WHERE id = ?', [this.lastID], (err) => {
          if (err) {
            console.error('删除测试消息失败:', err);
          } else {
            console.log('✅ 测试消息已清理');
          }
          
          // 关闭数据库连接
          db.close((err) => {
            if (err) {
              console.error('关闭数据库失败:', err);
            } else {
              console.log('✅ 数据库连接已关闭');
            }
          });
        });
      });
    }
  }
);
