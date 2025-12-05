package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.ScoutAffiliationControlDTO;
import com.yego.contractortracker.dto.ScoutAffiliationControlFiltersDTO;
import com.yego.contractortracker.dto.ScoutRegistrationDTO;
import com.yego.contractortracker.util.WeekISOUtil;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.entity.Scout;
import com.yego.contractortracker.entity.ScoutRegistration;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.repository.LeadMatchRepository;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.repository.ScoutRegistrationRepository;
import com.yego.contractortracker.repository.ScoutRepository;
import com.yego.contractortracker.repository.YangoTransactionRepository;
import com.yego.contractortracker.entity.LeadMatch;
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
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScoutRegistrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutRegistrationService.class);
    private static final int DAYS_BEFORE_REGISTRATION = 3;
    private static final int DAYS_AFTER_REGISTRATION = 7;
    
    @Autowired
    private ScoutRegistrationRepository registrationRepository;
    
    @Autowired
    private ScoutRepository scoutRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private YangoTransactionRepository yangoTransactionRepository;
    
    @Autowired
    private ScoutService scoutService;
    
    @Autowired
    private LeadMatchRepository leadMatchRepository;
    
    @Transactional
    public Map<String, Object> procesarArchivoCSV(MultipartFile file) {
        logger.info("Iniciando procesamiento de archivo CSV de registros de scouts: {}", file.getOriginalFilename());
        
        try {
            List<ScoutRegistration> registros = leerCSV(file);
            logger.info("Leídos {} registros del CSV", registros.size());
            
            if (registros.isEmpty()) {
                return crearResultado(0, 0, 0, "No se encontraron registros en el archivo", null, null);
            }
            
            int duplicados = 0;
            List<ScoutRegistration> registrosParaProcesar = new ArrayList<>();
            
            for (ScoutRegistration registro : registros) {
                List<ScoutRegistration> existentes = registrationRepository.findByScoutIdAndRegistrationDateBetween(
                    registro.getScoutId(),
                    registro.getRegistrationDate(),
                    registro.getRegistrationDate()
                );
                
                Optional<ScoutRegistration> existente = existentes.stream()
                    .filter(e -> Objects.equals(e.getDriverPhone(), registro.getDriverPhone()) &&
                                 Objects.equals(e.getDriverName(), registro.getDriverName()) &&
                                 Objects.equals(e.getRegistrationDate(), registro.getRegistrationDate()))
                    .findFirst();
                
                if (existente.isPresent()) {
                    ScoutRegistration regExistente = existente.get();
                    regExistente.setDriverLicense(registro.getDriverLicense());
                    regExistente.setAcquisitionMedium(registro.getAcquisitionMedium());
                    registrosParaProcesar.add(regExistente);
                    duplicados++;
                } else {
                    registrosParaProcesar.add(registro);
                }
            }
            
            logger.info("Registros del CSV: {}, Duplicados (actualizados): {}, Nuevos: {}", 
                registros.size(), duplicados, registrosParaProcesar.size() - duplicados);
            
            registros = registrosParaProcesar;
            
            hacerMatchConDrivers(registros);
            hacerMatchConLeads(registros);
            
            for (ScoutRegistration registro : registros) {
                if (registro.getId() != null) {
                    registrationRepository.save(registro);
                } else {
                    registrationRepository.save(registro);
                }
            }
            
            int matchedCount = (int) registros.stream().filter(ScoutRegistration::getIsMatched).count();
            int unmatchedCount = registros.size() - matchedCount;
            
            LocalDate minDateRegistro = registros.stream()
                .map(ScoutRegistration::getRegistrationDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
            
            LocalDate maxDateRegistro = registros.stream()
                .map(ScoutRegistration::getRegistrationDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
            
            logger.info("Procesamiento completado. Matched: {}, Unmatched: {}", matchedCount, unmatchedCount);
            
            return crearResultado(registros.size(), matchedCount, unmatchedCount, 
                String.format("Procesados %d registros: %d matcheados, %d sin match", 
                    registros.size(), matchedCount, unmatchedCount),
                minDateRegistro, maxDateRegistro);
            
        } catch (Exception e) {
            logger.error("Error al procesar archivo CSV", e);
            throw new RuntimeException("Error al procesar archivo CSV: " + e.getMessage(), e);
        }
    }
    
    private List<ScoutRegistration> leerCSV(MultipartFile file) throws Exception {
        List<ScoutRegistration> registros = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return registros;
            }
            
            String[] headers = headerLine.split(",");
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }
            
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] values = parseCSVLine(line);
                    if (values.length < headerMap.size()) {
                        logger.warn("Línea {} tiene menos columnas de las esperadas, omitiendo", lineNumber);
                        continue;
                    }
                    
                    ScoutRegistration registro = new ScoutRegistration();
                    
                    String fechaStr = getValue(values, headerMap, "fecha");
                    if (fechaStr != null && !fechaStr.isEmpty()) {
                        LocalDate fecha = parsearFecha(fechaStr);
                        if (fecha == null) {
                            logger.warn("No se pudo parsear fecha en línea {}: {}", lineNumber, fechaStr);
                            continue;
                        }
                        registro.setRegistrationDate(fecha);
                    } else {
                        logger.warn("Fecha vacía en línea {}, omitiendo", lineNumber);
                        continue;
                    }
                    
                    String scoutNombre = getValue(values, headerMap, "scout");
                    if (scoutNombre == null || scoutNombre.trim().isEmpty()) {
                        logger.warn("Scout vacío en línea {}, omitiendo", lineNumber);
                        continue;
                    }
                    
                    Scout scout = obtenerOCrearScout(scoutNombre);
                    registro.setScoutId(scout.getScoutId());
                    
                    registro.setDriverLicense(getValue(values, headerMap, "licencia del conductor"));
                    registro.setDriverName(getValue(values, headerMap, "nombre del conductor"));
                    registro.setDriverPhone(getValue(values, headerMap, "telefono del conductor"));
                    registro.setAcquisitionMedium(getValue(values, headerMap, "medio de  adquisición"));
                    
                    registros.add(registro);
                    
                } catch (Exception e) {
                    logger.warn("Error al procesar línea {}: {}", lineNumber, e.getMessage());
                }
            }
        }
        
        return registros;
    }
    
    private LocalDate parsearFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return null;
        }
        
        fechaStr = fechaStr.trim();
        
        if (fechaStr.toLowerCase().startsWith("jueves") || 
            fechaStr.toLowerCase().startsWith("lunes") ||
            fechaStr.toLowerCase().startsWith("martes") ||
            fechaStr.toLowerCase().startsWith("miércoles") ||
            fechaStr.toLowerCase().startsWith("miercoles") ||
            fechaStr.toLowerCase().startsWith("viernes") ||
            fechaStr.toLowerCase().startsWith("sábado") ||
            fechaStr.toLowerCase().startsWith("sabado") ||
            fechaStr.toLowerCase().startsWith("domingo")) {
            String[] partes = fechaStr.split("\\s+");
            if (partes.length >= 2) {
                try {
                    int dia = Integer.parseInt(partes[partes.length - 1]);
                    int mes = 11;
                    int año = 2025;
                    return LocalDate.of(año, mes, dia);
                } catch (Exception e) {
                    logger.warn("No se pudo parsear fecha con día de semana: {}", fechaStr);
                }
            }
        }
        
        try {
            if (fechaStr.contains("/")) {
                String[] partes = fechaStr.split("/");
                if (partes.length == 3) {
                    int dia = Integer.parseInt(partes[0]);
                    int mes = Integer.parseInt(partes[1]);
                    int año = Integer.parseInt(partes[2]);
                    if (año < 100) {
                        año += 2000;
                    }
                    return LocalDate.of(año, mes, dia);
                }
            }
        } catch (Exception e) {
            logger.warn("Error al parsear fecha: {}", fechaStr);
        }
        
        return null;
    }
    
    private Scout obtenerOCrearScout(String nombre) {
        String normalizedName = normalizarNombre(nombre);
        Optional<Scout> existing = scoutRepository.findByScoutName(normalizedName);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        return scoutService.crearOActualizarScout(nombre);
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
        Integer index = headerMap.get(key.toLowerCase());
        if (index != null && index < values.length) {
            String value = values[index];
            return value != null && !value.isEmpty() ? value.trim() : null;
        }
        return null;
    }
    
    private void hacerMatchConDrivers(List<ScoutRegistration> registros) {
        LocalDate minDateRegistro = registros.stream()
            .map(ScoutRegistration::getRegistrationDate)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now().minusDays(DAYS_AFTER_REGISTRATION));
        
        LocalDate maxDateRegistro = registros.stream()
            .map(ScoutRegistration::getRegistrationDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now().plusDays(DAYS_AFTER_REGISTRATION));
        
        LocalDate fechaDesde = minDateRegistro.minusDays(DAYS_BEFORE_REGISTRATION);
        LocalDate fechaHasta = maxDateRegistro.plusDays(DAYS_AFTER_REGISTRATION);
        
        logger.info("Rango de fechas de registros: {} a {}", minDateRegistro, maxDateRegistro);
        logger.info("Buscando drivers con hire_date entre {} y {} (margen: {} días antes, {} días después)", 
            fechaDesde, fechaHasta, DAYS_BEFORE_REGISTRATION, DAYS_AFTER_REGISTRATION);
        
        List<Map<String, Object>> drivers = buscarDriversEnRango(fechaDesde, fechaHasta);
        logger.info("Encontrados {} drivers en rango ampliado", drivers.size());
        
        DriverIndex driverIndex = crearIndiceDrivers(drivers);
        
        int matchedCount = 0;
        for (ScoutRegistration registro : registros) {
            Optional<Map<String, Object>> mejorMatch = encontrarMejorMatch(registro, driverIndex);
            
            if (mejorMatch.isPresent()) {
                Map<String, Object> match = mejorMatch.get();
                String driverId = (String) match.get("driver_id");
                Double score = (Double) match.get("score");
                LocalDate driverHireDate = null;
                Object hireDateObj = match.get("hire_date");
                if (hireDateObj != null) {
                    if (hireDateObj instanceof Date) {
                        driverHireDate = ((Date) hireDateObj).toLocalDate();
                    } else if (hireDateObj instanceof LocalDate) {
                        driverHireDate = (LocalDate) hireDateObj;
                    } else if (hireDateObj instanceof java.util.Date) {
                        driverHireDate = ((java.util.Date) hireDateObj).toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                    }
                }
                
                if (driverHireDate != null && registro.getRegistrationDate() != null) {
                    long diasDiferencia = java.time.temporal.ChronoUnit.DAYS.between(driverHireDate, registro.getRegistrationDate());
                    if (diasDiferencia >= -DAYS_BEFORE_REGISTRATION && diasDiferencia <= DAYS_AFTER_REGISTRATION) {
                        registro.setDriverId(driverId);
                        registro.setMatchScore(score);
                        registro.setIsMatched(true);
                        registro.setMatchSource("scout_registration");
                        registro.setReconciliationStatus("pending");
                        registro.setIsReconciled(false);
                        matchedCount++;
                        logger.debug("Match encontrado: registro fecha {}, driver hire_date {}, diferencia {} días", 
                            registro.getRegistrationDate(), driverHireDate, diasDiferencia);
                    } else {
                        registro.setIsMatched(false);
                        registro.setMatchScore(0.0);
                        logger.debug("Match descartado por fecha: registro fecha {}, driver hire_date {}, diferencia {} días (fuera de rango)", 
                            registro.getRegistrationDate(), driverHireDate, diasDiferencia);
                    }
                } else {
                    registro.setDriverId(driverId);
                    registro.setMatchScore(score);
                    registro.setIsMatched(true);
                    registro.setMatchSource("scout_registration");
                    registro.setReconciliationStatus("pending");
                    registro.setIsReconciled(false);
                    matchedCount++;
                }
            } else {
                registro.setIsMatched(false);
                registro.setMatchScore(0.0);
            }
        }
        
        logger.info("Matching completado: {} de {} registros matcheados", matchedCount, registros.size());
    }
    
    private void hacerMatchConLeads(List<ScoutRegistration> registros) {
        logger.info("Iniciando matching de {} registros de scouts con leads", registros.size());
        
        LocalDate minDateRegistro = registros.stream()
            .map(ScoutRegistration::getRegistrationDate)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now().minusDays(3));
        
        LocalDate maxDateRegistro = registros.stream()
            .map(ScoutRegistration::getRegistrationDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now().plusDays(3));
        
        LocalDate fechaDesde = minDateRegistro.minusDays(3);
        LocalDate fechaHasta = maxDateRegistro.plusDays(3);
        
        logger.info("Buscando leads con lead_created_at entre {} y {} para matching con registros de scouts", 
            fechaDesde, fechaHasta);
        
        List<LeadMatch> leads = leadMatchRepository.findByLeadCreatedAtBetween(fechaDesde, fechaHasta);
        logger.info("Encontrados {} leads en rango de fechas", leads.size());
        
        if (leads.isEmpty()) {
            logger.info("No se encontraron leads para matching");
            return;
        }
        
        Map<String, List<LeadMatch>> leadsByName = new HashMap<>();
        for (LeadMatch lead : leads) {
            String leadName = construirNombreCompleto(lead.getLeadFirstName(), lead.getLeadLastName());
            if (leadName != null && !leadName.trim().isEmpty()) {
                String normalizedName = normalizarNombre(leadName);
                leadsByName.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(lead);
            }
        }
        
        int matchedCount = 0;
        for (ScoutRegistration registro : registros) {
            if (registro.getDriverName() == null || registro.getDriverName().trim().isEmpty()) {
                continue;
            }
            
            if (registro.getRegistrationDate() == null) {
                continue;
            }
            
            String registroName = normalizarNombre(registro.getDriverName());
            Optional<LeadMatch> mejorMatch = encontrarMejorMatchConLead(registro, registroName, leadsByName);
            
            if (mejorMatch.isPresent()) {
                LeadMatch lead = mejorMatch.get();
                lead.setScoutRegistrationId(registro.getId());
                lead.setScoutMatchScore(calcularScoreMatch(registro, lead));
                lead.setScoutMatchDate(LocalDateTime.now());
                leadMatchRepository.save(lead);
                matchedCount++;
                logger.debug("Match encontrado: registro scout {} con lead {}", 
                    registro.getId(), lead.getExternalId());
            }
        }
        
        logger.info("Matching con leads completado: {} de {} registros matcheados con leads", 
            matchedCount, registros.size());
    }
    
    private Optional<LeadMatch> encontrarMejorMatchConLead(ScoutRegistration registro, 
                                                           String registroName, 
                                                           Map<String, List<LeadMatch>> leadsByName) {
        Optional<LeadMatch> mejorMatch = Optional.empty();
        double mejorScore = 0.0;
        
        List<LeadMatch> candidatos = new ArrayList<>();
        
        List<LeadMatch> exactos = leadsByName.getOrDefault(registroName, new ArrayList<>());
        candidatos.addAll(exactos);
        
        if (candidatos.isEmpty()) {
            for (Map.Entry<String, List<LeadMatch>> entry : leadsByName.entrySet()) {
                double similitud = calcularSimilitudNombre(registroName, entry.getKey());
                if (similitud >= 0.7) {
                    candidatos.addAll(entry.getValue());
                }
            }
        }
        
        for (LeadMatch lead : candidatos) {
            if (lead.getLeadCreatedAt() == null) {
                continue;
            }
            
            long diasDiferencia = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                registro.getRegistrationDate(), lead.getLeadCreatedAt()));
            
            if (diasDiferencia > 3) {
                continue;
            }
            
            String leadName = construirNombreCompleto(lead.getLeadFirstName(), lead.getLeadLastName());
            String normalizedLeadName = normalizarNombre(leadName);
            
            double scoreNombre = 0.0;
            if (registroName.equals(normalizedLeadName)) {
                scoreNombre = 1.0;
            } else {
                scoreNombre = calcularSimilitudNombre(registroName, normalizedLeadName);
            }
            
            double scoreFecha = 1.0 - (diasDiferencia / 3.0);
            double scoreTotal = (scoreNombre * 0.7) + (scoreFecha * 0.3);
            
            if (scoreTotal > mejorScore && scoreTotal >= 0.7) {
                mejorScore = scoreTotal;
                mejorMatch = Optional.of(lead);
            }
        }
        
        return mejorMatch;
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
    
    private double calcularScoreMatch(ScoutRegistration registro, LeadMatch lead) {
        String registroName = normalizarNombre(registro.getDriverName());
        String leadName = construirNombreCompleto(lead.getLeadFirstName(), lead.getLeadLastName());
        String normalizedLeadName = normalizarNombre(leadName);
        
        double scoreNombre = 0.0;
        if (registroName.equals(normalizedLeadName)) {
            scoreNombre = 1.0;
        } else {
            scoreNombre = calcularSimilitudNombre(registroName, normalizedLeadName);
        }
        
        long diasDiferencia = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
            registro.getRegistrationDate(), lead.getLeadCreatedAt()));
        double scoreFecha = 1.0 - (diasDiferencia / 3.0);
        
        return (scoreNombre * 0.7) + (scoreFecha * 0.3);
    }
    
    private List<Map<String, Object>> buscarDriversEnRango(LocalDate fechaDesde, LocalDate fechaHasta) {
        String sql = "SELECT driver_id, full_name, phone, license_number, hire_date " +
                     "FROM drivers " +
                     "WHERE hire_date BETWEEN ? AND ? " +
                     "ORDER BY hire_date";
        
        return jdbcTemplate.queryForList(sql, fechaDesde, fechaHasta);
    }
    
    private static class DriverIndex {
        Map<String, List<Map<String, Object>>> byPhone = new HashMap<>();
        Map<String, List<Map<String, Object>>> byLicense = new HashMap<>();
        Map<String, List<Map<String, Object>>> byName = new HashMap<>();
    }
    
    private DriverIndex crearIndiceDrivers(List<Map<String, Object>> drivers) {
        DriverIndex index = new DriverIndex();
        
        for (Map<String, Object> driver : drivers) {
            String phone = (String) driver.get("phone");
            String license = (String) driver.get("license_number");
            String fullName = (String) driver.get("full_name");
            
            if (phone != null && !phone.isEmpty()) {
                String normalizedPhone = normalizarTelefono(phone);
                index.byPhone.computeIfAbsent(normalizedPhone, k -> new ArrayList<>()).add(driver);
            }
            
            if (license != null && !license.isEmpty()) {
                String normalizedLicense = license.trim().toUpperCase();
                index.byLicense.computeIfAbsent(normalizedLicense, k -> new ArrayList<>()).add(driver);
            }
            
            if (fullName != null && !fullName.isEmpty()) {
                String normalizedName = normalizarNombre(fullName);
                index.byName.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(driver);
            }
        }
        
        return index;
    }
    
    private String normalizarTelefono(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }
    
    private Optional<Map<String, Object>> encontrarMejorMatch(ScoutRegistration registro, DriverIndex index) {
        String registroPhone = registro.getDriverPhone() != null ? normalizarTelefono(registro.getDriverPhone()) : null;
        String registroLicense = registro.getDriverLicense() != null ? registro.getDriverLicense().trim().toUpperCase() : null;
        String registroName = registro.getDriverName() != null ? normalizarNombre(registro.getDriverName()) : null;
        
        List<Map<String, Object>> candidatos = new ArrayList<>();
        
        if (registroPhone != null && !registroPhone.isEmpty()) {
            List<Map<String, Object>> porTelefono = index.byPhone.get(registroPhone);
            if (porTelefono != null) {
                candidatos.addAll(porTelefono);
            }
        }
        
        if (registroLicense != null && !registroLicense.isEmpty()) {
            List<Map<String, Object>> porLicencia = index.byLicense.get(registroLicense);
            if (porLicencia != null) {
                candidatos.addAll(porLicencia);
            }
        }
        
        if (registroName != null && !registroName.isEmpty()) {
            List<Map<String, Object>> porNombre = index.byName.get(registroName);
            if (porNombre != null) {
                candidatos.addAll(porNombre);
            }
        }
        
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        
        Map<String, Object> mejorMatch = null;
        double mejorScore = 0.0;
        
        for (Map<String, Object> candidato : candidatos) {
            double score = calcularScore(registro, candidato);
            if (score > mejorScore) {
                mejorScore = score;
                mejorMatch = candidato;
            }
        }
        
        if (mejorMatch != null && mejorScore >= 0.3) {
            mejorMatch.put("score", mejorScore);
            return Optional.of(mejorMatch);
        }
        
        return Optional.empty();
    }
    
    private double calcularScore(ScoutRegistration registro, Map<String, Object> driver) {
        double score = 0.0;
        int matches = 0;
        
        String registroPhone = registro.getDriverPhone() != null ? normalizarTelefono(registro.getDriverPhone()) : null;
        String driverPhone = driver.get("phone") != null ? normalizarTelefono((String) driver.get("phone")) : null;
        
        if (registroPhone != null && driverPhone != null && !registroPhone.isEmpty() && registroPhone.equals(driverPhone)) {
            score += 0.4;
            matches++;
        }
        
        String registroLicense = registro.getDriverLicense() != null ? registro.getDriverLicense().trim().toUpperCase() : null;
        String driverLicense = driver.get("license_number") != null ? ((String) driver.get("license_number")).trim().toUpperCase() : null;
        
        if (registroLicense != null && driverLicense != null && !registroLicense.isEmpty() && registroLicense.equals(driverLicense)) {
            score += 0.4;
            matches++;
        }
        
        String registroName = registro.getDriverName() != null ? normalizarNombre(registro.getDriverName()) : null;
        String driverName = driver.get("full_name") != null ? normalizarNombre((String) driver.get("full_name")) : null;
        
        if (registroName != null && driverName != null && !registroName.isEmpty()) {
            if (registroName.equals(driverName)) {
                score += 0.3;
                matches++;
            } else {
                double similitud = calcularSimilitudNombre(registroName, driverName);
                if (similitud > 0.8) {
                    score += 0.25;
                    matches++;
                } else if (similitud > 0.6) {
                    score += 0.15;
                }
            }
        }
        
        if (matches >= 2) {
            score = Math.min(score * 1.2, 1.0);
        }
        
        return Math.min(score, 1.0);
    }
    
    private double calcularSimilitudNombre(String nombre1, String nombre2) {
        if (nombre1 == null || nombre2 == null) {
            return 0.0;
        }
        
        String[] palabras1 = nombre1.split("\\s+");
        String[] palabras2 = nombre2.split("\\s+");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(palabras1));
        Set<String> set2 = new HashSet<>(Arrays.asList(palabras2));
        
        Set<String> interseccion = new HashSet<>(set1);
        interseccion.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) interseccion.size() / union.size();
    }
    
    public List<ScoutRegistrationDTO> obtenerRegistrosPorScout(String scoutId, LocalDate fechaInicio, LocalDate fechaFin) {
        List<ScoutRegistration> registros;
        
        if (fechaInicio != null && fechaFin != null) {
            registros = registrationRepository.findByScoutIdAndRegistrationDateBetween(scoutId, fechaInicio, fechaFin);
        } else {
            registros = registrationRepository.findByScoutId(scoutId);
        }
        
        return registros.stream().map(this::convertirADTO).collect(Collectors.toList());
    }
    
    public List<ScoutRegistrationDTO> obtenerRegistrosSinMatch() {
        List<ScoutRegistration> registros = registrationRepository.findByIsMatched(false);
        return registros.stream().map(this::convertirADTO).collect(Collectors.toList());
    }
    
    @Transactional
    public void asignarMatchManual(Long registrationId, String driverId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId no puede ser null");
        }
        if (driverId == null || driverId.isEmpty()) {
            throw new IllegalArgumentException("driverId no puede ser null o vacío");
        }
        
        Optional<ScoutRegistration> registroOpt = registrationRepository.findById(registrationId);
        if (!registroOpt.isPresent()) {
            throw new IllegalArgumentException("Registro de scout no encontrado: " + registrationId);
        }
        
        ScoutRegistration registro = registroOpt.get();
        registro.setDriverId(driverId);
        registro.setIsMatched(true);
        registro.setMatchScore(1.0);
        registro.setLastUpdated(LocalDateTime.now());
        
        registrationRepository.save(registro);
        logger.info("Match manual asignado: registro {} -> driver {}", registrationId, driverId);
    }
    
    public List<ScoutAffiliationControlDTO> obtenerControlAfiliaciones(String scoutId, LocalDate fechaInicio, LocalDate fechaFin) {
        ScoutAffiliationControlFiltersDTO filters = new ScoutAffiliationControlFiltersDTO();
        filters.setScoutId(scoutId);
        filters.setFechaInicio(fechaInicio);
        filters.setFechaFin(fechaFin);
        return obtenerControlAfiliaciones(filters);
    }
    
    public List<ScoutAffiliationControlDTO> obtenerControlAfiliaciones(ScoutAffiliationControlFiltersDTO filters) {
        // Convertir semana ISO a fechas si está presente
        LocalDate fechaInicio = filters.getFechaInicio();
        LocalDate fechaFin = filters.getFechaFin();
        
        if (filters.getWeekISO() != null && !filters.getWeekISO().isEmpty()) {
            LocalDate[] weekRange = WeekISOUtil.getWeekRange(filters.getWeekISO());
            if (weekRange != null && weekRange.length == 2) {
                fechaInicio = weekRange[0];
                fechaFin = weekRange[1];
            }
        }
        
        // Construir query SQL dinámica
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT sr.* ");
        sql.append("FROM scout_registrations sr ");
        sql.append("LEFT JOIN milestone_instances mi ON sr.driver_id = mi.driver_id AND mi.period_days = 7 ");
        sql.append("LEFT JOIN yango_transactions yt ON sr.scout_id = yt.scout_id AND sr.driver_id = yt.driver_id AND mi.milestone_type = yt.milestone_type AND yt.is_matched = true ");
        sql.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        // Filtro por scout
        if (filters.getScoutId() != null && !filters.getScoutId().isEmpty()) {
            sql.append("AND sr.scout_id = ? ");
            params.add(filters.getScoutId());
        }
        
        // Filtro por fechas
        if (fechaInicio != null) {
            sql.append("AND sr.registration_date >= ? ");
            params.add(fechaInicio);
        }
        if (fechaFin != null) {
            sql.append("AND sr.registration_date <= ? ");
            params.add(fechaFin);
        }
        
        // Filtro por match status
        if (filters.getIsMatched() != null) {
            sql.append("AND sr.is_matched = ? ");
            params.add(filters.getIsMatched());
        }
        
        // Filtro por medio de adquisición
        if (filters.getAcquisitionMedium() != null && !filters.getAcquisitionMedium().isEmpty()) {
            sql.append("AND sr.acquisition_medium = ? ");
            params.add(filters.getAcquisitionMedium());
        }
        
        // Búsqueda por nombre
        if (filters.getDriverName() != null && !filters.getDriverName().isEmpty()) {
            sql.append("AND LOWER(sr.driver_name) LIKE ? ");
            params.add("%" + filters.getDriverName().toLowerCase() + "%");
        }
        
        // Búsqueda por teléfono
        if (filters.getDriverPhone() != null && !filters.getDriverPhone().isEmpty()) {
            sql.append("AND sr.driver_phone LIKE ? ");
            params.add("%" + filters.getDriverPhone() + "%");
        }
        
        // Filtro por milestone type (requiere subquery o JOIN adicional)
        if (filters.getMilestoneType() != null) {
            sql.append("AND EXISTS (");
            sql.append("  SELECT 1 FROM milestone_instances mi2 ");
            sql.append("  WHERE mi2.driver_id = sr.driver_id ");
            sql.append("  AND mi2.period_days = 7 ");
            sql.append("  AND mi2.milestone_type = ? ");
            sql.append("  AND mi2.fulfillment_date <= sr.registration_date + INTERVAL '7 days' ");
            sql.append(") ");
            params.add(filters.getMilestoneType());
        }
        
        // Filtro por pago Yango
        if (filters.getHasYangoPayment() != null) {
            if (filters.getHasYangoPayment()) {
                sql.append("AND EXISTS (");
                sql.append("  SELECT 1 FROM yango_transactions yt2 ");
                sql.append("  WHERE yt2.scout_id = sr.scout_id ");
                sql.append("  AND yt2.driver_id = sr.driver_id ");
                sql.append("  AND yt2.is_matched = true ");
                sql.append("  AND yt2.milestone_type = (");
                sql.append("    SELECT mi3.milestone_type FROM milestone_instances mi3 ");
                sql.append("    WHERE mi3.driver_id = sr.driver_id ");
                sql.append("    AND mi3.period_days = 7 ");
                sql.append("    AND mi3.fulfillment_date <= sr.registration_date + INTERVAL '7 days' ");
                sql.append("    ORDER BY mi3.milestone_type DESC LIMIT 1");
                sql.append("  ) ");
                sql.append(") ");
            } else {
                sql.append("AND NOT EXISTS (");
                sql.append("  SELECT 1 FROM yango_transactions yt2 ");
                sql.append("  WHERE yt2.scout_id = sr.scout_id ");
                sql.append("  AND yt2.driver_id = sr.driver_id ");
                sql.append("  AND yt2.is_matched = true ");
                sql.append(") ");
            }
        }
        
        // Filtro por rango de montos
        if (filters.getAmountMin() != null || filters.getAmountMax() != null) {
            sql.append("AND EXISTS (");
            sql.append("  SELECT 1 FROM yango_transactions yt3 ");
            sql.append("  WHERE yt3.scout_id = sr.scout_id ");
            sql.append("  AND yt3.driver_id = sr.driver_id ");
            sql.append("  AND yt3.is_matched = true ");
            if (filters.getAmountMin() != null) {
                sql.append("  AND yt3.amount_yango >= ? ");
                params.add(filters.getAmountMin());
            }
            if (filters.getAmountMax() != null) {
                sql.append("  AND yt3.amount_yango <= ? ");
                params.add(filters.getAmountMax());
            }
            sql.append(") ");
        }
        
        sql.append("ORDER BY sr.registration_date DESC, sr.id");
        
        logger.debug("Query SQL: {}", sql.toString());
        logger.debug("Parámetros: {}", params);
        
        // Ejecutar query y mapear resultados
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        
        List<ScoutAffiliationControlDTO> resultado = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            ScoutRegistration registro = registrationRepository.findById(id).orElse(null);
            
            if (registro == null) {
                continue;
            }
            
            ScoutAffiliationControlDTO dto = new ScoutAffiliationControlDTO();
            dto.setRegistrationId(registro.getId());
            dto.setScoutId(registro.getScoutId());
            
            Optional<Scout> scout = scoutRepository.findById(registro.getScoutId());
            if (scout.isPresent()) {
                dto.setScoutName(scout.get().getScoutName());
            }
            
            dto.setRegistrationDate(registro.getRegistrationDate());
            dto.setDriverLicense(registro.getDriverLicense());
            dto.setDriverName(registro.getDriverName());
            dto.setDriverPhone(registro.getDriverPhone());
            dto.setAcquisitionMedium(registro.getAcquisitionMedium());
            dto.setDriverId(registro.getDriverId());
            dto.setIsMatched(registro.getIsMatched());
            dto.setMatchScore(registro.getMatchScore());
            
            if (registro.getDriverId() != null && registro.getIsMatched()) {
                MilestoneInstance milestone7d = obtenerMilestoneEn7Dias(registro.getDriverId(), registro.getRegistrationDate());
                if (milestone7d != null) {
                    dto.setMilestoneType7d(milestone7d.getMilestoneType());
                    dto.setTripCount7d(milestone7d.getTripCount());
                    dto.setMilestoneFulfillmentDate7d(milestone7d.getFulfillmentDate());
                    
                    YangoTransaction transaccion = verificarPagoYango(registro.getScoutId(), registro.getDriverId(), milestone7d.getMilestoneType());
                    if (transaccion != null) {
                        dto.setHasYangoPayment(true);
                        dto.setYangoPaymentAmount(transaccion.getAmountYango());
                        dto.setYangoPaymentDate(transaccion.getTransactionDate());
                        dto.setYangoTransactionId(transaccion.getId());
                    } else {
                        dto.setHasYangoPayment(false);
                    }
                }
            }
            
            resultado.add(dto);
        }
        
        return resultado;
    }
    
    public long contarRegistrosConFiltros(ScoutAffiliationControlFiltersDTO filters) {
        // Convertir semana ISO a fechas si está presente
        LocalDate fechaInicio = filters.getFechaInicio();
        LocalDate fechaFin = filters.getFechaFin();
        
        if (filters.getWeekISO() != null && !filters.getWeekISO().isEmpty()) {
            LocalDate[] weekRange = WeekISOUtil.getWeekRange(filters.getWeekISO());
            if (weekRange != null && weekRange.length == 2) {
                fechaInicio = weekRange[0];
                fechaFin = weekRange[1];
            }
        }
        
        // Construir query SQL dinámica para COUNT
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(DISTINCT sr.id) ");
        sql.append("FROM scout_registrations sr ");
        sql.append("LEFT JOIN milestone_instances mi ON sr.driver_id = mi.driver_id AND mi.period_days = 7 ");
        sql.append("LEFT JOIN yango_transactions yt ON sr.scout_id = yt.scout_id AND sr.driver_id = yt.driver_id AND mi.milestone_type = yt.milestone_type AND yt.is_matched = true ");
        sql.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        // Aplicar los mismos filtros que en obtenerControlAfiliaciones
        if (filters.getScoutId() != null && !filters.getScoutId().isEmpty()) {
            sql.append("AND sr.scout_id = ? ");
            params.add(filters.getScoutId());
        }
        
        if (fechaInicio != null) {
            sql.append("AND sr.registration_date >= ? ");
            params.add(fechaInicio);
        }
        if (fechaFin != null) {
            sql.append("AND sr.registration_date <= ? ");
            params.add(fechaFin);
        }
        
        if (filters.getIsMatched() != null) {
            sql.append("AND sr.is_matched = ? ");
            params.add(filters.getIsMatched());
        }
        
        if (filters.getAcquisitionMedium() != null && !filters.getAcquisitionMedium().isEmpty()) {
            sql.append("AND sr.acquisition_medium = ? ");
            params.add(filters.getAcquisitionMedium());
        }
        
        if (filters.getDriverName() != null && !filters.getDriverName().isEmpty()) {
            sql.append("AND LOWER(sr.driver_name) LIKE ? ");
            params.add("%" + filters.getDriverName().toLowerCase() + "%");
        }
        
        if (filters.getDriverPhone() != null && !filters.getDriverPhone().isEmpty()) {
            sql.append("AND sr.driver_phone LIKE ? ");
            params.add("%" + filters.getDriverPhone() + "%");
        }
        
        if (filters.getMilestoneType() != null) {
            sql.append("AND EXISTS (");
            sql.append("  SELECT 1 FROM milestone_instances mi2 ");
            sql.append("  WHERE mi2.driver_id = sr.driver_id ");
            sql.append("  AND mi2.period_days = 7 ");
            sql.append("  AND mi2.milestone_type = ? ");
            sql.append("  AND mi2.fulfillment_date <= sr.registration_date + INTERVAL '7 days' ");
            sql.append(") ");
            params.add(filters.getMilestoneType());
        }
        
        if (filters.getHasYangoPayment() != null) {
            if (filters.getHasYangoPayment()) {
                sql.append("AND EXISTS (");
                sql.append("  SELECT 1 FROM yango_transactions yt2 ");
                sql.append("  WHERE yt2.scout_id = sr.scout_id ");
                sql.append("  AND yt2.driver_id = sr.driver_id ");
                sql.append("  AND yt2.is_matched = true ");
                sql.append("  AND yt2.milestone_type = (");
                sql.append("    SELECT mi3.milestone_type FROM milestone_instances mi3 ");
                sql.append("    WHERE mi3.driver_id = sr.driver_id ");
                sql.append("    AND mi3.period_days = 7 ");
                sql.append("    AND mi3.fulfillment_date <= sr.registration_date + INTERVAL '7 days' ");
                sql.append("    ORDER BY mi3.milestone_type DESC LIMIT 1");
                sql.append("  ) ");
                sql.append(") ");
            } else {
                sql.append("AND NOT EXISTS (");
                sql.append("  SELECT 1 FROM yango_transactions yt2 ");
                sql.append("  WHERE yt2.scout_id = sr.scout_id ");
                sql.append("  AND yt2.driver_id = sr.driver_id ");
                sql.append("  AND yt2.is_matched = true ");
                sql.append(") ");
            }
        }
        
        if (filters.getAmountMin() != null || filters.getAmountMax() != null) {
            sql.append("AND EXISTS (");
            sql.append("  SELECT 1 FROM yango_transactions yt3 ");
            sql.append("  WHERE yt3.scout_id = sr.scout_id ");
            sql.append("  AND yt3.driver_id = sr.driver_id ");
            sql.append("  AND yt3.is_matched = true ");
            if (filters.getAmountMin() != null) {
                sql.append("  AND yt3.amount_yango >= ? ");
                params.add(filters.getAmountMin());
            }
            if (filters.getAmountMax() != null) {
                sql.append("  AND yt3.amount_yango <= ? ");
                params.add(filters.getAmountMax());
            }
            sql.append(") ");
        }
        
        Long count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
        return count != null ? count : 0L;
    }
    
    private MilestoneInstance obtenerMilestoneEn7Dias(String driverId, LocalDate fechaAfiliacion) {
        List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverIdAndPeriodDays(driverId, 7);
        
        if (milestones.isEmpty()) {
            return null;
        }
        
        LocalDate fechaLimite = fechaAfiliacion.plusDays(7);
        
        MilestoneInstance mejorMilestone = null;
        int mejorTipo = -1;
        
        for (MilestoneInstance milestone : milestones) {
            if (milestone.getFulfillmentDate().toLocalDate().isBefore(fechaLimite) || 
                milestone.getFulfillmentDate().toLocalDate().isEqual(fechaLimite)) {
                if (milestone.getMilestoneType() > mejorTipo) {
                    mejorTipo = milestone.getMilestoneType();
                    mejorMilestone = milestone;
                }
            }
        }
        
        return mejorMilestone;
    }
    
    private YangoTransaction verificarPagoYango(String scoutId, String driverId, Integer milestoneType) {
        List<YangoTransaction> transacciones = yangoTransactionRepository.findByScoutId(scoutId);
        
        return transacciones.stream()
            .filter(t -> driverId.equals(t.getDriverId()))
            .filter(t -> Objects.equals(milestoneType, t.getMilestoneType()))
            .filter(YangoTransaction::getIsMatched)
            .findFirst()
            .orElse(null);
    }
    
    private ScoutRegistrationDTO convertirADTO(ScoutRegistration registro) {
        ScoutRegistrationDTO dto = new ScoutRegistrationDTO();
        dto.setId(registro.getId());
        dto.setScoutId(registro.getScoutId());
        
        Optional<Scout> scout = scoutRepository.findById(registro.getScoutId());
        if (scout.isPresent()) {
            dto.setScoutName(scout.get().getScoutName());
        }
        
        dto.setRegistrationDate(registro.getRegistrationDate());
        dto.setDriverLicense(registro.getDriverLicense());
        dto.setDriverName(registro.getDriverName());
        dto.setDriverPhone(registro.getDriverPhone());
        dto.setAcquisitionMedium(registro.getAcquisitionMedium());
        dto.setDriverId(registro.getDriverId());
        dto.setMatchScore(registro.getMatchScore());
        dto.setIsMatched(registro.getIsMatched());
        dto.setCreatedAt(registro.getCreatedAt());
        dto.setLastUpdated(registro.getLastUpdated());
        
        return dto;
    }
    
    private Map<String, Object> crearResultado(int total, int matched, int unmatched, String mensaje) {
        return crearResultado(total, matched, unmatched, mensaje, null, null);
    }
    
    private Map<String, Object> crearResultado(int total, int matched, int unmatched, String mensaje, LocalDate dataDateFrom, LocalDate dataDateTo) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total", total);
        resultado.put("matched", matched);
        resultado.put("unmatched", unmatched);
        resultado.put("message", mensaje);
        resultado.put("timestamp", LocalDateTime.now());
        resultado.put("dataDateFrom", dataDateFrom);
        resultado.put("dataDateTo", dataDateTo);
        return resultado;
    }
    
    public com.yego.contractortracker.dto.UploadMetadataDTO obtenerMetadata() {
        try {
            String sql = "SELECT " +
                        "COUNT(*) as total, " +
                        "COUNT(CASE WHEN driver_id IS NOT NULL THEN 1 END) as matched, " +
                        "COUNT(CASE WHEN driver_id IS NULL THEN 1 END) as unmatched, " +
                        "MIN(registration_date) as min_date, " +
                        "MAX(registration_date) as max_date, " +
                        "MAX(last_updated) as last_updated " +
                        "FROM scout_registrations";
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            
            Integer totalRecords = ((Number) result.get("total")).intValue();
            Integer matchedCount = ((Number) result.get("matched")).intValue();
            Integer unmatchedCount = ((Number) result.get("unmatched")).intValue();
            
            LocalDateTime lastUploadDate = null;
            Object lastUpdatedObj = result.get("last_updated");
            if (lastUpdatedObj != null) {
                if (lastUpdatedObj instanceof java.sql.Timestamp) {
                    lastUploadDate = ((java.sql.Timestamp) lastUpdatedObj).toLocalDateTime();
                } else if (lastUpdatedObj instanceof LocalDateTime) {
                    lastUploadDate = (LocalDateTime) lastUpdatedObj;
                }
            }
            if (lastUploadDate == null) {
                lastUploadDate = LocalDateTime.now().minusYears(1);
            }
            
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
            
            com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO sourceDescription = 
                new com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO(
                    "Registros de Scouts",
                    "Reporte Diario",
                    null,
                    "Los registros de scouts provienen del reporte diario de los conductores que son afiliados por cada scout"
                );
            
            return new com.yego.contractortracker.dto.UploadMetadataDTO(
                lastUploadDate,
                dataDateFrom,
                dataDateTo,
                totalRecords,
                matchedCount,
                unmatchedCount,
                sourceDescription
            );
        } catch (Exception e) {
            logger.error("Error al obtener metadata de registros de scouts", e);
            com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO sourceDescription = 
                new com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO(
                    "Registros de Scouts",
                    "Reporte Diario",
                    null,
                    "Los registros de scouts provienen del reporte diario de los conductores que son afiliados por cada scout"
                );
            return new com.yego.contractortracker.dto.UploadMetadataDTO(
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
}

