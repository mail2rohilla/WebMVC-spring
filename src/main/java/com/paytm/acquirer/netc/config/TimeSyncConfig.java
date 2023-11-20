package com.paytm.acquirer.netc.config;

import com.paytm.acquirer.netc.service.TimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class TimeSyncConfig {
    @Autowired
    private TimeService timeService;

    @Scheduled(fixedRateString = "${netc.time-sync.interval-in-ms}")
    public void syncTimeWithNetc() {
        timeService.syncTimeWithNetc();
    }
}
