package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /dashboard 로 접속하면 정적 페이지를 보여준다 (/dashboard/stats 는 API)
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        registry.addViewController("/ledger").setViewName("forward:/ledger.html");
    }
}
