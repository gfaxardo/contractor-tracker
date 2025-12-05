package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.AuditLog;
import com.yego.contractortracker.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Async
    @Transactional
    public void registrarAccion(String username, String action, String endpoint, String method,
                                String requestBody, Integer responseStatus, String ipAddress,
                                String userAgent, String errorMessage) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setEndpoint(endpoint);
            auditLog.setMethod(method);
            auditLog.setRequestBody(requestBody);
            auditLog.setResponseStatus(responseStatus);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setTimestamp(LocalDateTime.now());
            auditLog.setErrorMessage(errorMessage);
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            logger.error("Error al registrar acción en auditoría", e);
        }
    }
}






