package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.AuthUser;
import com.yego.contractortracker.dto.LoginResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final WebClient webClient;
    private final SecretKey jwtSecretKey;
    
    @Value("${yego.api.url:https://api-int.yego.pro}")
    private String yegoApiUrl;
    
    @Value("${jwt.secret:YegoContractorTrackerSecretKeyForJWTTokenGeneration2024}")
    private String jwtSecret;
    
    @Value("${jwt.expiration-hours:24}")
    private int jwtExpirationHours;
    
    public AuthService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api-int.yego.pro")
                .build();
        this.jwtSecretKey = Keys.hmacShaKeyFor("YegoContractorTrackerSecretKeyForJWTTokenGeneration2024".getBytes(StandardCharsets.UTF_8));
    }
    
    public LoginResponse login(String username, String password) {
        try {
            Map<String, String> loginRequest = new HashMap<>();
            loginRequest.put("username", username);
            loginRequest.put("password", password);
            
            Map<String, Object> response = webClient.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        logger.warn("Error de autenticación para usuario: {}", username);
                        return Mono.error(new RuntimeException("Credenciales inválidas"));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        logger.error("Error del servidor de autenticación");
                        return Mono.error(new RuntimeException("Error del servidor de autenticación"));
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response == null) {
                return new LoginResponse(null, null, false, "Error al autenticar");
            }
            
            String token = generarJWT(username);
            
            AuthUser user = new AuthUser();
            user.setUsername(username);
            if (response.containsKey("email")) {
                user.setEmail((String) response.get("email"));
            }
            if (response.containsKey("nombre")) {
                user.setNombre((String) response.get("nombre"));
            }
            if (response.containsKey("name")) {
                user.setNombre((String) response.get("name"));
            }
            
            return new LoginResponse(token, user, true, "Login exitoso");
            
        } catch (Exception e) {
            logger.error("Error al autenticar usuario: {}", username, e);
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("timeout") || errorMessage.contains("Timeout") || 
                    e.getClass().getSimpleName().contains("Timeout")) {
                    return new LoginResponse(null, null, false, "El servidor de autenticación no respondió a tiempo. Por favor, intente nuevamente.");
                }
                if (errorMessage.contains("Connection refused") || errorMessage.contains("connect")) {
                    return new LoginResponse(null, null, false, "No se pudo conectar con el servidor de autenticación. Por favor, verifique su conexión.");
                }
            }
            return new LoginResponse(null, null, false, "Error al autenticar: " + (errorMessage != null ? errorMessage : "Error desconocido"));
        }
    }
    
    private String generarJWT(String username) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtExpirationHours, ChronoUnit.HOURS);
        
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(jwtSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public String validarToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            logger.error("Error al validar token", e);
            return null;
        }
    }
}

