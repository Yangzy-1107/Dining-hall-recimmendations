package cn.edu.canteen;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/metadata")
    public ApiResponse<Map<String, Object>> metadata() { return ApiResponse.ok(catalog.metadata()); }

    @GetMapping({"/dishes", "/dishes/random"})
    public ApiResponse<Map<String, Object>> dishes(
            @RequestParam(required=false) String keyword, @RequestParam(required=false) Long canteenId,
            @RequestParam(required=false) Long stallId, @RequestParam(required=false) String category,
            @RequestParam(required=false) String taste, @RequestParam(required=false) BigDecimal minPrice,
            @RequestParam(required=false) BigDecimal maxPrice, @RequestParam(defaultValue="default") String sort,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="12") int pageSize,
            jakarta.servlet.http.HttpServletRequest request) {
        var filter = new DishFilter(keyword, canteenId, stallId, category, taste, minPrice, maxPrice, sort, page, pageSize);
        return ApiResponse.ok(request.getRequestURI().endsWith("/random") ? catalog.random(filter) : catalog.list(filter));
    }

    @GetMapping("/dishes/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) { return ApiResponse.ok(catalog.detail(id)); }
}
