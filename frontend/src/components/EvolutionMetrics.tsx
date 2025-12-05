import React, { useState, useEffect, useCallback, useRef } from 'react';
import { api, EvolutionMetrics } from '../services/api';

interface EvolutionMetricsProps {
  parkId?: string;
}

const EvolutionMetricsComponent: React.FC<EvolutionMetricsProps> = ({ parkId }) => {
  const [activeTab, setActiveTab] = useState<'weeks' | 'months'>('weeks');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [weeksData, setWeeksData] = useState<EvolutionMetrics[]>([]);
  const [monthsData, setMonthsData] = useState<EvolutionMetrics[]>([]);
  const loadingRef = useRef(false);
  const lastParkIdRef = useRef<string | undefined>(undefined);

  const cargarDatos = useCallback(async () => {
    if (loadingRef.current) {
      return;
    }

    if (lastParkIdRef.current === parkId) {
      return;
    }

    loadingRef.current = true;
    lastParkIdRef.current = parkId;
    setLoading(true);
    setError(null);
    
    try {
      const [weeks, months] = await Promise.all([
        api.getEvolutionMetrics(parkId, 'weeks', 4),
        api.getEvolutionMetrics(parkId, 'months', 4)
      ]);
      setWeeksData(weeks);
      setMonthsData(months);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos de evolución');
      lastParkIdRef.current = undefined;
    } finally {
      setLoading(false);
      loadingRef.current = false;
    }
  }, [parkId]);

  useEffect(() => {
    cargarDatos();
  }, [cargarDatos]);

  const obtenerColorTasa = (tasa: number): string => {
    if (tasa >= 70) return '#28a745';
    if (tasa >= 40) return '#ffc107';
    return '#dc3545';
  };

  const datosActuales = activeTab === 'weeks' ? weeksData : monthsData;

  if (loading) {
    return <div className="loading">Cargando evolución temporal...</div>;
  }

  if (error) {
    return <div className="error">Error: {error}</div>;
  }

  if (datosActuales.length === 0) {
    return null;
  }

  const maxTotal = Math.max(...datosActuales.map(d => d.totalDrivers), 0);
  const maxTasa = Math.max(...datosActuales.map(d => Math.max(d.tasaRegistroAConexion, d.tasaConexionAViaje, d.tasaAlcanzo1Viaje)), 0);
  
  const calcularConectados = (item: EvolutionMetrics) => {
    return item.soloRegistro + item.conectoSinViajes + item.activoConViajes > 0 
      ? item.conectoSinViajes + item.activoConViajes 
      : 0;
  };
  
  const calcularConViajes = (item: EvolutionMetrics) => {
    return item.activoConViajes;
  };

  return (
    <div style={{ 
      backgroundColor: '#f8f9fa', 
      padding: '12px', 
      borderRadius: '6px',
      border: '1px solid #e5e7eb'
    }}>
      <div style={{ marginBottom: '12px', display: 'flex', gap: '8px' }}>
        <button
          onClick={() => setActiveTab('weeks')}
          style={{
            padding: '8px 16px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            backgroundColor: activeTab === 'weeks' ? '#2563eb' : 'white',
            color: activeTab === 'weeks' ? 'white' : '#333',
            cursor: 'pointer',
            fontWeight: activeTab === 'weeks' ? 'bold' : 'normal',
            fontSize: '13px'
          }}
        >
          Últimas 4 Semanas
        </button>
        <button
          onClick={() => setActiveTab('months')}
          style={{
            padding: '8px 16px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            backgroundColor: activeTab === 'months' ? '#2563eb' : 'white',
            color: activeTab === 'months' ? 'white' : '#333',
            cursor: 'pointer',
            fontWeight: activeTab === 'months' ? 'bold' : 'normal',
            fontSize: '13px'
          }}
        >
          Últimos 4 Meses
        </button>
      </div>

      <div style={{ overflowX: 'auto', marginBottom: '12px' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '11px', backgroundColor: 'white', borderRadius: '4px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f3f4f6' }}>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #d1d5db', position: 'sticky', left: 0, backgroundColor: '#f3f4f6', fontSize: '11px' }}>
                      {activeTab === 'weeks' ? 'Semana' : 'Mes'}
                    </th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>Total</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>Solo Reg.</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>Conectó</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>Con Viajes</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>R→C %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>C→V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>1V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>5V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>25V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #d1d5db', fontSize: '11px' }}>Total Viajes</th>
                  </tr>
                </thead>
                <tbody>
                  {datosActuales.map((item, index) => (
                    <tr key={item.period} style={{ borderBottom: '1px solid #e5e7eb' }}>
                      <td style={{ padding: '8px', fontWeight: '500', position: 'sticky', left: 0, backgroundColor: 'white', fontSize: '11px' }}>
                        {item.period}
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{item.totalDrivers}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{item.soloRegistro}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{item.conectoSinViajes}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{item.activoConViajes}</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(item.tasaRegistroAConexion), fontWeight: '600', fontSize: '11px' }}>
                        {item.tasaRegistroAConexion.toFixed(1)}%
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(item.tasaConexionAViaje), fontWeight: '600', fontSize: '11px' }}>
                        {item.tasaConexionAViaje.toFixed(1)}%
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(item.tasaAlcanzo1Viaje), fontWeight: '600', fontSize: '11px' }}>
                        {item.tasaAlcanzo1Viaje.toFixed(1)}%
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(item.tasaAlcanzo5Viajes), fontWeight: '600', fontSize: '11px' }}>
                        {item.tasaAlcanzo5Viajes.toFixed(1)}%
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(item.tasaAlcanzo25Viajes), fontWeight: '600', fontSize: '11px' }}>
                        {item.tasaAlcanzo25Viajes.toFixed(1)}%
                      </td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{item.totalViajes}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '12px', marginTop: '12px' }}>
              <div style={{ backgroundColor: 'white', padding: '12px', borderRadius: '4px', border: '1px solid #e5e7eb' }}>
                <h4 style={{ marginBottom: '10px', fontSize: '12px', fontWeight: '600', color: '#374151' }}>
                  Embudo de Conversión por {activeTab === 'weeks' ? 'Semana' : 'Mes'}
                </h4>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px', height: '400px', padding: '10px 0' }}>
            {datosActuales.map((item) => {
              const total = item.totalDrivers;
              const conectados = calcularConectados(item);
              const conViajes = calcularConViajes(item);
              const alturaContenedor = 400;
              const alturaTotal = maxTotal > 0 ? (total / maxTotal) * alturaContenedor : 0;
              const alturaConectados = maxTotal > 0 ? (conectados / maxTotal) * alturaContenedor : 0;
              const alturaConViajes = maxTotal > 0 ? (conViajes / maxTotal) * alturaContenedor : 0;
              
              return (
              <div key={item.period} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px', height: '100%' }}>
                <div style={{ position: 'relative', width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
                  <div
                    style={{
                      width: '100%',
                      height: `${alturaTotal}px`,
                      backgroundColor: '#dcfce7',
                      border: '2px solid #86efac',
                      borderRadius: '4px 4px 0 0',
                      minHeight: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      position: 'relative',
                      marginBottom: '2px'
                    }}
                    title={`Total: ${total}`}
                  >
                    {alturaTotal > 30 && (
                      <span style={{ fontSize: `${Math.max(12, alturaTotal * 0.08)}px`, fontWeight: 'bold', color: '#166534' }}>{total}</span>
                    )}
                  </div>
                  {conectados > 0 && (
                    <div
                      style={{
                        width: '100%',
                        height: `${alturaConectados}px`,
                        backgroundColor: '#dbeafe',
                        border: '2px solid #93c5fd',
                        borderRadius: '4px',
                        minHeight: conectados > 0 ? '15px' : '0',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative',
                        marginBottom: '2px'
                      }}
                      title={`Conectados: ${conectados}`}
                    >
                      {alturaConectados > 20 && (
                        <span style={{ fontSize: `${Math.max(11, alturaConectados * 0.08)}px`, fontWeight: 'bold', color: '#1e40af' }}>{conectados}</span>
                      )}
                    </div>
                  )}
                  {conViajes > 0 && (
                    <div
                      style={{
                        width: '100%',
                        height: `${alturaConViajes}px`,
                        backgroundColor: '#fef3c7',
                        border: '2px solid #fde68a',
                        borderRadius: '4px',
                        minHeight: conViajes > 0 ? '10px' : '0',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative'
                      }}
                      title={`Con Viajes: ${conViajes}`}
                    >
                      {alturaConViajes > 15 && (
                        <span style={{ fontSize: `${Math.max(11, alturaConViajes * 0.08)}px`, fontWeight: 'bold', color: '#92400e' }}>{conViajes}</span>
                      )}
                    </div>
                  )}
                </div>
                <div style={{ fontSize: '14px', color: '#6b7280', marginTop: '5px', textAlign: 'center', writingMode: activeTab === 'weeks' ? 'horizontal-tb' : 'horizontal-tb', whiteSpace: 'nowrap' }}>
                  {item.period}
                </div>
              </div>
              );
            })}
          </div>
        </div>

              <div style={{ backgroundColor: 'white', padding: '12px', borderRadius: '4px', border: '1px solid #e5e7eb' }}>
                <h4 style={{ marginBottom: '10px', fontSize: '12px', fontWeight: '600', color: '#374151' }}>
                  Tasas de Conversión
                </h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', height: '400px', justifyContent: 'space-between' }}>
            {datosActuales.map((item) => {
              return (
              <div key={item.period} style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
                <div style={{ fontSize: '14px', width: '60px', color: '#6b7280', flexShrink: 0, fontWeight: '500' }}>{item.period}</div>
                <div style={{ flex: 1, display: 'flex', gap: '4px', height: '100%', position: 'relative', width: '100%' }}>
                  <div
                    style={{
                      flex: item.tasaRegistroAConexion,
                      backgroundColor: '#3b82f6',
                      borderRadius: '2px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      minWidth: '40px',
                      position: 'relative'
                    }}
                    title={`R→C: ${item.tasaRegistroAConexion.toFixed(1)}%`}
                  >
                    <span style={{ fontSize: 'clamp(12px, 2.5vw, 18px)', fontWeight: '600', color: 'white', whiteSpace: 'nowrap' }}>
                      R→C: {item.tasaRegistroAConexion.toFixed(1)}%
                    </span>
                  </div>
                  <div
                    style={{
                      flex: item.tasaConexionAViaje,
                      backgroundColor: '#10b981',
                      borderRadius: '2px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      minWidth: '40px',
                      position: 'relative'
                    }}
                    title={`C→V: ${item.tasaConexionAViaje.toFixed(1)}%`}
                  >
                    <span style={{ fontSize: 'clamp(12px, 2.5vw, 18px)', fontWeight: '600', color: 'white', whiteSpace: 'nowrap' }}>
                      C→V: {item.tasaConexionAViaje.toFixed(1)}%
                    </span>
                  </div>
                  <div
                    style={{
                      flex: item.tasaAlcanzo1Viaje,
                      backgroundColor: '#f59e0b',
                      borderRadius: '2px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      minWidth: '40px',
                      position: 'relative'
                    }}
                    title={`1V: ${item.tasaAlcanzo1Viaje.toFixed(1)}%`}
                  >
                    <span style={{ fontSize: 'clamp(12px, 2.5vw, 18px)', fontWeight: '600', color: 'white', whiteSpace: 'nowrap' }}>
                      1V: {item.tasaAlcanzo1Viaje.toFixed(1)}%
                    </span>
                  </div>
                </div>
              </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

export default EvolutionMetricsComponent;


