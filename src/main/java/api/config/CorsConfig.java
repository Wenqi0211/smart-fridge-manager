package api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 让浏览器里的 Flutter 能访问本机 Java / Lets Flutter in browser call this Java server (CORS).
 * <p>Now 现在: 允许所有来源 <code>*</code>，方便做作业。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 演示或交作业展示时：把 * 改成你们前端真实的网址，更安全 / For demo day: replace * with your real front-end URL.
        // Stateless bearer tokens are injected by FridgeApiService; cookies are not used.
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
