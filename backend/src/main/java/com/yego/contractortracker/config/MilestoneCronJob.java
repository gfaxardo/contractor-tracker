package com.yego.contractortracker.config;

import com.yego.contractortracker.service.MilestoneTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MilestoneCronJob {
    
    private static final Logger logger = LoggerFactory.getLogger(MilestoneCronJob.class);
    
    @Autowired
    private MilestoneTrackingService milestoneTrackingService;
    
    @Scheduled(cron = "0 0 * * * *")
    public void calcularInstanciasProgramado() {
        logger.info("Iniciando cálculo programado de instancias (cron job cada hora)");
        
        try {
            String parkId = null;
            String jobId7d = milestoneTrackingService.calcularInstanciasAsync(parkId, 7);
            String jobId14d = milestoneTrackingService.calcularInstanciasAsync(parkId, 14);
            logger.info("Cálculo programado de instancias iniciado (7 días: {}, 14 días: {})", jobId7d, jobId14d);
        } catch (Exception e) {
            logger.error("Error al iniciar cálculo programado de instancias", e);
        }
    }
}



