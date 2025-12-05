package com.yego.contractortracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initializeTrackingHistoryTable() {
        try {
            logger.info("Inicialización de base de datos iniciada (ejecutándose en segundo plano)...");
            
            logger.info("Verificando existencia de tabla contractor_tracking_history...");
            
            String createTableSql = "CREATE TABLE IF NOT EXISTS contractor_tracking_history (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "driver_id VARCHAR(255) NOT NULL, " +
                "park_id VARCHAR(255) NOT NULL, " +
                "calculation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "total_trips_historical INTEGER NOT NULL DEFAULT 0, " +
                "sum_work_time_seconds BIGINT, " +
                "has_historical_connection BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_registered BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_connected BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_with_trips BOOLEAN NOT NULL DEFAULT FALSE, " +
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            try {
                jdbcTemplate.execute(createTableSql);
                logger.info("Tabla contractor_tracking_history creada o ya existe");
            } catch (Exception e) {
                logger.error("Error al crear tabla contractor_tracking_history: {}. La aplicación continuará sin esta tabla.", e.getMessage());
                return;
            }
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_tracking_driver_id ON contractor_tracking_history(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_park_id ON contractor_tracking_history(park_id)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_calculation_date ON contractor_tracking_history(calculation_date DESC)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_last_updated ON contractor_tracking_history(last_updated DESC)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "contractor_tracking_history");
            }
            
            String uniqueIndexSql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_tracking_driver_date ON contractor_tracking_history(driver_id, calculation_date)";
            crearIndiceConTimeout(uniqueIndexSql, "contractor_tracking_history (único)");
            
            logger.info("Índices de contractor_tracking_history procesados");
            
            try {
                logger.info("Agregando columna acquisition_channel a contractor_tracking_history si no existe...");
                String addColumnSql = "ALTER TABLE contractor_tracking_history ADD COLUMN IF NOT EXISTS acquisition_channel VARCHAR(255)";
                jdbcTemplate.execute(addColumnSql);
                logger.info("Columna acquisition_channel agregada o ya existe");
                
                String indexChannelSql = "CREATE INDEX IF NOT EXISTS idx_tracking_acquisition_channel ON contractor_tracking_history(acquisition_channel)";
                crearIndiceConTimeout(indexChannelSql, "acquisition_channel");
            } catch (Exception e) {
                logger.warn("Advertencia al agregar columna acquisition_channel: {}", e.getMessage());
            }
            
            try {
                logger.info("Verificando existencia de tabla lead_matches...");
                
                String createLeadMatchesTableSql = "CREATE TABLE IF NOT EXISTS lead_matches (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "external_id VARCHAR(255) NOT NULL UNIQUE, " +
                    "driver_id VARCHAR(255) NOT NULL, " +
                    "lead_created_at DATE NOT NULL, " +
                    "hire_date DATE NOT NULL, " +
                    "match_score DOUBLE PRECISION NOT NULL, " +
                    "is_manual BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "is_discarded BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "lead_phone VARCHAR(255), " +
                    "lead_first_name VARCHAR(255), " +
                    "lead_last_name VARCHAR(255)" +
                    ")";
                
                jdbcTemplate.execute(createLeadMatchesTableSql);
                logger.info("Tabla lead_matches creada o ya existe");
                
                String[] leadMatchesIndexStatements = {
                    "CREATE INDEX IF NOT EXISTS idx_lead_matches_external_id ON lead_matches(external_id)",
                    "CREATE INDEX IF NOT EXISTS idx_lead_matches_driver_id ON lead_matches(driver_id)",
                    "CREATE INDEX IF NOT EXISTS idx_lead_matches_lead_created_at ON lead_matches(lead_created_at)",
                    "CREATE INDEX IF NOT EXISTS idx_lead_matches_is_discarded ON lead_matches(is_discarded)"
                };
                
                for (String indexSql : leadMatchesIndexStatements) {
                    crearIndiceConTimeout(indexSql, "lead_matches");
                }
                
                logger.info("Índices de lead_matches creados o ya existen");
                
                // Agregar columnas de datos originales del lead si no existen
                try {
                    logger.info("Agregando columnas de datos originales del lead a lead_matches si no existen...");
                    String[] addColumnStatements = {
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS lead_phone VARCHAR(255)",
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS lead_first_name VARCHAR(255)",
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS lead_last_name VARCHAR(255)"
                    };
                    
                    for (String addColumnSql : addColumnStatements) {
                        try {
                            jdbcTemplate.execute(addColumnSql);
                        } catch (Exception e) {
                            logger.warn("Advertencia al agregar columna a lead_matches: {}", e.getMessage());
                        }
                    }
                    logger.info("Columnas de datos originales del lead verificadas o creadas");
                } catch (Exception e) {
                    logger.warn("Advertencia al agregar columnas de datos originales: {}", e.getMessage());
                }
                
                // Agregar columnas de scout si no existen
                try {
                    logger.info("Agregando columnas de scout a lead_matches si no existen...");
                    String[] addScoutColumnStatements = {
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS scout_registration_id BIGINT",
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS scout_match_score DOUBLE PRECISION",
                        "ALTER TABLE lead_matches ADD COLUMN IF NOT EXISTS scout_match_date TIMESTAMP"
                    };
                    
                    for (String addColumnSql : addScoutColumnStatements) {
                        try {
                            jdbcTemplate.execute(addColumnSql);
                        } catch (Exception e) {
                            logger.warn("Advertencia al agregar columna de scout a lead_matches: {}", e.getMessage());
                        }
                    }
                    
                    // Crear índice para scout_registration_id si no existe
                    try {
                        String indexScoutSql = "CREATE INDEX IF NOT EXISTS idx_lead_matches_scout_registration_id ON lead_matches(scout_registration_id)";
                        crearIndiceConTimeout(indexScoutSql, "lead_matches (scout_registration_id)");
                    } catch (Exception e) {
                        logger.warn("Advertencia al crear índice de scout_registration_id: {}", e.getMessage());
                    }
                    
                    // Agregar foreign key constraint si no existe
                    try {
                        String checkConstraintSql = "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE constraint_name = 'fk_lead_matches_scout_registration' " +
                            "AND table_name = 'lead_matches'";
                        Integer constraintExists = jdbcTemplate.queryForObject(checkConstraintSql, Integer.class);
                        
                        if (constraintExists == null || constraintExists == 0) {
                            String addConstraintSql = "ALTER TABLE lead_matches " +
                                "ADD CONSTRAINT fk_lead_matches_scout_registration " +
                                "FOREIGN KEY (scout_registration_id) " +
                                "REFERENCES scout_registrations(id) " +
                                "ON DELETE SET NULL";
                            jdbcTemplate.execute(addConstraintSql);
                            logger.info("Foreign key constraint fk_lead_matches_scout_registration agregada");
                        } else {
                            logger.debug("Foreign key constraint fk_lead_matches_scout_registration ya existe");
                        }
                    } catch (Exception e) {
                        logger.warn("Advertencia al agregar foreign key constraint de scout_registration: {}", e.getMessage());
                    }
                    
                    logger.info("Columnas de scout verificadas o creadas");
                } catch (Exception e) {
                    logger.warn("Advertencia al agregar columnas de scout: {}", e.getMessage());
                }
                
            } catch (Exception e) {
                logger.error("Error al inicializar tabla lead_matches: {}", e.getMessage(), e);
            }
            
            // Crear índices para summary_daily para optimizar queries de agregación
            try {
                logger.info("Creando índices para summary_daily...");
                
                String[] summaryDailyIndexStatements = {
                    "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_id ON summary_daily(driver_id)",
                    "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_work_time ON summary_daily(driver_id, sum_work_time_seconds) WHERE sum_work_time_seconds IS NOT NULL",
                    "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_orders ON summary_daily(driver_id, count_orders_completed) WHERE count_orders_completed IS NOT NULL"
                };
                
                for (String indexSql : summaryDailyIndexStatements) {
                    crearIndiceConTimeout(indexSql, "summary_daily");
                }
                
                // Intentar crear índice con INCLUDE (requiere PostgreSQL 11+)
                try {
                    String indexWithInclude = "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_aggregation ON summary_daily(driver_id) INCLUDE (sum_work_time_seconds, count_orders_completed, date_file)";
                    crearIndiceConTimeout(indexWithInclude, "summary_daily (INCLUDE)");
                    logger.info("Índice con INCLUDE creado exitosamente");
                } catch (Exception e) {
                    logger.debug("No se pudo crear índice con INCLUDE (puede requerir PostgreSQL 11+): {}", e.getMessage());
                }
                
                // Actualizar estadísticas (comentado para evitar bloqueos durante inicio)
                // Se puede ejecutar manualmente o en un job programado
                /*
                try {
                    jdbcTemplate.execute("ANALYZE summary_daily");
                    logger.info("Estadísticas de summary_daily actualizadas");
                } catch (Exception e) {
                    logger.warn("Advertencia al actualizar estadísticas de summary_daily: {}", e.getMessage());
                }
                */
                logger.info("ANALYZE summary_daily omitido durante inicio (ejecutar manualmente si es necesario)");
                
                logger.info("Índices de summary_daily creados o ya existen");
            } catch (Exception e) {
                logger.warn("Advertencia al crear índices de summary_daily: {}", e.getMessage());
            }
            
            initializeMilestoneInstancesTable();
            
            try {
                initializeScoutsTables();
            } catch (Exception e) {
                logger.error("Error al inicializar tablas de scouts (continuando...): {}", e.getMessage(), e);
                // No lanzar excepción para que el sistema pueda iniciar aunque falle la inicialización de scouts
            }
            
        } catch (Exception e) {
            logger.error("Error al inicializar tabla contractor_tracking_history: {}", e.getMessage(), e);
        }
    }
    
    private void initializeMilestoneInstancesTable() {
        try {
            logger.info("Verificando existencia de tabla milestone_instances...");
            
            String createTableSql = "CREATE TABLE IF NOT EXISTS milestone_instances (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "driver_id VARCHAR(255) NOT NULL, " +
                "park_id VARCHAR(255) NOT NULL, " +
                "milestone_type INTEGER NOT NULL, " +
                "period_days INTEGER NOT NULL, " +
                "fulfillment_date TIMESTAMP NOT NULL, " +
                "calculation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "trip_count INTEGER NOT NULL DEFAULT 0, " +
                "trip_details JSONB, " +
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT chk_milestone_type CHECK (milestone_type IN (1, 5, 25)), " +
                "CONSTRAINT chk_period_days CHECK (period_days IN (7, 14))" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla milestone_instances creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_milestone_driver_id ON milestone_instances(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_milestone_park_id ON milestone_instances(park_id)",
                "CREATE INDEX IF NOT EXISTS idx_milestone_type_period ON milestone_instances(milestone_type, period_days)",
                "CREATE INDEX IF NOT EXISTS idx_milestone_fulfillment_date ON milestone_instances(fulfillment_date DESC)",
                "CREATE INDEX IF NOT EXISTS idx_milestone_calculation_date ON milestone_instances(calculation_date DESC)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "milestone_instances");
            }
            
            String uniqueIndexSql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_milestone_driver_type_period ON milestone_instances(driver_id, milestone_type, period_days)";
            crearIndiceConTimeout(uniqueIndexSql, "milestone_instances (único)");
            
            logger.info("Índices de milestone_instances creados o ya existen");
            
        } catch (Exception e) {
            logger.error("Error al inicializar tabla milestone_instances: {}", e.getMessage(), e);
        }
    }
    
    private void initializeScoutsTables() {
        try {
            logger.info("Verificando existencia de tablas de scouts...");
            
            initializeScoutsTable();
            initializeYangoTransactionsTable();
            initializeScoutPaymentConfigTable();
            initializeScoutPaymentsTable();
            initializeScoutPaymentInstancesTable();
            initializeScoutRegistrationsTable();
            
            logger.info("Tablas de scouts inicializadas correctamente");
        } catch (Exception e) {
            logger.error("Error al inicializar tablas de scouts: {}", e.getMessage(), e);
        }
        
        try {
            logger.info("Verificando existencia de tabla audit_log...");
            
            String createAuditLogTableSql = "CREATE TABLE IF NOT EXISTS audit_log (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "username VARCHAR(255), " +
                "action VARCHAR(255) NOT NULL, " +
                "endpoint VARCHAR(500) NOT NULL, " +
                "method VARCHAR(10) NOT NULL, " +
                "request_body TEXT, " +
                "response_status INTEGER, " +
                "ip_address VARCHAR(45), " +
                "user_agent TEXT, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "error_message TEXT" +
                ")";
            
            jdbcTemplate.execute(createAuditLogTableSql);
            logger.info("Tabla audit_log creada o ya existe");
            
            String[] auditLogIndexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_audit_log_username ON audit_log(username)",
                "CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log(timestamp)",
                "CREATE INDEX IF NOT EXISTS idx_audit_log_endpoint ON audit_log(endpoint)",
                "CREATE INDEX IF NOT EXISTS idx_audit_log_method ON audit_log(method)"
            };
            
            for (String indexSql : auditLogIndexStatements) {
                crearIndiceConTimeout(indexSql, "audit_log");
            }
            
            logger.info("Índices de audit_log procesados");
        } catch (Exception e) {
            logger.error("Error al inicializar tabla audit_log: {}", e.getMessage(), e);
        }
    }
    
    private void initializeScoutsTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS scouts (" +
                "scout_id VARCHAR(255) PRIMARY KEY, " +
                "scout_name VARCHAR(255) NOT NULL UNIQUE, " +
                "driver_id VARCHAR(255), " +
                "is_active BOOLEAN DEFAULT true, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla scouts creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_scouts_name ON scouts(scout_name)",
                "CREATE INDEX IF NOT EXISTS idx_scouts_driver_id ON scouts(driver_id)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "scouts");
            }
        } catch (Exception e) {
            logger.error("Error al inicializar tabla scouts: {}", e.getMessage(), e);
        }
    }
    
    private void initializeYangoTransactionsTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS yango_transactions (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "transaction_date TIMESTAMP NOT NULL, " +
                "scout_id VARCHAR(255) NOT NULL REFERENCES scouts(scout_id), " +
                "driver_id VARCHAR(255), " +
                "driver_name_from_comment VARCHAR(255), " +
                "milestone_type INTEGER CHECK (milestone_type IN (1, 5, 25)), " +
                "amount_yango DECIMAL(10,2) NOT NULL, " +
                "amount_indicator INTEGER DEFAULT 1, " +
                "comment TEXT, " +
                "category_id VARCHAR(255), " +
                "category VARCHAR(255), " +
                "document VARCHAR(255), " +
                "initiated_by VARCHAR(255), " +
                "milestone_instance_id BIGINT REFERENCES milestone_instances(id), " +
                "match_confidence DECIMAL(3,2), " +
                "is_matched BOOLEAN DEFAULT false, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla yango_transactions creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_yango_transactions_scout ON yango_transactions(scout_id)",
                "CREATE INDEX IF NOT EXISTS idx_yango_transactions_driver ON yango_transactions(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_yango_transactions_date ON yango_transactions(transaction_date)",
                "CREATE INDEX IF NOT EXISTS idx_yango_transactions_matched ON yango_transactions(is_matched)",
                "CREATE INDEX IF NOT EXISTS idx_yango_transactions_milestone ON yango_transactions(milestone_type)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "yango_transactions");
            }
        } catch (Exception e) {
            logger.error("Error al inicializar tabla yango_transactions: {}", e.getMessage(), e);
        }
    }
    
    private void initializeScoutPaymentConfigTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS scout_payment_config (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "milestone_type INTEGER UNIQUE NOT NULL CHECK (milestone_type IN (1, 5, 10, 25)), " +
                "amount_scout DECIMAL(10,2) NOT NULL, " +
                "payment_days INTEGER NOT NULL DEFAULT 7, " +
                "is_active BOOLEAN DEFAULT true, " +
                "min_registrations_required INTEGER DEFAULT 8, " +
                "min_connection_seconds INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla scout_payment_config creada o ya existe");
            
            try {
                String addMinRegistrationsSql = "ALTER TABLE scout_payment_config ADD COLUMN IF NOT EXISTS min_registrations_required INTEGER DEFAULT 8";
                jdbcTemplate.execute(addMinRegistrationsSql);
                logger.info("Columna min_registrations_required verificada/agregada");
            } catch (Exception e) {
                logger.warn("Advertencia al agregar columna min_registrations_required: {}", e.getMessage());
            }
            
            try {
                String addMinSecondsSql = "ALTER TABLE scout_payment_config ADD COLUMN IF NOT EXISTS min_connection_seconds INTEGER DEFAULT 1";
                jdbcTemplate.execute(addMinSecondsSql);
                logger.info("Columna min_connection_seconds verificada/agregada");
            } catch (Exception e) {
                logger.warn("Advertencia al agregar columna min_connection_seconds: {}", e.getMessage());
            }
            
            try {
                String updateNullsSql = "UPDATE scout_payment_config SET min_registrations_required = 8 WHERE min_registrations_required IS NULL";
                jdbcTemplate.execute(updateNullsSql);
                String updateNullsSql2 = "UPDATE scout_payment_config SET min_connection_seconds = 1 WHERE min_connection_seconds IS NULL";
                jdbcTemplate.execute(updateNullsSql2);
                logger.info("Valores NULL actualizados en scout_payment_config");
            } catch (Exception e) {
                logger.warn("Advertencia al actualizar valores NULL: {}", e.getMessage());
            }
            
            inicializarDatosConfiguracionPagos();
        } catch (Exception e) {
            logger.error("Error al inicializar tabla scout_payment_config: {}", e.getMessage(), e);
        }
    }
    
    private void inicializarDatosConfiguracionPagos() {
        try {
            String checkSql = "SELECT COUNT(*) FROM scout_payment_config";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);
            
            if (count == null || count == 0) {
                logger.info("Inicializando datos por defecto de configuración de pagos de scouts...");
                
                String[] insertStatements = {
                    "INSERT INTO scout_payment_config (milestone_type, amount_scout, payment_days, is_active, min_registrations_required, min_connection_seconds) " +
                    "VALUES (1, 5.00, 7, true, 8, 1) ON CONFLICT (milestone_type) DO UPDATE SET amount_scout = EXCLUDED.amount_scout, min_registrations_required = EXCLUDED.min_registrations_required, min_connection_seconds = EXCLUDED.min_connection_seconds",
                    "INSERT INTO scout_payment_config (milestone_type, amount_scout, payment_days, is_active, min_registrations_required, min_connection_seconds) " +
                    "VALUES (5, 7.50, 7, true, 8, 1) ON CONFLICT (milestone_type) DO UPDATE SET amount_scout = EXCLUDED.amount_scout, min_registrations_required = EXCLUDED.min_registrations_required, min_connection_seconds = EXCLUDED.min_connection_seconds",
                    "INSERT INTO scout_payment_config (milestone_type, amount_scout, payment_days, is_active, min_registrations_required, min_connection_seconds) " +
                    "VALUES (10, 10.00, 7, true, 8, 1) ON CONFLICT (milestone_type) DO UPDATE SET amount_scout = EXCLUDED.amount_scout, min_registrations_required = EXCLUDED.min_registrations_required, min_connection_seconds = EXCLUDED.min_connection_seconds",
                    "INSERT INTO scout_payment_config (milestone_type, amount_scout, payment_days, is_active, min_registrations_required, min_connection_seconds) " +
                    "VALUES (25, 90.00, 7, true, 8, 1) ON CONFLICT (milestone_type) DO NOTHING"
                };
                
                for (String insertSql : insertStatements) {
                    try {
                        jdbcTemplate.execute(insertSql);
                    } catch (Exception e) {
                        logger.warn("Advertencia al insertar configuración de pago: {}", e.getMessage());
                    }
                }
                
                logger.info("Datos por defecto de configuración de pagos inicializados");
            } else {
                logger.debug("Configuración de pagos ya tiene datos, omitiendo inicialización");
            }
        } catch (Exception e) {
            logger.warn("Error al inicializar datos de configuración de pagos: {}", e.getMessage());
        }
    }
    
    private void initializeScoutPaymentsTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS scout_payments (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "scout_id VARCHAR(255) NOT NULL REFERENCES scouts(scout_id), " +
                "payment_period_start DATE NOT NULL, " +
                "payment_period_end DATE NOT NULL, " +
                "total_amount DECIMAL(10,2) NOT NULL, " +
                "transactions_count INTEGER NOT NULL DEFAULT 0, " +
                "status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'cancelled')), " +
                "paid_at TIMESTAMP, " +
                "instance_ids JSONB, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla scout_payments creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_scout_payments_scout ON scout_payments(scout_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payments_period ON scout_payments(payment_period_start, payment_period_end)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payments_status ON scout_payments(status)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payments_instance_ids ON scout_payments USING GIN (instance_ids)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "scout_payments");
            }
            
            try {
                String addColumnSql = "ALTER TABLE scout_payments ADD COLUMN IF NOT EXISTS instance_ids JSONB";
                jdbcTemplate.execute(addColumnSql);
                logger.info("Columna instance_ids agregada o ya existe en scout_payments");
            } catch (Exception e) {
                logger.warn("Advertencia al agregar columna instance_ids: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error al inicializar tabla scout_payments: {}", e.getMessage(), e);
        }
    }
    
    private void initializeScoutPaymentInstancesTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS scout_payment_instances (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "scout_id VARCHAR(255) NOT NULL, " +
                "driver_id VARCHAR(255) NOT NULL, " +
                "milestone_type INTEGER NOT NULL, " +
                "milestone_instance_id BIGINT REFERENCES milestone_instances(id), " +
                "amount DECIMAL(10,2) NOT NULL, " +
                "registration_date DATE NOT NULL, " +
                "milestone_fulfillment_date TIMESTAMP NOT NULL, " +
                "eligibility_verified BOOLEAN DEFAULT false, " +
                "eligibility_reason TEXT, " +
                "status VARCHAR(50) DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'cancelled')), " +
                "payment_id BIGINT REFERENCES scout_payments(id), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla scout_payment_instances creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_scout ON scout_payment_instances(scout_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_driver ON scout_payment_instances(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_status ON scout_payment_instances(status)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_registration_date ON scout_payment_instances(registration_date)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_milestone_type ON scout_payment_instances(milestone_type)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_payment_id ON scout_payment_instances(payment_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_payment_instances_scout_status ON scout_payment_instances(scout_id, status)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "scout_payment_instances");
            }
        } catch (Exception e) {
            logger.error("Error al inicializar tabla scout_payment_instances: {}", e.getMessage(), e);
        }
    }
    
    private void initializeScoutRegistrationsTable() {
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS scout_registrations (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "scout_id VARCHAR(255) NOT NULL REFERENCES scouts(scout_id), " +
                "registration_date DATE NOT NULL, " +
                "driver_license VARCHAR(255), " +
                "driver_name VARCHAR(255) NOT NULL, " +
                "driver_phone VARCHAR(255), " +
                "acquisition_medium VARCHAR(50), " +
                "driver_id VARCHAR(255), " +
                "match_score DOUBLE PRECISION, " +
                "is_matched BOOLEAN NOT NULL DEFAULT FALSE, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Tabla scout_registrations creada o ya existe");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_scout_id ON scout_registrations(scout_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_registration_date ON scout_registrations(registration_date)",
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_driver_id ON scout_registrations(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_is_matched ON scout_registrations(is_matched)",
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_driver_phone ON scout_registrations(driver_phone)",
                "CREATE INDEX IF NOT EXISTS idx_scout_registrations_scout_date ON scout_registrations(scout_id, registration_date)"
            };
            
            for (String indexSql : indexStatements) {
                crearIndiceConTimeout(indexSql, "scout_registrations");
            }
            
            logger.info("Índices de scout_registrations procesados");
        } catch (Exception e) {
            logger.error("Error al inicializar tabla scout_registrations: {}", e.getMessage(), e);
        }
    }
    
    private void crearIndiceConTimeout(String indexSql, String nombreIndice) {
        String nombreIndiceExtraido = extraerNombreIndice(indexSql);
        if (nombreIndiceExtraido == null) {
            nombreIndiceExtraido = nombreIndice;
        }
        
        try {
            if (indiceExiste(nombreIndiceExtraido)) {
                logger.debug("Índice {} ya existe, omitiendo creación", nombreIndiceExtraido);
                return;
            }
        } catch (Exception e) {
            logger.debug("No se pudo verificar existencia del índice {} (continuando con creación): {}", nombreIndiceExtraido, e.getMessage());
        }
        
        try {
            jdbcTemplate.setQueryTimeout(30);
            jdbcTemplate.execute(indexSql);
            jdbcTemplate.setQueryTimeout(0);
            logger.debug("Índice {} creado exitosamente", nombreIndiceExtraido);
        } catch (org.springframework.dao.QueryTimeoutException e) {
            logger.warn("Timeout al crear índice {} (puede que ya exista, continuando...): {}", nombreIndiceExtraido, e.getMessage());
            jdbcTemplate.setQueryTimeout(0);
        } catch (org.springframework.dao.DataAccessResourceFailureException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("timeout") || errorMsg.contains("timed out") || errorMsg.contains("Connection is not available")) {
                logger.warn("Timeout de conexión al crear índice {} (continuando...): {}", nombreIndiceExtraido, e.getMessage());
            } else {
                logger.warn("Error al crear índice {} (continuando...): {}", nombreIndiceExtraido, e.getMessage());
            }
            jdbcTemplate.setQueryTimeout(0);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            
            if (errorMsg.contains("timeout") || errorMsg.contains("timed out") || 
                causeMsg.contains("timeout") || causeMsg.contains("timed out") ||
                errorMsg.contains("Connection is not available")) {
                logger.warn("Timeout al crear índice {} (continuando...): {}", nombreIndiceExtraido, e.getMessage());
            } else if (errorMsg.contains("already exists") || errorMsg.contains("duplicate key") || 
                       errorMsg.contains("relation") && errorMsg.contains("already exists")) {
                logger.debug("Índice {} ya existe (continuando...): {}", nombreIndiceExtraido, e.getMessage());
            } else {
                logger.warn("Advertencia al crear índice {} (continuando...): {}", nombreIndiceExtraido, e.getMessage());
            }
            jdbcTemplate.setQueryTimeout(0);
        }
    }
    
    private String extraerNombreIndice(String indexSql) {
        try {
            String sqlUpper = indexSql.toUpperCase().trim();
            int idxStart = sqlUpper.indexOf("INDEX");
            if (idxStart == -1) return null;
            
            int idxIfNotExists = sqlUpper.indexOf("IF NOT EXISTS");
            int nombreStart;
            if (idxIfNotExists != -1 && idxIfNotExists < idxStart + 20) {
                nombreStart = sqlUpper.indexOf(" ", idxIfNotExists + 13) + 1;
            } else {
                nombreStart = sqlUpper.indexOf(" ", idxStart + 6) + 1;
            }
            
            if (nombreStart <= 0) return null;
            
            int nombreEnd = sqlUpper.indexOf(" ", nombreStart);
            if (nombreEnd == -1) nombreEnd = sqlUpper.indexOf(" ON ", nombreStart);
            if (nombreEnd == -1) return null;
            
            return indexSql.substring(nombreStart, nombreEnd).trim();
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean indiceExiste(String nombreIndice) {
        try {
            String checkSql = "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, nombreIndice);
            return count != null && count > 0;
        } catch (Exception e) {
            logger.debug("Error al verificar existencia del índice {}: {}", nombreIndice, e.getMessage());
            return false;
        }
    }
}


