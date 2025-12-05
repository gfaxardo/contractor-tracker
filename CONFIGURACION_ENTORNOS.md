# Configuración Automática de Entornos

## Detección Automática

El frontend ahora detecta automáticamente el entorno y usa la URL correcta:

### Desarrollo Local
- **Hostname**: `localhost`, `127.0.0.1`, o `10.10.5.17`
- **URL Backend**: `http://localhost:8080/api`

### Producción
- **Hostname**: `5.161.86.63`
- **URL Backend**: `http://5.161.86.63:8080/api`

## Cómo Funciona

El código en `frontend/src/services/api.ts` detecta automáticamente:
1. Si existe `VITE_API_BASE_URL` en variables de entorno → la usa
2. Si el `hostname` es `localhost`, `127.0.0.1`, o `10.10.5.17` → usa `http://localhost:8080/api`
3. Si el `hostname` es `5.161.86.63` → usa `http://5.161.86.63:8080/api`
4. Por defecto → usa `http://localhost:8080/api`

## Sobrescribir Manualmente

Si necesitas forzar una URL específica, crea un archivo `.env` en `frontend/`:

```bash
# frontend/.env
VITE_API_BASE_URL=http://tu-url-personalizada:8080/api
```

## Desarrollo

```bash
# En local, simplemente ejecuta:
cd frontend
npm run dev

# El frontend detectará automáticamente que está en localhost
# y usará http://localhost:8080/api
```

## Producción

```bash
# En producción, compila y sirve:
cd frontend
npm run build

# El frontend detectará automáticamente que está en 5.161.86.63
# y usará http://5.161.86.63:8080/api
```

## Verificación

Abre la consola del navegador y verifica:
- En local: `API_BASE_URL = http://localhost:8080/api`
- En producción: `API_BASE_URL = http://5.161.86.63:8080/api`

