package com.learnplatform.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Allow course documents up to 15 MB (the default limit is 1 MB). */
@Configuration
public class UploadConfig {
    @Bean
    MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory f = new MultipartConfigFactory();
        f.setMaxFileSize(DataSize.ofMegabytes(15));
        f.setMaxRequestSize(DataSize.ofMegabytes(16));
        return f.createMultipartConfig();
    }
}
