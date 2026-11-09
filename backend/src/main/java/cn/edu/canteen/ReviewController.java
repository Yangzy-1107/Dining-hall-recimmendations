package cn.edu.canteen;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dishes")
public class ReviewController {
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    public ReviewController(JdbcTemplate jdbc, CatalogService catalog) { this.jdbc = jdbc; this.catalog = catalog; }

    public record ReviewInput(@NotNull(message="请选择分数") @Min(value=1,message="评分为1～5的整数") @Max(value=5,message="评分为1～5的整数") Integer score,
                              @Size(max=200,message="留言不能超过200字") String comment) {}

    @PutMapping("/{id}/review")
    @Transactional
    public ApiResponse<Map<String, Object>> save(@PathVariable long id, @Valid @RequestBody ReviewInput input, HttpServletRequest request) {
        var user = AuthController.require(request, "STUDENT");
        // 与菜品删除共享行锁，防止删除和新增评价交错写入。
        var rows = jdbc.queryForList("SELECT id FROM dish WHERE id=? AND deleted=FALSE FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "菜品已删除或不存在，无法评价");
        String comment = input.comment() == null ? "" : input.comment().trim();
        int changed = jdbc.update("UPDATE review SET score=?,comment=?,updated_at=CURRENT_TIMESTAMP WHERE dish_id=? AND user_id=?",
                input.score(), comment, id, user.id());
        if (changed == 0) jdbc.update("INSERT INTO review(dish_id,user_id,score,comment) VALUES(?,?,?,?)", id, user.id(), input.score(), comment);
        return ApiResponse.ok(catalog.detail(id));
    }
}
