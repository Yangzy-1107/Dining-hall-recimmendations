package cn.edu.canteen;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;

@Component
@DependsOn("initialDataLoader")
public class FitnessDataLoader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public FitnessDataLoader(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    private record Meal(String name, String price, String ingredients, int calories) {}

    @PostConstruct
    public void initialize() {
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM seed_history WHERE name='fitness-window-v1'", Integer.class) > 0) return;
            var canteens = jdbc.queryForList("SELECT id FROM canteen WHERE name=?", Long.class, "同乐食堂");
            if (canteens.size() != 1) throw new IllegalStateException("未找到同乐食堂，无法添加健身餐窗口");
            long canteenId = canteens.getFirst();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM stall WHERE canteen_id=? AND name=?", Integer.class, canteenId, "轻食健身餐") > 0) {
                throw new IllegalStateException("同乐食堂已有同名窗口，请核对资料");
            }
            long stallId = insert("INSERT INTO stall(canteen_id,name) VALUES(?,?)", canteenId, "轻食健身餐");
            List<Meal> meals = List.of(
                    new Meal("鸡胸肉藜麦饭", "20.00", "鸡胸肉150克、熟藜麦100克、西兰花100克、橄榄油5克", 447),
                    new Meal("番茄鸡蛋豆腐", "12.00", "豆腐150克、鸡蛋1个、番茄80克、食用油5克", 245),
                    new Meal("香煎三文鱼蔬菜", "25.00", "三文鱼120克、芦笋120克、橄榄油5克", 320),
                    new Meal("糙米牛肉饭", "22.00", "熟牛肉150克、熟糙米150克、胡萝卜80克、食用油5克", 530),
                    new Meal("虾仁西兰花", "18.00", "虾仁120克、西兰花150克、食用油5克", 215),
                    new Meal("紫薯玉米南瓜", "10.00", "紫薯150克、玉米100克、南瓜100克", 255),
                    new Meal("燕麦酸奶水果杯", "13.00", "燕麦40克、无糖酸奶150克、苹果100克", 298),
                    new Meal("鸡肉蔬菜沙拉", "16.00", "鸡胸肉120克、生菜100克、番茄100克、橄榄油5克", 276));
            for (Meal meal : meals) {
                long id = insert("INSERT INTO dish(stall_id,name,category,taste,price,labels_verified) VALUES(?,?,'快餐','清淡',?,FALSE)",
                        stallId, meal.name(), new BigDecimal(meal.price()));
                jdbc.update("INSERT INTO dish_nutrition(dish_id,ingredients,calories) VALUES(?,?,?)", id, meal.ingredients(), meal.calories());
            }
            jdbc.update("INSERT INTO seed_history(name) VALUES('fitness-window-v1')");
        });
        transactions.executeWithoutResult(status -> {
            // 导入标记与六种单点在同一事务中保存，已有数据库只补充一次。
            if (jdbc.update("INSERT IGNORE INTO seed_history(name) VALUES('fitness-single-items-v1')") == 0) return;
            long stallId = jdbc.queryForObject("""
                    SELECT s.id FROM stall s JOIN canteen c ON s.canteen_id=c.id
                    WHERE c.name='同乐食堂' AND s.name='轻食健身餐'
                    """, Long.class);
            List<Meal> items = List.of(
                    new Meal("水煮蛋", "1.00", "鸡蛋1个，可食部分约50克", 78),
                    new Meal("清蒸西兰花", "1.50", "西兰花100克", 35),
                    new Meal("水煮玉米粒", "2.00", "玉米粒100克", 112),
                    new Meal("蒸紫薯", "2.00", "紫薯100克", 86),
                    new Meal("米饭", "0.50", "熟米饭100克", 130),
                    new Meal("水煮鸡胸肉", "3.00", "熟鸡胸肉100克", 165));
            for (Meal item : items) {
                long id = insert("INSERT INTO dish(stall_id,name,category,taste,price,labels_verified) VALUES(?,?,'单点食材','清淡',?,FALSE)",
                        stallId, item.name(), new BigDecimal(item.price()));
                jdbc.update("INSERT INTO dish_nutrition(dish_id,ingredients,calories) VALUES(?,?,?)", id, item.ingredients(), item.calories());
            }
        });
    }

    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
