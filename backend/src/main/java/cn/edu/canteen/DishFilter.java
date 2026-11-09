package cn.edu.canteen;

import java.math.BigDecimal;

public record DishFilter(String keyword, Long canteenId, Long stallId, String category, String taste,
                         BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int pageSize) {}
