import React, { useState, useEffect } from 'react';
import { api, Scout } from '../services/api';
import { getPreviousWeek, getNextWeek, formatWeekRange } from '../utils/weekUtils';

interface ScoutAffiliationControl {
  registrationId: number;
  scoutId: string;
  scoutName: string;
  registrationDate: string;
  driverLicense: string | null;
  driverName: string;
  driverPhone: string | null;
  acquisitionMedium: string | null;
  driverId: string | null;
  isMatched: boolean;
  matchScore: number | null;
  milestoneType7d: number | null;
  tripCount7d: number | null;
  milestoneFulfillmentDate7d: string | null;
  hasYangoPayment: boolean;
  yangoPaymentAmount: number | null;
  yangoPaymentDate: string | null;
  yangoTransactionId: number | null;
}

const ScoutAffiliationControl: React.FC = () => {
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [data, setData] = useState<ScoutAffiliationControl[]>([]);
  const [count, setCount] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Filtros básicos
  const [selectedScoutId, setSelectedScoutId] = useState<string>('');
  const [weekISO, setWeekISO] = useState<string>('');
  const [fechaInicio, setFechaInicio] = useState<string>('');
  const [fechaFin, setFechaFin] = useState<string>('');
  
  // Filtros avanzados
  const [milestoneType, setMilestoneType] = useState<string>('');
  const [isMatched, setIsMatched] = useState<string>('');
  const [hasYangoPayment, setHasYangoPayment] = useState<string>('');
  const [acquisitionMedium, setAcquisitionMedium] = useState<string>('');
  const [driverName, setDriverName] = useState<string>('');
  const [driverPhone, setDriverPhone] = useState<string>('');
  const [amountMin, setAmountMin] = useState<string>('');
  const [amountMax, setAmountMax] = useState<string>('');
  
  const [showAdvancedFilters, setShowAdvancedFilters] = useState<boolean>(false);

  useEffect(() => {
    cargarScouts();
  }, []);

  const cargarScouts = async () => {
    try {
      const scoutsData = await api.getScouts();
      setScouts(scoutsData);
    } catch (err) {
      console.error('Error al cargar scouts:', err);
    }
  };

  const cargarDatos = async () => {
    setLoading(true);
    setError(null);
    try {
      const filters: any = {
        scoutId: selectedScoutId || undefined,
        fechaInicio: (!weekISO && fechaInicio) ? fechaInicio : undefined,
        fechaFin: (!weekISO && fechaFin) ? fechaFin : undefined,
        weekISO: weekISO || undefined,
        milestoneType: milestoneType ? parseInt(milestoneType) : undefined,
        isMatched: isMatched !== '' ? isMatched === 'true' : undefined,
        hasYangoPayment: hasYangoPayment !== '' ? hasYangoPayment === 'true' : undefined,
        acquisitionMedium: acquisitionMedium || undefined,
        driverName: driverName || undefined,
        driverPhone: driverPhone || undefined,
        amountMin: amountMin ? parseFloat(amountMin) : undefined,
        amountMax: amountMax ? parseFloat(amountMax) : undefined,
      };
      
      const result = await api.getScoutAffiliationControl(filters);
      setData(result.data);
      setCount(result.count);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
      setData([]);
      setCount(0);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    cargarDatos();
  };

  const handlePreviousWeek = () => {
    if (weekISO) {
      const prevWeek = getPreviousWeek(weekISO);
      setWeekISO(prevWeek);
    }
  };

  const handleNextWeek = () => {
    if (weekISO) {
      const nextWeek = getNextWeek(weekISO);
      setWeekISO(nextWeek);
    }
  };

  const handleWeekISOInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    if (value.match(/^\d{4}-W\d{2}$/)) {
      const [yearStr, weekStr] = value.split('-W');
      const year = parseInt(yearStr, 10);
      const week = parseInt(weekStr, 10);
      
      if (!isNaN(year) && !isNaN(week) && week >= 1 && week <= 53 && year >= 2000 && year <= 2100) {
        setWeekISO(value);
      }
    } else if (value === '') {
      setWeekISO('');
    }
  };

  const formatearFecha = (fecha: string | null) => {
    if (!fecha) return 'N/A';
    return new Date(fecha).toLocaleDateString('es-ES');
  };

  const formatearMonto = (monto: number | null) => {
    if (monto === null) return 'N/A';
    return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(monto);
  };

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <h2 style={{ marginBottom: '20px' }}>Control de Afiliaciones de Scouts</h2>

      {/* Contador de registros */}
      {!loading && count > 0 && (
        <div style={{ 
          marginBottom: '20px', 
          padding: '12px', 
          backgroundColor: '#e7f3ff', 
          borderRadius: '8px',
          border: '1px solid #b3d9ff',
          fontWeight: 'bold',
          fontSize: '16px',
          color: '#004085'
        }}>
          {count} registro{count !== 1 ? 's' : ''} encontrado{count !== 1 ? 's' : ''}
        </div>
      )}

      {/* Filtros básicos */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px',
      }}>
        <div style={{ display: 'flex', gap: '15px', flexWrap: 'wrap', alignItems: 'end', marginBottom: '15px' }}>
          <div style={{ flex: 1, minWidth: '200px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Scout:</label>
            <select
              value={selectedScoutId}
              onChange={(e) => setSelectedScoutId(e.target.value)}
              style={{ 
                width: '100%', 
                padding: '8px', 
                fontSize: '14px', 
                border: '1px solid #ddd', 
                borderRadius: '4px' 
              }}
            >
              <option value="">Todos los scouts</option>
              {scouts.map((scout) => (
                <option key={scout.scoutId} value={scout.scoutId}>
                  {scout.scoutName}
                </option>
              ))}
            </select>
          </div>

          <div style={{ flex: 1, minWidth: '200px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Semana ISO:</label>
            <div style={{ display: 'flex', gap: '5px', alignItems: 'center' }}>
              <button
                onClick={handlePreviousWeek}
                disabled={!weekISO}
                style={{ 
                  padding: '8px 12px', 
                  fontSize: '14px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  backgroundColor: weekISO ? '#fff' : '#f5f5f5',
                  cursor: weekISO ? 'pointer' : 'not-allowed',
                  opacity: weekISO ? 1 : 0.5
                }}
              >
                ←
              </button>
              <input
                type="text"
                placeholder="2025-W01"
                value={weekISO}
                onChange={handleWeekISOInputChange}
                style={{ 
                  flex: 1,
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              />
              <button
                onClick={handleNextWeek}
                disabled={!weekISO}
                style={{ 
                  padding: '8px 12px', 
                  fontSize: '14px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  backgroundColor: weekISO ? '#fff' : '#f5f5f5',
                  cursor: weekISO ? 'pointer' : 'not-allowed',
                  opacity: weekISO ? 1 : 0.5
                }}
              >
                →
              </button>
            </div>
            {weekISO && (
              <div style={{ fontSize: '12px', color: '#666', marginTop: '5px' }}>
                {formatWeekRange(weekISO)}
              </div>
            )}
          </div>

          <div style={{ flex: 1, minWidth: '150px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Inicio:</label>
            <input
              type="date"
              value={fechaInicio}
              onChange={(e) => setFechaInicio(e.target.value)}
              disabled={!!weekISO}
              style={{ 
                width: '100%', 
                padding: '8px', 
                fontSize: '14px', 
                border: '1px solid #ddd', 
                borderRadius: '4px',
                backgroundColor: weekISO ? '#f5f5f5' : '#fff',
                cursor: weekISO ? 'not-allowed' : 'text'
              }}
            />
          </div>

          <div style={{ flex: 1, minWidth: '150px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Fin:</label>
            <input
              type="date"
              value={fechaFin}
              onChange={(e) => setFechaFin(e.target.value)}
              disabled={!!weekISO}
              style={{ 
                width: '100%', 
                padding: '8px', 
                fontSize: '14px', 
                border: '1px solid #ddd', 
                borderRadius: '4px',
                backgroundColor: weekISO ? '#f5f5f5' : '#fff',
                cursor: weekISO ? 'not-allowed' : 'text'
              }}
            />
          </div>

          <div>
            <button
              onClick={handleSearch}
              className="btn"
              style={{ padding: '8px 16px' }}
            >
              Buscar
            </button>
          </div>
        </div>

        {/* Botón para mostrar/ocultar filtros avanzados */}
        <button
          onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
          style={{
            padding: '8px 16px',
            fontSize: '14px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            backgroundColor: showAdvancedFilters ? '#007bff' : '#fff',
            color: showAdvancedFilters ? '#fff' : '#000',
            cursor: 'pointer'
          }}
        >
          {showAdvancedFilters ? '▼ Ocultar Filtros Avanzados' : '▶ Mostrar Filtros Avanzados'}
        </button>

        {/* Filtros avanzados */}
        {showAdvancedFilters && (
          <div style={{ 
            marginTop: '15px', 
            padding: '15px', 
            backgroundColor: '#fff', 
            borderRadius: '8px',
            border: '1px solid #ddd',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '15px'
          }}>
            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Tipo de Milestone:</label>
              <select
                value={milestoneType}
                onChange={(e) => setMilestoneType(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              >
                <option value="">Todos</option>
                <option value="1">M1</option>
                <option value="5">M5</option>
                <option value="25">M25</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Estado de Match:</label>
              <select
                value={isMatched}
                onChange={(e) => setIsMatched(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              >
                <option value="">Todos</option>
                <option value="true">Sí</option>
                <option value="false">No</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Pago Yango:</label>
              <select
                value={hasYangoPayment}
                onChange={(e) => setHasYangoPayment(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              >
                <option value="">Todos</option>
                <option value="true">Sí</option>
                <option value="false">No</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Medio de Adquisición:</label>
              <select
                value={acquisitionMedium}
                onChange={(e) => setAcquisitionMedium(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              >
                <option value="">Todos</option>
                <option value="FLEET">FLEET</option>
                <option value="CABINET">CABINET</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Nombre del Conductor:</label>
              <input
                type="text"
                placeholder="Buscar por nombre..."
                value={driverName}
                onChange={(e) => setDriverName(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Teléfono:</label>
              <input
                type="text"
                placeholder="Buscar por teléfono..."
                value={driverPhone}
                onChange={(e) => setDriverPhone(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Monto Mínimo:</label>
              <input
                type="number"
                placeholder="0.00"
                step="0.01"
                value={amountMin}
                onChange={(e) => setAmountMin(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold', fontSize: '13px' }}>Monto Máximo:</label>
              <input
                type="number"
                placeholder="999999.99"
                step="0.01"
                value={amountMax}
                onChange={(e) => setAmountMax(e.target.value)}
                style={{ 
                  width: '100%', 
                  padding: '8px', 
                  fontSize: '14px', 
                  border: '1px solid #ddd', 
                  borderRadius: '4px' 
                }}
              />
            </div>
          </div>
        )}
      </div>

      {error && (
        <div className="error" style={{ marginBottom: '15px' }}>
          Error: {error}
        </div>
      )}

      {loading ? (
        <div className="loading">Cargando datos...</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8f9fa' }}>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Scout</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Conductor</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Teléfono</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Licencia</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha Afiliación</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Medio</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Match</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Milestone 7d</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Viajes 7d</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Pago Yango</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Monto</th>
              </tr>
            </thead>
            <tbody>
              {data.length === 0 ? (
                <tr>
                  <td colSpan={11} style={{ padding: '20px', textAlign: 'center', color: '#666' }}>
                    No hay datos para mostrar. Aplica filtros y haz clic en "Buscar".
                  </td>
                </tr>
              ) : (
                data.map((item) => (
                  <tr key={item.registrationId} style={{ borderBottom: '1px solid #dee2e6' }}>
                    <td style={{ padding: '12px' }}>{item.scoutName}</td>
                    <td style={{ padding: '12px' }}>{item.driverName}</td>
                    <td style={{ padding: '12px' }}>{item.driverPhone || 'N/A'}</td>
                    <td style={{ padding: '12px' }}>{item.driverLicense || 'N/A'}</td>
                    <td style={{ padding: '12px' }}>{formatearFecha(item.registrationDate)}</td>
                    <td style={{ padding: '12px' }}>{item.acquisitionMedium || 'N/A'}</td>
                    <td style={{ padding: '12px' }}>
                      <span style={{ 
                        color: item.isMatched ? '#28a745' : '#dc3545',
                        fontWeight: 'bold'
                      }}>
                        {item.isMatched ? 'Sí' : 'No'}
                      </span>
                      {item.matchScore !== null && (
                        <span style={{ fontSize: '12px', color: '#666', marginLeft: '5px' }}>
                          ({Math.round(item.matchScore * 100)}%)
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '12px' }}>
                      {item.milestoneType7d !== null ? `M${item.milestoneType7d}` : 'N/A'}
                    </td>
                    <td style={{ padding: '12px' }}>{item.tripCount7d ?? 'N/A'}</td>
                    <td style={{ padding: '12px' }}>
                      <span style={{ 
                        color: item.hasYangoPayment ? '#28a745' : '#dc3545',
                        fontWeight: 'bold'
                      }}>
                        {item.hasYangoPayment ? 'Sí' : 'No'}
                      </span>
                    </td>
                    <td style={{ padding: '12px' }}>
                      {item.hasYangoPayment ? formatearMonto(item.yangoPaymentAmount) : 'N/A'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default ScoutAffiliationControl;
