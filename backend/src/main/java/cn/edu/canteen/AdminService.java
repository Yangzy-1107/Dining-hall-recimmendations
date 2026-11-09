package cn.edu.canteen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.Map;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final ImageService images;

    public AdminService(JdbcTemplate jdbc, ImageService images) { this.jdbc = jdbc; this.images = images; }

    public Map<String, Object> statistics() {
        return Map.of("canteens", count("canteen"), "stalls", count("stall"),
                "dishes", jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE deleted=FALSE", Long.class),
                "reviews", count("review"), "pendingLabels", jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE deleted=FALSE AND labels_verified=FALSE", Long.class));
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    public long saveCanteen(Long id, String name) {
        if (id == null) return insert("INSERT INTO canteen(name) VALUES(?)", name.trim());
        exists("canteen", id, "食堂");
        jdbc.update("UPDATE canteen SET name=? WHERE id=?", name.trim(), id);
        return id;
    }

    public long saveStall(Long id, AdminController.StallInput input) {
        exists("canteen", input.canteenId(), "所属食堂");
        CatalogService.amount(input.collectedAveragePrice(), "采集人均消费");
        BigDecimal rating = input.collectedRating();
        if (rating != null && (rating.signum() < 0 || rating.compareTo(new BigDecimal("5")) > 0 || rating.scale() > 1)) {
            throw new BusinessException(400, "窗口采集评分须为0～5，最多一位小数");
        }
        if (id == null) return insert("INSERT INTO stall(canteen_id,name,collected_average_price,collected_rating) VALUES(?,?,?,?)",
                input.canteenId(), input.name().trim(), input.collectedAveragePrice(), rating);
        exists("stall", id, "窗口");
        jdbc.update("UPDATE stall SET canteen_id=?,name=?,collected_average_price=?,collected_rating=? WHERE id=?",
                input.canteenId(), input.name().trim(), input.collectedAveragePrice(), rating, id);
        return id;
    }

    @Transactional
    public long saveDish(Long id, AdminController.DishInput input) {
        exists("stall", input.stallId(), "所属窗口");
        CatalogService.labels(input.category(), input.taste());
        CatalogService.amount(input.price(), "菜品价格");
        images.checkReference(input.imageUrl());
        if (input.labelsVerified() && ("待分类".equals(input.category()) || "待核实".equals(input.taste()))) {
            throw new BusinessException(400, "确认标签前请选择明确的分类和口味");
        }
        String ingredients = input.ingredients() == null ? "" : input.ingredients().trim();
        if (ingredients.isEmpty() != (input.calories() == null)) throw new BusinessException(400, "请同时填写配料和每份热量，或两项均留空");
        if ("单点食材".equals(input.category()) && ingredients.isEmpty()) {
            throw new BusinessException(400, "单点食材须填写每份配料及份量和热量");
        }
        long dishId;
        if (id == null) {
            dishId = insert("INSERT INTO dish(stall_id,name,category,taste,price,image_url,labels_verified) VALUES(?,?,?,?,?,?,?)",
                    input.stallId(), input.name().trim(), input.category(), input.taste(), input.price(), input.imageUrl(), input.labelsVerified());
        } else {
            var rows = jdbc.queryForList("SELECT id FROM dish WHERE id=? AND deleted=FALSE FOR UPDATE", id);
            if (rows.isEmpty()) throw new BusinessException(404, "菜品已删除或不存在");
            jdbc.update("UPDATE dish SET stall_id=?,name=?,category=?,taste=?,price=?,image_url=?,labels_verified=? WHERE id=?",
                    input.stallId(), input.name().trim(), input.category(), input.taste(), input.price(), input.imageUrl(), input.labelsVerified(), id);
            dishId = id;
        }
        if (ingredients.isEmpty()) {
            jdbc.update("DELETE FROM dish_nutrition WHERE dish_id=?", dishId);
        } else {
            int count = jdbc.update("UPDATE dish_nutrition SET ingredients=?,calories=? WHERE dish_id=?", ingredients, input.calories(), dishId);
            if (count == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM dish_nutrition WHERE dish_id=?", Integer.class, dishId) == 0) {
                jdbc.update("INSERT INTO dish_nutrition(dish_id,ingredients,calories) VALUES(?,?,?)", dishId, ingredients, input.calories());
            }
        }
        return dishId;
    }

    @Transactional
    public void deleteDish(long id) {
        var rows = jdbc.queryForList("SELECT id FROM dish WHERE id=? AND deleted=FALSE FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "菜品已删除或不存在");
        jdbc.update("UPDATE dish SET deleted=TRUE WHERE id=?", id);
    }

    @Transactional
    public void deleteStall(long id) {
        exists("stall", id, "窗口");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE stall_id=?", Long.class, id) > 0) {
            throw new BusinessException(409, "该窗口有关联菜品，不能删除");
        }
        jdbc.update("DELETE FROM stall WHERE id=?", id);
    }

    @Transactional
    public void deleteCanteen(long id) {
        exists("canteen", id, "食堂");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM stall WHERE canteen_id=?", Long.class, id) > 0) {
            throw new BusinessException(409, "该食堂有关联窗口，不能删除");
        }
        jdbc.update("DELETE FROM canteen WHERE id=?", id);
    }

    private void exists(String table, Long id, String label) {
        if (id == null || jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Long.class, id) == 0) {
            throw new BusinessException(400, label + "不存在");
        }
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
