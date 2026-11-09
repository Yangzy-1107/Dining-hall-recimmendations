package cn.edu.canteen;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Login(@NotBlank(message="请输入账号") @Size(max=40, message="账号过长") String username,
                        @NotBlank(message="请输入密码") @Size(max=72, message="密码过长") String password) {}

    public record Registration(
            @NotBlank(message="请输入账号") @Pattern(regexp="[a-zA-Z0-9_]{4,30}", message="账号须为4～30位字母、数字或下划线") String username,
            @NotBlank(message="请输入昵称") @Size(max=30, message="昵称不能超过30字") String displayName,
            @NotBlank(message="请输入密码") @Size(min=8, max=30, message="密码须为8～30位") String password) {}

    @PostMapping("/register")
    @Transactional
    public ApiResponse<Void> register(@Valid @RequestBody Registration input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException(400, "密码UTF-8长度不能超过72字节");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?", Integer.class, input.username()) > 0) {
            throw new BusinessException(409, "该账号已被使用");
        }
        jdbc.update("INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,'STUDENT')",
                input.username(), passwords.encode(input.password()), input.displayName().trim());
        return ApiResponse.ok(null);
    }

    @PostMapping("/login")
    public ApiResponse<UserSession> login(@Valid @RequestBody Login input, HttpServletRequest request) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException(401, "账号或密码错误");
        var users = jdbc.queryForList("SELECT * FROM app_user WHERE username=?", input.username().trim());
        if (users.isEmpty() || !passwords.matches(input.password(), (String) users.getFirst().get("password_hash"))) {
            throw new BusinessException(401, "账号或密码错误");
        }
        var row = users.getFirst();
        var user = new UserSession(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("display_name"), (String) row.get("role"));
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        request.getSession(true).setAttribute("user", user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/me")
    public ApiResponse<UserSession> me(HttpServletRequest request) { return ApiResponse.ok(current(request)); }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ApiResponse.ok(null);
    }

    public static UserSession current(HttpServletRequest request) {
        return request.getSession(false) == null ? null : (UserSession) request.getSession(false).getAttribute("user");
    }

    public static UserSession require(HttpServletRequest request, String role) {
        UserSession user = current(request);
        if (user == null) throw new BusinessException(401, "请先登录");
        if (!role.equals(user.role())) throw new BusinessException(403, "当前账号没有操作权限");
        return user;
    }
}
