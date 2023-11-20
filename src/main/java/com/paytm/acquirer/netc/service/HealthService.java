package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.dto.health.HealthResponse;
import com.paytm.acquirer.netc.dto.health.PingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Properties;

@Service
public class HealthService {

    private final static String GIT_COMMIT_ID = "git.commit.id";
    private final ResourceLoader resourceLoader;
    private final BuildProperties buildProperties;

    @Autowired
    public HealthService(ResourceLoader resourceLoader, BuildProperties buildProperties) {
        this.resourceLoader = resourceLoader;
        this.buildProperties = buildProperties;
    }

    public PingResponse getPingResponse() throws IOException {
        long curTime = System.currentTimeMillis();
        PingResponse response = new PingResponse();
        Resource resource = resourceLoader.getResource("classpath:git.properties");
        Properties props = PropertiesLoaderUtils.loadProperties(resource);
        response.setProgramName(buildProperties.getName());
        response.setVersion(buildProperties.getVersion());
        response.setRelease(props.getProperty(GIT_COMMIT_ID));
        response.setDatetime(System.currentTimeMillis());
        response.setStatus("success");
        response.setCode(200);
        response.getData().setMessage("My service is healthy");
        long timeDiff = System.currentTimeMillis() - curTime;
        response.getData().setDuration(timeDiff);
        return response;
    }

    public HealthResponse getHealthResponse() throws IOException {
        long curTime = System.currentTimeMillis();
        HealthResponse response = new HealthResponse();

        Resource resource = resourceLoader.getResource("classpath:git.properties");
        Properties props = PropertiesLoaderUtils.loadProperties(resource);
        response.setProgramName(buildProperties.getName());
        response.setVersion(buildProperties.getVersion());
        response.setRelease(props.getProperty(GIT_COMMIT_ID));
        response.setDatetime(System.currentTimeMillis());
        response.setStatus("successfull");
        response.setCode(200);
        response.setMessage("ok");
        response.getData().setMessage("My service is healthy");
        long timeDiff = System.currentTimeMillis() - curTime;
        response.getData().setDuration(timeDiff);
        return response;
    }
}
