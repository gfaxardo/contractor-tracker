package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.YangoPaymentConfig;
import com.yego.contractortracker.repository.YangoPaymentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class YangoPaymentConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(YangoPaymentConfigService.class);
    
    @Autowired
    private YangoPaymentConfigRepository configRepository;
    
    public List<YangoPaymentConfig> obtenerConfiguracion() {
        return configRepository.findAll();
    }
    
    public List<YangoPaymentConfig> obtenerConfiguracionPorPeriodo(Integer periodDays) {
        return configRepository.findByPeriodDaysAndIsActiveTrue(periodDays);
    }
    
    @Transactional
    public YangoPaymentConfig actualizarConfiguracion(Long id, BigDecimal amountYango, Integer periodDays, Boolean isActive) {
        YangoPaymentConfig config = configRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Configuración no encontrada: " + id));
        
        if (amountYango != null) {
            if (amountYango.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El monto debe ser positivo");
            }
            config.setAmountYango(amountYango);
        }
        
        if (periodDays != null) {
            config.setPeriodDays(periodDays);
        }
        
        if (isActive != null) {
            config.setIsActive(isActive);
        }
        
        return configRepository.save(config);
    }
    
    /**
     * Calcula el monto acumulativo de Yango para un milestone específico.
     * La lógica es acumulativa: M1=25, M5=25+35=60, M25=25+35+100=160
     * 
     * @param milestoneType Tipo de milestone (1, 5, 25)
     * @param periodDays Días del periodo (14 por defecto)
     * @return Monto acumulativo total
     */
    public BigDecimal calcularMontoYangoAcumulativo(Integer milestoneType, Integer periodDays) {
        if (milestoneType == null) {
            return BigDecimal.ZERO;
        }
        
        if (periodDays == null) {
            periodDays = 14; // Por defecto 14 días
        }
        
        // Obtener todas las configuraciones activas para el periodo, ordenadas por milestone_type
        List<YangoPaymentConfig> configs = configRepository
            .findByPeriodDaysAndIsActiveTrue(periodDays)
            .stream()
            .sorted(Comparator.comparing(YangoPaymentConfig::getMilestoneType))
            .collect(Collectors.toList());
        
        if (configs.isEmpty()) {
            logger.warn("No se encontraron configuraciones activas para periodDays={}, usando valores por defecto", periodDays);
            // Valores por defecto si no hay configuración
            switch (milestoneType) {
                case 1: return new BigDecimal("25.00");
                case 5: return new BigDecimal("60.00"); // 25 + 35
                case 25: return new BigDecimal("160.00"); // 25 + 35 + 100
                default: return BigDecimal.ZERO;
            }
        }
        
        BigDecimal total = BigDecimal.ZERO;
        for (YangoPaymentConfig config : configs) {
            total = total.add(config.getAmountYango());
            // Si llegamos al milestone solicitado, retornar el total acumulado
            if (config.getMilestoneType().equals(milestoneType)) {
                logger.debug("Monto acumulativo para M{} ({} días): {}", milestoneType, periodDays, total);
                return total;
            }
        }
        
        // Si el milestone no se encuentra en las configuraciones, retornar el total acumulado hasta el último disponible
        logger.warn("Milestone {} no encontrado en configuraciones para periodDays={}, retornando total acumulado: {}", 
            milestoneType, periodDays, total);
        return total;
    }
}

