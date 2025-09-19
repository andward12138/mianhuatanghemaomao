const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// 数据库路径
const dbPath = path.join(__dirname, '../db/messages.db');
console.log('数据库路径:', dbPath);

const db = new sqlite3.Database(dbPath, (err) => {
  if (err) {
    console.error('数据库连接失败:', err.message);
    return;
  }
  console.log('✅ 数据库连接成功');
});

// 1. 查看重复消息的情况
console.log('\n=== 检查重复消息 ===');
db.all(`
  SELECT sender, receiver, content, timestamp, COUNT(*) as count
  FROM chat_messages 
  GROUP BY sender, receiver, content, timestamp 
  HAVING COUNT(*) > 1
  ORDER BY timestamp DESC
`, (err, rows) => {
  if (err) {
    console.error('查询重复消息失败:', err);
    return;
  }
  
  if (rows.length === 0) {
    console.log('✅ 没有发现重复消息');
  } else {
    console.log(`❌ 发现 ${rows.length} 组重复消息:`);
    rows.forEach((row, index) => {
      console.log(`${index + 1}. ${row.sender} -> ${row.receiver}: "${row.content}" (重复${row.count}次) [${row.timestamp}]`);
    });
  }
  
  // 2. 显示最近的消息记录
  console.log('\n=== 最近20条消息记录 ===');
  db.all(`
    SELECT id, sender, receiver, content, timestamp
    FROM chat_messages 
    ORDER BY timestamp DESC 
    LIMIT 20
  `, (err, recentRows) => {
    if (err) {
      console.error('查询最近消息失败:', err);
      return;
    }
    
    console.log('ID\t发送者\t接收者\t内容\t\t时间戳');
    console.log('---\t---\t---\t---\t\t---');
    recentRows.forEach(row => {
      const content = row.content.length > 10 ? row.content.substring(0, 10) + '...' : row.content;
      console.log(`${row.id}\t${row.sender}\t${row.receiver}\t${content}\t\t${row.timestamp}`);
    });
    
    // 3. 提供清理选项
    if (rows.length > 0) {
      console.log('\n=== 清理重复消息 ===');
      console.log('发现重复消息，开始清理...');
      
      // 删除重复消息，只保留每组中ID最小的那条
      db.run(`
        DELETE FROM chat_messages 
        WHERE id NOT IN (
          SELECT MIN(id) 
          FROM chat_messages 
          GROUP BY sender, receiver, content, timestamp
        )
      `, function(err) {
        if (err) {
          console.error('清理重复消息失败:', err);
          return;
        }
        
        console.log(`✅ 成功清理了 ${this.changes} 条重复消息`);
        
        // 4. 验证清理结果
        db.all(`
          SELECT sender, receiver, content, timestamp, COUNT(*) as count
          FROM chat_messages 
          GROUP BY sender, receiver, content, timestamp 
          HAVING COUNT(*) > 1
        `, (err, duplicatesAfter) => {
          if (err) {
            console.error('验证清理结果失败:', err);
            return;
          }
          
          if (duplicatesAfter.length === 0) {
            console.log('✅ 清理完成，没有重复消息了');
          } else {
            console.log(`❌ 清理后仍有 ${duplicatesAfter.length} 组重复消息`);
          }
          
          // 5. 显示清理后的统计信息
          db.get('SELECT COUNT(*) as total FROM chat_messages', (err, totalRow) => {
            if (err) {
              console.error('查询总消息数失败:', err);
              return;
            }
            
            console.log(`\n📊 当前数据库中共有 ${totalRow.total} 条消息`);
            
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
      });
    } else {
      // 显示总消息数
      db.get('SELECT COUNT(*) as total FROM chat_messages', (err, totalRow) => {
        if (err) {
          console.error('查询总消息数失败:', err);
          return;
        }
        
        console.log(`\n📊 当前数据库中共有 ${totalRow.total} 条消息`);
        
        // 关闭数据库连接
        db.close((err) => {
          if (err) {
            console.error('关闭数据库失败:', err);
          } else {
            console.log('✅ 数据库连接已关闭');
          }
        });
      });
    }
  });
});
