package cn.edu.canteen;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CatalogService {
    public static final List<String> CATEGORIES = List.of("快餐", "面食", "粉类", "饺子", "煲类", "麻辣烫", "汉堡", "单点食材", "其他", "待分类");
    public static final List<String> TASTES = List.of("清淡", "微辣", "中辣", "重辣", "酸辣", "咸香", "咖喱", "待核实");
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private static final String FROM = " FROM dish d JOIN stall s ON d.stall_id=s.id JOIN canteen c ON s.canteen_id=c.id ";
    private static final String SELECT = """
            SELECT d.id,d.name,d.category,d.taste,d.price,d.image_url AS imageUrl,d.labels_verified AS labelsVerified,
                   s.id AS stallId,s.name AS stallName,c.id AS canteenId,c.name AS canteenName,
                   s.collected_average_price AS collectedAveragePrice,s.collected_rating AS collectedRating,
                   r.average_score AS averageScore,COALESCE(r.review_count,0) AS reviewCount,
                   n.ingredients AS ingredients,n.calories AS calories
            """ + FROM + """
             LEFT JOIN dish_nutrition n ON n.dish_id=d.id
             LEFT JOIN (SELECT dish_id,ROUND(AVG(score),1) AS average_score,COUNT(*) AS review_count
                        FROM review GROUP BY dish_id) r ON r.dish_id=d.id
            """;

    public CatalogService(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }

    public Map<String, Object> metadata() {
        return Map.of("canteens", jdbc.queryForList("SELECT id,name FROM canteen ORDER BY id"),
                "stalls", jdbc.queryForList("SELECT id,canteen_id AS canteenId,name,collected_average_price AS collectedAveragePrice,collected_rating AS collectedRating FROM stall ORDER BY id"),
                "categories", CATEGORIES, "tastes", TASTES);
    }

    public Map<String, Object> list(DishFilter filter) {
        var query = conditions(filter);
        long total = named.queryForObject("SELECT COUNT(*)" + FROM + query.where(), query.params(), Long.class);
        query.params().addValue("limit", filter.pageSize()).addValue("offset", (filter.page() - 1L) * filter.pageSize());
        String order = switch (filter.sort() == null ? "default" : filter.sort()) {
            case "default" -> "d.id DESC";
            case "priceAsc" -> "d.price ASC,d.id ASC";
            case "priceDesc" -> "d.price DESC,d.id ASC";
            case "rating" -> "COALESCE(r.average_score,0) DESC,COALESCE(r.review_count,0) DESC,d.id ASC";
            default -> throw new BusinessException(400, "排序条件无效");
        };
        var items = named.queryForList(SELECT + query.where() + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", query.params());
        return Map.of("items", items, "total", total, "page", filter.page(), "pageSize", filter.pageSize());
    }

    public Map<String, Object> detail(long id) {
        var rows = jdbc.queryForList(SELECT + " WHERE d.id=? AND d.deleted=FALSE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "菜品已删除或不存在");
        var result = rows.getFirst();
        result.put("reviews", jdbc.queryForList("""
                SELECT r.id,r.user_id AS userId,u.display_name AS displayName,r.score,r.comment,r.updated_at AS updatedAt
                FROM review r JOIN app_user u ON r.user_id=u.id WHERE r.dish_id=? ORDER BY r.updated_at DESC,r.id DESC
                """, id));
        return result;
    }

    public Map<String, Object> random(DishFilter filter) {
        var query = conditions(filter);
        List<Long> ids = named.queryForList("SELECT d.id" + FROM + query.where() + " ORDER BY d.id", query.params(), Long.class);
        if (ids.isEmpty()) throw new BusinessException(404, "当前条件下没有菜品，请调整筛选条件");
        return detail(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    public static void amount(BigDecimal value, String field) {
        if (value != null && (value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("999999.99")) > 0)) {
            throw new BusinessException(400, field + "须为非负金额，最多两位小数且不超过999999.99元");
        }
    }

    public static void labels(String category, String taste) {
        if (category == null || !CATEGORIES.contains(category)) throw new BusinessException(400, "菜品分类无效");
        if (taste == null || !TASTES.contains(taste)) throw new BusinessException(400, "口味标签无效");
    }

    private record Query(String where, MapSqlParameterSource params) {}

    private Query conditions(DishFilter f) {
        if (f.page() < 1 || f.pageSize() < 1 || f.pageSize() > 100) throw new BusinessException(400, "页码须为正整数，每页1～100条");
        amount(f.minPrice(), "最低价格"); amount(f.maxPrice(), "最高价格");
        if (f.minPrice() != null && f.maxPrice() != null && f.minPrice().compareTo(f.maxPrice()) > 0) throw new BusinessException(400, "最低价格不能高于最高价格");
        if (f.category() != null && !f.category().isBlank() && !CATEGORIES.contains(f.category())) throw new BusinessException(400, "菜品分类无效");
        if (f.taste() != null && !f.taste().isBlank() && !TASTES.contains(f.taste())) throw new BusinessException(400, "口味标签无效");
        List<String> conditions = new ArrayList<>(List.of("d.deleted=FALSE"));
        var params = new MapSqlParameterSource();
        if (f.keyword() != null && !f.keyword().isBlank()) {
            if (f.keyword().length() > 50) throw new BusinessException(400, "搜索内容不能超过50字");
            conditions.add("(LOCATE(:keyword,d.name)>0 OR LOCATE(:keyword,s.name)>0)"); params.addValue("keyword", f.keyword().trim());
        }
        if (f.canteenId() != null) { conditions.add("c.id=:canteenId"); params.addValue("canteenId", f.canteenId()); }
        if (f.stallId() != null) { conditions.add("s.id=:stallId"); params.addValue("stallId", f.stallId()); }
        if (f.category() != null && !f.category().isBlank()) { conditions.add("d.category=:category"); params.addValue("category", f.category()); }
        if (f.taste() != null && !f.taste().isBlank()) { conditions.add("d.taste=:taste"); params.addValue("taste", f.taste()); }
        if (f.minPrice() != null) { conditions.add("d.price>=:minPrice"); params.addValue("minPrice", f.minPrice()); }
        if (f.maxPrice() != null) { conditions.add("d.price<=:maxPrice"); params.addValue("maxPrice", f.maxPrice()); }
        return new Query(" WHERE " + String.join(" AND ", conditions), params);
    }
}
