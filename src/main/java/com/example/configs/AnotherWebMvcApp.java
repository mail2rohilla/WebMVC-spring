package com.example.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@EnableWebMvc
@Configuration
@ComponentScan(basePackages = {"com.example.anotherWebMvc"})
public class AnotherWebMvcApp implements WebMvcConfigurer {
//    public static void main(String[] args) {
//        SpringApplication.run(WebMvcApplication.class, args);
////        ApplicationContext ctx = new AnnotationConfigApplicationContext(WebMvcApplication.class);
//    }
//
//
    @Bean
    public InternalResourceViewResolver resolver() {
        System.out.println("entered bean configurer");
        InternalResourceViewResolver vr = new InternalResourceViewResolver();
        vr.setPrefix("/WEB-INF/view/");
        vr.setSuffix(".jsp");
        return vr;
    }
}
