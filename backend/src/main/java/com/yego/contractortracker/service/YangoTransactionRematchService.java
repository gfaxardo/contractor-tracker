package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.repository.YangoTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class YangoTransactionRematchService {
    
    private static final Logger logger = LoggerFactory.getLogger(YangoTransactionRematchService.class);
    private static final int BATCH_SIZE = 100;
    
    @Autowired
    private YangoTransactionRepository transactionRepository;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private MilestoneProgressService progressService;
    
    @Async
    public void rematchAllTransactionsAsync(String jobId) {
        logger.info("Iniciando re-matching de todas las transacciones Yango. JobId: {}", jobId);
        
        try {
            // Obtener todas las transacciones
            List<YangoTransaction> allTransactions = transactionRepository.findAll();
            int totalTransactions = allTransactions.size();
            
            if (totalTransactions == 0) {
                logger.info("No hay transacciones para re-matchear");
                progressService.startProgress(jobId, "yango-rematch", 0);
                progressService.completeProgress(jobId);
                return;
            }
            
            // Iniciar progreso
            progressService.startProgress(jobId, "yango-rematch", totalTransactions);
            
            // Procesar en batches
            int processedCount = 0;
            int matchedCount = 0;
            int unmatchedCount = 0;
            
            for (int i = 0; i < allTransactions.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, allTransactions.size());
                List<YangoTransaction> batch = allTransactions.subList(i, endIndex);
                
                RematchResult result = rematchBatch(batch);
                processedCount += result.processed;
                matchedCount += result.matched;
                unmatchedCount += result.unmatched;
                
                // Actualizar progreso
                progressService.updateProgress(jobId, processedCount, 0, 0, 0);
                
                logger.info("Procesado batch {}-{}: {} procesadas, {} matcheadas, {} sin match", 
                    i + 1, endIndex, result.processed, result.matched, result.unmatched);
            }
            
            // Completar progreso
            progressService.completeProgress(jobId);
            
            logger.info("Re-matching completado. Total: {}, Matcheadas: {}, Sin match: {}", 
                processedCount, matchedCount, unmatchedCount);
            
        } catch (Exception e) {
            logger.error("Error durante re-matching de transacciones Yango", e);
            progressService.failProgress(jobId, e.getMessage());
        }
    }
    
    @Async
    public void rematchTransactionsForDriversAsync(List<String> driverIds) {
        String jobId = "yango-rematch-drivers-" + System.currentTimeMillis();
        logger.info("Iniciando re-matching de transacciones Yango para {} drivers. JobId: {}", 
            driverIds.size(), jobId);
        
        try {
            // Obtener transacciones de los drivers especificados
            List<YangoTransaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getDriverId() != null && driverIds.contains(t.getDriverId()))
                .collect(Collectors.toList());
            int totalTransactions = transactions.size();
            
            if (totalTransactions == 0) {
                logger.info("No hay transacciones para los drivers especificados");
                progressService.startProgress(jobId, "yango-rematch", 0);
                progressService.completeProgress(jobId);
                return;
            }
            
            // Iniciar progreso
            progressService.startProgress(jobId, "yango-rematch", totalTransactions);
            
            // Procesar en batches
            int processedCount = 0;
            int matchedCount = 0;
            int unmatchedCount = 0;
            
            for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, transactions.size());
                List<YangoTransaction> batch = transactions.subList(i, endIndex);
                
                RematchResult result = rematchBatch(batch);
                processedCount += result.processed;
                matchedCount += result.matched;
                unmatchedCount += result.unmatched;
                
                // Actualizar progreso
                progressService.updateProgress(jobId, processedCount, 0, 0, 0);
                
                logger.info("Procesado batch {}-{}: {} procesadas, {} matcheadas, {} sin match", 
                    i + 1, endIndex, result.processed, result.matched, result.unmatched);
            }
            
            // Completar progreso
            progressService.completeProgress(jobId);
            
            logger.info("Re-matching completado para drivers. Total: {}, Matcheadas: {}, Sin match: {}", 
                processedCount, matchedCount, unmatchedCount);
            
        } catch (Exception e) {
            logger.error("Error durante re-matching de transacciones Yango para drivers", e);
            progressService.failProgress(jobId, e.getMessage());
        }
    }
    
    @Transactional
    private RematchResult rematchBatch(List<YangoTransaction> transactions) {
        RematchResult result = new RematchResult();
        result.processed = transactions.size();
        
        // Cargar milestones en batch para los drivers de estas transacciones
        Set<String> driverIds = transactions.stream()
            .map(YangoTransaction::getDriverId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        Map<String, List<MilestoneInstance>> milestonesByDriver = new HashMap<>();
        if (!driverIds.isEmpty()) {
            Set<String> driverIdsSet = new HashSet<>(driverIds);
            List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverIdIn(driverIdsSet);
            milestonesByDriver = milestones.stream()
                .collect(Collectors.groupingBy(MilestoneInstance::getDriverId));
        }
        
        // Procesar cada transacción
        for (YangoTransaction transaction : transactions) {
            try {
                boolean wasMatched = rematchTransaction(transaction, milestonesByDriver);
                if (wasMatched) {
                    result.matched++;
                } else {
                    result.unmatched++;
                }
            } catch (Exception e) {
                logger.warn("Error al re-matchear transacción {}: {}", transaction.getId(), e.getMessage());
                result.unmatched++;
            }
        }
        
        // Guardar todas las transacciones del batch
        transactionRepository.saveAll(transactions);
        
        return result;
    }
    
    private boolean rematchTransaction(YangoTransaction transaction, 
                                      Map<String, List<MilestoneInstance>> milestonesByDriver) {
        // Si no tiene driver_id, intentar matchear con driver por nombre
        if (transaction.getDriverId() == null || transaction.getDriverId().isEmpty()) {
            if (transaction.getDriverNameFromComment() != null && 
                !transaction.getDriverNameFromComment().isEmpty()) {
                // Usar el método de matching existente del YangoTransactionService
                // Por ahora, si no tiene driver_id, no podemos matchear
                transaction.setIsMatched(false);
                transaction.setMilestoneInstanceId(null);
                return false;
            }
        }
        
        String driverId = transaction.getDriverId();
        if (driverId == null || driverId.isEmpty()) {
            transaction.setIsMatched(false);
            transaction.setMilestoneInstanceId(null);
            return false;
        }
        
        // Obtener milestones del driver
        List<MilestoneInstance> driverMilestones = milestonesByDriver.getOrDefault(driverId, new ArrayList<>());
        
        // Intentar matchear con milestone
        Optional<MilestoneInstance> milestoneMatch = matchearConMilestone(transaction, driverMilestones);
        
        if (milestoneMatch.isPresent()) {
            MilestoneInstance milestone = milestoneMatch.get();
            transaction.setMilestoneInstanceId(milestone.getId());
            transaction.setIsMatched(true);
            transaction.setMatchConfidence(new BigDecimal("0.9")); // Confianza alta para re-matching
            return true;
        } else {
            // Si no hay match con milestone, pero tiene driver_id, mantener el driver_id
            // pero marcar como no matcheado con milestone
            transaction.setMilestoneInstanceId(null);
            transaction.setIsMatched(false);
            return false;
        }
    }
    
    private Optional<MilestoneInstance> matchearConMilestone(
            YangoTransaction transaccion, List<MilestoneInstance> driverMilestones) {
        
        if (transaccion.getMilestoneType() == null || driverMilestones == null || driverMilestones.isEmpty()) {
            return Optional.empty();
        }
        
        LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
        // Rango: ±14 días
        LocalDate fechaDesde = transactionDate.minusDays(14);
        LocalDate fechaHasta = transactionDate.plusDays(14);
        
        MilestoneInstance bestMatch = null;
        long minDaysDiff = Long.MAX_VALUE;
        MilestoneInstance bestMatchSinRestriccion = null;
        long minDaysDiffSinRestriccion = Long.MAX_VALUE;
        
        for (MilestoneInstance milestone : driverMilestones) {
            if (milestone.getMilestoneType().equals(transaccion.getMilestoneType())) {
                LocalDate fulfillmentDate = milestone.getFulfillmentDate().toLocalDate();
                long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(transactionDate, fulfillmentDate));
                
                // Primero buscar dentro del rango preferido (±14 días)
                if (!fulfillmentDate.isBefore(fechaDesde) && !fulfillmentDate.isAfter(fechaHasta)) {
                    if (daysDiff < minDaysDiff) {
                        minDaysDiff = daysDiff;
                        bestMatch = milestone;
                    }
                }
                
                // También buscar el más cercano sin restricción (hasta 30 días)
                if (daysDiff < minDaysDiffSinRestriccion) {
                    minDaysDiffSinRestriccion = daysDiff;
                    bestMatchSinRestriccion = milestone;
                }
            }
        }
        
        // Preferir match dentro del rango, pero si no hay, usar el más cercano (hasta 30 días)
        if (bestMatch != null) {
            return Optional.of(bestMatch);
        } else if (bestMatchSinRestriccion != null && minDaysDiffSinRestriccion <= 30) {
            return Optional.of(bestMatchSinRestriccion);
        }
        
        return Optional.empty();
    }
    
    private static class RematchResult {
        int processed = 0;
        int matched = 0;
        int unmatched = 0;
    }
}

