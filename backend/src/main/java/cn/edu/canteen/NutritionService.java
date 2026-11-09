package cn.edu.canteen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NutritionService {
    private static final int MAX_DISHES = 5;
    private final JdbcTemplate jdbc;

    public NutritionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record Choice(Map<String, Object> dish, Choice previous) {}

    public List<Map<String, Object>> singleItems() {
        return jdbc.queryForList("""
                SELECT d.id,d.name,d.price,d.image_url AS imageUrl,n.ingredients,n.calories,
                       c.name AS canteenName,s.name AS stallName
                FROM dish d JOIN dish_nutrition n ON n.dish_id=d.id
                JOIN stall s ON d.stall_id=s.id JOIN canteen c ON s.canteen_id=c.id
                WHERE d.deleted=FALSE AND d.category='单点食材' ORDER BY d.id
                """);
    }

    public Map<String, Object> recommend(int calories, String ingredient) {
        if (calories < 1 || calories > 5000) throw new BusinessException(400, "目标热量须为1～5000千卡的整数");
        String required = ingredient == null ? "" : ingredient.trim();
        if (required.length() > 50) throw new BusinessException(400, "食材名称不能超过50字");
        var dishes = jdbc.queryForList("""
                SELECT d.id,d.name,d.price,d.image_url AS imageUrl,n.ingredients,n.calories,
                       c.name AS canteenName,s.name AS stallName
                FROM dish d JOIN dish_nutrition n ON n.dish_id=d.id
                JOIN stall s ON d.stall_id=s.id JOIN canteen c ON s.canteen_id=c.id
                WHERE d.deleted=FALSE AND n.calories<=? ORDER BY d.id
                """, calories);
        Choice[][][] states = new Choice[calories + 1][MAX_DISHES + 1][2];
        states[0][0][0] = new Choice(null, null);
        for (var dish : dishes) {
            int energy = ((Number) dish.get("calories")).intValue();
            boolean matches = required.isEmpty() || ((String) dish.get("ingredients")).contains(required);
            for (int sum = calories; sum >= energy; sum--) {
                for (int count = MAX_DISHES; count >= 1; count--) {
                    for (int hadIngredient = 0; hadIngredient <= 1; hadIngredient++) {
                        Choice previous = states[sum - energy][count - 1][hadIngredient];
                        int hasIngredient = matches || hadIngredient == 1 ? 1 : 0;
                        if (previous != null && states[sum][count][hasIngredient] == null) {
                            states[sum][count][hasIngredient] = new Choice(dish, previous);
                        }
                    }
                }
            }
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (int sum = calories; sum > 0 && results.size() < 3; sum--) {
            for (int count = 1; count <= MAX_DISHES && results.size() < 3; count++) {
                Choice current = states[sum][count][1];
                if (current == null) continue;
                List<Map<String, Object>> selected = new ArrayList<>();
                BigDecimal price = BigDecimal.ZERO;
                while (current.dish() != null) {
                    selected.add(current.dish());
                    price = price.add((BigDecimal) current.dish().get("price"));
                    current = current.previous();
                }
                results.add(Map.of("dishes", selected, "totalCalories", sum,
                        "remainingCalories", calories - sum, "totalPrice", price));
            }
        }
        return Map.of("targetCalories", calories, "ingredient", required, "results", results);
    }
}
