package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.ScoutPayment;
import com.yego.contractortracker.entity.ScoutPaymentConfig;
import com.yego.contractortracker.entity.ScoutPaymentInstance;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.repository.ScoutPaymentConfigRepository;
import com.yego.contractortracker.repository.ScoutPaymentInstanceRepository;
import com.yego.contractortracker.repository.ScoutPaymentRepository;
import com.yego.contractortracker.repository.YangoTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScoutPaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutPaymentService.class);
    
    @Autowired
    private ScoutPaymentConfigRepository configRepository;
    
    @Autowired
    private ScoutPaymentRepository paymentRepository;
    
    @Autowired
    private YangoTransactionRepository transactionRepository;
    
    @Autowired
    private ScoutPaymentInstanceRepository instanceRepository;
    
    public List<ScoutPaymentConfig> obtenerConfiguracion() {
        return configRepository.findByIsActiveTrue();
    }
    
    @Transactional
    public ScoutPaymentConfig actualizarConfiguracion(Long id, BigDecimal amountScout, Integer paymentDays, Boolean isActive, Integer minRegistrationsRequired, Integer minConnectionSeconds) {
        ScoutPaymentConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada: " + id));
        
        if (amountScout != null) {
            config.setAmountScout(amountScout);
        }
        if (paymentDays != null) {
            config.setPaymentDays(paymentDays);
        }
        if (isActive != null) {
            config.setIsActive(isActive);
        }
        if (minRegistrationsRequired != null) {
            config.setMinRegistrationsRequired(minRegistrationsRequired);
        }
        if (minConnectionSeconds != null) {
            config.setMinConnectionSeconds(minConnectionSeconds);
        }
        config.setLastUpdated(LocalDateTime.now());
        
        return configRepository.save(config);
    }
    
    public Map<String, Object> calcularLiquidacionSemanal(String scoutId, LocalDate fechaInicio, LocalDate fechaFin) {
        if (scoutId == null || scoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("scoutId es requerido");
        }
        if (fechaInicio == null || fechaFin == null) {
            throw new IllegalArgumentException("fechaInicio y fechaFin son requeridos");
        }
        if (fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("fechaInicio no puede ser posterior a fechaFin");
        }
        
        List<YangoTransaction> transacciones = transactionRepository.findByScoutId(scoutId).stream()
                .filter(t -> t.getIsMatched() && 
                        !t.getTransactionDate().toLocalDate().isBefore(fechaInicio) &&
                        !t.getTransactionDate().toLocalDate().isAfter(fechaFin))
                .collect(Collectors.toList());
        
        Map<Integer, ScoutPaymentConfig> configs = configRepository.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(ScoutPaymentConfig::getMilestoneType, c -> c));
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        int transactionsCount = 0;
        Map<Integer, Integer> milestoneCounts = new HashMap<>();
        
        for (YangoTransaction transaccion : transacciones) {
            if (transaccion.getMilestoneType() != null) {
                ScoutPaymentConfig config = configs.get(transaccion.getMilestoneType());
                if (config != null) {
                    totalAmount = totalAmount.add(config.getAmountScout());
                    transactionsCount++;
                    milestoneCounts.put(transaccion.getMilestoneType(), 
                            milestoneCounts.getOrDefault(transaccion.getMilestoneType(), 0) + 1);
                }
            }
        }
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("scoutId", scoutId);
        resultado.put("fechaInicio", fechaInicio);
        resultado.put("fechaFin", fechaFin);
        resultado.put("totalAmount", totalAmount);
        resultado.put("transactionsCount", transactionsCount);
        resultado.put("milestoneCounts", milestoneCounts);
        
        return resultado;
    }
    
    @Transactional
    public ScoutPayment generarPagoScout(String scoutId, LocalDate fechaInicio, LocalDate fechaFin) {
        Map<String, Object> calculo = calcularLiquidacionSemanal(scoutId, fechaInicio, fechaFin);
        
        ScoutPayment pago = new ScoutPayment();
        pago.setScoutId(scoutId);
        pago.setPaymentPeriodStart(fechaInicio);
        pago.setPaymentPeriodEnd(fechaFin);
        pago.setTotalAmount((BigDecimal) calculo.get("totalAmount"));
        pago.setTransactionsCount((Integer) calculo.get("transactionsCount"));
        pago.setStatus("pending");
        pago.setCreatedAt(LocalDateTime.now());
        pago.setLastUpdated(LocalDateTime.now());
        
        return paymentRepository.save(pago);
    }
    
    public List<ScoutPayment> obtenerPagosScout(String scoutId) {
        if (scoutId == null || scoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("scoutId es requerido");
        }
        return paymentRepository.findByScoutId(scoutId);
    }
    
    @Transactional
    public ScoutPayment marcarPagoComoPagado(Long paymentId) {
        ScoutPayment pago = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado: " + paymentId));
        
        pago.setStatus("paid");
        pago.setPaidAt(LocalDateTime.now());
        pago.setLastUpdated(LocalDateTime.now());
        
        return paymentRepository.save(pago);
    }
    
    @Transactional
    public ScoutPayment generarPagoDesdeInstancias(String scoutId, List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una instancia para generar el pago");
        }
        
        List<ScoutPaymentInstance> instancias = instanceRepository.findAllById(instanceIds);
        if (instancias.isEmpty()) {
            throw new RuntimeException("No se encontraron instancias con los IDs proporcionados");
        }
        
        BigDecimal totalAmount = instancias.stream()
                .map(ScoutPaymentInstance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        LocalDate fechaInicio = instancias.stream()
                .map(ScoutPaymentInstance::getRegistrationDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        LocalDate fechaFin = instancias.stream()
                .map(ScoutPaymentInstance::getRegistrationDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        ScoutPayment pago = new ScoutPayment();
        pago.setScoutId(scoutId);
        pago.setPaymentPeriodStart(fechaInicio);
        pago.setPaymentPeriodEnd(fechaFin);
        pago.setTotalAmount(totalAmount);
        pago.setTransactionsCount(instancias.size());
        pago.setStatus("pending");
        pago.setInstanceIds(instanceIds);
        pago.setCreatedAt(LocalDateTime.now());
        pago.setLastUpdated(LocalDateTime.now());
        
        ScoutPayment savedPayment = paymentRepository.save(pago);
        
        for (ScoutPaymentInstance instance : instancias) {
            if ("pending".equals(instance.getStatus())) {
                instance.setStatus("paid");
                instance.setPaymentId(savedPayment.getId());
                instance.setLastUpdated(LocalDateTime.now());
                instanceRepository.save(instance);
            }
        }
        
        return savedPayment;
    }
    
    @Transactional
    public ScoutPayment generarPagoTodasPendientes(String scoutId) {
        List<ScoutPaymentInstance> instanciasPendientes = instanceRepository.findByScoutIdAndStatus(scoutId, "pending");
        
        if (instanciasPendientes.isEmpty()) {
            throw new RuntimeException("No hay instancias pendientes para el scout " + scoutId);
        }
        
        List<Long> instanceIds = instanciasPendientes.stream()
                .map(ScoutPaymentInstance::getId)
                .collect(Collectors.toList());
        
        return generarPagoDesdeInstancias(scoutId, instanceIds);
    }
}

