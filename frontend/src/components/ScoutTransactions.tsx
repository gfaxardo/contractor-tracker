import React, { useState, useEffect } from 'react';
import { api, YangoTransaction, Scout, YangoTransactionFilters } from '../services/api';

const ScoutTransactions: React.FC = () => {
  const [transactions, setTransactions] = useState<YangoTransaction[]>([]);
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [filters, setFilters] = useState<YangoTransactionFilters>({});

  useEffect(() => {
    cargarScouts().catch((err) => {
      console.error('Error no manejado al cargar scouts:', err);
    });
  }, []);

  useEffect(() => {
    cargarTransacciones().catch((err) => {
      console.error('Error no manejado al cargar transacciones:', err);
    });
  }, [filters]);

  const cargarScouts = async () => {
    try {
      const data = await api.getScouts();
      setScouts(data);
    } catch (err) {
      console.error('Error al cargar scouts:', err);
    }
  };

  const cargarTransacciones = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getYangoTransactions(filters);
      setTransactions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar transacciones');
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('es-ES');
  };

  const getScoutName = (scoutId: string) => {
    const scout = scouts.find(s => s.scoutId === scoutId);
    return scout?.scoutName || scoutId;
  };

  return (
    <div className="card">
      <h2 className="card-title">Transacciones Yango</h2>

      {error && (
        <div className="error">
          Error: {error}
        </div>
      )}

      <div className="filter-panel">
        <h3 style={{ marginBottom: '12px', fontSize: '16px', fontWeight: '600' }}>Filtros</h3>
        <div className="filter-form">
          <select
            value={filters.scoutId || ''}
            onChange={(e) => setFilters({ ...filters, scoutId: e.target.value || undefined })}
            className="form-group select"
          >
            <option value="">Todos los scouts</option>
            {scouts.map(scout => (
              <option key={scout.scoutId} value={scout.scoutId}>{scout.scoutName}</option>
            ))}
          </select>
          
          <input
            type="date"
            placeholder="Fecha desde"
            value={filters.dateFrom || ''}
            onChange={(e) => setFilters({ ...filters, dateFrom: e.target.value || undefined })}
            className="form-group input"
          />
          
          <input
            type="date"
            placeholder="Fecha hasta"
            value={filters.dateTo || ''}
            onChange={(e) => setFilters({ ...filters, dateTo: e.target.value || undefined })}
            className="form-group input"
          />
          
          <select
            value={filters.milestoneType?.toString() || ''}
            onChange={(e) => setFilters({ ...filters, milestoneType: e.target.value ? parseInt(e.target.value) : undefined })}
            className="form-group select"
          >
            <option value="">Todos los milestones</option>
            <option value="1">Milestone 1</option>
            <option value="5">Milestone 5</option>
            <option value="25">Milestone 25</option>
          </select>
          
          <select
            value={filters.isMatched === undefined ? '' : filters.isMatched.toString()}
            onChange={(e) => setFilters({ ...filters, isMatched: e.target.value === '' ? undefined : e.target.value === 'true' })}
            className="form-group select"
          >
            <option value="">Todos</option>
            <option value="true">Matcheadas</option>
            <option value="false">Sin match</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="loading">Cargando transacciones...</div>
      ) : (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Fecha</th>
                <th>Scout</th>
                <th>Driver</th>
                <th>Milestone</th>
                <th>Monto Yango</th>
                <th>Match</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((trans) => (
                <tr key={trans.id}>
                  <td>{trans.id}</td>
                  <td>{formatDate(trans.transactionDate)}</td>
                  <td>{getScoutName(trans.scoutId)}</td>
                  <td>{trans.driverNameFromComment || 'N/A'}</td>
                  <td>{trans.milestoneType || 'N/A'}</td>
                  <td>S/ {trans.amountYango.toFixed(2)}</td>
                  <td>
                    <span style={{ 
                      color: trans.isMatched ? '#059669' : '#dc2626',
                      fontWeight: '600'
                    }}>
                      {trans.isMatched ? '✓' : '✗'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default ScoutTransactions;

