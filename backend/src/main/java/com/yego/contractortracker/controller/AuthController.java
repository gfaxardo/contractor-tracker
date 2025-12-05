package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.LoginRequest;
import com.yego.contractortracker.dto.LoginResponse;
import com.yego.contractortracker.service.AuditService;
import com.yego.contractortracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private AuditService auditService;
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LoginResponse loginResponse = authService.login(loginRequest.getUsername(), loginRequest.getPassword());
            
            if (loginResponse.isSuccess()) {
                response.put("success", true);
                response.put("token", loginResponse.getToken());
                response.put("user", loginResponse.getUser());
                response.put("message", loginResponse.getMessage());
                
                String ipAddress = obtenerIpAddress(request);
                String userAgent = request.getHeader("User-Agent");
                
                auditService.registrarAccion(
                    loginRequest.getUsername(),
                    "POST /api/auth/login",
                    "/api/auth/login",
                    "POST",
                    null,
                    200,
                    ipAddress,
                    userAgent,
                    null
                );
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", loginResponse.getMessage());
                
                String ipAddress = obtenerIpAddress(request);
                String userAgent = request.getHeader("User-Agent");
                
                auditService.registrarAccion(
                    loginRequest.getUsername() != null ? loginRequest.getUsername() : "unknown",
                    "POST /api/auth/login",
                    "/api/auth/login",
                    "POST",
                    null,
                    401,
                    ipAddress,
                    userAgent,
                    "Login fallido: " + loginResponse.getMessage()
                );
                
                return ResponseEntity.status(401).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error al procesar login: " + e.getMessage());
            
            String ipAddress = obtenerIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            
            auditService.registrarAccion(
                loginRequest.getUsername() != null ? loginRequest.getUsername() : "unknown",
                "POST /api/auth/login",
                "/api/auth/login",
                "POST",
                null,
                500,
                ipAddress,
                userAgent,
                "Error: " + e.getMessage()
            );
            
            return ResponseEntity.status(500).body(response);
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





