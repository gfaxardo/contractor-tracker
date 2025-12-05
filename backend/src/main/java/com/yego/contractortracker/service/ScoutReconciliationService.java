package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.ScoutRegistration;
import com.yego.contractortracker.repository.ScoutRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScoutReconciliationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutReconciliationService.class);
    
    @Autowired
    private ScoutRegistrationRepository scoutRegistrationRepository;
    
    @Transactional
    public Map<String, Object> reconciliarMatches() {
        logger.info("Iniciando conciliación de matches de scouts");
        
        List<ScoutRegistration> registrosMatcheados = scoutRegistrationRepository.findByIsMatched(true);
        
        Map<String, List<ScoutRegistration>> agrupados = registrosMatcheados.stream()
            .filter(r -> r.getScoutId() != null && r.getDriverId() != null)
            .collect(Collectors.groupingBy(r -> r.getScoutId() + "|" + r.getDriverId()));
        
        int dobleMatches = 0;
        int soloScoutRegistration = 0;
        int soloYangoTransaction = 0;
        
        for (Map.Entry<String, List<ScoutRegistration>> entry : agrupados.entrySet()) {
            List<ScoutRegistration> registros = entry.getValue();
            
            List<ScoutRegistration> desdeScoutReg = registros.stream()
                .filter(r -> "scout_registration".equals(r.getMatchSource()))
                .collect(Collectors.toList());
            
            List<ScoutRegistration> desdeYango = registros.stream()
                .filter(r -> "yango_transaction".equals(r.getMatchSource()))
                .collect(Collectors.toList());
            
            if (!desdeScoutReg.isEmpty() && !desdeYango.isEmpty()) {
                dobleMatches++;
                for (ScoutRegistration reg : registros) {
                    reg.setReconciliationStatus("matched_both_sources");
                    reg.setIsReconciled(true);
                    scoutRegistrationRepository.save(reg);
                }
                logger.debug("Doble match encontrado: scout={}, driver={}", 
                    registros.get(0).getScoutId(), registros.get(0).getDriverId());
            } else if (!desdeScoutReg.isEmpty()) {
                soloScoutRegistration++;
                for (ScoutRegistration reg : desdeScoutReg) {
                    reg.setReconciliationStatus("only_scout_registration");
                    reg.setIsReconciled(false);
                    scoutRegistrationRepository.save(reg);
                }
            } else if (!desdeYango.isEmpty()) {
                soloYangoTransaction++;
                for (ScoutRegistration reg : desdeYango) {
                    reg.setReconciliationStatus("only_yango_transaction");
                    reg.setIsReconciled(false);
                    scoutRegistrationRepository.save(reg);
                }
            }
        }
        
        List<ScoutRegistration> noMatcheados = scoutRegistrationRepository.findByIsMatched(false);
        int noMatcheadosScoutReg = 0;
        int noMatcheadosYango = 0;
        
        for (ScoutRegistration reg : noMatcheados) {
            if ("scout_registration".equals(reg.getMatchSource())) {
                reg.setReconciliationStatus("unmatched_scout_registration");
                noMatcheadosScoutReg++;
            } else if ("yango_transaction".equals(reg.getMatchSource())) {
                reg.setReconciliationStatus("unmatched_yango_transaction");
                noMatcheadosYango++;
            }
            scoutRegistrationRepository.save(reg);
        }
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("dobleMatches", dobleMatches);
        resultado.put("soloScoutRegistration", soloScoutRegistration);
        resultado.put("soloYangoTransaction", soloYangoTransaction);
        resultado.put("noMatcheadosScoutReg", noMatcheadosScoutReg);
        resultado.put("noMatcheadosYango", noMatcheadosYango);
        resultado.put("totalProcesados", registrosMatcheados.size() + noMatcheados.size());
        
        logger.info("Conciliación completada: {} doble matches, {} solo scout_reg, {} solo yango, {} no matcheados scout_reg, {} no matcheados yango",
            dobleMatches, soloScoutRegistration, soloYangoTransaction, noMatcheadosScoutReg, noMatcheadosYango);
        
        return resultado;
    }
    
    @Transactional
    public void eliminarDobleMatches(String scoutId, String driverId, boolean eliminarScoutReg, boolean eliminarYango) {
        List<ScoutRegistration> registros = scoutRegistrationRepository
            .findByScoutIdAndDriverId(scoutId, driverId);
        
        for (ScoutRegistration reg : registros) {
            if (eliminarScoutReg && "scout_registration".equals(reg.getMatchSource())) {
                scoutRegistrationRepository.delete(reg);
                logger.info("Eliminado registro scout_registration: id={}", reg.getId());
            } else if (eliminarYango && "yango_transaction".equals(reg.getMatchSource())) {
                scoutRegistrationRepository.delete(reg);
                logger.info("Eliminado registro yango_transaction: id={}", reg.getId());
            }
        }
    }
}

