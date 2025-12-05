package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.*;
import com.yego.contractortracker.entity.ContractorTrackingHistory;
import com.yego.contractortracker.entity.LeadMatch;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.repository.ContractorTrackingHistoryRepository;
import com.yego.contractortracker.repository.LeadMatchRepository;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeadProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LeadProcessingService.class);
    private static final int DEFAULT_TIME_MARGIN_DAYS = 3;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private LeadMatchRepository leadMatchRepository;
    
    @Autowired
    private ContractorTrackingHistoryRepository trackingHistoryRepository;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private YangoTransactionService yangoTransactionService;
    
    @Transactional
    public LeadProcessingResultDTO procesarArchivoCSV(MultipartFile file) {
        logger.info("Iniciando procesamiento de archivo CSV: {}", file.getOriginalFilename());
        
        try {
            List<LeadDTO> leads = leerCSV(file);
            logger.info("Leídos {} leads del CSV", leads.size());
            
            if (leads.isEmpty()) {
                return new LeadProcessingResultDTO(0, 0, 0, 0, LocalDateTime.now(), "No se encontraron leads en el archivo", null, null);
            }
            
            Map<String, Object> mapeoGeneral = crearMapeoGeneral(leads);
            LocalDate minDate = (LocalDate) mapeoGeneral.get("minDate");
            LocalDate maxDate = (LocalDate) mapeoGeneral.get("maxDate");
            
            logger.info("Rango de fechas de leads: {} a {}", minDate, maxDate);
            
            List<Map<String, Object>> drivers = buscarDriversEnRango(minDate, maxDate, DEFAULT_TIME_MARGIN_DAYS);
            logger.info("Encontrados {} drivers en rango ampliado", drivers.size());
            
            DriverIndex driverIndex = crearIndiceDrivers(drivers);
            logger.info("Índice creado: {} por teléfono, {} por nombre", driverIndex.byPhone.size(), driverIndex.byName.size());
            
            Set<String> externalIds = leads.stream()
                    .map(LeadDTO::getExternalId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            Map<String, LeadMatch> matchesExistentes = new HashMap<>();
            if (!externalIds.isEmpty()) {
                List<LeadMatch> existentes = leadMatchRepository.findAll().stream()
                        .filter(lm -> externalIds.contains(lm.getExternalId()))
                        .collect(Collectors.toList());
                for (LeadMatch lm : existentes) {
                    matchesExistentes.put(lm.getExternalId(), lm);
                }
            }
            
            int matchedCount = 0;
            int unmatchedCount = 0;
            List<LeadMatch> matchesParaGuardar = new ArrayList<>();
            Set<String> driverIdsParaActualizar = new HashSet<>();
            
            logger.info("Iniciando matching de {} leads...", leads.size());
            int procesados = 0;
            
            for (LeadDTO lead : leads) {
                Optional<Map<String, Object>> mejorMatch = encontrarMejorMatchOptimizado(lead, driverIndex, DEFAULT_TIME_MARGIN_DAYS);
                
                LeadMatch leadMatch = matchesExistentes.getOrDefault(lead.getExternalId(), new LeadMatch());
                
                if (mejorMatch.isPresent()) {
                    Map<String, Object> match = mejorMatch.get();
                    String driverId = (String) match.get("driver_id");
                    Double score = (Double) match.get("score");
                    LocalDate hireDate = (LocalDate) match.get("hire_date");
                    
                    if (leadMatch.getId() == null) {
                        leadMatch.setExternalId(lead.getExternalId());
                    }
                    leadMatch.setDriverId(driverId);
                    leadMatch.setLeadCreatedAt(lead.getLeadCreatedAt());
                    leadMatch.setHireDate(hireDate);
                    leadMatch.setMatchScore(score);
                    leadMatch.setIsManual(false);
                    leadMatch.setIsDiscarded(false);
                    leadMatch.setMatchedAt(LocalDateTime.now());
                    // Guardar/actualizar datos originales del lead (siempre actualizar para tener datos completos)
                    leadMatch.setLeadPhone(lead.getPhone());
                    leadMatch.setLeadFirstName(lead.getFirstName());
                    leadMatch.setLeadLastName(lead.getLastName());
                    if (procesados <= 3) {
                        logger.debug("Lead {} - Phone: {}, FirstName: {}, LastName: {}", 
                            lead.getExternalId(), lead.getPhone(), lead.getFirstName(), lead.getLastName());
                    }
                    
                    matchesParaGuardar.add(leadMatch);
                    driverIdsParaActualizar.add(driverId);
                    matchedCount++;
                } else {
                    if (leadMatch.getId() == null) {
                        leadMatch.setExternalId(lead.getExternalId());
                        leadMatch.setDriverId("");
                        leadMatch.setLeadCreatedAt(lead.getLeadCreatedAt());
                        leadMatch.setHireDate(lead.getLeadCreatedAt());
                        leadMatch.setMatchScore(0.0);
                        leadMatch.setIsManual(false);
                        leadMatch.setIsDiscarded(false);
                        leadMatch.setMatchedAt(LocalDateTime.now());
                    }
                    // Siempre actualizar datos originales del lead (incluso si ya existe)
                    leadMatch.setLeadPhone(lead.getPhone());
                    leadMatch.setLeadFirstName(lead.getFirstName());
                    leadMatch.setLeadLastName(lead.getLastName());
                    if (procesados <= 3) {
                        logger.debug("Lead sin match {} - Phone: {}, FirstName: {}, LastName: {}", 
                            lead.getExternalId(), lead.getPhone(), lead.getFirstName(), lead.getLastName());
                    }
                    // Siempre agregar para guardar/actualizar (tanto nuevos como existentes)
                    matchesParaGuardar.add(leadMatch);
                    unmatchedCount++;
                }
                
                procesados++;
                if (procesados % 50 == 0) {
                    logger.info("Procesados {}/{} leads...", procesados, leads.size());
                }
            }
            
            logger.info("Guardando {} matches en batch...", matchesParaGuardar.size());
            leadMatchRepository.saveAll(matchesParaGuardar);
            
            logger.info("Actualizando canal de adquisición para {} drivers...", driverIdsParaActualizar.size());
            actualizarCanalesAdquisicionBatch(driverIdsParaActualizar);
            
            LocalDateTime lastUpdated = LocalDateTime.now();
            
            logger.info("Procesamiento completado. Matched: {}, Unmatched: {}", matchedCount, unmatchedCount);
            
            return new LeadProcessingResultDTO(
                leads.size(),
                matchedCount,
                unmatchedCount,
                0,
                lastUpdated,
                String.format("Procesados %d leads: %d matcheados, %d sin match", leads.size(), matchedCount, unmatchedCount),
                minDate,
                maxDate
            );
            
        } catch (Exception e) {
            logger.error("Error al procesar archivo CSV", e);
            throw new RuntimeException("Error al procesar archivo CSV: " + e.getMessage(), e);
        }
    }
    
    private List<LeadDTO> leerCSV(MultipartFile file) throws Exception {
        List<LeadDTO> leads = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return leads;
            }
            
            String[] headers = headerLine.split(",");
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim(), i);
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] values = parseCSVLine(line);
                if (values.length < headerMap.size()) {
                    continue;
                }
                
                LeadDTO lead = new LeadDTO();
                lead.setExternalId(getValue(values, headerMap, "external_id"));
                lead.setFirstName(getValue(values, headerMap, "first_name"));
                lead.setLastName(getValue(values, headerMap, "last_name"));
                lead.setMiddleName(getValue(values, headerMap, "middle_name"));
                lead.setPhone(getValue(values, headerMap, "phone"));
                
                // Log para debug - primeros 3 leads
                if (leads.size() < 3) {
                    logger.info("CSV Lead {} - Phone: '{}', FirstName: '{}', LastName: '{}'", 
                        lead.getExternalId(), lead.getPhone(), lead.getFirstName(), lead.getLastName());
                    logger.info("Header map keys: {}", headerMap.keySet());
                }
                lead.setAssetPlateNumber(getValue(values, headerMap, "asset_plate_number"));
                lead.setStatus(getValue(values, headerMap, "status"));
                lead.setParkName(getValue(values, headerMap, "park_name"));
                lead.setTargetCity(getValue(values, headerMap, "target_city"));
                
                String leadCreatedAtStr = getValue(values, headerMap, "lead_created_at");
                if (leadCreatedAtStr != null && !leadCreatedAtStr.isEmpty()) {
                    try {
                        if (leadCreatedAtStr.contains("T")) {
                            lead.setLeadCreatedAt(LocalDate.parse(leadCreatedAtStr.substring(0, 10), DATE_FORMATTER));
                        } else {
                            lead.setLeadCreatedAt(LocalDate.parse(leadCreatedAtStr, DATE_FORMATTER));
                        }
                    } catch (Exception e) {
                        logger.warn("Error al parsear fecha lead_created_at: {}", leadCreatedAtStr);
                    }
                }
                
                leads.add(lead);
            }
        }
        
        return leads;
    }
    
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentValue = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        values.add(currentValue.toString().trim());
        
        return values.toArray(new String[0]);
    }
    
    private String getValue(String[] values, Map<String, Integer> headerMap, String key) {
        Integer index = headerMap.get(key);
        if (index != null && index < values.length) {
            String value = values[index];
            return value != null && !value.isEmpty() ? value.trim() : null;
        }
        return null;
    }
    
    private Map<String, Object> crearMapeoGeneral(List<LeadDTO> leads) {
        LocalDate minDate = null;
        LocalDate maxDate = null;
        Set<String> telefonosUnicos = new HashSet<>();
        Set<String> nombresUnicos = new HashSet<>();
        
        for (LeadDTO lead : leads) {
            if (lead.getLeadCreatedAt() != null) {
                if (minDate == null || lead.getLeadCreatedAt().isBefore(minDate)) {
                    minDate = lead.getLeadCreatedAt();
                }
                if (maxDate == null || lead.getLeadCreatedAt().isAfter(maxDate)) {
                    maxDate = lead.getLeadCreatedAt();
                }
            }
            
            if (lead.getPhone() != null) {
                telefonosUnicos.add(normalizarTelefono(lead.getPhone()));
            }
            
            if (lead.getFirstName() != null && lead.getLastName() != null) {
                String nombreCompleto = normalizarNombre(lead.getFirstName() + " " + lead.getLastName());
                nombresUnicos.add(nombreCompleto);
            }
        }
        
        Map<String, Object> mapeo = new HashMap<>();
        mapeo.put("minDate", minDate);
        mapeo.put("maxDate", maxDate);
        mapeo.put("telefonosUnicos", telefonosUnicos);
        mapeo.put("nombresUnicos", nombresUnicos);
        
        return mapeo;
    }
    
    private List<Map<String, Object>> buscarDriversEnRango(LocalDate minDate, LocalDate maxDate, int marginDays) {
        LocalDate fechaDesde = minDate != null ? minDate.minusDays(marginDays) : LocalDate.now().minusMonths(3);
        LocalDate fechaHasta = maxDate != null ? maxDate.plusDays(marginDays) : LocalDate.now();
        
        String sql = "SELECT driver_id, park_id, full_name, phone, hire_date " +
                     "FROM drivers " +
                     "WHERE hire_date BETWEEN ? AND ? " +
                     "ORDER BY hire_date";
        
        return jdbcTemplate.queryForList(sql, fechaDesde, fechaHasta);
    }
    
    private static class DriverIndex {
        Map<String, List<Map<String, Object>>> byPhone = new HashMap<>();
        Map<String, List<Map<String, Object>>> byName = new HashMap<>();
        Map<String, List<Map<String, Object>>> byNameWords = new HashMap<>();
        Map<String, Map<String, Object>> byId = new HashMap<>();
    }
    
    private DriverIndex crearIndiceDrivers(List<Map<String, Object>> drivers) {
        DriverIndex index = new DriverIndex();
        
        for (Map<String, Object> driver : drivers) {
            String driverId = (String) driver.get("driver_id");
            String phone = (String) driver.get("phone");
            String fullName = (String) driver.get("full_name");
            Object hireDateObj = driver.get("hire_date");
            
            LocalDate hireDate = null;
            if (hireDateObj instanceof java.sql.Date) {
                hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
            } else if (hireDateObj instanceof LocalDate) {
                hireDate = (LocalDate) hireDateObj;
            }
            
            Map<String, Object> driverInfo = new HashMap<>();
            driverInfo.put("driver_id", driverId);
            driverInfo.put("phone", phone != null ? normalizarTelefono(phone) : null);
            driverInfo.put("full_name", fullName != null ? normalizarNombre(fullName) : null);
            driverInfo.put("hire_date", hireDate);
            driverInfo.put("original_phone", phone);
            driverInfo.put("original_full_name", fullName);
            
            index.byId.put(driverId, driverInfo);
            
            String normalizedPhone = phone != null ? normalizarTelefono(phone) : null;
            if (normalizedPhone != null && !normalizedPhone.isEmpty()) {
                index.byPhone.computeIfAbsent(normalizedPhone, k -> new ArrayList<>()).add(driverInfo);
            }
            
            String normalizedName = fullName != null ? normalizarNombre(fullName) : null;
            if (normalizedName != null && !normalizedName.isEmpty()) {
                index.byName.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(driverInfo);
                
                // Índice por palabras individuales para búsqueda flexible
                String[] palabras = normalizedName.split("\\s+");
                for (String palabra : palabras) {
                    if (palabra.length() >= 3) { // Solo palabras de 3+ caracteres
                        index.byNameWords.computeIfAbsent(palabra, k -> new ArrayList<>()).add(driverInfo);
                    }
                }
            }
        }
        
        return index;
    }
    
    private Optional<Map<String, Object>> encontrarMejorMatchOptimizado(LeadDTO lead, DriverIndex index, int marginDays) {
        return encontrarMejorMatchConSimilitud(lead, index, marginDays, true, true, 0.5, 0.7, 2, false);
    }
    
    private Optional<Map<String, Object>> encontrarMejorMatchConSimilitud(
            LeadDTO lead, DriverIndex index, int marginDays,
            boolean matchByPhone, boolean matchByName,
            double nameThreshold, double phoneThreshold,
            int minWordsMatch, boolean ignoreSecondLastName) {
        
        String leadPhone = lead.getPhone() != null ? normalizarTelefono(lead.getPhone()) : null;
        String leadNameOriginal = null;
        if (lead.getFirstName() != null && lead.getLastName() != null) {
            leadNameOriginal = lead.getFirstName() + " " + lead.getLastName();
        }
        String leadName = leadNameOriginal != null ? normalizarNombre(leadNameOriginal) : null;
        
        Set<String> driverIdsCandidatos = new HashSet<>();
        
        // Búsqueda exacta inicial (rápida)
        if (leadPhone != null && !leadPhone.isEmpty() && matchByPhone) {
            List<Map<String, Object>> driversPorTelefono = index.byPhone.get(leadPhone);
            if (driversPorTelefono != null) {
                for (Map<String, Object> driver : driversPorTelefono) {
                    driverIdsCandidatos.add((String) driver.get("driver_id"));
                }
            }
        }
        
        if (leadName != null && !leadName.isEmpty() && matchByName) {
            List<Map<String, Object>> driversPorNombre = index.byName.get(leadName);
            if (driversPorNombre != null) {
                for (Map<String, Object> driver : driversPorNombre) {
                    driverIdsCandidatos.add((String) driver.get("driver_id"));
                }
            }
        }
        
        // Búsqueda flexible por palabras individuales si no hay match exacto
        if (driverIdsCandidatos.isEmpty() && matchByName && leadNameOriginal != null) {
            String[] palabras = normalizarNombreParaComparacion(leadNameOriginal).split("\\s+");
            for (String palabra : palabras) {
                if (palabra.length() >= 3 && index.byNameWords != null) {
                    List<Map<String, Object>> driversPorPalabra = index.byNameWords.get(palabra);
                    if (driversPorPalabra != null) {
                        for (Map<String, Object> driver : driversPorPalabra) {
                            driverIdsCandidatos.add((String) driver.get("driver_id"));
                        }
                    }
                }
            }
        }
        
        // Si aún no hay candidatos y hay teléfono, buscar por similitud de teléfono
        if (driverIdsCandidatos.isEmpty() && matchByPhone && leadPhone != null && !leadPhone.isEmpty()) {
            // Buscar en todos los drivers del índice (última opción, más lento)
            for (Map<String, Object> driver : index.byId.values()) {
                String driverPhone = (String) driver.get("phone");
                if (driverPhone != null) {
                    double phoneSim = calcularSimilitudTelefono(leadPhone, driverPhone, phoneThreshold);
                    if (phoneSim >= phoneThreshold) {
                        driverIdsCandidatos.add((String) driver.get("driver_id"));
                    }
                }
            }
        }
        
        if (driverIdsCandidatos.isEmpty()) {
            return Optional.empty();
        }
        
        List<Map<String, Object>> posiblesMatches = new ArrayList<>();
        
        for (String driverId : driverIdsCandidatos) {
            Map<String, Object> driver = index.byId.get(driverId);
            if (driver == null) continue;
            
            String driverPhone = (String) driver.get("phone");
            String driverNameOriginal = (String) driver.get("original_full_name");
            LocalDate hireDate = (LocalDate) driver.get("hire_date");
            
            // Calcular similitudes
            double phoneSim = 0.0;
            double nameSim = 0.0;
            
            if (matchByPhone && leadPhone != null && driverPhone != null) {
                phoneSim = calcularSimilitudTelefono(leadPhone, driverPhone, phoneThreshold);
            }
            
            if (matchByName && leadNameOriginal != null && driverNameOriginal != null) {
                nameSim = calcularSimilitudNombre(leadNameOriginal, driverNameOriginal, nameThreshold, minWordsMatch, ignoreSecondLastName);
            }
            
            // Determinar si hay match
            boolean phoneMatch = phoneSim >= phoneThreshold;
            boolean nameMatch = nameSim >= nameThreshold;
            
            if (phoneMatch || nameMatch) {
                if (lead.getLeadCreatedAt() != null && hireDate != null) {
                    long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(lead.getLeadCreatedAt(), hireDate));
                    
                    if (daysDiff <= marginDays) {
                        // Calcular score flexible
                        double score = calcularScoreFlexible(phoneSim, nameSim, phoneMatch, nameMatch);
                        
                        Map<String, Object> match = new HashMap<>();
                        match.put("driver_id", driverId);
                        match.put("hire_date", hireDate);
                        match.put("score", score);
                        match.put("days_diff", daysDiff);
                        match.put("phone_sim", phoneSim);
                        match.put("name_sim", nameSim);
                        
                        posiblesMatches.add(match);
                    }
                }
            }
        }
        
        if (posiblesMatches.isEmpty()) {
            return Optional.empty();
        }
        
        posiblesMatches.sort((a, b) -> {
            int scoreCompare = Double.compare((Double) b.get("score"), (Double) a.get("score"));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Long.compare((Long) a.get("days_diff"), (Long) b.get("days_diff"));
        });
        
        Map<String, Object> mejorMatch = posiblesMatches.get(0);
        
        // Logging detallado para debugging
        Double nameSim = (Double) mejorMatch.get("name_sim");
        Double phoneSim = (Double) mejorMatch.get("phone_sim");
        String driverNameOriginal = (String) index.byId.get(mejorMatch.get("driver_id")).get("original_full_name");
        
        if (nameSim != null && nameSim > 0.0 && nameSim < 1.0) {
            logger.info("Match por similitud de nombre - Lead: '{}' vs Driver: '{}' - NameSim: {:.2f}, PhoneSim: {:.2f}, Score: {:.2f}",
                leadNameOriginal, driverNameOriginal, 
                String.format("%.2f", nameSim), 
                String.format("%.2f", phoneSim != null ? phoneSim : 0.0), 
                String.format("%.2f", mejorMatch.get("score")));
        } else if (phoneSim != null && phoneSim > 0.0 && phoneSim < 1.0) {
            logger.info("Match por similitud de teléfono - Lead: '{}' vs Driver: '{}' - NameSim: {:.2f}, PhoneSim: {:.2f}, Score: {:.2f}",
                leadNameOriginal, driverNameOriginal, 
                String.format("%.2f", nameSim != null ? nameSim : 0.0), 
                String.format("%.2f", phoneSim), 
                String.format("%.2f", mejorMatch.get("score")));
        }
        
        return Optional.of(mejorMatch);
    }
    
    private double calcularScoreFlexible(double phoneSim, double nameSim, boolean phoneMatch, boolean nameMatch) {
        // Score 1.0: Coincidencia exacta (teléfono + nombre)
        if (phoneSim == 1.0 && nameSim == 1.0) {
            return 1.0;
        }
        // Score 0.9: Teléfono exacto + nombre con similitud > 0.8
        if (phoneSim == 1.0 && nameSim > 0.8) {
            return 0.9;
        }
        // Score 0.8: Teléfono similar + nombre con similitud > 0.7
        if (phoneSim >= 0.7 && nameSim > 0.7) {
            return 0.8;
        }
        // Score 0.7: Solo teléfono exacto
        if (phoneSim == 1.0 && nameSim == 0.0) {
            return 0.7;
        }
        // Score 0.6: Solo nombre con similitud > 0.6
        if (phoneSim == 0.0 && nameSim > 0.6) {
            return 0.6;
        }
        // Score 0.5: Solo teléfono similar o solo nombre con similitud > 0.5
        if (phoneSim >= 0.5 || nameSim >= 0.5) {
            return 0.5;
        }
        return 0.0;
    }
    
    private String normalizarTelefono(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[\\s\\-\\(\\)]", "").trim();
    }
    
    private String normalizarNombre(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("\\s+", " ")
                .trim();
    }
    
    private String normalizarNombreParaComparacion(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        // Normalización básica
        String normalized = name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("\\s+", " ")
                .trim();
        
        // Dividir en palabras y filtrar palabras muy comunes
        String[] palabras = normalized.split("\\s+");
        List<String> palabrasFiltradas = new ArrayList<>();
        Set<String> palabrasComunes = Set.of("de", "la", "del", "los", "las", "y", "e");
        
        for (String palabra : palabras) {
            if (!palabrasComunes.contains(palabra) && palabra.length() > 1) {
                palabrasFiltradas.add(palabra);
            }
        }
        
        // Ordenar alfabéticamente para manejar orden invertido
        Collections.sort(palabrasFiltradas);
        
        return String.join(" ", palabrasFiltradas);
    }
    
    private double calcularSimilitudNombre(String nombre1, String nombre2, double threshold, int minWordsMatch, boolean ignoreSecondLastName) {
        if (nombre1 == null || nombre2 == null) {
            return 0.0;
        }
        
        String norm1 = normalizarNombreParaComparacion(nombre1);
        String norm2 = normalizarNombreParaComparacion(nombre2);
        
        if (norm1 == null || norm2 == null || norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        // Coincidencia exacta después de normalización
        if (norm1.equals(norm2)) {
            return 1.0;
        }
        
        // Dividir en palabras
        String[] palabras1 = norm1.split("\\s+");
        String[] palabras2 = norm2.split("\\s+");
        
        // Si ignoreSecondLastName, tomar solo las primeras 2-3 palabras
        if (ignoreSecondLastName) {
            if (palabras1.length > 2) {
                palabras1 = Arrays.copyOf(palabras1, 2);
            }
            if (palabras2.length > 2) {
                palabras2 = Arrays.copyOf(palabras2, 2);
            }
        }
        
        Set<String> set1 = new HashSet<>(Arrays.asList(palabras1));
        Set<String> set2 = new HashSet<>(Arrays.asList(palabras2));
        
        // Calcular intersección (palabras comunes)
        Set<String> interseccion = new HashSet<>(set1);
        interseccion.retainAll(set2);
        
        // Calcular unión
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        // Jaccard similarity
        if (union.isEmpty()) {
            return 0.0;
        }
        
        double jaccard = (double) interseccion.size() / union.size();
        
        // Verificar si hay suficientes palabras coincidentes
        if (interseccion.size() >= minWordsMatch && jaccard >= threshold) {
            // Ajustar score si hay palabras similares (typos menores)
            double adjustedScore = jaccard;
            List<String> palabrasSimilares = new ArrayList<>();
            for (String p1 : set1) {
                for (String p2 : set2) {
                    if (!p1.equals(p2) && calcularDistanciaLevenshtein(p1, p2) <= 2 && Math.max(p1.length(), p2.length()) >= 4) {
                        // Palabras similares (typo menor)
                        palabrasSimilares.add(p1 + "~" + p2);
                        adjustedScore = Math.max(adjustedScore, jaccard + 0.2);
                        break;
                    }
                }
            }
            
            // Logging detallado si hay match por similitud
            if (adjustedScore > jaccard || interseccion.size() > 0) {
                logger.debug("Similitud nombre - '{}' vs '{}': Intersección: {}, Unión: {}, Jaccard: {}, Ajustado: {}, Palabras similares: {}",
                    nombre1, nombre2, interseccion.size(), union.size(), 
                    String.format("%.2f", jaccard), String.format("%.2f", adjustedScore), palabrasSimilares);
            }
            
            return Math.min(adjustedScore, 1.0);
        }
        
        return 0.0;
    }
    
    private int calcularDistanciaLevenshtein(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        if (s1.equals(s2)) {
            return 0;
        }
        if (s1.length() == 0) {
            return s2.length();
        }
        if (s2.length() == 0) {
            return s1.length();
        }
        
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + 1);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    private double calcularSimilitudTelefono(String telefono1, String telefono2, double threshold) {
        if (telefono1 == null || telefono2 == null) {
            return 0.0;
        }
        
        String norm1 = normalizarTelefono(telefono1);
        String norm2 = normalizarTelefono(telefono2);
        
        if (norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        // Coincidencia exacta
        if (norm1.equals(norm2)) {
            return 1.0;
        }
        
        // Comparar últimos 7-9 dígitos (ignorar código de país/prefijo)
        int len1 = norm1.length();
        int len2 = norm2.length();
        
        int minLen = Math.min(len1, len2);
        int maxLen = Math.max(len1, len2);
        
        // Si las longitudes son muy diferentes, no es match
        if (maxLen - minLen > 3) {
            return 0.0;
        }
        
        // Comparar últimos dígitos (mínimo 7)
        int compareLen = Math.min(minLen, 9);
        String last1 = len1 >= compareLen ? norm1.substring(len1 - compareLen) : norm1;
        String last2 = len2 >= compareLen ? norm2.substring(len2 - compareLen) : norm2;
        
        // Calcular diferencias
        int diferencias = 0;
        int minCompare = Math.min(last1.length(), last2.length());
        for (int i = 0; i < minCompare; i++) {
            if (last1.charAt(i) != last2.charAt(i)) {
                diferencias++;
            }
        }
        diferencias += Math.abs(last1.length() - last2.length());
        
        // Calcular similitud basada en diferencias
        if (diferencias == 0) {
            return 1.0;
        } else if (diferencias == 1) {
            return 0.9;
        } else if (diferencias == 2) {
            return 0.7;
        } else if (diferencias <= 3 && minCompare >= 7) {
            return 0.5;
        }
        
        return 0.0;
    }
    
    private void actualizarCanalAdquisicion(String driverId) {
        Optional<ContractorTrackingHistory> latest = trackingHistoryRepository.findLatestByDriverId(driverId);
        
        if (latest.isPresent()) {
            ContractorTrackingHistory history = latest.get();
            history.setAcquisitionChannel("cabinet");
            trackingHistoryRepository.save(history);
        } else {
            ContractorTrackingHistory newHistory = new ContractorTrackingHistory();
            newHistory.setDriverId(driverId);
            newHistory.setParkId("");
            newHistory.setAcquisitionChannel("cabinet");
            newHistory.setCalculationDate(LocalDateTime.now());
            newHistory.setTotalTripsHistorical(0);
            newHistory.setSumWorkTimeSeconds(null);
            newHistory.setHasHistoricalConnection(false);
            newHistory.setStatusRegistered(false);
            newHistory.setStatusConnected(false);
            newHistory.setStatusWithTrips(false);
            trackingHistoryRepository.save(newHistory);
        }
    }
    
    private void actualizarCanalesAdquisicionBatch(Set<String> driverIds) {
        if (driverIds.isEmpty()) {
            return;
        }
        
        String sql = "UPDATE contractor_tracking_history " +
                     "SET acquisition_channel = 'cabinet' " +
                     "WHERE driver_id IN (" + 
                     driverIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
        
        jdbcTemplate.update(sql, driverIds.toArray());
        
        String sqlInsert = "INSERT INTO contractor_tracking_history " +
                          "(driver_id, park_id, acquisition_channel, calculation_date, total_trips_historical, " +
                          "has_historical_connection, status_registered, status_connected, status_with_trips, last_updated) " +
                          "SELECT d.driver_id, d.park_id, 'cabinet', CURRENT_TIMESTAMP, 0, false, false, false, false, CURRENT_TIMESTAMP " +
                          "FROM drivers d " +
                          "WHERE d.driver_id IN (" + 
                          driverIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ") " +
                          "AND NOT EXISTS (SELECT 1 FROM contractor_tracking_history cth WHERE cth.driver_id = d.driver_id)";
        
        try {
            jdbcTemplate.update(sqlInsert, driverIds.toArray());
        } catch (Exception e) {
            logger.warn("Error al insertar nuevos registros de tracking history: {}", e.getMessage());
        }
    }
    
    public List<LeadMatchDTO> obtenerLeadsSinMatch() {
        List<LeadMatch> leads = leadMatchRepository.findUnmatchedLeads();
        
        logger.info("Obteniendo {} leads sin match", leads.size());
        if (!leads.isEmpty()) {
            LeadMatch first = leads.get(0);
            logger.debug("Primer lead sin match - ExternalId: {}, Phone: {}, FirstName: {}, LastName: {}", 
                first.getExternalId(), first.getLeadPhone(), first.getLeadFirstName(), first.getLeadLastName());
        }
        
        return leads.stream().map(lead -> {
            LeadMatchDTO dto = new LeadMatchDTO();
            dto.setExternalId(lead.getExternalId());
            dto.setDriverId(lead.getDriverId() != null && !lead.getDriverId().isEmpty() ? lead.getDriverId() : null);
            dto.setLeadCreatedAt(lead.getLeadCreatedAt());
            dto.setHireDate(lead.getHireDate() != null && lead.getDriverId() != null && !lead.getDriverId().isEmpty() ? lead.getHireDate() : null);
            dto.setDateMatch(lead.getHireDate() != null && lead.getLeadCreatedAt() != null && 
                    lead.getHireDate().equals(lead.getLeadCreatedAt()));
            dto.setMatchScore(lead.getMatchScore());
            dto.setIsManual(lead.getIsManual());
            dto.setIsDiscarded(lead.getIsDiscarded());
            // Mapear datos originales del lead
            dto.setLeadPhone(lead.getLeadPhone());
            dto.setLeadFirstName(lead.getLeadFirstName());
            dto.setLeadLastName(lead.getLeadLastName());
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Transactional
    public void asignarMatchManual(String externalId, String driverId) {
        LeadMatch match = leadMatchRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado: " + externalId));
        
        String sql = "SELECT hire_date FROM drivers WHERE driver_id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, driverId);
        
        if (results.isEmpty()) {
            throw new RuntimeException("Driver no encontrado: " + driverId);
        }
        
        Object hireDateObj = results.get(0).get("hire_date");
        LocalDate hireDate = null;
        if (hireDateObj instanceof java.sql.Date) {
            hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
        } else if (hireDateObj instanceof LocalDate) {
            hireDate = (LocalDate) hireDateObj;
        }
        
        match.setDriverId(driverId);
        match.setHireDate(hireDate);
        match.setIsManual(true);
        match.setIsDiscarded(false);
        match.setMatchedAt(LocalDateTime.now());
        leadMatchRepository.save(match);
        
        actualizarCanalAdquisicion(driverId);
    }
    
    @Transactional
    public void descartarLead(String externalId) {
        LeadMatch match = leadMatchRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado: " + externalId));
        
        match.setIsDiscarded(true);
        leadMatchRepository.save(match);
    }
    
    @Transactional
    public LeadProcessingResultDTO reprocesarConReglas(LeadReprocessConfig config) {
        logger.info("Iniciando reprocesamiento con reglas: {}", config);
        
        int timeMargin = config.getTimeMarginDays() != null ? config.getTimeMarginDays() : DEFAULT_TIME_MARGIN_DAYS;
        boolean matchByPhone = config.getMatchByPhone() != null ? config.getMatchByPhone() : true;
        boolean matchByName = config.getMatchByName() != null ? config.getMatchByName() : true;
        double threshold = config.getMatchThreshold() != null ? config.getMatchThreshold() : 0.5;
        
        List<LeadMatch> leadsToReprocess;
        String scope = (config.getReprocessScope() != null) ? config.getReprocessScope() : "unmatched";
        
        if ("all".equalsIgnoreCase(scope)) {
            leadsToReprocess = leadMatchRepository.findAll().stream()
                    .filter(lm -> !lm.getIsDiscarded())
                    .collect(Collectors.toList());
        } else if ("unmatched".equalsIgnoreCase(scope)) {
            leadsToReprocess = leadMatchRepository.findUnmatchedLeads();
        } else if ("discarded".equalsIgnoreCase(scope)) {
            leadsToReprocess = leadMatchRepository.findAll().stream()
                    .filter(lm -> lm.getIsDiscarded())
                    .collect(Collectors.toList());
        } else {
            leadsToReprocess = leadMatchRepository.findUnmatchedLeads();
        }
        
        if (leadsToReprocess.isEmpty()) {
            return new LeadProcessingResultDTO(0, 0, 0, 0, LocalDateTime.now(), "No hay leads para reprocesar", null, null);
        }
        
        LocalDate minDate = leadsToReprocess.stream()
                .map(LeadMatch::getLeadCreatedAt)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now().minusMonths(3));
        
        LocalDate maxDate = leadsToReprocess.stream()
                .map(LeadMatch::getLeadCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        List<Map<String, Object>> drivers = buscarDriversEnRango(minDate, maxDate, timeMargin);
        DriverIndex driverIndex = crearIndiceDrivers(drivers);
        
        int matchedCount = 0;
        int unmatchedCount = 0;
        
        for (LeadMatch leadMatch : leadsToReprocess) {
            LeadDTO lead = new LeadDTO();
            lead.setExternalId(leadMatch.getExternalId());
            lead.setLeadCreatedAt(leadMatch.getLeadCreatedAt());
            // Recuperar datos originales del lead desde LeadMatch
            lead.setPhone(leadMatch.getLeadPhone());
            lead.setFirstName(leadMatch.getLeadFirstName());
            lead.setLastName(leadMatch.getLeadLastName());
            
            Optional<Map<String, Object>> mejorMatch = encontrarMejorMatchConReglasOptimizado(
                    lead, driverIndex, timeMargin, matchByPhone, matchByName, threshold, config);
            
            if (mejorMatch.isPresent()) {
                Map<String, Object> match = mejorMatch.get();
                String driverId = (String) match.get("driver_id");
                Double score = (Double) match.get("score");
                LocalDate hireDate = (LocalDate) match.get("hire_date");
                
                leadMatch.setDriverId(driverId);
                leadMatch.setHireDate(hireDate);
                leadMatch.setMatchScore(score);
                leadMatch.setIsManual(false);
                leadMatch.setIsDiscarded(false);
                leadMatch.setMatchedAt(LocalDateTime.now());
                leadMatchRepository.save(leadMatch);
                
                actualizarCanalAdquisicion(driverId);
                matchedCount++;
            } else {
                leadMatch.setDriverId("");
                leadMatch.setMatchScore(0.0);
                leadMatch.setIsManual(false);
                leadMatch.setIsDiscarded(false);
                leadMatchRepository.save(leadMatch);
                unmatchedCount++;
            }
        }
        
        return new LeadProcessingResultDTO(
                leadsToReprocess.size(),
                matchedCount,
                unmatchedCount,
                0,
                LocalDateTime.now(),
                String.format("Reprocesados %d leads: %d matcheados, %d sin match", 
                        leadsToReprocess.size(), matchedCount, unmatchedCount),
                minDate,
                maxDate
        );
    }
    
    private Optional<Map<String, Object>> encontrarMejorMatchConReglasOptimizado(
            LeadDTO lead, DriverIndex index, 
            int marginDays, boolean matchByPhone, boolean matchByName, double threshold,
            LeadReprocessConfig config) {
        
        // Obtener valores de configuración o usar defaults
        double nameThreshold = (config != null && config.getNameSimilarityThreshold() != null) 
            ? config.getNameSimilarityThreshold() : 0.5;
        double phoneThreshold = (config != null && config.getPhoneSimilarityThreshold() != null) 
            ? config.getPhoneSimilarityThreshold() : 0.7;
        int minWordsMatch = (config != null && config.getMinWordsMatch() != null) 
            ? config.getMinWordsMatch() : 2;
        boolean ignoreSecondLastName = (config != null && config.getIgnoreSecondLastName() != null) 
            ? config.getIgnoreSecondLastName() : false;
        boolean enableFuzzy = (config != null && config.getEnableFuzzyMatching() != null) 
            ? config.getEnableFuzzyMatching() : true;
        
        if (!enableFuzzy) {
            // Si fuzzy matching está deshabilitado, usar matching exacto tradicional
            nameThreshold = 1.0;
            phoneThreshold = 1.0;
        }
        
        return encontrarMejorMatchConSimilitud(lead, index, marginDays, 
            matchByPhone, matchByName, nameThreshold, phoneThreshold, 
            minWordsMatch, ignoreSecondLastName);
    }
    
    public List<Map<String, Object>> obtenerDriversPorFecha(LocalDate date, String parkId) {
        String sql = "SELECT d.driver_id, d.full_name, d.phone, d.hire_date, d.license_number " +
                     "FROM drivers d " +
                     "WHERE d.hire_date = ? ";
        
        List<Object> params = new ArrayList<>();
        params.add(date);
        
        if (parkId != null && !parkId.trim().isEmpty()) {
            sql += "AND d.park_id = ? ";
            params.add(parkId);
        }
        
        sql += "ORDER BY d.hire_date DESC, d.full_name";
        
        return jdbcTemplate.queryForList(sql, params.toArray());
    }
    
    public LocalDateTime obtenerUltimaActualizacion() {
        return leadMatchRepository.findLastUpdated()
                .orElse(LocalDateTime.now().minusYears(1));
    }
    
    public UploadMetadataDTO obtenerMetadata() {
        try {
            LocalDateTime lastUploadDate = obtenerUltimaActualizacion();
            
            String sql = "SELECT " +
                        "COUNT(*) as total, " +
                        "COUNT(CASE WHEN driver_id IS NOT NULL AND driver_id != '' THEN 1 END) as matched, " +
                        "COUNT(CASE WHEN driver_id IS NULL OR driver_id = '' THEN 1 END) as unmatched, " +
                        "MIN(lead_created_at) as min_date, " +
                        "MAX(lead_created_at) as max_date " +
                        "FROM lead_matches " +
                        "WHERE is_discarded = false";
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            
            Integer totalRecords = ((Number) result.get("total")).intValue();
            Integer matchedCount = ((Number) result.get("matched")).intValue();
            Integer unmatchedCount = ((Number) result.get("unmatched")).intValue();
            
            LocalDate dataDateFrom = null;
            LocalDate dataDateTo = null;
            
            Object minDateObj = result.get("min_date");
            Object maxDateObj = result.get("max_date");
            
            if (minDateObj != null) {
                if (minDateObj instanceof java.sql.Date) {
                    dataDateFrom = ((java.sql.Date) minDateObj).toLocalDate();
                } else if (minDateObj instanceof java.sql.Timestamp) {
                    dataDateFrom = ((java.sql.Timestamp) minDateObj).toLocalDateTime().toLocalDate();
                } else if (minDateObj instanceof LocalDate) {
                    dataDateFrom = (LocalDate) minDateObj;
                }
            }
            
            if (maxDateObj != null) {
                if (maxDateObj instanceof java.sql.Date) {
                    dataDateTo = ((java.sql.Date) maxDateObj).toLocalDate();
                } else if (maxDateObj instanceof java.sql.Timestamp) {
                    dataDateTo = ((java.sql.Timestamp) maxDateObj).toLocalDateTime().toLocalDate();
                } else if (maxDateObj instanceof LocalDate) {
                    dataDateTo = (LocalDate) maxDateObj;
                }
            }
            
            UploadMetadataDTO.SourceDescriptionDTO sourceDescription = new UploadMetadataDTO.SourceDescriptionDTO(
                "Leads",
                "Módulo Supervisor",
                "https://partners-app.yango.com/agents",
                "Los leads provienen del módulo supervisor de https://partners-app.yango.com/agents"
            );
            
            return new UploadMetadataDTO(
                lastUploadDate,
                dataDateFrom,
                dataDateTo,
                totalRecords,
                matchedCount,
                unmatchedCount,
                sourceDescription
            );
        } catch (Exception e) {
            logger.error("Error al obtener metadata de leads", e);
            UploadMetadataDTO.SourceDescriptionDTO sourceDescription = new UploadMetadataDTO.SourceDescriptionDTO(
                "Leads",
                "Módulo Supervisor",
                "https://partners-app.yango.com/agents",
                "Los leads provienen del módulo supervisor de https://partners-app.yango.com/agents"
            );
            return new UploadMetadataDTO(
                LocalDateTime.now().minusYears(1),
                null,
                null,
                0,
                0,
                0,
                sourceDescription
            );
        }
    }
    
    public List<Map<String, Object>> obtenerSugerenciasScout(String externalId) {
        LeadMatch lead = leadMatchRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado: " + externalId));
        
        if (lead.getLeadCreatedAt() == null) {
            return new ArrayList<>();
        }
        
        LocalDate fechaDesde = lead.getLeadCreatedAt().minusDays(3);
        LocalDate fechaHasta = lead.getLeadCreatedAt().plusDays(3);
        
        String leadName = construirNombreCompleto(lead.getLeadFirstName(), lead.getLeadLastName());
        String normalizedLeadName = leadName != null ? normalizarNombre(leadName) : null;
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT sr.id, sr.scout_id, sr.registration_date, sr.driver_name, ");
        sql.append("  sr.driver_phone, sr.driver_license, s.scout_name, ");
        sql.append("  CASE ");
        sql.append("    WHEN LOWER(TRIM(sr.driver_name)) = LOWER(TRIM(?)) THEN 1.0 ");
        sql.append("    ELSE 0.7 ");
        sql.append("  END as name_score, ");
        sql.append("  CASE ");
        sql.append("    WHEN ABS(EXTRACT(DAY FROM (sr.registration_date - ?::date))) <= 3 ");
        sql.append("    THEN 1.0 - (ABS(EXTRACT(DAY FROM (sr.registration_date - ?::date))) / 3.0) ");
        sql.append("    ELSE 0.0 ");
        sql.append("  END as date_score ");
        sql.append("FROM scout_registrations sr ");
        sql.append("LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("WHERE sr.registration_date BETWEEN ? AND ? ");
        if (normalizedLeadName != null && !normalizedLeadName.isEmpty()) {
            sql.append("  AND (LOWER(TRIM(sr.driver_name)) LIKE ? OR LOWER(TRIM(sr.driver_name)) LIKE ?) ");
        }
        sql.append("ORDER BY name_score DESC, date_score DESC ");
        sql.append("LIMIT 20 ");
        
        List<Object> params = new ArrayList<>();
        if (normalizedLeadName != null && !normalizedLeadName.isEmpty()) {
            params.add(normalizedLeadName);
        } else {
            params.add("");
        }
        params.add(lead.getLeadCreatedAt());
        params.add(lead.getLeadCreatedAt());
        params.add(fechaDesde);
        params.add(fechaHasta);
        if (normalizedLeadName != null && !normalizedLeadName.isEmpty()) {
            params.add("%" + normalizedLeadName + "%");
            String[] palabras = normalizedLeadName.split("\\s+");
            if (palabras.length > 0) {
                params.add("%" + palabras[0] + "%");
            } else {
                params.add("%" + normalizedLeadName + "%");
            }
        }
        
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        
        for (Map<String, Object> resultado : resultados) {
            Object nameScoreObj = resultado.get("name_score");
            Object dateScoreObj = resultado.get("date_score");
            double nameScore = nameScoreObj != null && nameScoreObj instanceof Number ? 
                ((Number) nameScoreObj).doubleValue() : 0.0;
            double dateScore = dateScoreObj != null && dateScoreObj instanceof Number ? 
                ((Number) dateScoreObj).doubleValue() : 0.0;
            double totalScore = (nameScore * 0.7) + (dateScore * 0.3);
            resultado.put("total_score", totalScore);
        }
        
        resultados.sort((a, b) -> {
            double scoreA = ((Number) a.get("total_score")).doubleValue();
            double scoreB = ((Number) b.get("total_score")).doubleValue();
            return Double.compare(scoreB, scoreA);
        });
        
        return resultados;
    }
    
    @Transactional
    public void asignarScoutRegistration(String externalId, Long scoutRegistrationId) {
        LeadMatch lead = leadMatchRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado: " + externalId));
        
        String sql = "SELECT id, scout_id, registration_date, driver_name FROM scout_registrations WHERE id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, scoutRegistrationId);
        
        if (results.isEmpty()) {
            throw new RuntimeException("Scout registration no encontrado: " + scoutRegistrationId);
        }
        
        Map<String, Object> scoutReg = results.get(0);
        Object registrationDateObj = scoutReg.get("registration_date");
        LocalDate registrationDate = null;
        if (registrationDateObj instanceof java.sql.Date) {
            registrationDate = ((java.sql.Date) registrationDateObj).toLocalDate();
        } else if (registrationDateObj instanceof LocalDate) {
            registrationDate = (LocalDate) registrationDateObj;
        }
        
        String scoutDriverName = (String) scoutReg.get("driver_name");
        String leadName = construirNombreCompleto(lead.getLeadFirstName(), lead.getLeadLastName());
        String normalizedLeadName = leadName != null ? normalizarNombre(leadName) : null;
        String normalizedScoutName = scoutDriverName != null ? normalizarNombre(scoutDriverName) : null;
        
        double scoreNombre = 0.0;
        if (normalizedLeadName != null && normalizedScoutName != null) {
            if (normalizedLeadName.equals(normalizedScoutName)) {
                scoreNombre = 1.0;
            } else {
                scoreNombre = calcularSimilitudNombre(normalizedLeadName, normalizedScoutName, 0.7, 2, false);
            }
        }
        
        long diasDiferencia = 0;
        if (lead.getLeadCreatedAt() != null && registrationDate != null) {
            diasDiferencia = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                lead.getLeadCreatedAt(), registrationDate));
        }
        double scoreFecha = diasDiferencia <= 3 ? (1.0 - (diasDiferencia / 3.0)) : 0.0;
        double scoreTotal = (scoreNombre * 0.7) + (scoreFecha * 0.3);
        
        lead.setScoutRegistrationId(scoutRegistrationId);
        lead.setScoutMatchScore(scoreTotal);
        lead.setScoutMatchDate(LocalDateTime.now());
        leadMatchRepository.save(lead);
    }
    
    private String construirNombreCompleto(String firstName, String lastName) {
        StringBuilder nombre = new StringBuilder();
        if (firstName != null && !firstName.trim().isEmpty()) {
            nombre.append(firstName.trim());
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (nombre.length() > 0) {
                nombre.append(" ");
            }
            nombre.append(lastName.trim());
        }
        return nombre.length() > 0 ? nombre.toString() : null;
    }
    
    public List<LeadCabinetDTO> obtenerTodosLosLeadsConEstado(
            LocalDate dateFrom, 
            LocalDate dateTo,
            String matchStatus,
            String driverStatus,
            Integer milestoneType,
            Integer milestonePeriod,
            String search,
            Boolean includeDiscarded) {
        
        logger.info("Obteniendo leads para cabinet con filtros - dateFrom: {}, dateTo: {}, matchStatus: {}, driverStatus: {}, milestoneType: {}, milestonePeriod: {}, search: {}, includeDiscarded: {}",
                dateFrom, dateTo, matchStatus, driverStatus, milestoneType, milestonePeriod, search, includeDiscarded);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  lm.external_id, ");
        sql.append("  lm.driver_id, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.hire_date, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  lm.is_discarded, ");
        sql.append("  lm.lead_phone, ");
        sql.append("  lm.lead_first_name, ");
        sql.append("  lm.lead_last_name, ");
        sql.append("  d.full_name as driver_full_name, ");
        sql.append("  d.phone as driver_phone, ");
        sql.append("  d.hire_date as driver_hire_date ");
        sql.append("  COALESCE(SUM(CASE WHEN sd.driver_id IS NOT NULL ");
        sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date ");
        sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' ");
        sql.append("    THEN sd.count_orders_completed ELSE 0 END), 0) as total_trips_14d, ");
        sql.append("  SUM(CASE WHEN sd.driver_id IS NOT NULL ");
        sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date ");
        sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' ");
        sql.append("    THEN sd.sum_work_time_seconds ELSE NULL END) as sum_work_time_seconds ");
        sql.append("FROM lead_matches lm ");
        sql.append("LEFT JOIN drivers d ON (lm.driver_id IS NOT NULL AND lm.driver_id != '' AND lm.driver_id = d.driver_id) ");
        sql.append("LEFT JOIN summary_daily sd ON (d.driver_id IS NOT NULL AND d.driver_id = sd.driver_id ");
        sql.append("  AND d.hire_date IS NOT NULL ");
        sql.append("  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date ");
        sql.append("  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days') ");
        sql.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        if (dateFrom != null) {
            sql.append("AND lm.lead_created_at >= ? ");
            params.add(dateFrom);
        }
        
        if (dateTo != null) {
            sql.append("AND lm.lead_created_at <= ? ");
            params.add(dateTo);
        }
        
        if (includeDiscarded == null || !includeDiscarded) {
            sql.append("AND COALESCE(lm.is_discarded, false) = false ");
        }
        
        if (matchStatus != null && !matchStatus.equals("all")) {
            if ("matched".equals(matchStatus)) {
                sql.append("AND lm.driver_id IS NOT NULL AND lm.driver_id != '' ");
            } else if ("unmatched".equals(matchStatus)) {
                sql.append("AND (lm.driver_id IS NULL OR lm.driver_id = '') ");
            }
        }
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND ( ");
            sql.append("  LOWER(lm.external_id) LIKE ? OR ");
            sql.append("  LOWER(lm.lead_first_name) LIKE ? OR ");
            sql.append("  LOWER(lm.lead_last_name) LIKE ? OR ");
            sql.append("  lm.lead_phone LIKE ? ");
            sql.append(") ");
            String searchPattern = "%" + search.toLowerCase().trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
            params.add("%" + search.trim() + "%");
        }
        
        sql.append("GROUP BY ");
        sql.append("  lm.external_id, lm.driver_id, lm.lead_created_at, lm.hire_date, ");
        sql.append("  lm.match_score, lm.is_manual, lm.is_discarded, ");
        sql.append("  lm.lead_phone, lm.lead_first_name, lm.lead_last_name, ");
        sql.append("  d.full_name, d.phone, d.hire_date ");
        sql.append("ORDER BY lm.lead_created_at DESC NULLS LAST, lm.external_id ");
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            logger.info("Se obtuvieron {} leads para cabinet", rows.size());
            
            List<LeadCabinetDTO> leads = new ArrayList<>();
            Set<String> driverIds = new HashSet<>();
            
            for (Map<String, Object> row : rows) {
                LeadCabinetDTO lead = new LeadCabinetDTO();
                lead.setExternalId((String) row.get("external_id"));
                
                String driverId = (String) row.get("driver_id");
                lead.setDriverId(driverId != null && !driverId.isEmpty() ? driverId : null);
                
                Object leadCreatedAtObj = row.get("lead_created_at");
                if (leadCreatedAtObj instanceof java.sql.Date) {
                    lead.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                } else if (leadCreatedAtObj instanceof LocalDate) {
                    lead.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                }
                
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj != null) {
                    if (hireDateObj instanceof java.sql.Date) {
                        lead.setHireDate(((java.sql.Date) hireDateObj).toLocalDate());
                    } else if (hireDateObj instanceof LocalDate) {
                        lead.setHireDate((LocalDate) hireDateObj);
                    }
                }
                
                lead.setDateMatch(lead.getHireDate() != null && lead.getLeadCreatedAt() != null && 
                        lead.getHireDate().equals(lead.getLeadCreatedAt()));
                
                Object matchScoreObj = row.get("match_score");
                if (matchScoreObj != null && matchScoreObj instanceof Number) {
                    lead.setMatchScore(((Number) matchScoreObj).doubleValue());
                }
                
                Object isManualObj = row.get("is_manual");
                if (isManualObj instanceof Boolean) {
                    lead.setIsManual((Boolean) isManualObj);
                } else if (isManualObj instanceof String) {
                    lead.setIsManual(Boolean.parseBoolean((String) isManualObj));
                } else {
                    lead.setIsManual(false);
                }
                
                Object isDiscardedObj = row.get("is_discarded");
                if (isDiscardedObj instanceof Boolean) {
                    lead.setIsDiscarded((Boolean) isDiscardedObj);
                } else if (isDiscardedObj instanceof String) {
                    lead.setIsDiscarded(Boolean.parseBoolean((String) isDiscardedObj));
                } else {
                    lead.setIsDiscarded(false);
                }
                
                lead.setLeadPhone((String) row.get("lead_phone"));
                lead.setLeadFirstName((String) row.get("lead_first_name"));
                lead.setLeadLastName((String) row.get("lead_last_name"));
                
                lead.setDriverFullName((String) row.get("driver_full_name"));
                lead.setDriverPhone((String) row.get("driver_phone"));
                
                
                Object totalTripsObj = row.get("total_trips_14d");
                if (totalTripsObj != null && totalTripsObj instanceof Number) {
                    lead.setTotalTrips14d(((Number) totalTripsObj).intValue());
                } else {
                    lead.setTotalTrips14d(0);
                }
                
                Object sumWorkTimeObj = row.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null && sumWorkTimeObj instanceof Number) {
                    lead.setSumWorkTimeSeconds(((Number) sumWorkTimeObj).longValue());
                }
                
                if (lead.getTotalTrips14d() != null && lead.getTotalTrips14d() > 0) {
                    lead.setDriverStatus("activo_con_viajes");
                } else if (lead.getSumWorkTimeSeconds() != null && lead.getSumWorkTimeSeconds() > 0) {
                    lead.setDriverStatus("conecto_sin_viajes");
                } else if (lead.getDriverId() != null && !lead.getDriverId().isEmpty()) {
                    lead.setDriverStatus("solo_registro");
                } else {
                    lead.setDriverStatus(null);
                }
                
                if (driverStatus != null && !driverStatus.equals("all") && lead.getDriverStatus() != null) {
                    if (!lead.getDriverStatus().equals(driverStatus)) {
                        continue;
                    }
                }
                
                if (lead.getDriverId() != null && !lead.getDriverId().isEmpty()) {
                    driverIds.add(lead.getDriverId());
                }
                
                leads.add(lead);
            }
            
            Map<String, List<MilestoneInstance>> milestonesByDriver = new HashMap<>();
            if (!driverIds.isEmpty()) {
                List<MilestoneInstance> allMilestones = milestoneInstanceRepository.findByDriverIdIn(driverIds);
                for (MilestoneInstance mi : allMilestones) {
                    milestonesByDriver.computeIfAbsent(mi.getDriverId(), k -> new ArrayList<>()).add(mi);
                }
            }
            
            for (LeadCabinetDTO lead : leads) {
                if (lead.getDriverId() != null && milestonesByDriver.containsKey(lead.getDriverId())) {
                    List<MilestoneInstance> driverMilestones = milestonesByDriver.get(lead.getDriverId());
                    List<MilestoneInstanceDTO> milestoneDTOs = new ArrayList<>();
                    
                    for (MilestoneInstance mi : driverMilestones) {
                        if (milestoneType != null && !mi.getMilestoneType().equals(milestoneType)) {
                            continue;
                        }
                        if (milestonePeriod != null && !mi.getPeriodDays().equals(milestonePeriod)) {
                            continue;
                        }
                        
                        MilestoneInstanceDTO dto = new MilestoneInstanceDTO();
                        dto.setId(mi.getId());
                        dto.setDriverId(mi.getDriverId());
                        dto.setParkId(mi.getParkId());
                        dto.setMilestoneType(mi.getMilestoneType());
                        dto.setPeriodDays(mi.getPeriodDays());
                        dto.setFulfillmentDate(mi.getFulfillmentDate());
                        dto.setCalculationDate(mi.getCalculationDate());
                        dto.setTripCount(mi.getTripCount());
                        dto.setTripDetails(null);
                        milestoneDTOs.add(dto);
                    }
                    
                    lead.setMilestones(milestoneDTOs);
                } else {
                    lead.setMilestones(new ArrayList<>());
                }
                
                if (milestoneType != null || milestonePeriod != null) {
                    if (lead.getMilestones().isEmpty() && lead.getDriverId() != null) {
                        continue;
                    }
                }
            }
            
            leads.removeIf(lead -> {
                if (milestoneType != null || milestonePeriod != null) {
                    return lead.getMilestones().isEmpty() && lead.getDriverId() != null;
                }
                return false;
            });
            
            cargarTransaccionesYango14dParaLeads(leads);
            
            Collections.sort(leads, (lead1, lead2) -> {
                boolean hasMilestones1 = lead1.getMilestones() != null && !lead1.getMilestones().isEmpty();
                boolean hasMilestones2 = lead2.getMilestones() != null && !lead2.getMilestones().isEmpty();
                
                if (hasMilestones1 && !hasMilestones2) {
                    return -1;
                }
                if (!hasMilestones1 && hasMilestones2) {
                    return 1;
                }
                
                LocalDate date1 = lead1.getLeadCreatedAt();
                LocalDate date2 = lead2.getLeadCreatedAt();
                
                if (date1 != null && date2 != null) {
                    int dateCompare = date2.compareTo(date1);
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                } else if (date1 != null) {
                    return -1;
                } else if (date2 != null) {
                    return 1;
                }
                
                String extId1 = lead1.getExternalId();
                String extId2 = lead2.getExternalId();
                if (extId1 != null && extId2 != null) {
                    return extId1.compareTo(extId2);
                }
                return 0;
            });
            
            logger.info("Retornando {} leads para cabinet después de aplicar filtros", leads.size());
            return leads;
            
        } catch (Exception e) {
            logger.error("Error al obtener leads para cabinet", e);
            logger.error("SQL ejecutado: {}", sql.toString());
            logger.error("Parámetros: {}", params);
            e.printStackTrace();
            throw new RuntimeException("Error al obtener leads para cabinet: " + e.getMessage(), e);
        }
    }
    
    private void cargarTransaccionesYango14dParaLeads(List<LeadCabinetDTO> leads) {
        if (leads == null || leads.isEmpty()) {
            return;
        }
        
        try {
            List<String> driverIds = leads.stream()
                .map(LeadCabinetDTO::getDriverId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
            
            if (driverIds.isEmpty()) {
                for (LeadCabinetDTO lead : leads) {
                    lead.setYangoTransactions14d(new ArrayList<>());
                }
                return;
            }
            
            Map<String, LocalDate> driverHireDates = new HashMap<>();
            for (LeadCabinetDTO lead : leads) {
                if (lead.getDriverId() != null && !lead.getDriverId().isEmpty() && lead.getHireDate() != null) {
                    driverHireDates.put(lead.getDriverId(), lead.getHireDate());
                }
            }
            
            if (driverHireDates.isEmpty()) {
                for (LeadCabinetDTO lead : leads) {
                    lead.setYangoTransactions14d(new ArrayList<>());
                }
                return;
            }
            
            LocalDate minHireDate = driverHireDates.values().stream()
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
            LocalDate maxEndDate = driverHireDates.values().stream()
                .map(date -> date.plusDays(14))
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
            
            Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> transaccionesPorDriver = 
                yangoTransactionService.obtenerTransaccionesMatcheadasPorDriver(
                    driverIds, minHireDate, maxEndDate
                );
            
            for (LeadCabinetDTO lead : leads) {
                if (lead.getDriverId() == null || lead.getDriverId().isEmpty() || lead.getHireDate() == null) {
                    lead.setYangoTransactions14d(new ArrayList<>());
                    continue;
                }
                
                LocalDate fechaDesde = lead.getHireDate();
                LocalDate fechaHasta = lead.getHireDate().plusDays(14);
                
                List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO> transaccionesDriver = 
                    transaccionesPorDriver.getOrDefault(lead.getDriverId(), new ArrayList<>());
                
                List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO> transacciones14d = 
                    transaccionesDriver.stream()
                        .filter(t -> {
                            if (t.getTransactionDate() == null) return false;
                            LocalDate transactionDate = t.getTransactionDate().toLocalDate();
                            return !transactionDate.isBefore(fechaDesde) && !transactionDate.isAfter(fechaHasta);
                        })
                        .collect(Collectors.toList());
                
                lead.setYangoTransactions14d(transacciones14d);
            }
            
            logger.debug("Transacciones Yango cargadas para {} leads con drivers", 
                leads.stream().filter(l -> l.getYangoTransactions14d() != null && !l.getYangoTransactions14d().isEmpty()).count());
            
        } catch (Exception e) {
            logger.error("Error al cargar transacciones Yango para leads", e);
            for (LeadCabinetDTO lead : leads) {
                if (lead.getYangoTransactions14d() == null) {
                    lead.setYangoTransactions14d(new ArrayList<>());
                }
            }
        }
    }
}

