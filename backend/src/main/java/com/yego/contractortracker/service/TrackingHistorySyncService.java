package com.yego.contractortracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrackingHistorySyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(TrackingHistorySyncService.class);
    private static final String DEFAULT_PARK_ID = "08e20910d81d42658d4334d3f6d10ac0";
    
    @Autowired
    private OnboardingService onboardingService;
    
    @Scheduled(cron = "0 0 * * * ?")
    public void syncAllParks() {
        logger.info("=== Iniciando sincronización de datos históricos (cron job) ===");
        
        try {
            onboardingService.calculateAndSaveMetrics(DEFAULT_PARK_ID);
            logger.info("=== Sincronización completada exitosamente ===");
        } catch (Exception e) {
            logger.error("=== Error durante la sincronización de datos históricos ===", e);
        }
    }
}













