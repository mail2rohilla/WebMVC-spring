package com.example.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

//@SpringBootApplication
//@EnableWebMvc
//@Configuration
@ComponentScan(basePackages = {"com.example.webmvc", "com.example.service"})
@SpringBootApplication
public class WebMvcApplication implements WebMvcConfigurer{

    public static void main(String[] args) {
        SpringApplication.run(WebMvcApplication.class, args);
//        ApplicationContext ctx = new AnnotationConfigApplicationContext(WebMvcApplication.class);
    }


    @Bean
    public InternalResourceViewResolver resolver() {
        System.out.println("entered bean configurer");
        InternalResourceViewResolver vr = new InternalResourceViewResolver();
        vr.setPrefix("/WEB-INF/view/");
        vr.setSuffix(".jsp");
        return vr;
    }

//    @Bean
//    public ServletRegistrationBean exampleServletBean() {
//        DispatcherServlet dispatcherServlet = new DispatcherServlet();
//
//        AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
//        applicationContext.register(AnotherWebMvcApp.class);
//        dispatcherServlet.setApplicationContext(applicationContext);
//
//        ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean(dispatcherServlet, "/app/*");
//        servletRegistrationBean.setName("app");
//        return servletRegistrationBean;
//    }

}
