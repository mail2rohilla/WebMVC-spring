package com.example.configs;


import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class ServerConfig extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        System.out.println("entered here for root config classes");

        return new Class[]{RootConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{WebMvcApplication.class};
    }

    @Override
    protected String[] getServletMappings() {
        System.out.println("entered here for url mappings");
        return new String[]{"/test"};
    }


}
