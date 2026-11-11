package com.testproject.calendar.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class FrontendConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    private static class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            if (path.equals("api") || path.startsWith("api/")
                    || path.equals("actuator") || path.startsWith("actuator/")) {
                return null;
            }

            Resource resource = super.getResource(path, location);
            if (resource != null) {
                return resource;
            }

            if (!path.contains(".")) {
                return super.getResource("index.html", location);
            }
            return null;
        }
    }
}
