# Script para inicializar la tabla contractor_tracking_history
# Ejecutar cuando el servidor Spring Boot esté corriendo en localhost:8080

$uri = "http://localhost:8080/api/inspect/init-tracking-history-table"

Write-Host "Inicializando tabla contractor_tracking_history..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri $uri -Method POST -UseBasicParsing
    $content = $response.Content | ConvertFrom-Json
    Write-Host "Respuesta: $($content.message)" -ForegroundColor Green
    Write-Host "Estado: $($content.status)" -ForegroundColor Green
} catch {
    Write-Host "Error al conectar con el servidor. Asegúrate de que el servidor Spring Boot esté corriendo en localhost:8080" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}













