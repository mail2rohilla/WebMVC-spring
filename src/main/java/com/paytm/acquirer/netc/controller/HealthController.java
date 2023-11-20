package com.paytm.acquirer.netc.controller;

import com.paytm.acquirer.netc.dto.health.HealthResponse;
import com.paytm.acquirer.netc.dto.health.PingResponse;
import com.paytm.acquirer.netc.service.HealthService;
import com.paytm.transport.metrics.Monitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Tag(name = "Health Check", description = "APIs are used to check health of the service")
public class HealthController {

    private final HealthService healthService;

    @Autowired
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Monitor(name = "ping", metricGroup = Monitor.ServiceGroup.API_IN)
    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "Used to check system is working fine and receiving http requests")
    @ApiResponse(
      responseCode = "200",
      description = "Success response returned successfully",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = PingResponse.class),
        examples = {
          @ExampleObject(value = "{\"programName\":\"NETC Engine\",\"version\":\"1.0.0-SNAPSHOT\",\"release\":null,\"datetime\":1671699012160,\"status\":\"success\",\"code\":200,\"message\":\"OK\",\"data\":{\"duration\":0.0,\"message\":\"My service is healthy\"}}")
        }
      )
    )
    public @ResponseBody
    PingResponse getPing() throws IOException {
        return healthService.getPingResponse();
    }

    @Monitor(name = "health", metricGroup = Monitor.ServiceGroup.API_IN)
    @GetMapping("/health")
    @Operation(summary = "Health", description = "Used to check availability of the system")
    @ApiResponse(
      responseCode = "200",
      description = "Success response returned successfully",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = HealthResponse.class),
        examples = {
          @ExampleObject(value = "{\"programName\":\"NETC Engine\",\"version\":\"1.0.0-SNAPSHOT\",\"release\":null,\"datetime\":1671699087145,\"status\":\"successfull\",\"code\":200,\"message\":\"ok\",\"dependentService\":null,\"data\":{\"duration\":0.0,\"message\":\"My service is healthy\"}}")
        }
      )
    )
    public @ResponseBody
    HealthResponse getHealth() throws IOException {
        return healthService.getHealthResponse();
    }

}
