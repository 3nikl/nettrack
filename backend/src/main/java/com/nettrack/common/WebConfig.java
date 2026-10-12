package com.nettrack.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS: browsers block a page on localhost:5173 (React) from calling localhost:8080 (API)
// unless the API explicitly allows that origin.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors-origins:http://localhost:5173}")
    private String[] origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PATCH");
        registry.addMapping("/tmf-api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PATCH")
                .exposedHeaders("ETag", "Location");
    }
}
