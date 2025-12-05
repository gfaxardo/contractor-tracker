package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.YangoTransactionGroup;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.entity.Scout;
import com.yego.contractortracker.entity.ScoutRegistration;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.repository.ScoutRegistrationRepository;
import com.yego.contractortracker.repository.ScoutRepository;
import com.yego.contractortracker.repository.YangoTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class YangoTransactionService {
    
    private static final Logger logger = LoggerFactory.getLogger(YangoTransactionService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final int DEFAULT_TIME_MARGIN_DAYS = 14;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private YangoTransactionRepository transactionRepository;
    
    @Autowired
    private ScoutRepository scoutRepository;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private ScoutRegistrationRepository scoutRegistrationRepository;
    
    @Transactional
    public Map<String, Object> procesarArchivoCSV(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        logger.info("Iniciando procesamiento de archivo CSV de transacciones Yango: {}", file.getOriginalFilename());
        
        try {
            List<YangoTransaction> transacciones = leerCSV(file);
            logger.info("Leídas {} transacciones del CSV", transacciones.size());
            
            if (transacciones.isEmpty()) {
                return crearResultado(0, 0, 0, "No se encontraron transacciones en el archivo", null, null);
            }
            
            // Filtrar duplicados
            List<YangoTransaction> transaccionesSinDuplicados = new ArrayList<>();
            int duplicadosEncontrados = 0;

            for (YangoTransaction transaccion : transacciones) {
                Optional<YangoTransaction> existente = transactionRepository.findByUniqueFields(
                    transaccion.getTransactionDate(),
                    transaccion.getScoutId(),
                    transaccion.getComment(),
                    transaccion.getMilestoneType()
                );
                
                if (existente.isEmpty()) {
                    transaccionesSinDuplicados.add(transaccion);
                } else {
                    duplicadosEncontrados++;
                    logger.debug("Transacción duplicada omitida: fecha={}, scout={}, comment={}", 
                        transaccion.getTransactionDate(), transaccion.getScoutId(), 
                        transaccion.getComment() != null ? transaccion.getComment().substring(0, Math.min(50, transaccion.getComment().length())) : "null");
                }
            }

            logger.info("Transacciones del CSV: {}, Duplicados omitidos: {}, A procesar: {}", 
                transacciones.size(), duplicadosEncontrados, transaccionesSinDuplicados.size());

            transacciones = transaccionesSinDuplicados; // Usar la lista filtrada
            
            // Optimización: Calcular rango de fechas global
            LocalDate minDate = transacciones.stream()
                .map(t -> t.getTransactionDate().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now().minusDays(DEFAULT_TIME_MARGIN_DAYS));
            
            LocalDate maxDate = transacciones.stream()
                .map(t -> t.getTransactionDate().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now().plusDays(DEFAULT_TIME_MARGIN_DAYS));
            
            logger.info("Rango de fechas de transacciones: {} a {}", minDate, maxDate);
            
            // Cargar todos los drivers de una vez (usando 30 días de margen)
            List<Map<String, Object>> allDrivers = buscarDriversEnRango(minDate, maxDate, 30);
            logger.info("Cargados {} drivers para matching", allDrivers.size());
            
            // Crear índice en memoria por nombre normalizado
            Map<String, List<Map<String, Object>>> driverIndex = crearIndiceDrivers(allDrivers);
            logger.info("Índice de drivers creado con {} entradas", driverIndex.size());
            
            int matchedCount = 0;
            int unmatchedCount = 0;
            Set<String> driverIdsMatched = new HashSet<>();
            
            // Procesar matching de drivers
            long matchingStartTime = System.currentTimeMillis();
            int processedWithName = 0;
            int processedWithoutName = 0;
            
            for (YangoTransaction transaccion : transacciones) {
                String driverName = transaccion.getDriverNameFromComment();
                LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
                
                if (driverName != null && !driverName.isEmpty()) {
                    processedWithName++;
                    Optional<Map<String, Object>> match = matchearConDriverOptimizado(driverName, transactionDate, driverIndex);
                    
                    if (match.isPresent()) {
                        String driverId = (String) match.get().get("driver_id");
                        transaccion.setDriverId(driverId);
                        driverIdsMatched.add(driverId);
                    } else {
                        transaccion.setIsMatched(false);
                        unmatchedCount++;
                    }
                } else {
                    processedWithoutName++;
                    transaccion.setIsMatched(false);
                    unmatchedCount++;
                }
            }
            
            long matchingElapsedTime = System.currentTimeMillis() - matchingStartTime;
            logger.info("Matching de drivers completado en {} ms: {} matcheados, {} sin match ({} con nombre, {} sin nombre)", 
                matchingElapsedTime, driverIdsMatched.size(), unmatchedCount, processedWithName, processedWithoutName);
            
            logger.info("Cargando milestones en batch para {} drivers únicos...", driverIdsMatched.size());
            
            // Cargar milestones en batch para todos los drivers matcheados
            Map<String, List<MilestoneInstance>> milestonesByDriver = cargarMilestonesBatch(driverIdsMatched);
            
            // Procesar matching de milestones
            long milestoneMatchingStartTime = System.currentTimeMillis();
            int milestonesMatched = 0;
            int milestonesUnmatched = 0;
            
            for (YangoTransaction transaccion : transacciones) {
                if (transaccion.getDriverId() != null && !transaccion.getDriverId().isEmpty()) {
                    List<MilestoneInstance> driverMilestones = milestonesByDriver.get(transaccion.getDriverId());
                    if (driverMilestones != null && !driverMilestones.isEmpty()) {
                        Optional<MilestoneInstance> milestone = matchearConMilestoneOptimizado(transaccion, driverMilestones);
                        if (milestone.isPresent()) {
                            transaccion.setMilestoneInstanceId(milestone.get().getId());
                            transaccion.setIsMatched(true);
                            transaccion.setMatchConfidence(new BigDecimal("0.9"));
                            matchedCount++;
                            milestonesMatched++;
                        } else {
                            transaccion.setIsMatched(false);
                            unmatchedCount++;
                            milestonesUnmatched++;
                        }
                    } else {
                        transaccion.setIsMatched(false);
                        unmatchedCount++;
                        milestonesUnmatched++;
                    }
                }
            }
            
            long milestoneMatchingElapsedTime = System.currentTimeMillis() - milestoneMatchingStartTime;
            logger.info("Matching de milestones completado en {} ms: {} matcheados, {} sin match", 
                milestoneMatchingElapsedTime, milestonesMatched, milestonesUnmatched);
            
            // Guardar en batches
            int batchSize = 25;
            int totalBatches = (transacciones.size() + batchSize - 1) / batchSize;
            int savedCount = 0;
            List<String> batchErrors = new ArrayList<>();
            
            for (int i = 0; i < transacciones.size(); i += batchSize) {
                int end = Math.min(i + batchSize, transacciones.size());
                List<YangoTransaction> batch = transacciones.subList(i, end);
                
                try {
                    transactionRepository.saveAll(batch);
                    savedCount += batch.size();
                    logger.debug("Guardado batch {}/{}: {} transacciones ({} - {})", 
                        (i / batchSize) + 1, totalBatches, batch.size(), i + 1, end);
                } catch (Exception e) {
                    String errorMsg = String.format("Error al guardar batch %d-%d: %s", i + 1, end, e.getMessage());
                    logger.error(errorMsg, e);
                    batchErrors.add(errorMsg);
                    // Continuar con los demás batches
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Procesamiento completado en {} ms. Matched: {}, Unmatched: {}, Guardadas: {}/{}", 
                elapsedTime, matchedCount, unmatchedCount, savedCount, transacciones.size());
            
            if (!batchErrors.isEmpty()) {
                logger.warn("Errores en {} batches: {}", batchErrors.size(), batchErrors);
            }
            
            // Crear registros en scout_registrations desde transacciones Yango matcheadas
            int scoutRegistrationsCreated = 0;
            int scoutRegistrationsUpdated = 0;
            
            for (YangoTransaction transaccion : transacciones) {
                if (transaccion.getDriverId() != null && 
                    !transaccion.getDriverId().isEmpty() && 
                    transaccion.getScoutId() != null &&
                    transaccion.getIsMatched() != null &&
                    transaccion.getIsMatched()) {
                    
                    try {
                        boolean fueActualizado = crearOActualizarScoutRegistrationDesdeYango(transaccion);
                        if (fueActualizado) {
                            scoutRegistrationsUpdated++;
                        } else {
                            scoutRegistrationsCreated++;
                        }
                    } catch (Exception e) {
                        logger.warn("Error al crear scout_registration desde transacción Yango {}: {}", 
                            transaccion.getId(), e.getMessage());
                    }
                }
            }
            
            logger.info("Scout registrations desde Yango: {} creados, {} actualizados", 
                scoutRegistrationsCreated, scoutRegistrationsUpdated);
            
            return crearResultado(transacciones.size(), matchedCount, unmatchedCount, 
                String.format("Procesadas %d transacciones: %d matcheadas, %d sin match (tiempo: %d ms)", 
                    transacciones.size(), matchedCount, unmatchedCount, elapsedTime),
                minDate, maxDate);
            
        } catch (Exception e) {
            logger.error("Error al procesar archivo CSV", e);
            throw new RuntimeException("Error al procesar archivo CSV: " + e.getMessage(), e);
        }
    }
    
    private List<YangoTransaction> leerCSV(MultipartFile file) throws Exception {
        List<YangoTransaction> transacciones = new ArrayList<>();
        int lineNumber = 0;
        int skippedLines = 0;
        int processedLines = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            lineNumber++;
            
            if (headerLine == null) {
                logger.warn("El archivo CSV está vacío o no tiene header");
                return transacciones;
            }
            
            headerLine = removerBOM(headerLine);
            logger.debug("Header leído: {}", headerLine);
            
            String[] headers = parseCSVLine(headerLine, ';');
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String headerName = headers[i].trim();
                headerMap.put(headerName, i);
                logger.debug("Header [{}]: '{}'", i, headerName);
            }
            
            logger.info("Header procesado con {} columnas: {}", headers.length, headerMap.keySet());
            
            if (headerMap.isEmpty()) {
                logger.error("No se pudo procesar el header del CSV");
                return transacciones;
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) {
                    skippedLines++;
                    logger.debug("Línea {} vacía, saltando", lineNumber);
                    continue;
                }
                
                String[] values = parseCSVLine(line, ';');
                
                if (values.length < 3) {
                    skippedLines++;
                    logger.debug("Línea {} tiene muy pocos valores ({}), saltando. Contenido: {}", 
                        lineNumber, values.length, line.substring(0, Math.min(100, line.length())));
                    continue;
                }
                
                YangoTransaction transaccion = new YangoTransaction();
                boolean validTransaction = true;
                
                String dateStr = getValue(values, headerMap, "Date");
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        transaccion.setTransactionDate(LocalDateTime.parse(dateStr, DATE_FORMATTER));
                    } catch (Exception e) {
                        skippedLines++;
                        logger.warn("Línea {}: Error al parsear fecha '{}': {}", lineNumber, dateStr, e.getMessage());
                        validTransaction = false;
                    }
                } else {
                    skippedLines++;
                    logger.debug("Línea {}: Fecha vacía o no encontrada", lineNumber);
                    validTransaction = false;
                }
                
                if (!validTransaction) {
                    continue;
                }
                
                String scoutName = getValue(values, headerMap, "Driver");
                if (scoutName != null && !scoutName.isEmpty()) {
                    Scout scout = identificarOCrearScout(scoutName);
                    transaccion.setScoutId(scout.getScoutId());
                } else {
                    skippedLines++;
                    logger.debug("Línea {}: Nombre de scout vacío o no encontrado", lineNumber);
                    continue;
                }
                
                String comment = getValue(values, headerMap, "Comment");
                if (comment != null && !comment.isEmpty()) {
                    transaccion.setComment(comment);
                    String driverName = extraerNombreDriverDelComment(comment);
                    transaccion.setDriverNameFromComment(driverName);
                    
                    Integer milestoneType = extraerMilestoneDelComment(comment);
                    transaccion.setMilestoneType(milestoneType);
                    
                    if (milestoneType != null) {
                        BigDecimal amount = calcularMontoYango(milestoneType);
                        transaccion.setAmountYango(amount);
                    }
                }
                
                transaccion.setCategoryId(getValue(values, headerMap, "Category ID"));
                transaccion.setCategory(getValue(values, headerMap, "Category"));
                transaccion.setDocument(getValue(values, headerMap, "Document"));
                transaccion.setInitiatedBy(getValue(values, headerMap, "Initiated by"));
                
                String amountStr = getValue(values, headerMap, "Amount");
                if (amountStr != null && !amountStr.isEmpty()) {
                    try {
                        transaccion.setAmountIndicator(Integer.parseInt(amountStr.split("\\.")[0]));
                    } catch (Exception e) {
                        transaccion.setAmountIndicator(1);
                    }
                }
                
                transacciones.add(transaccion);
                processedLines++;
                
                if (processedLines % 10 == 0) {
                    logger.debug("Procesadas {} transacciones válidas hasta la línea {}", processedLines, lineNumber);
                }
            }
            
            logger.info("Procesamiento CSV completado. Total líneas: {}, Procesadas: {}, Saltadas: {}", 
                lineNumber, processedLines, skippedLines);
        }
        
        return transacciones;
    }
    
    private String removerBOM(String line) {
        if (line != null && line.length() > 0 && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }
    
    private String[] parseCSVLine(String line, char separator) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentValue = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        values.add(currentValue.toString().trim());
        
        while (values.size() < 9) {
            values.add("");
        }
        
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
    
    private String extraerNombreDriverDelComment(String comment) {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        
        String lowerComment = comment.toLowerCase();
        int startIdx = lowerComment.indexOf("for driver");
        if (startIdx == -1) {
            return null;
        }
        
        int nameStart = startIdx + "for driver".length();
        int nameEnd = lowerComment.indexOf(" for completing", nameStart);
        if (nameEnd == -1) {
            nameEnd = comment.length();
        }
        
        String name = comment.substring(nameStart, nameEnd).trim();
        return name.isEmpty() ? null : name;
    }
    
    private Integer extraerMilestoneDelComment(String comment) {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        
        String lowerComment = comment.toLowerCase();
        if (lowerComment.contains("1 trip")) {
            return 1;
        } else if (lowerComment.contains("5 trip")) {
            return 5;
        } else if (lowerComment.contains("25 trip")) {
            return 25;
        }
        
        return null;
    }
    
    private BigDecimal calcularMontoYango(Integer milestoneType) {
        if (milestoneType == null) {
            return BigDecimal.ZERO;
        }
        
        switch (milestoneType) {
            case 1:
                return new BigDecimal("25.00");
            case 5:
                return new BigDecimal("35.00");
            case 25:
                return new BigDecimal("100.00");
            default:
                return BigDecimal.ZERO;
        }
    }
    
    private Scout identificarOCrearScout(String nombreScout) {
        String normalizedName = normalizarNombre(nombreScout);
        
        Optional<Scout> existing = scoutRepository.findByScoutName(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Scout scout = new Scout();
        scout.setScoutId(UUID.randomUUID().toString());
        scout.setScoutName(normalizedName);
        scout.setIsActive(true);
        scout.setCreatedAt(LocalDateTime.now());
        scout.setLastUpdated(LocalDateTime.now());
        
        return scoutRepository.save(scout);
    }
    
    private Optional<Map<String, Object>> matchearConDriver(String nombre, LocalDate fecha) {
        String normalizedName = normalizarNombre(nombre);
        LocalDate fechaDesde = fecha.minusDays(DEFAULT_TIME_MARGIN_DAYS);
        LocalDate fechaHasta = fecha.plusDays(DEFAULT_TIME_MARGIN_DAYS);
        
        String sql = "SELECT driver_id, full_name, phone, hire_date " +
                     "FROM drivers " +
                     "WHERE hire_date BETWEEN ? AND ? " +
                     "ORDER BY hire_date";
        
        List<Map<String, Object>> drivers = jdbcTemplate.queryForList(sql, fechaDesde, fechaHasta);
        
        for (Map<String, Object> driver : drivers) {
            String driverName = (String) driver.get("full_name");
            if (driverName != null) {
                String normalizedDriverName = normalizarNombre(driverName);
                if (normalizedDriverName.equals(normalizedName) || 
                    calcularSimilitudNombre(nombre, driverName, 0.7, 2, false) >= 0.7) {
                    return Optional.of(driver);
                }
            }
        }
        
        return Optional.empty();
    }
    
    private Optional<MilestoneInstance> matchearConMilestone(YangoTransaction transaccion, String driverId) {
        if (transaccion.getMilestoneType() == null) {
            return Optional.empty();
        }
        
        LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
        LocalDate fechaDesde = transactionDate.minusDays(7);
        LocalDate fechaHasta = transactionDate.plusDays(7);
        
        List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverId(driverId);
        
        for (MilestoneInstance milestone : milestones) {
            if (milestone.getMilestoneType().equals(transaccion.getMilestoneType())) {
                LocalDate fulfillmentDate = milestone.getFulfillmentDate().toLocalDate();
                if (!fulfillmentDate.isBefore(fechaDesde) && !fulfillmentDate.isAfter(fechaHasta)) {
                    return Optional.of(milestone);
                }
            }
        }
        
        return Optional.empty();
    }
    
    public List<YangoTransaction> obtenerTransaccionesSinMatch() {
        return transactionRepository.findByIsMatchedFalse();
    }
    
    public List<YangoTransaction> obtenerTransaccionesConFiltros(
            String scoutId, LocalDate dateFrom, LocalDate dateTo, 
            Integer milestoneType, Boolean isMatched) {
        
        List<YangoTransaction> allTransactions = transactionRepository.findAll();
        
        return allTransactions.stream()
                .filter(t -> scoutId == null || t.getScoutId().equals(scoutId))
                .filter(t -> dateFrom == null || !t.getTransactionDate().toLocalDate().isBefore(dateFrom))
                .filter(t -> dateTo == null || !t.getTransactionDate().toLocalDate().isAfter(dateTo))
                .filter(t -> milestoneType == null || (t.getMilestoneType() != null && t.getMilestoneType().equals(milestoneType)))
                .filter(t -> isMatched == null || t.getIsMatched().equals(isMatched))
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void asignarMatchManual(Long transactionId, String driverId) {
        YangoTransaction transaccion = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada: " + transactionId));
        
        transaccion.setDriverId(driverId);
        transaccion.setIsMatched(true);
        transaccion.setMatchConfidence(new BigDecimal("1.0"));
        
        Optional<MilestoneInstance> milestone = matchearConMilestone(transaccion, driverId);
        if (milestone.isPresent()) {
            transaccion.setMilestoneInstanceId(milestone.get().getId());
        }
        
        transactionRepository.save(transaccion);
    }
    
    @Transactional
    public int asignarMatchBatch(List<Long> transactionIds, String driverId, List<Long> milestoneInstanceIds) {
        List<YangoTransaction> transacciones = transactionRepository.findAllById(transactionIds);
        
        if (transacciones.size() != transactionIds.size()) {
            logger.warn("Algunas transacciones no fueron encontradas. Esperadas: {}, Encontradas: {}", 
                transactionIds.size(), transacciones.size());
        }
        
        // Si se proporcionan milestoneInstanceIds, crear un mapa para matching
        Map<Long, Long> milestoneMap = new HashMap<>();
        if (milestoneInstanceIds != null && !milestoneInstanceIds.isEmpty()) {
            for (int i = 0; i < Math.min(transactionIds.size(), milestoneInstanceIds.size()); i++) {
                milestoneMap.put(transactionIds.get(i), milestoneInstanceIds.get(i));
            }
        }
        
        int matchedCount = 0;
        for (YangoTransaction transaccion : transacciones) {
            transaccion.setDriverId(driverId);
            transaccion.setIsMatched(true);
            transaccion.setMatchConfidence(new BigDecimal("1.0"));
            
            // Si hay milestone específico para esta transacción, usarlo
            Long milestoneId = milestoneMap.get(transaccion.getId());
            if (milestoneId != null) {
                transaccion.setMilestoneInstanceId(milestoneId);
            } else {
                // Intentar matchear automáticamente con milestone
                Optional<MilestoneInstance> milestone = matchearConMilestone(transaccion, driverId);
                if (milestone.isPresent()) {
                    transaccion.setMilestoneInstanceId(milestone.get().getId());
                }
            }
            
            matchedCount++;
        }
        
        transactionRepository.saveAll(transacciones);
        logger.info("Matcheadas {} transacciones en batch para driver {}", matchedCount, driverId);
        
        return matchedCount;
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
    
    private List<Map<String, Object>> buscarDriversEnRango(LocalDate minDate, LocalDate maxDate, int marginDays) {
        long startTime = System.currentTimeMillis();
        // marginDays ya no se usa para expandir hacia adelante
        // Solo buscar drivers con hire_date entre (minDate - 30 días) y maxDate
        LocalDate fechaDesde = minDate != null ? minDate.minusDays(30) : LocalDate.now().minusMonths(3);
        LocalDate fechaHasta = maxDate != null ? maxDate : LocalDate.now();
        
        logger.debug("Buscando drivers en rango: {} a {} (hire_date entre {} y {})", 
            minDate, maxDate, fechaDesde, fechaHasta);
        
        String sql = "SELECT driver_id, full_name, phone, hire_date " +
                     "FROM drivers " +
                     "WHERE hire_date BETWEEN ? AND ? " +
                     "ORDER BY hire_date";
        
        List<Map<String, Object>> drivers = jdbcTemplate.queryForList(sql, fechaDesde, fechaHasta);
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("Búsqueda de drivers completada: {} drivers encontrados en {} ms (rango: {} a {})", 
            drivers.size(), elapsedTime, fechaDesde, fechaHasta);
        
        return drivers;
    }
    
    private Map<String, List<Map<String, Object>>> crearIndiceDrivers(List<Map<String, Object>> drivers) {
        long startTime = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> index = new HashMap<>();
        Map<String, List<Map<String, Object>>> indexBySortedWords = new HashMap<>();
        
        int processedCount = 0;
        int skippedCount = 0;
        
        for (Map<String, Object> driver : drivers) {
            String fullName = (String) driver.get("full_name");
            if (fullName != null) {
                // Índice exacto (existente)
                String normalized = normalizarNombre(fullName);
                index.computeIfAbsent(normalized, k -> new ArrayList<>()).add(driver);
                
                // Índice por palabras ordenadas (nuevo)
                String sortedWords = normalizarNombreParaComparacion(fullName);
                if (sortedWords != null && !sortedWords.isEmpty()) {
                    indexBySortedWords.computeIfAbsent(sortedWords, k -> new ArrayList<>()).add(driver);
                }
                processedCount++;
            } else {
                skippedCount++;
            }
        }
        
        // Combinar ambos índices
        index.putAll(indexBySortedWords);
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("Índice de drivers creado: {} entradas ({} procesados, {} saltados) en {} ms", 
            index.size(), processedCount, skippedCount, elapsedTime);
        
        return index;
    }
    
    private Optional<Map<String, Object>> matchearConDriverOptimizado(
            String nombre, LocalDate fecha, 
            Map<String, List<Map<String, Object>>> driverIndex) {
        
        String normalizedName = normalizarNombre(nombre);
        String sortedWordsName = normalizarNombreParaComparacion(nombre);
        
        // Paso 1: Búsqueda exacta
        List<Map<String, Object>> candidates = driverIndex.get(normalizedName);
        
        // Paso 2: Búsqueda por palabras ordenadas
        if ((candidates == null || candidates.isEmpty()) && sortedWordsName != null) {
            candidates = driverIndex.get(sortedWordsName);
        }
        
        // Paso 3: Búsqueda por similitud mejorada (más flexible)
        if (candidates == null || candidates.isEmpty()) {
            double bestSimilarity = 0.0;
            List<Map<String, Object>> bestCandidates = null;
            
            for (Map.Entry<String, List<Map<String, Object>>> entry : driverIndex.entrySet()) {
                double similarity = calcularSimilitudNombreMejorado(nombre, entry.getKey());
                if (similarity > bestSimilarity && similarity >= 0.4) { // Más flexible: 0.4 (antes 0.5)
                    bestSimilarity = similarity;
                    bestCandidates = entry.getValue();
                }
            }
            
            if (bestCandidates != null) {
                candidates = bestCandidates;
            }
        }
        
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        
        // Paso 4: Filtrar por fecha más cercana con scoring combinado
        Map<String, Object> bestMatch = null;
        double bestScore = 0.0;
        String driverFullName = null;
        
        // Threshold más flexible según número de palabras
        String[] nombrePalabras = nombre.trim().split("\\s+");
        int numPalabras = nombrePalabras.length;
        double thresholdMinimo;
        
        if (numPalabras >= 4) {
            thresholdMinimo = 0.45; // Aún más flexible (antes 0.5)
        } else if (numPalabras == 3) {
            thresholdMinimo = 0.50; // Aún más flexible (antes 0.55)
        } else if (numPalabras == 2) {
            thresholdMinimo = 0.55; // Aún más flexible (antes 0.6)
        } else {
            thresholdMinimo = 0.60; // Aún más flexible (antes 0.65)
        }
        
        for (Map<String, Object> driver : candidates) {
            Object hireDateObj = driver.get("hire_date");
            LocalDate hireDate = null;
            if (hireDateObj instanceof java.sql.Date) {
                hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
            } else if (hireDateObj instanceof LocalDate) {
                hireDate = (LocalDate) hireDateObj;
            }
            
            if (hireDate != null) {
                // Validar que hire_date <= transaction_date (driver debe existir antes de la transacción)
                if (hireDate.isAfter(fecha)) {
                    logger.debug("Skipping driver {} because hire_date {} is after transaction_date {}", 
                        driver.get("driver_id"), hireDate, fecha);
                    continue; 
                }
                
                long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(hireDate, fecha);
                
                // Validar que la diferencia no sea mayor a 30 días
                if (daysDiff < 0 || daysDiff > 30) {
                    logger.debug("Skipping driver {} because daysDiff {} is outside 0-30 day range for transaction_date {}", 
                        driver.get("driver_id"), daysDiff, fecha);
                    continue; 
                }
                
                // Calcular similitud de nombre (siempre, incluso para matches exactos)
                String fullName = (String) driver.get("full_name");
                double nameSimilarity = calcularSimilitudNombreMejorado(nombre, fullName);
                
                // Si es match exacto o por palabras ordenadas, dar score alto
                if (nameSimilarity >= 0.95) {
                    nameSimilarity = 1.0;
                }
                
                // Scoring de fecha: más flexible
                double dateScore;
                if (daysDiff <= 3) {
                    dateScore = 1.0;
                } else if (daysDiff <= 7) {
                    dateScore = 0.9;
                } else if (daysDiff <= 14) {
                    dateScore = 0.7;
                } else if (daysDiff <= 21) {
                    dateScore = 0.6;
                } else {
                    dateScore = 0.5;
                }
                
                // Score combinado: nombre tiene más peso, pero si la similitud es muy alta, reducir threshold
                double combinedScore = (nameSimilarity * 0.75) + (dateScore * 0.25);
                
                // Si la similitud de nombre es muy alta, ser más flexible con el threshold
                double thresholdAplicado = thresholdMinimo;
                if (nameSimilarity >= 0.85) {
                    thresholdAplicado = thresholdMinimo * 0.9; // Reducir threshold en 10% si similitud alta
                } else if (nameSimilarity >= 0.75) {
                    thresholdAplicado = thresholdMinimo * 0.95; // Reducir threshold en 5% si similitud moderada
                }
                
                if (combinedScore > bestScore && combinedScore >= thresholdAplicado) {
                    bestScore = combinedScore;
                    bestMatch = driver;
                    driverFullName = fullName;
                }
            }
        }
        
        if (bestMatch != null) {
            logger.info("Match encontrado para '{}' con driver '{}' (score: {}, threshold: {})", 
                nombre, driverFullName, String.format("%.2f", bestScore), String.format("%.2f", thresholdMinimo));
            return Optional.of(bestMatch);
        } else {
            logger.debug("No se encontró match para '{}' con score >= {}", nombre, String.format("%.2f", thresholdMinimo));
        }
        
        return Optional.empty();
    }
    
    private Map<String, List<MilestoneInstance>> cargarMilestonesBatch(Set<String> driverIds) {
        long startTime = System.currentTimeMillis();
        if (driverIds == null || driverIds.isEmpty()) {
            logger.debug("No hay driver IDs para cargar milestones");
            return new HashMap<>();
        }
        
        logger.debug("Cargando milestones en batch para {} drivers", driverIds.size());
        List<MilestoneInstance> allMilestones = milestoneInstanceRepository.findByDriverIdIn(driverIds);
        
        Map<String, List<MilestoneInstance>> milestonesByDriver = allMilestones.stream()
            .collect(Collectors.groupingBy(MilestoneInstance::getDriverId));
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("Milestones cargados en batch: {} milestones para {} drivers únicos en {} ms", 
            allMilestones.size(), milestonesByDriver.size(), elapsedTime);
        
        return milestonesByDriver;
    }
    
    private Optional<MilestoneInstance> matchearConMilestoneOptimizado(
            YangoTransaction transaccion, List<MilestoneInstance> driverMilestones) {
        
        if (transaccion.getMilestoneType() == null || driverMilestones == null || driverMilestones.isEmpty()) {
            return Optional.empty();
        }
        
        LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
        // Rango más amplio: ±14 días (antes era ±7)
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
                
                // También buscar el más cercano sin restricción (para casos donde el milestone está fuera del rango pero es el único disponible)
                if (daysDiff < minDaysDiffSinRestriccion) {
                    minDaysDiffSinRestriccion = daysDiff;
                    bestMatchSinRestriccion = milestone;
                }
            }
        }
        
        // Preferir match dentro del rango, pero si no hay, usar el más cercano (hasta 30 días de diferencia)
        if (bestMatch != null) {
            logger.debug("Match de milestone encontrado dentro del rango (±14 días): tipo={}, diferencia={} días", 
                transaccion.getMilestoneType(), minDaysDiff);
            return Optional.of(bestMatch);
        } else if (bestMatchSinRestriccion != null && minDaysDiffSinRestriccion <= 30) {
            logger.debug("Match de milestone encontrado fuera del rango pero dentro de 30 días: tipo={}, diferencia={} días", 
                transaccion.getMilestoneType(), minDaysDiffSinRestriccion);
            return Optional.of(bestMatchSinRestriccion);
        }
        
        return Optional.empty();
    }
    
    public List<YangoTransactionGroup> obtenerTransaccionesSinMatchAgrupadas() {
        List<YangoTransaction> transacciones = transactionRepository.findByIsMatchedFalse();
        
        if (transacciones.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Separar transacciones con y sin driver_name_from_comment
        Map<Boolean, List<YangoTransaction>> partitioned = transacciones.stream()
            .collect(Collectors.partitioningBy(t -> 
                t.getDriverNameFromComment() != null && !t.getDriverNameFromComment().isEmpty()));
        
        List<YangoTransactionGroup> grupos = new ArrayList<>();
        
        // Agrupar solo las que tienen driver_name_from_comment
        List<YangoTransaction> conNombre = partitioned.get(true);
        if (conNombre != null && !conNombre.isEmpty()) {
            Map<String, List<YangoTransaction>> gruposConNombre = conNombre.stream()
                .collect(Collectors.groupingBy(YangoTransaction::getDriverNameFromComment));
            
            gruposConNombre.entrySet().stream()
                .map(entry -> new YangoTransactionGroup(entry.getKey(), entry.getValue()))
                .forEach(grupos::add);
        }
        
        // Las que no tienen driver_name_from_comment se muestran individualmente
        List<YangoTransaction> sinNombre = partitioned.get(false);
        if (sinNombre != null && !sinNombre.isEmpty()) {
            for (YangoTransaction t : sinNombre) {
                grupos.add(new YangoTransactionGroup(null, Collections.singletonList(t)));
            }
        }
        
        return grupos;
    }
    
    @Transactional
    public Map<String, Object> reprocesarTransaccionesSinMatch() {
        long startTime = System.currentTimeMillis();
        logger.info("Iniciando reprocesamiento de transacciones sin match");
        
        // Obtener todas las transacciones sin match
        List<YangoTransaction> transaccionesSinMatch = transactionRepository.findByIsMatchedFalse();
        
        if (transaccionesSinMatch.isEmpty()) {
            return crearResultado(0, 0, 0, "No hay transacciones sin match para reprocesar", null, null);
        }
        
        logger.info("Encontradas {} transacciones sin match para reprocesar", transaccionesSinMatch.size());
        
        // Calcular rango de fechas
        LocalDate minDate = transaccionesSinMatch.stream()
            .map(t -> t.getTransactionDate().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now().minusDays(DEFAULT_TIME_MARGIN_DAYS));
        
        LocalDate maxDate = transaccionesSinMatch.stream()
            .map(t -> t.getTransactionDate().toLocalDate())
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now().plusDays(DEFAULT_TIME_MARGIN_DAYS));
        
        logger.info("Rango de fechas de transacciones sin match: {} a {}", minDate, maxDate);
        
        // Cargar drivers y crear índice
        List<Map<String, Object>> allDrivers = buscarDriversEnRango(minDate, maxDate, DEFAULT_TIME_MARGIN_DAYS);
        Map<String, List<Map<String, Object>>> driverIndex = crearIndiceDrivers(allDrivers);
        logger.info("Cargados {} drivers para reprocesamiento", allDrivers.size());
        
        // Resetear matches previos
        for (YangoTransaction transaccion : transaccionesSinMatch) {
            transaccion.setDriverId(null);
            transaccion.setMilestoneInstanceId(null);
            transaccion.setIsMatched(false);
            transaccion.setMatchConfidence(null);
        }
        
        // Procesar matching de drivers
        long reprocessMatchingStartTime = System.currentTimeMillis();
        Set<String> driverIdsMatched = new HashSet<>();
        int matchedCount = 0;
        int reprocessUnmatched = 0;
        
        for (YangoTransaction transaccion : transaccionesSinMatch) {
            String driverName = transaccion.getDriverNameFromComment();
            LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
            
            if (driverName != null && !driverName.isEmpty()) {
                Optional<Map<String, Object>> match = matchearConDriverOptimizado(driverName, transactionDate, driverIndex);
                
                if (match.isPresent()) {
                    String driverId = (String) match.get().get("driver_id");
                    transaccion.setDriverId(driverId);
                    driverIdsMatched.add(driverId);
                } else {
                    reprocessUnmatched++;
                }
            } else {
                reprocessUnmatched++;
            }
        }
        
        long reprocessMatchingElapsedTime = System.currentTimeMillis() - reprocessMatchingStartTime;
        logger.info("Reprocesamiento - Matching de drivers completado en {} ms: {} matcheados, {} sin match", 
            reprocessMatchingElapsedTime, driverIdsMatched.size(), reprocessUnmatched);
        
        logger.info("Cargando milestones en batch para {} drivers únicos...", driverIdsMatched.size());
        
        // Cargar milestones y matchear
        Map<String, List<MilestoneInstance>> milestonesByDriver = cargarMilestonesBatch(driverIdsMatched);
        
        long reprocessMilestoneMatchingStartTime = System.currentTimeMillis();
        int reprocessMilestonesMatched = 0;
        int reprocessMilestonesUnmatched = 0;
        
        for (YangoTransaction transaccion : transaccionesSinMatch) {
            if (transaccion.getDriverId() != null) {
                List<MilestoneInstance> driverMilestones = milestonesByDriver.get(transaccion.getDriverId());
                if (driverMilestones != null && !driverMilestones.isEmpty()) {
                    Optional<MilestoneInstance> milestone = matchearConMilestoneOptimizado(transaccion, driverMilestones);
                    if (milestone.isPresent()) {
                        transaccion.setMilestoneInstanceId(milestone.get().getId());
                        transaccion.setIsMatched(true);
                        transaccion.setMatchConfidence(new BigDecimal("0.9"));
                        matchedCount++;
                        reprocessMilestonesMatched++;
                    } else {
                        reprocessMilestonesUnmatched++;
                    }
                } else {
                    reprocessMilestonesUnmatched++;
                }
            }
        }
        
        long reprocessMilestoneMatchingElapsedTime = System.currentTimeMillis() - reprocessMilestoneMatchingStartTime;
        logger.info("Reprocesamiento - Matching de milestones completado en {} ms: {} matcheados, {} sin match", 
            reprocessMilestoneMatchingElapsedTime, reprocessMilestonesMatched, reprocessMilestonesUnmatched);
        
        // Guardar en batches
        int batchSize = 25;
        int totalBatches = (transaccionesSinMatch.size() + batchSize - 1) / batchSize;
        int savedCount = 0;
        
        for (int i = 0; i < transaccionesSinMatch.size(); i += batchSize) {
            int end = Math.min(i + batchSize, transaccionesSinMatch.size());
            List<YangoTransaction> batch = transaccionesSinMatch.subList(i, end);
            
            try {
                transactionRepository.saveAll(batch);
                savedCount += batch.size();
                logger.debug("Guardado batch {}/{}: {} transacciones ({} - {})", 
                    (i / batchSize) + 1, totalBatches, batch.size(), i + 1, end);
            } catch (Exception e) {
                logger.error("Error al guardar batch {}-{}: {}", i + 1, end, e.getMessage(), e);
            }
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        int unmatchedCount = transaccionesSinMatch.size() - matchedCount;
        
        logger.info("Reprocesamiento completado en {} ms. Matched: {}, Unmatched: {}, Guardadas: {}/{}", 
            elapsedTime, matchedCount, unmatchedCount, savedCount, transaccionesSinMatch.size());
        
        return crearResultado(transaccionesSinMatch.size(), matchedCount, unmatchedCount,
            String.format("Reprocesadas %d transacciones: %d matcheadas, %d sin match (tiempo: %d ms)",
                transaccionesSinMatch.size(), matchedCount, unmatchedCount, elapsedTime),
            minDate, maxDate);
    }
    
    @Transactional
    public Map<String, Object> limpiarDuplicados() {
        logger.info("Iniciando limpieza de transacciones duplicadas");
        
        String sql = "SELECT transaction_date, scout_id, comment, milestone_type, COUNT(*) as count, " +
                     "ARRAY_AGG(id ORDER BY created_at DESC) as ids " +
                     "FROM yango_transactions " +
                     "WHERE comment IS NOT NULL " +
                     "GROUP BY transaction_date, scout_id, comment, milestone_type " +
                     "HAVING COUNT(*) > 1";
        
        List<Map<String, Object>> duplicados = jdbcTemplate.queryForList(sql);
        int totalDuplicados = 0;
        int eliminados = 0;
        
        for (Map<String, Object> grupo : duplicados) {
            Integer count = ((Number) grupo.get("count")).intValue();
            totalDuplicados += count;
            
            Object idsObj = grupo.get("ids");
            List<Long> ids = new ArrayList<>();
            
            if (idsObj instanceof java.sql.Array) {
                try {
                    Object[] idsArray = (Object[]) ((java.sql.Array) idsObj).getArray();
                    for (Object id : idsArray) {
                        ids.add(((Number) id).longValue());
                    }
                } catch (Exception e) {
                    logger.warn("Error al procesar array de IDs: {}", e.getMessage());
                    continue;
                }
            }
            
            if (ids.size() > 1) {
                List<Long> idsAEliminar = ids.subList(1, ids.size());
                transactionRepository.deleteAllById(idsAEliminar);
                eliminados += idsAEliminar.size();
                
                logger.debug("Eliminados {} duplicados de grupo: fecha={}, scout={}", 
                    idsAEliminar.size(), grupo.get("transaction_date"), grupo.get("scout_id"));
            }
        }
        
        logger.info("Limpieza completada: {} grupos duplicados encontrados, {} transacciones eliminadas de {} totales", 
            duplicados.size(), eliminados, totalDuplicados);
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("duplicateGroups", duplicados.size());
        resultado.put("totalDuplicates", totalDuplicados);
        resultado.put("deleted", eliminados);
        resultado.put("kept", totalDuplicados - eliminados);
        
        return resultado;
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
    
    private double calcularSimilitudNombre(String nombre1, String nombre2, double threshold, int minWordsMatch, boolean ignoreSecondLastName) {
        if (nombre1 == null || nombre2 == null) {
            return 0.0;
        }
        
        String norm1 = normalizarNombreParaComparacion(nombre1);
        String norm2 = normalizarNombreParaComparacion(nombre2);
        
        if (norm1 == null || norm2 == null || norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        if (norm1.equals(norm2)) {
            return 1.0;
        }
        
        String[] palabras1 = norm1.split("\\s+");
        String[] palabras2 = norm2.split("\\s+");
        
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
        
        Set<String> interseccion = new HashSet<>(set1);
        interseccion.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        double jaccard = (double) interseccion.size() / union.size();
        
        if (interseccion.size() >= minWordsMatch && jaccard >= threshold) {
            return Math.min(jaccard, 1.0);
        }
        
        return 0.0;
    }
    
    private String normalizarNombreParaComparacion(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String normalized = name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("\\s+", " ")
                .trim();
        
        String[] palabras = normalized.split("\\s+");
        List<String> palabrasFiltradas = new ArrayList<>();
        Set<String> palabrasComunes = Set.of("de", "la", "del", "los", "las", "y", "e");
        
        for (String palabra : palabras) {
            if (!palabrasComunes.contains(palabra) && palabra.length() > 1) {
                palabrasFiltradas.add(palabra);
            }
        }
        
        Collections.sort(palabrasFiltradas);
        
        return String.join(" ", palabrasFiltradas);
    }
    
    private double calcularSimilitudNombreMejorado(String nombre1, String nombre2) {
        if (nombre1 == null || nombre2 == null) {
            return 0.0;
        }
        
        String norm1 = normalizarNombreParaComparacion(nombre1);
        String norm2 = normalizarNombreParaComparacion(nombre2);
        
        if (norm1 == null || norm2 == null || norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        // Match exacto
        if (norm1.equals(norm2)) {
            return 1.0;
        }
        
        String[] palabras1 = norm1.split("\\s+");
        String[] palabras2 = norm2.split("\\s+");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(palabras1));
        Set<String> set2 = new HashSet<>(Arrays.asList(palabras2));
        
        Set<String> interseccion = new HashSet<>(set1);
        interseccion.retainAll(set2);
        
        // Match perfecto: todas las palabras coinciden (independiente del orden)
        if (interseccion.size() == palabras1.length && 
            interseccion.size() == palabras2.length && 
            interseccion.size() >= 2) {
            return 1.0;
        }
        
        // Si hay al menos 3 palabras coincidentes de 4, o 2 de 3, considerar match alto
        int maxWords = Math.max(palabras1.length, palabras2.length);
        
        if (maxWords >= 4 && interseccion.size() >= 3) {
            // 3 de 4 palabras coinciden: score alto
            double jaccard = (double) interseccion.size() / Math.max(palabras1.length, palabras2.length);
            return Math.max(jaccard, 0.85); // Mínimo 0.85 si hay 3 de 4 palabras
        }
        
        if (maxWords == 3 && interseccion.size() >= 2) {
            // 2 de 3 palabras coinciden: score alto
            double jaccard = (double) interseccion.size() / Math.max(palabras1.length, palabras2.length);
            return Math.max(jaccard, 0.8); // Mínimo 0.8 si hay 2 de 3 palabras
        }
        
        // Calcular Jaccard estándar
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        if (union.isEmpty()) {
            return 0.0;
        }
        
        double jaccard = (double) interseccion.size() / union.size();
        
        // Threshold adaptativo aún más flexible
        double threshold;
        int minWordsMatch;
        
        if (maxWords >= 4) {
            threshold = 0.40; // Aún más flexible (antes 0.45)
            minWordsMatch = 3;
        } else if (maxWords == 3) {
            threshold = 0.50; // Aún más flexible (antes 0.55)
            minWordsMatch = 2;
        } else if (maxWords == 2) {
            threshold = 0.65; // Aún más flexible (antes 0.7)
            minWordsMatch = 2;
        } else {
            return 0.0; // 1 palabra: no hacer match automático
        }
        
        if (interseccion.size() >= minWordsMatch && jaccard >= threshold) {
            return Math.min(jaccard, 1.0);
        }
        
        return 0.0;
    }
    
    private Map<String, Object> crearResultado(int total, int matched, int unmatched, String message) {
        return crearResultado(total, matched, unmatched, message, null, null);
    }
    
    private Map<String, Object> crearResultado(int total, int matched, int unmatched, String message, LocalDate dataDateFrom, LocalDate dataDateTo) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("totalTransactions", total);
        resultado.put("matchedCount", matched);
        resultado.put("unmatchedCount", unmatched);
        resultado.put("lastUpdated", LocalDateTime.now());
        resultado.put("message", message);
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
                        "MIN(transaction_date::date) as min_date, " +
                        "MAX(transaction_date::date) as max_date, " +
                        "MAX(last_updated) as last_updated " +
                        "FROM yango_transactions";
            
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
                    "Transacciones Yango",
                    "Fleet Ajhla",
                    null,
                    "Las transacciones Yango provienen del fleet 'ajhla' donde se ven todas las afiliaciones pagadas a distintas instancias"
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
            logger.error("Error al obtener metadata de transacciones Yango", e);
            com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO sourceDescription = 
                new com.yego.contractortracker.dto.UploadMetadataDTO.SourceDescriptionDTO(
                    "Transacciones Yango",
                    "Fleet Ajhla",
                    null,
                    "Las transacciones Yango provienen del fleet 'ajhla' donde se ven todas las afiliaciones pagadas a distintas instancias"
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
    
    public Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> obtenerTransaccionesMatcheadasPorDriver(
            List<String> driverIds, LocalDate fechaDesde, LocalDate fechaHasta) {
        
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        logger.info("Obteniendo transacciones matcheadas para {} drivers entre {} y {}", 
            driverIds.size(), fechaDesde, fechaHasta);
        
        Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> result = new HashMap<>();
        final int BATCH_SIZE = 50;
        int totalBatches = (driverIds.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        logger.info("Dividiendo consulta en {} lotes de máximo {} drivers cada uno", totalBatches, BATCH_SIZE);
        
        for (int i = 0; i < driverIds.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, driverIds.size());
            List<String> batch = driverIds.subList(i, endIndex);
            
            int currentBatch = (i / BATCH_SIZE) + 1;
            logger.info("Procesando lote {}/{}: {} drivers (índices {} a {})", 
                currentBatch, 
                totalBatches,
                batch.size(), 
                i, 
                endIndex - 1);
            
            try {
                Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> batchResult = 
                    ejecutarConsultaPorLote(batch, fechaDesde, fechaHasta);
                result.putAll(batchResult);
            } catch (org.springframework.dao.DataAccessResourceFailureException e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                if (errorMessage.contains("Connection reset") || errorMessage.contains("I/O error") || 
                    errorMessage.contains("Unable to commit")) {
                    logger.error("Error de conexión al procesar lote de drivers (índices {} a {}). La conexión se reseteó. Continuando con siguiente lote.", 
                        i, endIndex - 1, e);
                } else {
                    logger.error("Error de acceso a recursos al procesar lote de drivers (índices {} a {}). Continuando con siguiente lote.", 
                        i, endIndex - 1, e);
                }
            } catch (Exception e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                if (errorMessage.contains("Connection reset") || errorMessage.contains("I/O error") || 
                    errorMessage.contains("PSQLException") || errorMessage.contains("Unable to commit")) {
                    logger.error("Error de conexión/PostgreSQL al procesar lote de drivers (índices {} a {}). Continuando con siguiente lote.", 
                        i, endIndex - 1, e);
                } else {
                    logger.error("Error inesperado al procesar lote de drivers (índices {} a {}). Continuando con siguiente lote.", 
                        i, endIndex - 1, e);
                }
            }
        }
        
        logger.info("Transacciones matcheadas obtenidas: {} drivers con transacciones de {} totales procesados", 
            result.size(), driverIds.size());
        return result;
    }
    
    private Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> ejecutarConsultaPorLote(
            List<String> driverIds, LocalDate fechaDesde, LocalDate fechaHasta) {
        
        String placeholders = String.join(",", Collections.nCopies(driverIds.size(), "?"));
        
        String sql = "SELECT " +
                     "  yt.id, " +
                     "  yt.transaction_date, " +
                     "  yt.milestone_type, " +
                     "  yt.amount_yango, " +
                     "  yt.milestone_instance_id, " +
                     "  yt.driver_id, " +
                     "  mi.id as mi_id, " +
                     "  mi.driver_id as mi_driver_id, " +
                     "  mi.park_id as mi_park_id, " +
                     "  mi.milestone_type as mi_milestone_type, " +
                     "  mi.period_days as mi_period_days, " +
                     "  mi.fulfillment_date as mi_fulfillment_date, " +
                     "  mi.calculation_date as mi_calculation_date, " +
                     "  mi.trip_count as mi_trip_count " +
                     "FROM yango_transactions yt " +
                     "LEFT JOIN milestone_instances mi ON yt.milestone_instance_id = mi.id " +
                     "WHERE yt.is_matched = true " +
                     "  AND yt.driver_id IS NOT NULL " +
                     "  AND yt.milestone_instance_id IS NOT NULL " +
                     "  AND yt.driver_id IN (" + placeholders + ") " +
                     "  AND DATE(yt.transaction_date) >= ? " +
                     "  AND DATE(yt.transaction_date) <= ? " +
                     "ORDER BY yt.driver_id, yt.transaction_date";
        
        List<Object> params = new ArrayList<>();
        params.addAll(driverIds);
        params.add(fechaDesde);
        params.add(fechaHasta);
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        
        Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> result = new HashMap<>();
        
        for (Map<String, Object> row : rows) {
            String driverId = (String) row.get("driver_id");
            if (driverId == null) continue;
            
            com.yego.contractortracker.dto.YangoTransactionMatchedDTO dto = 
                new com.yego.contractortracker.dto.YangoTransactionMatchedDTO();
            
            Object idObj = row.get("id");
            if (idObj instanceof Number) {
                dto.setId(((Number) idObj).longValue());
            }
            
            Object transactionDateObj = row.get("transaction_date");
            if (transactionDateObj instanceof java.sql.Timestamp) {
                dto.setTransactionDate(((java.sql.Timestamp) transactionDateObj).toLocalDateTime());
            } else if (transactionDateObj instanceof LocalDateTime) {
                dto.setTransactionDate((LocalDateTime) transactionDateObj);
            }
            
            Object milestoneTypeObj = row.get("milestone_type");
            if (milestoneTypeObj instanceof Number) {
                dto.setMilestoneType(((Number) milestoneTypeObj).intValue());
            }
            
            Object amountObj = row.get("amount_yango");
            if (amountObj instanceof Number) {
                dto.setAmountYango(BigDecimal.valueOf(((Number) amountObj).doubleValue()));
            } else if (amountObj instanceof BigDecimal) {
                dto.setAmountYango((BigDecimal) amountObj);
            }
            
            Object milestoneInstanceIdObj = row.get("milestone_instance_id");
            if (milestoneInstanceIdObj instanceof Number) {
                dto.setMilestoneInstanceId(((Number) milestoneInstanceIdObj).longValue());
            }
            
            if (row.get("mi_id") != null) {
                com.yego.contractortracker.dto.MilestoneInstanceDTO milestoneDTO = 
                    new com.yego.contractortracker.dto.MilestoneInstanceDTO();
                
                Object miIdObj = row.get("mi_id");
                if (miIdObj instanceof Number) {
                    milestoneDTO.setId(((Number) miIdObj).longValue());
                }
                
                milestoneDTO.setDriverId((String) row.get("mi_driver_id"));
                milestoneDTO.setParkId((String) row.get("mi_park_id"));
                
                Object miMilestoneTypeObj = row.get("mi_milestone_type");
                if (miMilestoneTypeObj instanceof Number) {
                    milestoneDTO.setMilestoneType(((Number) miMilestoneTypeObj).intValue());
                }
                
                Object miPeriodDaysObj = row.get("mi_period_days");
                if (miPeriodDaysObj instanceof Number) {
                    milestoneDTO.setPeriodDays(((Number) miPeriodDaysObj).intValue());
                }
                
                Object miFulfillmentDateObj = row.get("mi_fulfillment_date");
                if (miFulfillmentDateObj instanceof java.sql.Timestamp) {
                    milestoneDTO.setFulfillmentDate(((java.sql.Timestamp) miFulfillmentDateObj).toLocalDateTime());
                } else if (miFulfillmentDateObj instanceof LocalDateTime) {
                    milestoneDTO.setFulfillmentDate((LocalDateTime) miFulfillmentDateObj);
                }
                
                Object miCalculationDateObj = row.get("mi_calculation_date");
                if (miCalculationDateObj instanceof java.sql.Timestamp) {
                    milestoneDTO.setCalculationDate(((java.sql.Timestamp) miCalculationDateObj).toLocalDateTime());
                } else if (miCalculationDateObj instanceof LocalDateTime) {
                    milestoneDTO.setCalculationDate((LocalDateTime) miCalculationDateObj);
                }
                
                Object miTripCountObj = row.get("mi_trip_count");
                if (miTripCountObj instanceof Number) {
                    milestoneDTO.setTripCount(((Number) miTripCountObj).intValue());
                }
                
                dto.setMilestoneInstance(milestoneDTO);
            }
            
            result.computeIfAbsent(driverId, k -> new ArrayList<>()).add(dto);
        }
        
        return result;
    }
    
    private boolean crearOActualizarScoutRegistrationDesdeYango(YangoTransaction transaccion) {
        LocalDate transactionDate = transaccion.getTransactionDate().toLocalDate();
        
        // Buscar si ya existe un registro para este scout-driver en un rango de fechas cercano
        List<ScoutRegistration> existentes = scoutRegistrationRepository
            .findByScoutIdAndDriverIdAndRegistrationDateBetween(
                transaccion.getScoutId(),
                transaccion.getDriverId(),
                transactionDate.minusDays(3),
                transactionDate.plusDays(3)
            );
        
        ScoutRegistration registro = null;
        boolean fueActualizado = false;
        
        // Buscar si hay uno con match_source = 'yango_transaction' y mismo yango_transaction_id
        for (ScoutRegistration existente : existentes) {
            if ("yango_transaction".equals(existente.getMatchSource()) &&
                transaccion.getId() != null &&
                transaccion.getId().equals(existente.getYangoTransactionId())) {
                registro = existente;
                fueActualizado = true;
                break;
            }
        }
        
        if (registro == null && !existentes.isEmpty()) {
            // Si hay uno existente de otra fuente, usar el más cercano en fecha
            registro = existentes.stream()
                .min((a, b) -> {
                    long diffA = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        a.getRegistrationDate(), transactionDate));
                    long diffB = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        b.getRegistrationDate(), transactionDate));
                    return Long.compare(diffA, diffB);
                })
                .orElse(null);
            if (registro != null) {
                fueActualizado = true;
            }
        }
        
        if (registro == null) {
            registro = new ScoutRegistration();
            registro.setScoutId(transaccion.getScoutId());
            registro.setDriverId(transaccion.getDriverId());
            registro.setRegistrationDate(transactionDate);
            registro.setIsMatched(true);
            registro.setMatchSource("yango_transaction");
            registro.setReconciliationStatus("pending");
            registro.setIsReconciled(false);
            
            if (transaccion.getMatchConfidence() != null) {
                registro.setMatchScore(transaccion.getMatchConfidence().doubleValue());
            } else {
                registro.setMatchScore(0.9);
            }
            
            if (transaccion.getDriverNameFromComment() != null) {
                registro.setDriverName(transaccion.getDriverNameFromComment());
            } else {
                registro.setDriverName("Driver desde Yango");
            }
            
            logger.debug("Creando nuevo scout_registration desde Yango: scout={}, driver={}, fecha={}", 
                transaccion.getScoutId(), transaccion.getDriverId(), transactionDate);
        } else {
            // Actualizar registro existente
            if (transaccion.getMatchConfidence() != null) {
                registro.setMatchScore(transaccion.getMatchConfidence().doubleValue());
            }
            registro.setLastUpdated(LocalDateTime.now());
            logger.debug("Actualizando scout_registration {} desde Yango", registro.getId());
        }
        
        registro.setYangoTransactionId(transaccion.getId());
        scoutRegistrationRepository.save(registro);
        
        return fueActualizado;
    }
}

