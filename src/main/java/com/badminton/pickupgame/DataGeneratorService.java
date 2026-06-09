package com.badminton.pickupgame;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DataGeneratorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 批次產生 100 萬筆臨打揪團資料，用於效能測試
     */
    public String generateMillionPickupGames() {
        // 先查詢資料庫裡現有的會員與場地，作為關聯資料
        List<Integer> memberIds = jdbcTemplate.queryForList("SELECT TOP 100 member_id FROM Members", Integer.class);
        List<Integer> courtIds = jdbcTemplate.queryForList("SELECT TOP 10 court_id FROM Courts", Integer.class);

        if (memberIds.isEmpty() || courtIds.isEmpty()) {
            return "錯誤：資料庫缺乏基礎的會員(Members)或場地(Courts)資料，無法產生。";
        }

        String[] statuses = {"OPEN", "CLOSED", "CANCELLED"};
        String[] skillLevels = {"ALL", "BEGINNER", "INTERMEDIATE", "ADVANCED"};
        Random random = new Random();
        
        int totalRecords = 1_000_000;
        int batchSize = 10_000; // 每次批次處理 1 萬筆，避免記憶體爆炸
        
        String sql = "INSERT INTO PickupGames (host_id, court_id, game_date, start_time, end_time, " +
                     "max_players, current_players, skill_level, required_gender, fee_per_person, " +
                     "description, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRecords / batchSize; i++) {
            List<Object[]> batchArgs = new ArrayList<>();
            for (int j = 0; j < batchSize; j++) {
                int hostId = memberIds.get(random.nextInt(memberIds.size()));
                int courtId = courtIds.get(random.nextInt(courtIds.size()));
                
                // 產生前後 30 天的隨機日期
                LocalDate gameDate = LocalDate.now().plusDays(random.nextInt(60) - 30);
                LocalTime gameStart = LocalTime.of(random.nextInt(14) + 8, 0); // 08:00 ~ 21:00
                LocalTime gameEnd = gameStart.plusHours(2);
                
                String status = statuses[random.nextInt(statuses.length)];
                String skillLevel = skillLevels[random.nextInt(skillLevels.length)];
                
                batchArgs.add(new Object[] {
                    hostId, courtId, gameDate, gameStart, gameEnd,
                    6, 1, skillLevel, "ALL", 150,
                    "海量資料壓力測試產生", status, LocalDateTime.now()
                });
            }
            jdbcTemplate.batchUpdate(sql, batchArgs);
            System.out.println("✅ 已寫入 " + ((i + 1) * batchSize) + " 筆資料...");
        }

        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;
        return "成功產生 1,000,000 筆資料！總耗時：" + seconds + " 秒";
    }

    /**
     * 測量複雜查詢的執行時間（毫秒）
     */
    public String measureSlowQuery() {
        String sql = "SELECT COUNT(*) FROM PickupGames " +
                     "WHERE status = 'OPEN' " +
                     "  AND skill_level = 'BEGINNER' " +
                     "  AND game_date >= '2026-06-07'";
                     
        long startTime = System.currentTimeMillis();
        // 執行查詢
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        return "查詢完成！找到 " + count + " 筆符合條件的資料。花費時間：" + duration + " 毫秒 (" + (duration/1000.0) + " 秒)";
    }

    /**
     * 建立複合索引 (Composite Index) 以大幅提升效能
     */
    public String createCompositeIndex() {
        try {
            // 針對查詢條件 (status, skill_level, game_date) 建立複合索引
            String sql = "CREATE INDEX idx_game_status_level_date ON PickupGames (status, skill_level, game_date)";
            jdbcTemplate.execute(sql);
            return "複合索引 (idx_game_status_level_date) 建立成功！";
        } catch (Exception e) {
            return "建立索引失敗（可能已經建立過了）：" + e.getMessage();
        }
    }

    /**
     * 刪除複合索引 (用於還原測試)
     */
    public String dropCompositeIndex() {
        try {
            String sql = "DROP INDEX idx_game_status_level_date ON PickupGames";
            jdbcTemplate.execute(sql);
            return "複合索引已刪除！";
        } catch (Exception e) {
            return "刪除索引失敗：" + e.getMessage();
        }
    }
}
