package com.yego.contractortracker.config;

import com.yego.contractortracker.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditInterceptor.class);
    
    @Autowired
    private AuditService auditService;
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) throws Exception {
        try {
            String username = "anonymous";
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
                username = authentication.getName();
            }
            
            String method = request.getMethod();
            String endpoint = request.getRequestURI();
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                endpoint += "?" + queryString;
            }
            
            String requestBody = "";
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrappedRequest = (ContentCachingRequestWrapper) request;
                byte[] content = wrappedRequest.getContentAsByteArray();
                if (content.length > 0) {
                    requestBody = new String(content, StandardCharsets.UTF_8);
                    if (requestBody.length() > 5000) {
                        requestBody = requestBody.substring(0, 5000) + "... (truncado)";
                    }
                }
            }
            
            int responseStatus = response.getStatus();
            String ipAddress = obtenerIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String errorMessage = ex != null ? ex.getMessage() : null;
            
            String action = method + " " + endpoint;
            
            auditService.registrarAccion(
                username,
                action,
                endpoint,
                method,
                requestBody,
                responseStatus,
                ipAddress,
                userAgent,
                errorMessage
            );
        } catch (Exception e) {
            logger.error("Error al registrar auditoría", e);
        }
    }
    
    private String obtenerIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

