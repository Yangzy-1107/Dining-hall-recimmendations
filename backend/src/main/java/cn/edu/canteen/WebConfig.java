package cn.edu.canteen;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadDir;

    public WebConfig(@Value("${app.upload-dir}") String uploadDir) { this.uploadDir = uploadDir; }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnector() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(Path.of(uploadDir).toAbsolutePath().toUri().toString() + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                        && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                    throw new BusinessException(403, "请求来源无效，请通过系统页面操作");
                }
                if (request.getRequestURI().startsWith("/api/admin/")) AuthController.require(request, "ADMIN");
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
