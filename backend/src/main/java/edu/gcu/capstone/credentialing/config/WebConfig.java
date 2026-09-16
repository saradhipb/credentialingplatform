package edu.gcu.capstone.credentialing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * WebConfig.java
 *
 * <p>Web-layer configuration. Three concerns are handled here:</p>
 * <ol>
 *   <li><b>Single-page application hosting.</b> The compiled Angular bundle is
 *       copied into {@code src/main/resources/static}. Because Angular uses
 *       client-side routing, any request that is not an API call and does not
 *       match a real file is forwarded to {@code index.html} so that deep links
 *       such as {@code /risk/17} work after a browser refresh.</li>
 *   <li><b>Cross-origin access during development.</b> When the Angular dev
 *       server runs on port 4200 it must be allowed to call the API on
 *       port 8080. Production serves both from the same origin, so this rule is
 *       harmless there.</li>
 *   <li><b>Outbound HTTP client.</b> A single {@link RestTemplate} with explicit
 *       connect and read timeouts is shared by the NPPES client, preventing a
 *       slow external registry from holding request threads open.</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApplicationProperties properties;

    public WebConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the shared HTTP client used for outbound registry calls.
     *
     * @return a RestTemplate whose timeouts come from application.yml
     */
    @Bean
    public RestTemplate nppesRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getNppes().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getNppes().getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    /** Permits the Angular development server to call the API during development. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Serves static assets and falls back to {@code index.html} for Angular
     * routes, while leaving {@code /api/**} and {@code /h2-console/**} alone.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("h2-console")) {
                            return null; // Let the dispatcher handle it as a controller mapping.
                        }
                        ClassPathResource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
