import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { api, ReconciliationSummary } from '../services/api';
import { formatWeekISO, getLastNWeeks } from '../utils/weekUtils';
import { Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, LineChart, Line, ComposedChart } from 'recharts';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

const ReconciliationDashboard: React.FC = () => {
  const [summaries, setSummaries] = useState<ReconciliationSummary[]>([]);
  const [dailyClosure, setDailyClosure] = useState<ReconciliationSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string | undefined>(undefined);
  
  const [viewMode, setViewMode] = useState<'last6weeks' | 'custom'>('last6weeks');
  const [periodType, setPeriodType] = useState<'day' | 'week'>('week');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [parkId, setParkId] = useState(DEFAULT_PARK_ID);
  const [scoutId, setScoutId] = useState('');
  const [channel, setChannel] = useState('');
  const [showTable, setShowTable] = useState(false);

  const cargarResumen = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      let filters: any = {
        periodType: 'week',
        parkId: parkId || DEFAULT_PARK_ID
      };

      if (viewMode === 'last6weeks') {
        const last6Weeks = getLastNWeeks(6);
        filters.weekISOs = last6Weeks;
      } else {
        if (dateFrom) filters.dateFrom = dateFrom;
        if (dateTo) filters.dateTo = dateTo;
        filters.periodType = periodType;
      }

      if (scoutId) filters.scoutId = scoutId;
      if (channel) filters.channel = channel;

      const data = await api.getReconciliationSummary(filters);
      setSummaries(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar resumen');
    } finally {
      setLoading(false);
    }
  }, [viewMode, periodType, dateFrom, dateTo, parkId, scoutId, channel]);

  const cargarCierreDiaAnterior = useCallback(async () => {
    try {
      const data = await api.getDailyClosure(parkId || DEFAULT_PARK_ID);
      setDailyClosure(data);
    } catch (err) {
      console.error('Error al cargar cierre del día anterior:', err);
    }
  }, [parkId]);

  const cargarUltimaActualizacion = useCallback(async () => {
    try {
      const status = await api.getDataLastUpdated();
      setLastUpdated(status.lastUpdated);
    } catch (err) {
      console.error('Error al cargar última actualización:', err);
    }
  }, []);

  useEffect(() => {
    let mounted = true;
    
    const loadData = async () => {
      if (mounted) {
        await Promise.all([
          cargarResumen(),
          cargarCierreDiaAnterior(),
          cargarUltimaActualizacion()
        ]);
      }
    };
    
    loadData();
    
    return () => {
      mounted = false;
    };
  }, [viewMode, periodType, dateFrom, dateTo, parkId, scoutId, channel]);

  const formatNumber = (num: number) => {
    return new Intl.NumberFormat('es-ES').format(num);
  };

  const formatPercent = (num: number) => {
    return num.toFixed(2) + '%';
  };

  const funnelData = useMemo(() => {
    return summaries.map(s => ({
      periodo: s.periodType === 'week' ? formatWeekISO(s.period) : s.period,
      'Registrados': s.totals.registrados,
      'Conectados': s.totals.conectados,
      'Con Viajes': s.totals.conViajes14d,
      'Milestone 1': s.totals.conMilestone1,
      'Milestone 5': s.totals.conMilestone5,
      'Milestone 25': s.totals.conMilestone25,
      'Con Pago': s.totals.conPagoYango
    }));
  }, [summaries]);

  const conversionData = useMemo(() => {
    return summaries.map(s => ({
      periodo: s.periodType === 'week' ? formatWeekISO(s.period) : s.period,
      'Tasa Conexión': s.conversionMetrics.tasaConexion,
      'Tasa Activación': s.conversionMetrics.tasaActivacion,
      'Tasa Pago': s.conversionMetrics.tasaPagoYango
    }));
  }, [summaries]);

  const totalSummary = summaries.reduce((acc, s) => ({
    registrados: acc.registrados + s.totals.registrados,
    porCabinet: acc.porCabinet + s.totals.porCabinet,
    porOtrosMedios: acc.porOtrosMedios + s.totals.porOtrosMedios,
    conectados: acc.conectados + s.totals.conectados,
    conViajes7d: acc.conViajes7d + s.totals.conViajes7d,
    conViajes14d: acc.conViajes14d + s.totals.conViajes14d,
    conMilestone1: acc.conMilestone1 + s.totals.conMilestone1,
    conMilestone5: acc.conMilestone5 + s.totals.conMilestone5,
    conMilestone25: acc.conMilestone25 + s.totals.conMilestone25,
    conPagoYango: acc.conPagoYango + s.totals.conPagoYango
  }), {
    registrados: 0,
    porCabinet: 0,
    porOtrosMedios: 0,
    conectados: 0,
    conViajes7d: 0,
    conViajes14d: 0,
    conMilestone1: 0,
    conMilestone5: 0,
    conMilestone25: 0,
    conPagoYango: 0
  });

  const avgConversion = summaries.length > 0 ? {
    tasaConexion: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaConexion, 0) / summaries.length,
    tasaActivacion: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaActivacion, 0) / summaries.length,
    tasaMilestone1: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaMilestone1, 0) / summaries.length,
    tasaMilestone5: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaMilestone5, 0) / summaries.length,
    tasaMilestone25: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaMilestone25, 0) / summaries.length,
    tasaPagoYango: summaries.reduce((acc, s) => acc + s.conversionMetrics.tasaPagoYango, 0) / summaries.length
  } : null;

  const renderMetricCard = (title: string, value: number | string, subtitle?: string, color?: string) => {
    return (
      <div style={{
        backgroundColor: 'white',
        padding: '20px',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        borderLeft: color ? `4px solid ${color}` : '4px solid #007bff'
      }}>
        <div style={{ fontSize: '14px', color: '#666', marginBottom: '8px' }}>{title}</div>
        <div style={{ fontSize: '32px', fontWeight: 'bold', color: color || '#007bff' }}>
          {typeof value === 'number' ? formatNumber(value) : value}
        </div>
        {subtitle && (
          <div style={{ fontSize: '12px', color: '#999', marginTop: '4px' }}>{subtitle}</div>
        )}
      </div>
    );
  };

  return (
    <div>
      <div className="container-header" style={{ marginBottom: '20px' }}>
        <h1>Vista Embudo Consolidada</h1>
        <p>Reconciliación completa de todos los puntos del sistema</p>
        {lastUpdated && (
          <p style={{ fontSize: '12px', color: '#666', marginTop: '5px' }}>
            Última actualización: {new Date(lastUpdated).toLocaleString('es-ES')}
          </p>
        )}
      </div>

      {error && (
        <div className="error" style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#fee', border: '1px solid #fcc', borderRadius: '4px', color: '#c33' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px' 
      }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '15px', marginBottom: '15px' }}>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Vista:</label>
            <select
              value={viewMode}
              onChange={(e) => {
                setViewMode(e.target.value as 'last6weeks' | 'custom');
                if (e.target.value === 'last6weeks') {
                  setDateFrom('');
                  setDateTo('');
                }
              }}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="last6weeks">Últimas 6 Semanas</option>
              <option value="custom">Rango Personalizado</option>
            </select>
          </div>

          {viewMode === 'custom' && (
            <>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Tipo de Período:</label>
                <select
                  value={periodType}
                  onChange={(e) => setPeriodType(e.target.value as 'day' | 'week')}
                  style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                >
                  <option value="day">Por Día</option>
                  <option value="week">Por Semana</option>
                </select>
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Desde:</label>
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Hasta:</label>
                <input
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                />
              </div>
            </>
          )}

          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Park ID:</label>
            <input
              type="text"
              value={parkId}
              onChange={(e) => setParkId(e.target.value)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Scout ID:</label>
            <input
              type="text"
              value={scoutId}
              onChange={(e) => setScoutId(e.target.value)}
              placeholder="Opcional"
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Canal:</label>
            <select
              value={channel}
              onChange={(e) => setChannel(e.target.value)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="cabinet">Cabinet</option>
              <option value="otros">Otros</option>
            </select>
          </div>
        </div>
      </div>

      {dailyClosure && (
        <div style={{ 
          marginBottom: '20px', 
          padding: '20px', 
          backgroundColor: '#e7f3ff', 
          borderRadius: '8px',
          border: '2px solid #007bff'
        }}>
          <h2 style={{ marginTop: 0, color: '#0056b3' }}>Cierre del Día Anterior ({new Date(dailyClosure.period).toLocaleDateString('es-ES')})</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '15px' }}>
            {renderMetricCard('Registrados', dailyClosure.totals.registrados)}
            {renderMetricCard('Por Cabinet', dailyClosure.totals.porCabinet, `${formatPercent((dailyClosure.totals.porCabinet / dailyClosure.totals.registrados) * 100)} del total`)}
            {renderMetricCard('Conectados', dailyClosure.totals.conectados, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaConexion)}`, '#28a745')}
            {renderMetricCard('Con Viajes 14d', dailyClosure.totals.conViajes14d, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaActivacion)}`, '#17a2b8')}
            {renderMetricCard('Milestone 1', dailyClosure.totals.conMilestone1, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaMilestone1)}`)}
            {renderMetricCard('Milestone 5', dailyClosure.totals.conMilestone5, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaMilestone5)}`)}
            {renderMetricCard('Milestone 25', dailyClosure.totals.conMilestone25, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaMilestone25)}`)}
            {renderMetricCard('Con Pago Yango', dailyClosure.totals.conPagoYango, `Tasa: ${formatPercent(dailyClosure.conversionMetrics.tasaPagoYango)}`, '#ffc107')}
          </div>
        </div>
      )}

      {summaries.length > 0 && (
        <>
          <div style={{ marginBottom: '20px' }}>
            <h2>Resumen Consolidado</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '15px', marginBottom: '20px' }}>
              {renderMetricCard('Total Registrados', totalSummary.registrados)}
              {renderMetricCard('Por Cabinet', totalSummary.porCabinet, `${formatPercent((totalSummary.porCabinet / totalSummary.registrados) * 100)} del total`)}
              {renderMetricCard('Por Otros Medios', totalSummary.porOtrosMedios, `${formatPercent((totalSummary.porOtrosMedios / totalSummary.registrados) * 100)} del total`)}
              {renderMetricCard('Conectados', totalSummary.conectados, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaConexion)}` : undefined, '#28a745')}
              {renderMetricCard('Con Viajes 7d', totalSummary.conViajes7d)}
              {renderMetricCard('Con Viajes 14d', totalSummary.conViajes14d, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaActivacion)}` : undefined, '#17a2b8')}
              {renderMetricCard('Milestone 1', totalSummary.conMilestone1, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaMilestone1)}` : undefined)}
              {renderMetricCard('Milestone 5', totalSummary.conMilestone5, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaMilestone5)}` : undefined)}
              {renderMetricCard('Milestone 25', totalSummary.conMilestone25, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaMilestone25)}` : undefined)}
              {renderMetricCard('Con Pago Yango', totalSummary.conPagoYango, avgConversion ? `Tasa Promedio: ${formatPercent(avgConversion.tasaPagoYango)}` : undefined, '#ffc107')}
            </div>
          </div>

          <div style={{ marginBottom: '30px', backgroundColor: 'white', padding: '20px', borderRadius: '8px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
            <h2 style={{ marginTop: 0 }}>Gráfico de Embudo Comparativo</h2>
            <ResponsiveContainer width="100%" height={400}>
              <ComposedChart data={funnelData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="periodo" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="Registrados" fill="#007bff" />
                <Bar dataKey="Conectados" fill="#28a745" />
                <Bar dataKey="Con Viajes" fill="#17a2b8" />
                <Bar dataKey="Milestone 1" fill="#ffc107" />
                <Bar dataKey="Milestone 5" fill="#fd7e14" />
                <Bar dataKey="Milestone 25" fill="#dc3545" />
                <Bar dataKey="Con Pago" fill="#6f42c1" />
              </ComposedChart>
            </ResponsiveContainer>
          </div>

          <div style={{ marginBottom: '30px', backgroundColor: 'white', padding: '20px', borderRadius: '8px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
            <h2 style={{ marginTop: 0 }}>Tasas de Conversión</h2>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={conversionData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="periodo" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="Tasa Conexión" stroke="#28a745" strokeWidth={2} />
                <Line type="monotone" dataKey="Tasa Activación" stroke="#17a2b8" strokeWidth={2} />
                <Line type="monotone" dataKey="Tasa Pago" stroke="#ffc107" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div style={{ marginBottom: '20px' }}>
            <button
              onClick={() => setShowTable(!showTable)}
              style={{
                padding: '10px 20px',
                backgroundColor: '#007bff',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '14px',
                fontWeight: 'bold'
              }}
            >
              {showTable ? 'Ocultar' : 'Mostrar'} Tabla Detallada
            </button>
          </div>

          {showTable && (
            <div style={{ overflowX: 'auto', backgroundColor: 'white', padding: '20px', borderRadius: '8px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                <thead style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                  <tr>
                    <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Período</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Registrados</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Cabinet</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Otros</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Conectados</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Viajes 7d</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Viajes 14d</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>M1</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>M5</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>M25</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Pago Yango</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Tasa Conexión</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Tasa Activación</th>
                    <th style={{ padding: '10px', textAlign: 'right', borderBottom: '2px solid #dee2e6' }}>Tasa Pago</th>
                  </tr>
                </thead>
                <tbody>
                  {summaries.map((summary, idx) => (
                    <tr key={idx} style={{ borderBottom: '1px solid #dee2e6' }}>
                      <td style={{ padding: '10px' }}>{summary.periodType === 'week' ? formatWeekISO(summary.period) : summary.period}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.registrados)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.porCabinet)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.porOtrosMedios)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conectados)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conViajes7d)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conViajes14d)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conMilestone1)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conMilestone5)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conMilestone25)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatNumber(summary.totals.conPagoYango)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatPercent(summary.conversionMetrics.tasaConexion)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatPercent(summary.conversionMetrics.tasaActivacion)}</td>
                      <td style={{ padding: '10px', textAlign: 'right' }}>{formatPercent(summary.conversionMetrics.tasaPagoYango)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {loading && (
        <div className="loading">Cargando datos...</div>
      )}

      {!loading && summaries.length === 0 && (
        <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
          No se encontraron datos para los filtros seleccionados.
        </div>
      )}
    </div>
  );
};

export default ReconciliationDashboard;
