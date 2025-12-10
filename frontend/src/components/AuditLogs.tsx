import React, { useState, useEffect } from 'react';
import { api, AuditLog } from '../services/api';
import './AuditLogs.css';

const AuditLogs: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    username: '',
    endpoint: '',
    method: '',
    fechaDesde: '',
    fechaHasta: ''
  });
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [stats, setStats] = useState<{
    totalLogs: number;
    uniqueUsers: number;
    uniqueEndpoints: number;
    methodCounts: Record<string, number>;
    statusCounts: Record<string, number>;
  } | null>(null);

  const cargarLogs = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const fechaDesde = filters.fechaDesde ? new Date(filters.fechaDesde).toISOString() : undefined;
      const fechaHasta = filters.fechaHasta ? new Date(filters.fechaHasta + 'T23:59:59').toISOString() : undefined;
      
      const response = await api.getAuditLogs({
        username: filters.username || undefined,
        endpoint: filters.endpoint || undefined,
        method: filters.method || undefined,
        fechaDesde,
        fechaHasta,
        page: currentPage,
        size: pageSize
      });
      
      setLogs(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar logs de auditoría');
      console.error('Error al cargar logs:', err);
    } finally {
      setLoading(false);
    }
  };

  const cargarStats = async () => {
    try {
      const fechaDesde = filters.fechaDesde ? new Date(filters.fechaDesde).toISOString() : undefined;
      const fechaHasta = filters.fechaHasta ? new Date(filters.fechaHasta + 'T23:59:59').toISOString() : undefined;
      
      const response = await api.getAuditStats({
        fechaDesde,
        fechaHasta
      });
      
      setStats(response);
    } catch (err) {
      console.error('Error al cargar estadísticas:', err);
    }
  };

  useEffect(() => {
    cargarLogs();
    cargarStats();
  }, [currentPage, pageSize]);

  const handleFilterChange = (field: string, value: string) => {
    setFilters(prev => ({ ...prev, [field]: value }));
    setCurrentPage(0);
  };

  const handleSearch = () => {
    setCurrentPage(0);
    cargarLogs();
    cargarStats();
  };

  const handleClearFilters = () => {
    setFilters({
      username: '',
      endpoint: '',
      method: '',
      fechaDesde: '',
      fechaHasta: ''
    });
    setCurrentPage(0);
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('es-ES', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const getStatusColor = (status?: number) => {
    if (!status) return '';
    if (status >= 200 && status < 300) return 'status-success';
    if (status >= 400 && status < 500) return 'status-warning';
    if (status >= 500) return 'status-error';
    return '';
  };

  return (
    <div className="audit-logs-container">
      <h1>Logs de Auditoría</h1>
      
      {stats && (
        <div className="audit-stats">
          <div className="stat-card">
            <div className="stat-label">Total de Logs</div>
            <div className="stat-value">{stats.totalLogs}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Usuarios Únicos</div>
            <div className="stat-value">{stats.uniqueUsers}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Endpoints Únicos</div>
            <div className="stat-value">{stats.uniqueEndpoints}</div>
          </div>
        </div>
      )}

      <div className="audit-filters">
        <div className="filter-row">
          <div className="filter-group">
            <label>Usuario</label>
            <input
              type="text"
              value={filters.username}
              onChange={(e) => handleFilterChange('username', e.target.value)}
              placeholder="Filtrar por usuario"
            />
          </div>
          <div className="filter-group">
            <label>Endpoint</label>
            <input
              type="text"
              value={filters.endpoint}
              onChange={(e) => handleFilterChange('endpoint', e.target.value)}
              placeholder="Filtrar por endpoint"
            />
          </div>
          <div className="filter-group">
            <label>Método</label>
            <select
              value={filters.method}
              onChange={(e) => handleFilterChange('method', e.target.value)}
            >
              <option value="">Todos</option>
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
              <option value="PATCH">PATCH</option>
            </select>
          </div>
        </div>
        <div className="filter-row">
          <div className="filter-group">
            <label>Fecha Desde</label>
            <input
              type="date"
              value={filters.fechaDesde}
              onChange={(e) => handleFilterChange('fechaDesde', e.target.value)}
            />
          </div>
          <div className="filter-group">
            <label>Fecha Hasta</label>
            <input
              type="date"
              value={filters.fechaHasta}
              onChange={(e) => handleFilterChange('fechaHasta', e.target.value)}
            />
          </div>
          <div className="filter-actions">
            <button onClick={handleSearch} className="btn-search">Buscar</button>
            <button onClick={handleClearFilters} className="btn-clear">Limpiar</button>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="audit-table-container">
        <div className="table-header">
          <div>Total: {totalElements} registros</div>
          <div>
            <label>Registros por página: </label>
            <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(0); }}>
              <option value="25">25</option>
              <option value="50">50</option>
              <option value="100">100</option>
              <option value="200">200</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div className="loading">Cargando logs...</div>
        ) : (
          <>
            <table className="audit-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Usuario</th>
                  <th>Método</th>
                  <th>Endpoint</th>
                  <th>Estado</th>
                  <th>IP</th>
                  <th>Fecha/Hora</th>
                  <th>Error</th>
                </tr>
              </thead>
              <tbody>
                {logs.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="no-data">No se encontraron logs</td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id}>
                      <td>{log.id}</td>
                      <td>{log.username || 'anonymous'}</td>
                      <td><span className={`method-badge method-${log.method}`}>{log.method}</span></td>
                      <td className="endpoint-cell" title={log.endpoint}>{log.endpoint}</td>
                      <td>
                        {log.responseStatus && (
                          <span className={`status-badge ${getStatusColor(log.responseStatus)}`}>
                            {log.responseStatus}
                          </span>
                        )}
                      </td>
                      <td>{log.ipAddress || '-'}</td>
                      <td>{formatDate(log.timestamp)}</td>
                      <td>
                        {log.errorMessage && (
                          <span className="error-badge" title={log.errorMessage}>⚠️</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            {totalPages > 1 && (
              <div className="pagination">
                <button
                  onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                  disabled={currentPage === 0}
                >
                  Anterior
                </button>
                <span>
                  Página {currentPage + 1} de {totalPages}
                </span>
                <button
                  onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                  disabled={currentPage >= totalPages - 1}
                >
                  Siguiente
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default AuditLogs;






