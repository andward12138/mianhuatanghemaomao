package com.example.message.services;
import com.example.message.util.DBUtil;
import com.example.message.model.Diary;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DiaryService {

    //更新日记内容
    public static void updateDiary(int id, String newContent, String newTags) {
        String updateSQL = "UPDATE diary_entries SET content = ?, tags = ? WHERE id = ?";

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {

            preparedStatement.setString(1, newContent); // 设置新的日记内容
            preparedStatement.setString(2, newTags);    // 设置新的标签
            preparedStatement.setInt(3, id);             // 设置要更新的日记ID

            preparedStatement.executeUpdate();           // 执行更新
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 删除日记
    public static void deleteDiary(int id) {
        String deleteSQL = "DELETE FROM diary_entries WHERE id = ?";

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(deleteSQL)) {

            preparedStatement.setInt(1, id);  // 设置要删除的日记ID
            preparedStatement.executeUpdate(); // 执行删除操作
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 根据关键词和标签过滤日记
    public static List<Diary> searchDiaries(String keyword, String startDate, String endDate) {
        List<Diary> diaries = new ArrayList<>();
        String selectSQL = "SELECT id, date, content, timestamp, tags FROM diary_entries WHERE (content LIKE ? OR tags LIKE ?)";

        // 添加日期范围筛选条件
        if (startDate != null && !startDate.isEmpty()) {
            selectSQL += " AND date >= ?";
        }
        if (endDate != null && !endDate.isEmpty()) {
            selectSQL += " AND date <= ?";
        }

        selectSQL += " ORDER BY timestamp DESC"; // 按时间排序

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {

            preparedStatement.setString(1, "%" + keyword + "%");
            preparedStatement.setString(2, "%" + keyword + "%");

            // 设置日期范围条件
            int paramIndex = 3;
            if (startDate != null && !startDate.isEmpty()) {
                preparedStatement.setString(paramIndex++, startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                preparedStatement.setString(paramIndex, endDate);
            }

            ResultSet resultSet = preparedStatement.executeQuery();

            // 遍历结果集，获取过滤后的日记
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String date = resultSet.getString("date");
                String content = resultSet.getString("content");
                String timestamp = resultSet.getString("timestamp");
                String tags = resultSet.getString("tags");

                // 创建Diary对象并添加到列表
                diaries.add(new Diary(id, date, content, timestamp, tags));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return diaries;
    }


    // 保存日记
    public static void saveDiary(String content, String tags) {
        String date = java.time.LocalDate.now().toString();  // 获取当前日期
        String timestamp = java.time.LocalDateTime.now().toString();  // 获取当前的日期时间

        // SQL插入语句
        String insertSQL = "INSERT INTO diary_entries (date, content, timestamp, tags) VALUES (?, ?, ?, ?)";

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {

            // 设置插入的参数
            preparedStatement.setString(1, date);
            preparedStatement.setString(2, content);
            preparedStatement.setString(3, timestamp);  // 将日期时间插入到timestamp字段
            preparedStatement.setString(4, tags);      // 将标签插入到tags字段

            // 执行插入操作
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    // 获取所有日记
    public static List<Diary> getAllDiaries() {
        List<Diary> diaries = new ArrayList<>();
        String selectSQL = "SELECT id, date, content, timestamp, tags FROM diary_entries ORDER BY timestamp DESC"; // 按时间排序

        try (Connection connection = DBUtil.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectSQL)) {

            // 遍历结果集，获取所有日记
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String date = resultSet.getString("date");
                String content = resultSet.getString("content");
                String timestamp = resultSet.getString("timestamp");
                String tags = resultSet.getString("tags");

                // 创建Diary对象并添加到列表
                diaries.add(new Diary(id, date, content, timestamp, tags));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return diaries;
    }


}
