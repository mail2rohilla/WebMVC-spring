package com.example.webmvc.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

//@SpringBootApplication
@EnableWebMvc
@Configuration
@ComponentScan(basePackages = "com.example.webmvc")
//@SpringBootApplication
public class WebMvcApplication implements WebMvcConfigurer{

//    public static void main(String[] args) {
//        SpringApplication.run(WebMvcApplication.class, args);
//        ApplicationContext ctx = new AnnotationConfigApplicationContext(WebMvcApplication.class);
//    }


    @Bean
    public InternalResourceViewResolver resolver() {
        System.out.println("entered bean configurer");
        InternalResourceViewResolver vr = new InternalResourceViewResolver();
        vr.setPrefix("/WEB-INF/view/");
        vr.setSuffix(".jsp");
        return vr;
    }

}
