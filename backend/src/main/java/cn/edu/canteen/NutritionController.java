package cn.edu.canteen;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {
    private final NutritionService nutrition;

    public NutritionController(NutritionService nutrition) { this.nutrition = nutrition; }

    @GetMapping("/single-items")
    public ApiResponse<List<Map<String, Object>>> singleItems() {
        return ApiResponse.ok(nutrition.singleItems());
    }

    @GetMapping("/recommendations")
    public ApiResponse<Map<String, Object>> recommendations(@RequestParam int calories,
            @RequestParam(required=false, defaultValue="") String ingredient) {
        return ApiResponse.ok(nutrition.recommend(calories, ingredient));
    }
}
