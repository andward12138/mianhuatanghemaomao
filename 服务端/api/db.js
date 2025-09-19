const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// 数据库文件路径，指向上级目录的db文件夹
const dbPath = path.resolve(__dirname, '../db/messages.db');

// 创建数据库连接
const db = new sqlite3.Database(dbPath, (err) => {
  if (err) {
    console.error('无法连接到数据库', err);
  } else {
    console.log('已连接到SQLite数据库: ' + dbPath);
  }
});

module.exports = db;
