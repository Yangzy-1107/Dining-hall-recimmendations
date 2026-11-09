package cn.edu.canteen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;

@Component
@DependsOnDatabaseInitialization
public class InitialDataLoader {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String adminUsername;
    private final String adminPassword;
    private final TransactionTemplate transactions;

    public InitialDataLoader(JdbcTemplate jdbc, ObjectMapper mapper, TransactionTemplate transactions,
            @Value("${app.admin-username}") String adminUsername, @Value("${app.admin-password}") String adminPassword) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = transactions;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public record DishData(String name, BigDecimal price, String category) {}
    public record StallData(String name, BigDecimal averagePrice, BigDecimal rating, List<DishData> dishes) {}
    public record CanteenData(String name, List<StallData> stalls) {}

    @PostConstruct
    public void initialize() {
        transactions.executeWithoutResult(status -> {
            try { load(); }
            catch (Exception ex) { throw new IllegalStateException("初始化资料加载失败", ex); }
        });
    }

    private void load() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role='ADMIN'", Integer.class) == 0) {
            String password = adminPassword;
            if (password.isBlank()) {
                byte[] bytes = new byte[15];
                new SecureRandom().nextBytes(bytes);
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                Path file = Path.of(".run", "initial-admin-password.txt");
                Files.createDirectories(file.getParent());
                Files.writeString(file, "账号：" + adminUsername + "\n密码：" + password + "\n", StandardCharsets.UTF_8);
            }
            if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalStateException("管理员初始密码至少8位且UTF-8长度不超过72字节");
            jdbc.update("INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,'ADMIN')",
                    adminUsername, new BCryptPasswordEncoder().encode(password), "食堂管理员");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM seed_history WHERE name='canteen-collection-v1'", Integer.class) > 0) return;
        // 已有业务资料时停止初始化，避免重复导入或覆盖人工维护的数据。
        if (jdbc.queryForObject("SELECT COUNT(*) FROM canteen", Integer.class) > 0) {
            throw new IllegalStateException("数据库已有食堂资料但缺少导入记录，请核对后再启动");
        }
        List<CanteenData> data;
        try (var stream = new ClassPathResource("initial-data.json").getInputStream()) {
            data = mapper.readValue(stream, new TypeReference<>() {});
        }
        for (var canteen : data) {
            long canteenId = insert("INSERT INTO canteen(name) VALUES(?)", canteen.name());
            for (var stall : canteen.stalls()) {
                long stallId = insert("INSERT INTO stall(canteen_id,name,collected_average_price,collected_rating) VALUES(?,?,?,?)",
                        canteenId, stall.name(), stall.averagePrice(), stall.rating());
                for (var dish : stall.dishes()) {
                    jdbc.update("INSERT INTO dish(stall_id,name,category,taste,price,labels_verified) VALUES(?,?,?,'待核实',?,FALSE)",
                            stallId, dish.name(), dish.category(), dish.price());
                }
            }
        }
        jdbc.update("INSERT INTO seed_history(name) VALUES('canteen-collection-v1')");
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
