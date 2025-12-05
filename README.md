# Contractor Tracker - Sistema de Análisis de Onboarding de Drivers

Sistema completo para analizar el comportamiento de drivers durante sus primeros 14 días de operación, incluyendo métricas de conexión, viajes y estados de activación.

## Arquitectura

- **Backend**: Spring Boot 3.1.x con Java 17
- **Frontend**: React 18 con TypeScript y Vite
- **Base de Datos**: PostgreSQL (yego_integral)

## Estructura del Proyecto

```
CONTRACTOR_TRACKER/
├── backend/                 # Aplicación Spring Boot
│   ├── src/main/java/
│   │   └── com/yego/contractortracker/
│   │       ├── config/      # Configuración (CORS, etc.)
│   │       ├── controller/  # Controladores REST
│   │       ├── dto/         # Data Transfer Objects
│   │       ├── service/     # Lógica de negocio
│   │       └── exception/   # Manejo de excepciones
│   └── pom.xml
├── frontend/                # Aplicación React
│   ├── src/
│   │   ├── components/      # Componentes React
│   │   ├── services/        # Cliente API
│   │   └── App.tsx
│   └── package.json
└── README.md
```

## Requisitos Previos

- Java 17 o superior
- Maven 3.6+
- Node.js 18+ y npm
- PostgreSQL (acceso a servidor yego_integral)

## Configuración del Backend

### 1. Configurar Variables de Entorno

El backend usa variables de entorno para la conexión a la base de datos. Puedes configurarlas de dos formas:

**Opción A: Variables de entorno del sistema**
```bash
export DB_HOST=168.119.226.236
export DB_PORT=5432
export DB_NAME=yego_integral
export DB_USER=yego_user
export DB_PASSWORD=37>MNA&-35+
```

**Opción B: Archivo .env (requiere plugin adicional)**
Por defecto, Spring Boot no lee archivos .env directamente. Las credenciales están hardcodeadas en `application.properties` como valores por defecto, pero se pueden sobrescribir con variables de entorno.

### 2. Compilar y Ejecutar

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

El backend estará disponible en `http://localhost:8080`

### 3. Endpoints Disponibles

#### Inspección de Schema (temporal)
- `GET /api/inspect/drivers` - Muestra 5 registros de la tabla `drivers`
- `GET /api/inspect/summary-daily` - Muestra 5 registros de la tabla `summary_daily`

#### Endpoint Principal
- `GET /api/drivers/onboarding-14d`
  - Parámetros:
    - `parkId` (opcional, default: `08e20910d81d42658d4334d3f6d10ac0`)
    - `startDateFrom` (opcional, formato: YYYY-MM-DD)
    - `startDateTo` (opcional, formato: YYYY-MM-DD)
    - `channel` (opcional)
  - Retorna: Lista de drivers con sus primeros 14 días de actividad

## Configuración del Frontend

### 1. Instalar Dependencias

```bash
cd frontend
npm install
```

### 2. Configurar URL del Backend

Editar `frontend/.env`:
```
VITE_API_BASE_URL=http://localhost:8080/api
```

### 3. Ejecutar en Modo Desarrollo

```bash
npm run dev
```

El frontend estará disponible en `http://localhost:3000`

### 4. Compilar para Producción

```bash
npm run build
```

Los archivos compilados estarán en `frontend/dist/`

## Uso del Sistema

1. **Iniciar Backend**: Ejecutar Spring Boot en el puerto 8080
2. **Iniciar Frontend**: Ejecutar `npm run dev` en el puerto 3000
3. **Abrir Navegador**: Ir a `http://localhost:3000`
4. **Configurar Filtros**:
   - Park ID (obligatorio, por defecto: `08e20910d81d42658d4334d3f6d10ac0`)
   - Rango de fechas de inicio (opcional)
   - Canal (opcional)
5. **Buscar**: Click en "Buscar" para obtener resultados
6. **Explorar**: Click en una fila de driver para ver detalles de los 14 días

## Supuestos sobre el Schema

El código hace los siguientes supuestos sobre las columnas de la base de datos. **Estos deben verificarse usando los endpoints de inspección**:

### Tabla `drivers`
- `driver_id` (String/UUID): Identificador único del driver
- `park_id` (String/UUID): ID del parque/flota
- `start_date` (Date): Fecha de ingreso del driver
- `channel` o `acquisition_channel` (String): Canal de adquisición

### Tabla `summary_daily`
- `driver_id` (String/UUID): Identificador del driver (para JOIN)
- `date` (Date): Fecha de actividad
- `trips` o `completed_trips` (Integer): Número de viajes del día
- `online_time_minutes` o `online_time_hours` (Numeric): Tiempo de conexión

**Nota**: El código usa `COALESCE` para manejar diferentes nombres de columnas posibles. Si los nombres reales son diferentes, modificar la consulta SQL en `OnboardingService.java`.

## Estados de Activación (status_14d)

- **solo_registro**: Driver nunca se conectó ni tuvo viajes en los primeros 14 días
- **conecto_sin_viajes**: Driver tuvo conexión (>0 minutos) pero 0 viajes
- **activo_con_viajes**: Driver tuvo al menos 1 viaje en los primeros 14 días

## Extensibilidad

### Agregar Más KPIs

1. Modificar la consulta SQL en `OnboardingService.java` para incluir nuevas columnas
2. Agregar campos al `DayActivityDTO` y `DriverOnboardingDTO`
3. Actualizar el cálculo de agregados en el servicio
4. Mostrar nuevos campos en `DriverTable.tsx`

### Cambiar Período de Análisis

1. Modificar el `generate_series(0, 13)` en la consulta SQL (cambiar 13 por N-1 para N días)
2. Actualizar comentarios y documentación

### Agregar Nuevos Estados

1. Modificar la lógica de cálculo de `status_14d` en `OnboardingService.java`
2. Agregar estilos CSS para nuevos estados en `index.css`
3. Actualizar la tabla de resumen en `DriverTable.tsx`

### Agregar Endpoint de Resumen

Crear nuevo método en `OnboardingService` que agrupe por semana de `start_date`, canal y estado, luego exponerlo en `OnboardingController`.

## Troubleshooting

### Error de Conexión a BD
- Verificar que las credenciales en `application.properties` sean correctas
- Verificar que el servidor PostgreSQL esté accesible desde tu máquina
- Verificar firewall y configuración de red

### CORS Errors
- Verificar que `CorsConfig.java` permita el origen correcto (por defecto: `http://localhost:3000`)
- Verificar que el backend esté corriendo en el puerto 8080

### Errores de Columnas No Encontradas
- Usar los endpoints `/api/inspect/drivers` y `/api/inspect/summary-daily` para ver la estructura real
- Ajustar los nombres de columnas en la consulta SQL de `OnboardingService.java`

## Licencia

Proyecto interno de Yego.













