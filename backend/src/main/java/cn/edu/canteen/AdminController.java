package cn.edu.canteen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final ImageService images;
    public AdminController(AdminService admin, ImageService images) { this.admin = admin; this.images = images; }

    public record CanteenInput(@NotBlank(message="请输入食堂名称") @Size(max=50,message="食堂名称不能超过50字") String name) {}
    public record StallInput(@NotNull(message="请选择所属食堂") Long canteenId,
                             @NotBlank(message="请输入窗口名称") @Size(max=50,message="窗口名称不能超过50字") String name,
                             BigDecimal collectedAveragePrice, BigDecimal collectedRating) {}
    public record DishInput(@NotNull(message="请选择所属窗口") Long stallId,
                            @NotBlank(message="请输入菜品名称") @Size(max=50,message="菜品名称不能超过50字") String name,
                            String category, String taste,
                            @NotNull(message="请输入菜品价格") BigDecimal price,
                            String imageUrl, boolean labelsVerified,
                            @Size(max=500,message="配料不能超过500字") String ingredients,
                            @Min(value=1,message="热量须为1～5000千卡") @Max(value=5000,message="热量须为1～5000千卡") Integer calories) {}

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() { return ApiResponse.ok(admin.statistics()); }

    @PostMapping("/canteens")
    public ApiResponse<Long> addCanteen(@Valid @RequestBody CanteenInput input) { return ApiResponse.ok(admin.saveCanteen(null,input.name())); }
    @PutMapping("/canteens/{id}")
    public ApiResponse<Long> editCanteen(@PathVariable long id,@Valid @RequestBody CanteenInput input) { return ApiResponse.ok(admin.saveCanteen(id,input.name())); }
    @DeleteMapping("/canteens/{id}")
    public ApiResponse<Void> deleteCanteen(@PathVariable long id) { admin.deleteCanteen(id); return ApiResponse.ok(null); }

    @PostMapping("/stalls")
    public ApiResponse<Long> addStall(@Valid @RequestBody StallInput input) { return ApiResponse.ok(admin.saveStall(null,input)); }
    @PutMapping("/stalls/{id}")
    public ApiResponse<Long> editStall(@PathVariable long id,@Valid @RequestBody StallInput input) { return ApiResponse.ok(admin.saveStall(id,input)); }
    @DeleteMapping("/stalls/{id}")
    public ApiResponse<Void> deleteStall(@PathVariable long id) { admin.deleteStall(id); return ApiResponse.ok(null); }

    @PostMapping("/dishes")
    public ApiResponse<Long> addDish(@Valid @RequestBody DishInput input) { return ApiResponse.ok(admin.saveDish(null,input)); }
    @PutMapping("/dishes/{id}")
    public ApiResponse<Long> editDish(@PathVariable long id,@Valid @RequestBody DishInput input) { return ApiResponse.ok(admin.saveDish(id,input)); }
    @DeleteMapping("/dishes/{id}")
    public ApiResponse<Void> deleteDish(@PathVariable long id) { admin.deleteDish(id); return ApiResponse.ok(null); }

    @PostMapping("/images")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("url", images.save(file)));
    }
}
