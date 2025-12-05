package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.DriverOnboardingDTO;
import com.yego.contractortracker.dto.EvolutionMetricsDTO;
import com.yego.contractortracker.dto.OnboardingFilterDTO;
import com.yego.contractortracker.dto.PaginatedResponse;
import com.yego.contractortracker.service.OnboardingService;
import com.yego.contractortracker.util.WeekISOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
public class OnboardingController {
    
    @Autowired
    private OnboardingService onboardingService;
    
    @PostMapping("/by-ids")
    public ResponseEntity<List<DriverOnboardingDTO>> getDriversByIds(
            @RequestBody List<String> driverIds,
            @RequestParam(required = false) String parkId) {
        try {
            List<DriverOnboardingDTO> drivers = onboardingService.getDriversByIds(driverIds, parkId);
            return ResponseEntity.ok(drivers);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    @GetMapping("/onboarding-14d")
    public ResponseEntity<PaginatedResponse<DriverOnboardingDTO>> getOnboarding14d(
            @RequestParam(required = false) String parkId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String weekISO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        System.out.println("=== Controller recibió request ===");
        System.out.println("parkId: " + parkId);
        System.out.println("startDateFrom: " + startDateFrom);
        System.out.println("startDateTo: " + startDateTo);
        System.out.println("channel: " + channel);
        System.out.println("weekISO: " + weekISO);
        System.out.println("page: " + page + ", size: " + size);
        
        OnboardingFilterDTO filter = new OnboardingFilterDTO();
        filter.setParkId(parkId);
        filter.setChannel(channel);
        filter.setWeekISO(weekISO);
        filter.setPage(page);
        filter.setSize(size);
        
        if (weekISO != null && !weekISO.isEmpty()) {
            System.out.println("Calculando rango de fechas para semana ISO: " + weekISO);
            try {
                LocalDate[] weekRange = WeekISOUtil.getWeekRange(weekISO);
                if (weekRange != null && weekRange.length == 2) {
                    filter.setStartDateFrom(weekRange[0]);
                    filter.setStartDateTo(weekRange[1]);
                    System.out.println("Controller - Semana ISO: " + weekISO + " -> Fechas aplicadas: " + weekRange[0] + " a " + weekRange[1]);
                } else {
                    System.err.println("ERROR: getWeekRange retornó null o array inválido para semana ISO: " + weekISO);
                }
            } catch (Exception e) {
                System.err.println("ERROR al calcular semana ISO " + weekISO + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No hay weekISO, usando fechas directas");
            filter.setStartDateFrom(startDateFrom);
            filter.setStartDateTo(startDateTo);
        }
        
        System.out.println("Controller - Filtro final - startDateFrom: " + filter.getStartDateFrom() + ", startDateTo: " + filter.getStartDateTo());
        System.out.println("=== Fin Controller ===");
        
        PaginatedResponse<DriverOnboardingDTO> results = onboardingService.getOnboarding14dPaginated(filter);
        return ResponseEntity.ok(results);
    }
    
    @PostMapping("/sync-metrics")
    public ResponseEntity<Map<String, Object>> syncMetrics(@RequestParam(required = false) String parkId) {
        Map<String, Object> response = new HashMap<>();
        try {
            onboardingService.calculateAndSaveMetrics(parkId);
            response.put("status", "success");
            response.put("message", "Métricas calculadas y guardadas exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al calcular métricas: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/evolution")
    public ResponseEntity<List<EvolutionMetricsDTO>> getEvolutionMetrics(
            @RequestParam(required = false) String parkId,
            @RequestParam(defaultValue = "weeks") String periodType,
            @RequestParam(defaultValue = "4") int periods) {
        try {
            List<EvolutionMetricsDTO> results = onboardingService.getEvolutionMetrics(parkId, periodType, periods);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

