import React, { useState, useEffect, useRef } from 'react';
import { api, Scout, ScoutWeeklyView } from '../services/api';
import { getCurrentWeekISO, getPreviousWeek, getNextWeek, formatWeekRange, formatWeekISO, getWeekRange } from '../utils/weekUtils';

type TabType = 'weekly' | 'daily' | 'historical';

const ScoutPaymentInstances: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('weekly');
  
  const [currentWeek, setCurrentWeek] = useState(getCurrentWeekISO());
  const [currentDate, setCurrentDate] = useState(new Date().toISOString().split('T')[0]);
  const [historicalMonths, setHistoricalMonths] = useState(3);
  const [historicalOffset, setHistoricalOffset] = useState(0);
  const [historicalHasMore, setHistoricalHasMore] = useState(false);
  const [historicalLoadingMore, setHistoricalLoadingMore] = useState(false);
  
  const [data, setData] = useState<ScoutWeeklyView[]>([]);
  const [historicalData, setHistoricalData] = useState<ScoutWeeklyView[]>([]);
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedDrivers, setSelectedDrivers] = useState<Set<string>>(new Set());
  const [fechaUsadaDiaria, setFechaUsadaDiaria] = useState<string | null>(null);
  const [_hizoFallbackDiaria, setHizoFallbackDiaria] = useState(false);
  const [filters, setFilters] = useState({
    scoutId: '',
    hasConnection: '',
    reachedMilestone: '',
    isEligible: '',
    scoutReached8: '',
    status: ''
  });
  const [weekSelector, setWeekSelector] = useState('');
  const [dateSelector, setDateSelector] = useState('');
  
  const observerRef = useRef<IntersectionObserver | null>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    cargarScouts().catch((err) => {
      console.error('Error al cargar scouts:', err);
    });
  }, []);

  useEffect(() => {
    if (activeTab === 'weekly') {
      cargarDatosSemanal();
    } else if (activeTab === 'daily') {
      cargarDatosDiario();
    } else if (activeTab === 'historical') {
      cargarDatosHistorico(true);
    }
  }, [activeTab, currentWeek, currentDate, historicalMonths, filters.scoutId]);

  useEffect(() => {
    if (activeTab === 'historical' && loadMoreRef.current) {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
      
      observerRef.current = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting && historicalHasMore && !historicalLoadingMore) {
            cargarMasHistorico();
          }
        },
        { threshold: 0.1 }
      );
      
      observerRef.current.observe(loadMoreRef.current);
      
      return () => {
        if (observerRef.current) {
          observerRef.current.disconnect();
        }
      };
    }
  }, [activeTab, historicalHasMore, historicalLoadingMore]);

  const cargarScouts = async () => {
    try {
      const data = await api.getScouts();
      setScouts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar scouts');
    }
  };

  const cargarDatosSemanal = async () => {
    setLoading(true);
    setError(null);
    try {
      const vista = await api.getScoutWeeklyPaymentView(currentWeek, filters.scoutId || undefined);
      setData(vista);
      setSelectedDrivers(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
    } finally {
      setLoading(false);
    }
  };

  const cargarDatosDiario = async () => {
    setLoading(true);
    setError(null);
    try {
      const resultado = await api.getScoutDailyPaymentView(currentDate, filters.scoutId || undefined);
      setData(resultado.data);
      setFechaUsadaDiaria(resultado.fechaUsada);
      setHizoFallbackDiaria(resultado.hizoFallback);
      setSelectedDrivers(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
    } finally {
      setLoading(false);
    }
  };

  const cargarDatosHistorico = async (reset: boolean = false) => {
    if (reset) {
      setHistoricalOffset(0);
      setHistoricalData([]);
    }
    
    setLoading(reset);
    setHistoricalLoadingMore(!reset);
    setError(null);
    try {
      const resultado = await api.getScoutHistoricalPaymentView(
        historicalMonths,
        reset ? 0 : historicalOffset,
        50,
        filters.scoutId || undefined
      );
      
      if (reset) {
        setHistoricalData(resultado.data);
      } else {
        setHistoricalData(prev => [...prev, ...resultado.data]);
      }
      
      setHistoricalHasMore(resultado.hasMore);
      setSelectedDrivers(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
    } finally {
      setLoading(false);
      setHistoricalLoadingMore(false);
    }
  };

  const cargarMasHistorico = async () => {
    if (historicalLoadingMore || !historicalHasMore) return;
    
    const newOffset = historicalOffset + 50;
    setHistoricalOffset(newOffset);
    setHistoricalLoadingMore(true);
    setError(null);
    try {
      const resultado = await api.getScoutHistoricalPaymentView(
        historicalMonths,
        newOffset,
        50,
        filters.scoutId || undefined
      );
      
      setHistoricalData(prev => [...prev, ...resultado.data]);
      setHistoricalHasMore(resultado.hasMore);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar más datos');
    } finally {
      setHistoricalLoadingMore(false);
    }
  };

  const handlePreviousWeek = () => {
    setCurrentWeek(getPreviousWeek(currentWeek));
  };

  const handleNextWeek = () => {
    setCurrentWeek(getNextWeek(currentWeek));
  };

  const handlePreviousDay = () => {
    const date = new Date(currentDate);
    date.setDate(date.getDate() - 1);
    setCurrentDate(date.toISOString().split('T')[0]);
  };

  const handleNextDay = () => {
    const date = new Date(currentDate);
    date.setDate(date.getDate() + 1);
    setCurrentDate(date.toISOString().split('T')[0]);
  };

  const handleWeekSelectorChange = (weekISO: string) => {
    if (weekISO) {
      setCurrentWeek(weekISO);
      setWeekSelector('');
    }
  };

  const handleDateSelectorChange = (fecha: string) => {
    if (fecha) {
      setCurrentDate(fecha);
      setDateSelector('');
    }
  };

  const handleCalcularInstancias = async () => {
    if (!filters.scoutId) {
      setError('Selecciona un scout para calcular instancias');
      return;
    }

    let fechaDesde: string;
    let fechaHasta: string;

    if (activeTab === 'weekly') {
      const weekRange = getWeekRange(currentWeek);
      fechaDesde = weekRange.start.toISOString().split('T')[0];
      fechaHasta = weekRange.end.toISOString().split('T')[0];
    } else if (activeTab === 'daily') {
      fechaDesde = currentDate;
      fechaHasta = currentDate;
    } else {
      const fechaInicio = new Date();
      fechaInicio.setMonth(fechaInicio.getMonth() - historicalMonths);
      fechaDesde = fechaInicio.toISOString().split('T')[0];
      fechaHasta = new Date().toISOString().split('T')[0];
    }

    setLoading(true);
    setError(null);
    try {
      await api.calculateScoutPaymentInstances(filters.scoutId, fechaDesde, fechaHasta);
      if (activeTab === 'weekly') {
        await cargarDatosSemanal();
      } else if (activeTab === 'daily') {
        await cargarDatosDiario();
      } else {
        await cargarDatosHistorico(true);
      }
      alert('Instancias calculadas exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al calcular instancias');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleDriver = (scoutId: string, driverId: string, instanceId: number | null | undefined) => {
    if (!instanceId) return;
    const key = `${scoutId}-${driverId}-${instanceId}`;
    const newSelected = new Set(selectedDrivers);
    if (newSelected.has(key)) {
      newSelected.delete(key);
    } else {
      newSelected.add(key);
    }
    setSelectedDrivers(newSelected);
  };


  const handlePagarSeleccionados = async () => {
    if (selectedDrivers.size === 0) {
      setError('Selecciona al menos un driver para pagar');
      return;
    }

    try {
      const instanceIds: number[] = [];
      const scoutIds = new Set<string>();
      
      const currentDataToUse = activeTab === 'historical' ? historicalData : data;
      
      for (const scoutView of currentDataToUse) {
        for (const driver of scoutView.drivers) {
          // Verificar cada milestone individualmente
          if (driver.milestone1InstanceId) {
            const key1 = `${scoutView.scoutId}-${driver.driverId}-${driver.milestone1InstanceId}`;
            if (selectedDrivers.has(key1)) {
              instanceIds.push(driver.milestone1InstanceId);
              scoutIds.add(scoutView.scoutId);
            }
          }
          if (driver.milestone5InstanceId) {
            const key5 = `${scoutView.scoutId}-${driver.driverId}-${driver.milestone5InstanceId}`;
            if (selectedDrivers.has(key5)) {
              instanceIds.push(driver.milestone5InstanceId);
              scoutIds.add(scoutView.scoutId);
            }
          }
          if (driver.milestone25InstanceId) {
            const key25 = `${scoutView.scoutId}-${driver.driverId}-${driver.milestone25InstanceId}`;
            if (selectedDrivers.has(key25)) {
              instanceIds.push(driver.milestone25InstanceId);
              scoutIds.add(scoutView.scoutId);
            }
          }
        }
      }

      if (instanceIds.length === 0) {
        setError('No hay instancias válidas para pagar');
        return;
      }

      if (scoutIds.size !== 1) {
        setError('Solo puedes pagar instancias de un scout a la vez');
        return;
      }

      const scoutId = Array.from(scoutIds)[0];
      await api.payScoutPaymentInstances(scoutId, instanceIds);
      
      if (activeTab === 'weekly') {
        await cargarDatosSemanal();
      } else if (activeTab === 'daily') {
        await cargarDatosDiario();
      } else {
        await cargarDatosHistorico(true);
      }
      
      alert('Instancias pagadas exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al pagar instancias');
    }
  };

  const currentDataToUse = activeTab === 'historical' ? historicalData : data;
  const allDrivers = currentDataToUse.flatMap(scoutView => scoutView.drivers);
  const filteredDrivers = allDrivers.filter(driver => {
    const scoutView = currentDataToUse.find(s => s.drivers.includes(driver));
    if (!scoutView) return false;

    if (filters.hasConnection === 'si' && !driver.hasConnection) return false;
    if (filters.hasConnection === 'no' && driver.hasConnection) return false;
    if (filters.reachedMilestone === 'si' && !driver.reachedMilestone1) return false;
    if (filters.reachedMilestone === 'no' && driver.reachedMilestone1) return false;
    if (filters.isEligible === 'si' && !driver.isEligible) return false;
    if (filters.isEligible === 'no' && driver.isEligible) return false;
    if (filters.scoutReached8 === 'si' && !driver.scoutReached8Registrations) return false;
    if (filters.scoutReached8 === 'no' && driver.scoutReached8Registrations) return false;
    if (filters.status === 'pending' && driver.status !== 'pending') return false;
    if (filters.status === 'paid' && driver.status !== 'paid') return false;
    if (filters.status === 'cancelled' && driver.status !== 'cancelled') return false;

    return true;
  });

  const filteredData = currentDataToUse.map(scoutView => ({
    ...scoutView,
    drivers: scoutView.drivers.filter(driver => filteredDrivers.includes(driver))
  })).filter(scoutView => scoutView.drivers.length > 0);

  const totalEligible = allDrivers.filter(d => d.isEligible && d.status === 'pending').length;
  const totalSelected = selectedDrivers.size;
  const totalAmount = allDrivers
    .filter(d => {
      const key = `${currentDataToUse.find(s => s.drivers.includes(d))?.scoutId}-${d.driverId}-${d.instanceId}`;
      return selectedDrivers.has(key);
    })
    .reduce((sum, d) => sum + d.amount, 0);

  const hasInstances = allDrivers.some(d => d.instanceId !== null);
  const needsCalculation = !hasInstances && allDrivers.some(d => d.reachedMilestone1);

  const weekOptions: string[] = [];
  let week = getCurrentWeekISO();
  for (let i = 0; i < 52; i++) {
    weekOptions.push(week);
    week = getPreviousWeek(week);
  }
  weekOptions.reverse();

  const renderNavigation = () => {
    if (activeTab === 'weekly') {
      return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button onClick={handlePreviousWeek} className="btn">← Semana Anterior</button>
            <span style={{ fontWeight: 'bold', minWidth: '200px', textAlign: 'center' }}>
              {formatWeekISO(currentWeek)}
            </span>
            <span style={{ fontSize: '12px', color: '#6c757d' }}>
              {formatWeekRange(currentWeek)}
            </span>
            <button onClick={handleNextWeek} className="btn">Semana Siguiente →</button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <select
              value={weekSelector}
              onChange={(e) => handleWeekSelectorChange(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Seleccionar semana...</option>
              {weekOptions.map(weekISO => (
                <option key={weekISO} value={weekISO}>
                  {formatWeekISO(weekISO)} - {formatWeekRange(weekISO)}
                </option>
              ))}
            </select>
          </div>
        </div>
      );
    } else if (activeTab === 'daily') {
      const fechaMostrar = fechaUsadaDiaria || currentDate;
      const fechaMostrarObj = new Date(fechaMostrar);
      const fechaSolicitadaObj = new Date(currentDate);
      const fechasDiferentes = fechaMostrar !== currentDate;
      
      return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexDirection: 'column' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <button onClick={handlePreviousDay} className="btn">← Día Anterior</button>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: '250px' }}>
                <span style={{ fontWeight: 'bold', textAlign: 'center' }}>
                  {fechaMostrarObj.toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                </span>
                {fechasDiferentes && (
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#ff9800', 
                    fontStyle: 'italic',
                    marginTop: '4px'
                  }}>
                    (Solicitado: {fechaSolicitadaObj.toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' })})
                  </span>
                )}
              </div>
              <button onClick={handleNextDay} className="btn">Día Siguiente →</button>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <input
              type="date"
              value={dateSelector || currentDate}
              onChange={(e) => handleDateSelectorChange(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
          </div>
        </div>
      );
    } else {
      return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <label style={{ fontSize: '14px', fontWeight: 'bold' }}>Últimos:</label>
            <select
              value={historicalMonths}
              onChange={(e) => {
                setHistoricalMonths(parseInt(e.target.value));
                setHistoricalOffset(0);
                setHistoricalData([]);
              }}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="3">3 meses</option>
              <option value="6">6 meses</option>
              <option value="12">12 meses</option>
            </select>
            <span style={{ fontSize: '12px', color: '#6c757d' }}>
              Mostrando {historicalData.length} de {historicalData.length + (historicalHasMore ? '+' : '')} registros
            </span>
          </div>
        </div>
      );
    }
  };

  const renderTable = () => {
    return (
      <div style={{ overflowX: 'auto' }}>
        {filteredData.length === 0 ? (
          <p style={{ textAlign: 'center', color: '#6c757d', marginTop: '20px' }}>
            No hay datos para el período seleccionado con los filtros aplicados
          </p>
        ) : (
          <>
            <table style={{ width: 'auto', borderCollapse: 'collapse', fontSize: '12px', tableLayout: 'auto' }}>
              <thead>
                <tr style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>✓</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'normal' }}>Scout</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Driver</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Teléfono</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Fecha Reg.</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Fecha Cont.</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Conexión</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>M1</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>M5</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>M25</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Fecha M1</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Fecha M5</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left', whiteSpace: 'nowrap' }}>Fecha M25</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Estado M1</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', whiteSpace: 'nowrap' }}>Monto M1</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Estado M5</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', whiteSpace: 'nowrap' }}>Monto M5</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Estado M25</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', whiteSpace: 'nowrap' }}>Monto M25</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>8+ Reg.</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Elegible</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', whiteSpace: 'nowrap' }}>Monto Total</th>
                  <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', whiteSpace: 'nowrap' }}>Estado</th>
                </tr>
              </thead>
              <tbody>
                {filteredData.map(scoutView => {
                  // Obtener todas las instancias pendientes de todos los drivers del scout
                  const pendingInstances: Array<{driverId: string, instanceId: number, milestoneType: string}> = [];
                  scoutView.drivers.forEach(driver => {
                    if (driver.milestone1InstanceId && driver.milestone1Status === 'pending') {
                      pendingInstances.push({driverId: driver.driverId, instanceId: driver.milestone1InstanceId, milestoneType: 'M1'});
                    }
                    if (driver.milestone5InstanceId && driver.milestone5Status === 'pending') {
                      pendingInstances.push({driverId: driver.driverId, instanceId: driver.milestone5InstanceId, milestoneType: 'M5'});
                    }
                    if (driver.milestone25InstanceId && driver.milestone25Status === 'pending') {
                      pendingInstances.push({driverId: driver.driverId, instanceId: driver.milestone25InstanceId, milestoneType: 'M25'});
                    }
                  });
                  
                  const allSelected = pendingInstances.length > 0 && pendingInstances.every(inst => 
                    selectedDrivers.has(`${scoutView.scoutId}-${inst.driverId}-${inst.instanceId}`));

                  return scoutView.drivers.map((driver, index) => {
                    const key1 = driver.milestone1InstanceId ? `${scoutView.scoutId}-${driver.driverId}-${driver.milestone1InstanceId}` : null;
                    const key5 = driver.milestone5InstanceId ? `${scoutView.scoutId}-${driver.driverId}-${driver.milestone5InstanceId}` : null;
                    const key25 = driver.milestone25InstanceId ? `${scoutView.scoutId}-${driver.driverId}-${driver.milestone25InstanceId}` : null;
                    
                    const isSelected1 = key1 ? selectedDrivers.has(key1) : false;
                    const isSelected5 = key5 ? selectedDrivers.has(key5) : false;
                    const isSelected25 = key25 ? selectedDrivers.has(key25) : false;
                    
                    // La fila está seleccionada si al menos uno de los milestones está seleccionado
                    const isRowSelected = isSelected1 || isSelected5 || isSelected25;
                    
                    const canSelect1 = driver.milestone1Status === 'pending' && driver.milestone1InstanceId !== null;
                    const canSelect5 = driver.milestone5Status === 'pending' && driver.milestone5InstanceId !== null;
                    const canSelect25 = driver.milestone25Status === 'pending' && driver.milestone25InstanceId !== null;

                    return (
                      <tr 
                        key={`${scoutView.scoutId}-${driver.driverId}-${index}`}
                        style={{ 
                          backgroundColor: isRowSelected ? '#d4edda' : 'white',
                          opacity: driver.status === 'cancelled' ? 0.6 : 1
                        }}
                      >
                        <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>
                          {index === 0 && (
                            <input
                              type="checkbox"
                              checked={allSelected}
                              onChange={() => {
                                const newSelected = new Set(selectedDrivers);
                                if (allSelected) {
                                  pendingInstances.forEach(inst => {
                                    newSelected.delete(`${scoutView.scoutId}-${inst.driverId}-${inst.instanceId}`);
                                  });
                                } else {
                                  pendingInstances.forEach(inst => {
                                    newSelected.add(`${scoutView.scoutId}-${inst.driverId}-${inst.instanceId}`);
                                  });
                                }
                                setSelectedDrivers(newSelected);
                              }}
                              disabled={pendingInstances.length === 0}
                              style={{ marginRight: '5px' }}
                            />
                          )}
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                            {canSelect1 && (
                              <input
                                type="checkbox"
                                checked={isSelected1}
                                onChange={() => handleToggleDriver(scoutView.scoutId, driver.driverId, driver.milestone1InstanceId)}
                                title="M1"
                              />
                            )}
                            {canSelect5 && (
                              <input
                                type="checkbox"
                                checked={isSelected5}
                                onChange={() => handleToggleDriver(scoutView.scoutId, driver.driverId, driver.milestone5InstanceId)}
                                title="M5"
                              />
                            )}
                            {canSelect25 && (
                              <input
                                type="checkbox"
                                checked={isSelected25}
                                onChange={() => handleToggleDriver(scoutView.scoutId, driver.driverId, driver.milestone25InstanceId)}
                                title="M25"
                              />
                            )}
                          </div>
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'normal',
                          wordWrap: 'break-word'
                        }}>
                          {index === 0 && scoutView.scoutName}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          maxWidth: '200px'
                        }}>
                          {driver.driverName || driver.driverId}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.driverPhone || '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {new Date(driver.registrationDate).toLocaleDateString('es-ES')}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {new Date(driver.hireDate).toLocaleDateString('es-ES')}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          {driver.hasConnection ? '✓' : '✗'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          {driver.reachedMilestone1 ? '✓' : '✗'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          {driver.reachedMilestone5 ? '✓' : '✗'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          {driver.reachedMilestone25 ? '✓' : '✗'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone1Date ? new Date(driver.milestone1Date).toLocaleDateString('es-ES') : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone5Date ? new Date(driver.milestone5Date).toLocaleDateString('es-ES') : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone25Date ? new Date(driver.milestone25Date).toLocaleDateString('es-ES') : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          backgroundColor: driver.milestone1ExpirationStatus === 'in_progress' ? 'rgba(40, 167, 69, 0.1)' :
                                          driver.milestone1ExpirationStatus === 'expired' ? 'rgba(220, 53, 69, 0.1)' : 'transparent'
                        }}>
                          {driver.milestone1ExpirationStatus ? (
                            <span style={{ 
                              color: driver.milestone1ExpirationStatus === 'in_progress' ? '#28a745' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone1ExpirationStatus === 'in_progress' ? '🟢 En Proceso' : '🔴 Vencido'}
                            </span>
                          ) : driver.milestone1Status ? (
                            <span style={{ 
                              color: driver.milestone1Status === 'paid' ? '#28a745' : 
                                     driver.milestone1Status === 'pending' ? '#ffc107' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone1Status === 'paid' ? '✓ Pagado' : 
                               driver.milestone1Status === 'pending' ? '⏳ Pendiente' : '✗ Cancelado'}
                            </span>
                          ) : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'right',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone1Amount !== undefined ? `S/ ${driver.milestone1Amount.toFixed(2)}` : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          backgroundColor: driver.milestone5ExpirationStatus === 'in_progress' ? 'rgba(40, 167, 69, 0.1)' :
                                          driver.milestone5ExpirationStatus === 'expired' ? 'rgba(220, 53, 69, 0.1)' : 'transparent'
                        }}>
                          {driver.milestone5ExpirationStatus ? (
                            <span style={{ 
                              color: driver.milestone5ExpirationStatus === 'in_progress' ? '#28a745' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone5ExpirationStatus === 'in_progress' ? '🟢 En Proceso' : '🔴 Vencido'}
                            </span>
                          ) : driver.milestone5Status ? (
                            <span style={{ 
                              color: driver.milestone5Status === 'paid' ? '#28a745' : 
                                     driver.milestone5Status === 'pending' ? '#ffc107' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone5Status === 'paid' ? '✓ Pagado' : 
                               driver.milestone5Status === 'pending' ? '⏳ Pendiente' : '✗ Cancelado'}
                            </span>
                          ) : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'right',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone5Amount !== undefined ? `S/ ${driver.milestone5Amount.toFixed(2)}` : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          backgroundColor: driver.milestone25ExpirationStatus === 'in_progress' ? 'rgba(40, 167, 69, 0.1)' :
                                          driver.milestone25ExpirationStatus === 'expired' ? 'rgba(220, 53, 69, 0.1)' : 'transparent'
                        }}>
                          {driver.milestone25ExpirationStatus ? (
                            <span style={{ 
                              color: driver.milestone25ExpirationStatus === 'in_progress' ? '#28a745' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone25ExpirationStatus === 'in_progress' ? '🟢 En Proceso' : '🔴 Vencido'}
                            </span>
                          ) : driver.milestone25Status ? (
                            <span style={{ 
                              color: driver.milestone25Status === 'paid' ? '#28a745' : 
                                     driver.milestone25Status === 'pending' ? '#ffc107' : '#dc3545',
                              fontWeight: 'bold',
                              fontSize: '11px'
                            }}>
                              {driver.milestone25Status === 'paid' ? '✓ Pagado' : 
                               driver.milestone25Status === 'pending' ? '⏳ Pendiente' : '✗ Cancelado'}
                            </span>
                          ) : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'right',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {driver.milestone25Amount !== undefined ? `S/ ${driver.milestone25Amount.toFixed(2)}` : '-'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          {driver.scoutReached8Registrations ? '✓' : '✗'}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}>
                          <span style={{ 
                            color: driver.isEligible ? '#28a745' : '#dc3545',
                            fontWeight: 'bold'
                          }}>
                            {driver.isEligible ? '✓' : '✗'}
                          </span>
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'right',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          S/ {driver.amount.toFixed(2)}
                        </td>
                        <td style={{ 
                          padding: '10px', 
                          border: '1px solid #dee2e6', 
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          <span style={{ 
                            color: driver.status === 'all_paid' ? '#28a745' : 
                                   driver.status === 'partial_paid' ? '#17a2b8' :
                                   driver.status === 'paid' ? '#28a745' : 
                                   driver.status === 'pending' ? '#ffc107' : '#dc3545',
                            fontWeight: 'bold',
                            fontSize: '11px'
                          }}>
                            {driver.status === 'all_paid' ? 'Todos Pagados' : 
                             driver.status === 'partial_paid' ? 'Parcial Pagado' :
                             driver.status === 'paid' ? 'Pagado' : 
                             driver.status === 'pending' ? 'Pendiente' : 'Cancelado'}
                          </span>
                        </td>
                      </tr>
                    );
                  });
                })}
              </tbody>
            </table>
            {activeTab === 'historical' && historicalHasMore && (
              <div ref={loadMoreRef} style={{ textAlign: 'center', padding: '20px' }}>
                {historicalLoadingMore ? (
                  <div className="loading">Cargando más datos...</div>
                ) : (
                  <div style={{ color: '#6c757d' }}>Desplázate para cargar más</div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    );
  };

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <h2 style={{ marginBottom: '20px' }}>Instancias de Pago a Scouts</h2>

      <div style={{ marginBottom: '20px', borderBottom: '2px solid #dee2e6' }}>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={() => setActiveTab('weekly')}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: activeTab === 'weekly' ? '#007bff' : '#f8f9fa',
              color: activeTab === 'weekly' ? 'white' : '#333',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: activeTab === 'weekly' ? 'bold' : 'normal'
            }}
          >
            Vista Semanal
          </button>
          <button
            onClick={() => setActiveTab('daily')}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: activeTab === 'daily' ? '#007bff' : '#f8f9fa',
              color: activeTab === 'daily' ? 'white' : '#333',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: activeTab === 'daily' ? 'bold' : 'normal'
            }}
          >
            Vista Diaria
          </button>
          <button
            onClick={() => setActiveTab('historical')}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: activeTab === 'historical' ? '#007bff' : '#f8f9fa',
              color: activeTab === 'historical' ? 'white' : '#333',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: activeTab === 'historical' ? 'bold' : 'normal'
            }}
          >
            Vista Histórica
          </button>
        </div>
      </div>

      {error && (
        <div className="error" style={{ marginBottom: '15px', padding: '10px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        {renderNavigation()}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px', marginBottom: '15px' }}>
          <select
            value={filters.scoutId}
            onChange={(e) => setFilters({...filters, scoutId: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Scouts</option>
            {scouts.map(scout => (
              <option key={scout.scoutId} value={scout.scoutId}>{scout.scoutName}</option>
            ))}
          </select>

          <select
            value={filters.hasConnection}
            onChange={(e) => setFilters({...filters, hasConnection: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todas las Conexiones</option>
            <option value="si">Con Conexión</option>
            <option value="no">Sin Conexión</option>
          </select>

          <select
            value={filters.reachedMilestone}
            onChange={(e) => setFilters({...filters, reachedMilestone: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Milestones</option>
            <option value="si">Alcanzó Milestone</option>
            <option value="no">No Alcanzó Milestone</option>
          </select>

          <select
            value={filters.isEligible}
            onChange={(e) => setFilters({...filters, isEligible: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Estados</option>
            <option value="si">Elegibles</option>
            <option value="no">No Elegibles</option>
          </select>

          <select
            value={filters.scoutReached8}
            onChange={(e) => setFilters({...filters, scoutReached8: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Scouts</option>
            <option value="si">Scout con 8+ Registros</option>
            <option value="no">Scout con menos de 8</option>
          </select>

          <select
            value={filters.status}
            onChange={(e) => setFilters({...filters, status: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Estados</option>
            <option value="pending">Pendiente</option>
            <option value="paid">Pagado</option>
            <option value="cancelled">Cancelado</option>
          </select>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          {needsCalculation && (
            <button
              onClick={handleCalcularInstancias}
              disabled={loading || !filters.scoutId}
              className="btn"
            >
              {loading ? 'Calculando...' : '🔄 Calcular Instancias'}
            </button>
          )}
          {totalEligible > 0 && (
            <>
              <button
                onClick={handlePagarSeleccionados}
                disabled={totalSelected === 0}
                className="btn"
                style={{ backgroundColor: '#28a745', color: 'white' }}
              >
                💰 Pagar Seleccionados ({totalSelected})
              </button>
              <span style={{ fontSize: '14px' }}>
                <strong>Total Elegibles:</strong> {totalEligible} | 
                <strong> Seleccionados:</strong> {totalSelected} | 
                <strong> Monto:</strong> S/ {totalAmount.toFixed(2)}
              </span>
            </>
          )}
        </div>
      </div>

      {loading && activeTab !== 'historical' ? (
        <div className="loading">Cargando datos...</div>
      ) : (
        renderTable()
      )}
    </div>
  );
};

export default ScoutPaymentInstances;
