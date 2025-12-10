import React, { useState, useEffect, useRef } from 'react';
import { api, MilestonePaymentViewItem, YangoRematchProgress, YangoPaymentConfig, MilestoneInstance } from '../services/api';
import { getCurrentWeekISO, getPreviousWeek, getNextWeek, formatWeekRange, formatWeekISO } from '../utils/weekUtils';

type TabType = 'weekly' | 'daily' | 'range' | 'pending';

const MilestonePaymentView: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('weekly');
  const [currentWeek, setCurrentWeek] = useState(getCurrentWeekISO());
  const [currentDate, setCurrentDate] = useState(new Date().toISOString().split('T')[0]);
  const [data, setData] = useState<MilestonePaymentViewItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    milestoneType: '',
    paymentStatus: ''
  });
  const [weekSelector, setWeekSelector] = useState('');
  const [dateSelector, setDateSelector] = useState('');
  const [_rematchJobId, setRematchJobId] = useState<string | null>(null);
  const [rematchProgress, setRematchProgress] = useState<YangoRematchProgress | null>(null);
  const [rematchLoading, setRematchLoading] = useState(false);
  const [rematchNotification, setRematchNotification] = useState<string | null>(null);
  const progressIntervalRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  
  // Estados para modal de detalles de viajes
  const [showTripDetailsModal, setShowTripDetailsModal] = useState(false);
  const [selectedTripDetails, setSelectedTripDetails] = useState<MilestoneInstance | null>(null);
  const [loadingTripDetails, setLoadingTripDetails] = useState(false);
  const [tripDetailsError, setTripDetailsError] = useState<string | null>(null);
  
  // Estados para rango personalizado
  const [rangeFechaDesde, setRangeFechaDesde] = useState(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
  const [rangeFechaHasta, setRangeFechaHasta] = useState(new Date().toISOString().split('T')[0]);
  
  // Estados para pagos pendientes
  const [pendingFilters, setPendingFilters] = useState({
    milestoneType: '',
    fechaDesde: '',
    fechaHasta: '',
    driverName: ''
  });
  
  // Estados para configuración de pagos Yango
  const [yangoConfigs, setYangoConfigs] = useState<YangoPaymentConfig[]>([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [showConfig, setShowConfig] = useState(false);
  const [editingConfig, setEditingConfig] = useState<YangoPaymentConfig | null>(null);

  // Definir funciones con useCallback para evitar problemas de dependencias
  const cargarDatosSemanal = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      console.log('Cargando datos semanales para semana:', currentWeek);
      const vista = await api.getMilestonePaymentViewWeekly(currentWeek);
      setData(vista);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
    } finally {
      setLoading(false);
    }
  }, [currentWeek]);

  const cargarDatosDiario = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      console.log('Cargando datos diarios para fecha:', currentDate);
      const vista = await api.getMilestonePaymentViewDaily(currentDate);
      setData(vista);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
    } finally {
      setLoading(false);
    }
  }, [currentDate]);
  
  const cargarDatosRango = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (!rangeFechaDesde || !rangeFechaHasta) {
        setError('Debe seleccionar fecha desde y fecha hasta');
        setLoading(false);
        setData([]);
        return;
      }
      if (new Date(rangeFechaDesde) > new Date(rangeFechaHasta)) {
        setError('La fecha desde debe ser anterior a la fecha hasta');
        setLoading(false);
        setData([]);
        return;
      }
      console.log('Cargando datos de rango:', rangeFechaDesde, rangeFechaHasta);
      const vista = await api.getMilestonePaymentViewRange(rangeFechaDesde, rangeFechaHasta);
      console.log('Datos recibidos:', vista.length, 'registros');
      setData(vista);
    } catch (err) {
      console.error('Error al cargar datos de rango:', err);
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [rangeFechaDesde, rangeFechaHasta]);
  
  const cargarDatosPendientes = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const milestoneType = pendingFilters.milestoneType && pendingFilters.milestoneType.trim() 
        ? parseInt(pendingFilters.milestoneType) 
        : undefined;
      const fechaDesde = pendingFilters.fechaDesde && pendingFilters.fechaDesde.trim() 
        ? pendingFilters.fechaDesde 
        : undefined;
      const fechaHasta = pendingFilters.fechaHasta && pendingFilters.fechaHasta.trim() 
        ? pendingFilters.fechaHasta 
        : undefined;
      
      console.log('Cargando datos pendientes:', { milestoneType, fechaDesde, fechaHasta });
      const vista = await api.getMilestonePaymentViewPending(undefined, milestoneType, fechaDesde, fechaHasta);
      console.log('Datos pendientes recibidos:', vista.length, 'registros');
      
      // Filtrar por nombre de driver si se especifica
      let filtered = vista;
      if (pendingFilters.driverName && pendingFilters.driverName.trim()) {
        const searchTerm = pendingFilters.driverName.toLowerCase().trim();
        filtered = vista.filter(item => 
          item.driverName?.toLowerCase().includes(searchTerm) ||
          item.driverId?.toLowerCase().includes(searchTerm) ||
          item.driverPhone?.toLowerCase().includes(searchTerm)
        );
      }
      
      setData(filtered);
    } catch (err) {
      console.error('Error al cargar datos pendientes:', err);
      setError(err instanceof Error ? err.message : 'Error al cargar datos');
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [pendingFilters]);

  useEffect(() => {
    console.log('useEffect ejecutado - activeTab:', activeTab);
    if (activeTab === 'weekly') {
      cargarDatosSemanal();
    } else if (activeTab === 'daily') {
      cargarDatosDiario();
    } else if (activeTab === 'range') {
      console.log('Tab range activo - fechas:', rangeFechaDesde, rangeFechaHasta);
      // Solo cargar si las fechas están definidas
      if (rangeFechaDesde && rangeFechaHasta) {
        cargarDatosRango();
      } else {
        console.log('Fechas no definidas, limpiando datos');
        // Si no hay fechas, limpiar datos pero no mostrar error
        setData([]);
        setLoading(false);
        setError(null);
      }
    } else if (activeTab === 'pending') {
      console.log('Tab pending activo - filtros:', pendingFilters);
      cargarDatosPendientes();
    }
  }, [activeTab, cargarDatosSemanal, cargarDatosDiario, cargarDatosRango, cargarDatosPendientes, rangeFechaDesde, rangeFechaHasta]);
  
  useEffect(() => {
    if (showConfig) {
      cargarConfiguracionYango();
    }
  }, [showConfig]);

  // Limpiar intervalo al desmontar
  useEffect(() => {
    return () => {
      if (progressIntervalRef.current) {
        clearInterval(progressIntervalRef.current);
      }
    };
  }, []);
  
  // Manejar tecla ESC para cerrar modal
  useEffect(() => {
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && showTripDetailsModal) {
        setShowTripDetailsModal(false);
      }
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [showTripDetailsModal]);

  
  const cargarConfiguracionYango = async () => {
    setConfigLoading(true);
    try {
      const configs = await api.getYangoPaymentConfig();
      setYangoConfigs(configs);
    } catch (err) {
      console.error('Error al cargar configuración Yango:', err);
    } finally {
      setConfigLoading(false);
    }
  };
  
  const handleUpdateConfig = async (config: YangoPaymentConfig) => {
    try {
      await api.updateYangoPaymentConfig(config.id, config.amountYango, config.periodDays, config.isActive);
      setEditingConfig(null);
      await cargarConfiguracionYango();
      alert('Configuración actualizada exitosamente');
    } catch (err) {
      alert(`Error al actualizar configuración: ${err instanceof Error ? err.message : 'Error desconocido'}`);
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
  
  const handleDriverNameClick = async (item: MilestonePaymentViewItem) => {
    setShowTripDetailsModal(true);
    setLoadingTripDetails(true);
    setTripDetailsError(null);
    setSelectedTripDetails(null);
    
    try {
      const details = await api.getMilestoneDetail(item.driverId, item.milestoneType, item.periodDays);
      setSelectedTripDetails(details);
    } catch (err) {
      setTripDetailsError(err instanceof Error ? err.message : 'Error al cargar detalles de viajes');
      console.error('Error al cargar detalles de viajes:', err);
    } finally {
      setLoadingTripDetails(false);
    }
  };

  const handleRematch = async () => {
    setRematchLoading(true);
    setRematchNotification(null);
    setRematchProgress(null);
    
    try {
      const result = await api.rematchYangoTransactions();
      setRematchJobId(result.jobId);
      
      // Iniciar polling de progreso
      startProgressPolling(result.jobId);
      
      setRematchNotification('Re-sincronización iniciada. Procesando transacciones...');
    } catch (err) {
      setRematchNotification(`Error al iniciar re-sincronización: ${err instanceof Error ? err.message : 'Error desconocido'}`);
      setRematchLoading(false);
    }
  };

  const startProgressPolling = (jobId: string) => {
    // Limpiar intervalo anterior si existe
    if (progressIntervalRef.current) {
      clearInterval(progressIntervalRef.current);
    }

    // Consultar progreso inmediatamente
    checkProgress(jobId);

    // Consultar cada 2 segundos
    progressIntervalRef.current = setInterval(() => {
      checkProgress(jobId);
    }, 2000);
  };

  const checkProgress = async (jobId: string) => {
    try {
      const progress = await api.getYangoRematchProgress(jobId);
      setRematchProgress(progress);

      if (progress.status === 'completed') {
        // Detener polling
        if (progressIntervalRef.current) {
          clearInterval(progressIntervalRef.current);
          progressIntervalRef.current = null;
        }
        
        setRematchLoading(false);
        setRematchNotification(`Re-sincronización completada. ${progress.processedTransactions} transacciones procesadas.`);
        
        // Recargar datos después de 1 segundo
        setTimeout(() => {
          if (activeTab === 'weekly') {
            cargarDatosSemanal();
          } else {
            cargarDatosDiario();
          }
          // Limpiar notificación después de 5 segundos
          setTimeout(() => setRematchNotification(null), 5000);
        }, 1000);
      } else if (progress.status === 'failed') {
        // Detener polling
        if (progressIntervalRef.current) {
          clearInterval(progressIntervalRef.current);
          progressIntervalRef.current = null;
        }
        
        setRematchLoading(false);
        setRematchNotification(`Error en re-sincronización: ${progress.error || 'Error desconocido'}`);
        
        // Limpiar notificación después de 5 segundos
        setTimeout(() => setRematchNotification(null), 5000);
      }
    } catch (err) {
      // Si el job no se encuentra, puede que haya terminado
      if (err instanceof Error && err.message.includes('no encontrado')) {
        if (progressIntervalRef.current) {
          clearInterval(progressIntervalRef.current);
          progressIntervalRef.current = null;
        }
        setRematchLoading(false);
        setRematchNotification('Re-sincronización completada.');
        setTimeout(() => {
          if (activeTab === 'weekly') {
            cargarDatosSemanal();
          } else {
            cargarDatosDiario();
          }
          setTimeout(() => setRematchNotification(null), 5000);
        }, 1000);
      }
    }
  };

  const filteredData = data.filter(item => {
    if (filters.milestoneType && item.milestoneType.toString() !== filters.milestoneType) {
      return false;
    }
    if (filters.paymentStatus && item.paymentStatus !== filters.paymentStatus) {
      return false;
    }
    return true;
  });

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
      return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button onClick={handlePreviousDay} className="btn">← Día Anterior</button>
            <span style={{ fontWeight: 'bold', minWidth: '250px', textAlign: 'center' }}>
              {new Date(currentDate).toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
            </span>
            <button onClick={handleNextDay} className="btn">Día Siguiente →</button>
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
    } else if (activeTab === 'range') {
      return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <label style={{ fontWeight: 'bold' }}>Desde:</label>
            <input
              type="date"
              value={rangeFechaDesde}
              onChange={(e) => setRangeFechaDesde(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <label style={{ fontWeight: 'bold' }}>Hasta:</label>
            <input
              type="date"
              value={rangeFechaHasta}
              onChange={(e) => setRangeFechaHasta(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <button 
              onClick={() => {
                // El useEffect se encargará de cargar los datos cuando cambien las fechas
                if (rangeFechaDesde && rangeFechaHasta) {
                  cargarDatosRango();
                }
              }}
              className="btn"
              style={{ padding: '8px 16px' }}
            >
              Aplicar Rango
            </button>
          </div>
        </div>
      );
    } else if (activeTab === 'pending') {
      return (
        <div style={{ marginBottom: '15px' }}>
          <div style={{ display: 'flex', gap: '10px', marginBottom: '10px', flexWrap: 'wrap', alignItems: 'center' }}>
            <label style={{ fontWeight: 'bold' }}>Milestone:</label>
            <select
              value={pendingFilters.milestoneType}
              onChange={(e) => setPendingFilters({...pendingFilters, milestoneType: e.target.value})}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="1">M1</option>
              <option value="5">M5</option>
              <option value="25">M25</option>
            </select>
            
            <label style={{ fontWeight: 'bold' }}>Desde:</label>
            <input
              type="date"
              value={pendingFilters.fechaDesde}
              onChange={(e) => setPendingFilters({...pendingFilters, fechaDesde: e.target.value})}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            
            <label style={{ fontWeight: 'bold' }}>Hasta:</label>
            <input
              type="date"
              value={pendingFilters.fechaHasta}
              onChange={(e) => setPendingFilters({...pendingFilters, fechaHasta: e.target.value})}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            
            <label style={{ fontWeight: 'bold' }}>Buscar Driver:</label>
            <input
              type="text"
              placeholder="Nombre, ID o teléfono..."
              value={pendingFilters.driverName}
              onChange={(e) => setPendingFilters({...pendingFilters, driverName: e.target.value})}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px', minWidth: '200px' }}
            />
          </div>
        </div>
      );
    }
    return null;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'paid':
        return '#28a745';
      case 'missing':
        return '#dc3545';
      case 'pending':
        return '#ffc107';
      default:
        return '#6c757d';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'paid':
        return '✓ Pagado';
      case 'missing':
        return '✗ Falta Pago';
      case 'pending':
        return '⏳ Pendiente';
      default:
        return status;
    }
  };
  
  const calcularMontoAcumulativoEsperado = (milestoneType: number, periodDays: number = 14): number => {
    // Filtrar configuraciones activas del periodo
    const configs = yangoConfigs
      .filter(c => c.periodDays === periodDays && c.isActive)
      .sort((a, b) => a.milestoneType - b.milestoneType);
    
    if (configs.length === 0) {
      // Valores por defecto si no hay configuración
      switch (milestoneType) {
        case 1: return 25.00;
        case 5: return 60.00; // 25 + 35
        case 25: return 160.00; // 25 + 35 + 100
        default: return 0;
      }
    }
    
    let total = 0;
    for (const config of configs) {
      total += config.amountYango;
      if (config.milestoneType === milestoneType) {
        return total;
      }
    }
    
    return total;
  };

  const renderTable = () => {
    return (
      <div style={{ overflowX: 'auto' }}>
        {filteredData.length === 0 ? (
          <p style={{ textAlign: 'center', color: '#6c757d', marginTop: '20px' }}>
            No hay datos para el período seleccionado con los filtros aplicados
          </p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Driver ID</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Nombre</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Teléfono</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Fecha Cont.</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>Milestone</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Fecha Cumpl.</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Viajes</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Monto Esperado</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Monto Yango</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Diferencia</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Fecha Pago</th>
                <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>Estado</th>
              </tr>
            </thead>
            <tbody>
              {filteredData.map((item, index) => (
                <tr 
                  key={`${item.driverId}-${item.milestoneInstanceId}-${index}`}
                  style={{ backgroundColor: 'white' }}
                >
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                    {item.driverId}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6' }}>
                    {item.driverName ? (
                      <span
                        onClick={() => handleDriverNameClick(item)}
                        style={{
                          cursor: 'pointer',
                          color: '#007bff',
                          textDecoration: 'underline',
                          fontWeight: '500'
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.color = '#0056b3';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.color = '#007bff';
                        }}
                        title="Haz clic para ver detalles de viajes"
                      >
                        {item.driverName}
                      </span>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                    {item.driverPhone || '-'}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                    {new Date(item.hireDate).toLocaleDateString('es-ES')}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center', fontWeight: 'bold' }}>
                    M{item.milestoneType}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                    {new Date(item.fulfillmentDate).toLocaleDateString('es-ES')}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                    {item.tripCount}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', fontWeight: 'bold' }}>
                    S/ {calcularMontoAcumulativoEsperado(item.milestoneType, item.periodDays).toFixed(2)}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                    {item.amountYango ? `S/ ${item.amountYango.toFixed(2)}` : '-'}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                    {(() => {
                      const esperado = calcularMontoAcumulativoEsperado(item.milestoneType, item.periodDays);
                      const pagado = item.amountYango || 0;
                      const diferencia = esperado - pagado;
                      if (diferencia === 0) {
                        return <span style={{ color: '#28a745' }}>✓</span>;
                      } else if (diferencia > 0) {
                        return <span style={{ color: '#dc3545' }}>Falta S/ {diferencia.toFixed(2)}</span>;
                      } else {
                        return <span style={{ color: '#ffc107' }}>+S/ {Math.abs(diferencia).toFixed(2)}</span>;
                      }
                    })()}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                    {item.yangoPaymentDate ? new Date(item.yangoPaymentDate).toLocaleDateString('es-ES') : '-'}
                  </td>
                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>
                    <span style={{ 
                      color: getStatusColor(item.paymentStatus),
                      fontWeight: 'bold',
                      fontSize: '11px'
                    }}>
                      {getStatusLabel(item.paymentStatus)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    );
  };

  const totalPaid = filteredData.filter(d => {
    const esperado = calcularMontoAcumulativoEsperado(d.milestoneType, d.periodDays);
    const pagado = d.amountYango || 0;
    return pagado >= esperado;
  }).length;
  
  const totalMissing = filteredData.filter(d => {
    const esperado = calcularMontoAcumulativoEsperado(d.milestoneType, d.periodDays);
    const pagado = d.amountYango || 0;
    return pagado < esperado;
  }).length;
  
  const totalPending = filteredData.filter(d => {
    const esperado = calcularMontoAcumulativoEsperado(d.milestoneType, d.periodDays);
    const pagado = d.amountYango || 0;
    return pagado < esperado && d.paymentStatus === 'pending';
  }).length;
  
  const totalAmountPaid = filteredData
    .filter(d => d.amountYango)
    .reduce((sum, d) => sum + (d.amountYango || 0), 0);
  
  const totalAmountToPay = filteredData
    .filter(d => {
      const esperado = calcularMontoAcumulativoEsperado(d.milestoneType, d.periodDays);
      const pagado = d.amountYango || 0;
      return pagado < esperado;
    })
    .reduce((sum, d) => {
      const esperado = calcularMontoAcumulativoEsperado(d.milestoneType, d.periodDays);
      const pagado = d.amountYango || 0;
      return sum + (esperado - pagado);
    }, 0);
  

  const renderTripDetailsModal = () => {
    if (!showTripDetailsModal) return null;
    
    return (
      <div
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1000,
          padding: '20px'
        }}
        onClick={(e) => {
          if (e.target === e.currentTarget) {
            setShowTripDetailsModal(false);
          }
        }}
      >
        <div
          style={{
            backgroundColor: 'white',
            borderRadius: '8px',
            maxWidth: '900px',
            width: '100%',
            maxHeight: '90vh',
            overflow: 'auto',
            boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)'
          }}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div style={{
            padding: '20px',
            borderBottom: '2px solid #dee2e6',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            backgroundColor: '#f8f9fa'
          }}>
            <h2 style={{ margin: 0 }}>
              Detalles de Viajes - {selectedTripDetails ? 
                (filteredData.find(d => d.driverId === selectedTripDetails.driverId)?.driverName || selectedTripDetails.driverId) 
                : 'Cargando...'}
            </h2>
            <button
              onClick={() => setShowTripDetailsModal(false)}
              style={{
                background: 'none',
                border: 'none',
                fontSize: '24px',
                cursor: 'pointer',
                color: '#6c757d',
                padding: '0 10px'
              }}
            >
              ×
            </button>
          </div>
          
          {/* Content */}
          <div style={{ padding: '20px' }}>
            {loadingTripDetails ? (
              <div style={{ textAlign: 'center', padding: '40px' }}>
                <div className="loading">Cargando detalles de viajes...</div>
              </div>
            ) : tripDetailsError ? (
              <div style={{
                padding: '20px',
                backgroundColor: '#f8d7da',
                color: '#721c24',
                borderRadius: '4px',
                marginBottom: '20px'
              }}>
                <strong>Error:</strong> {tripDetailsError}
              </div>
            ) : !selectedTripDetails ? (
              <div style={{ textAlign: 'center', padding: '40px', color: '#6c757d' }}>
                No se encontraron detalles de viajes
              </div>
            ) : (
              <>
                {/* Información del Milestone */}
                <div style={{
                  marginBottom: '20px',
                  padding: '15px',
                  backgroundColor: '#f8f9fa',
                  borderRadius: '4px'
                }}>
                  <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Información del Milestone</h3>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '10px', fontSize: '14px' }}>
                    <div><strong>Driver ID:</strong> {selectedTripDetails.driverId}</div>
                    <div><strong>Nombre:</strong> {filteredData.find(d => d.driverId === selectedTripDetails.driverId)?.driverName || '-'}</div>
                    <div><strong>Teléfono:</strong> {filteredData.find(d => d.driverId === selectedTripDetails.driverId)?.driverPhone || '-'}</div>
                    <div><strong>Milestone:</strong> M{selectedTripDetails.milestoneType} ({selectedTripDetails.periodDays} días)</div>
                    <div><strong>Fecha Cumplimiento:</strong> {new Date(selectedTripDetails.fulfillmentDate).toLocaleDateString('es-ES')}</div>
                    <div><strong>Total Viajes:</strong> {selectedTripDetails.tripCount}</div>
                    <div><strong>Fecha Cálculo:</strong> {new Date(selectedTripDetails.calculationDate).toLocaleDateString('es-ES')}</div>
                  </div>
                </div>
                
                {/* Tabla de Detalles de Viajes por Día */}
                {selectedTripDetails.tripDetails && selectedTripDetails.tripDetails.length > 0 ? (
                  <div>
                    <h3 style={{ marginBottom: '15px' }}>Detalles de Viajes por Día</h3>
                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                        <thead>
                          <tr style={{ backgroundColor: '#e9ecef' }}>
                            <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Fecha</th>
                            <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Cantidad de Viajes</th>
                            <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Día desde Contratación</th>
                          </tr>
                        </thead>
                        <tbody>
                          {selectedTripDetails.tripDetails
                            .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
                            .map((detail, index) => {
                              const maxViajes = Math.max(...selectedTripDetails.tripDetails!.map(d => d.tripCount || 0));
                              const intensity = detail.tripCount && maxViajes > 0 ? (detail.tripCount / maxViajes) : 0;
                              
                              return (
                                <tr key={index} style={{ backgroundColor: 'white' }}>
                                  <td style={{ padding: '10px', border: '1px solid #dee2e6', whiteSpace: 'nowrap' }}>
                                    {new Date(detail.date).toLocaleDateString('es-ES')}
                                  </td>
                                  <td style={{
                                    padding: '10px',
                                    border: '1px solid #dee2e6',
                                    textAlign: 'right',
                                    fontWeight: detail.tripCount && detail.tripCount > 0 ? 'bold' : 'normal',
                                    backgroundColor: detail.tripCount && detail.tripCount > 0 
                                      ? `rgba(40, 167, 69, ${0.2 + intensity * 0.3})` 
                                      : 'transparent'
                                  }}>
                                    {detail.tripCount || 0}
                                  </td>
                                  <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                                    {detail.dayFromHireDate !== undefined ? `Día ${detail.dayFromHireDate + 1}` : '-'}
                                  </td>
                                </tr>
                              );
                            })}
                        </tbody>
                        <tfoot>
                          <tr style={{ backgroundColor: '#f8f9fa', fontWeight: 'bold' }}>
                            <td style={{ padding: '10px', border: '1px solid #dee2e6' }}>Total</td>
                            <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                              {selectedTripDetails.tripDetails.reduce((sum, d) => sum + (d.tripCount || 0), 0)}
                            </td>
                            <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                              {selectedTripDetails.tripDetails.length} días
                            </td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '40px', color: '#6c757d' }}>
                    No hay detalles de viajes disponibles para este milestone
                  </div>
                )}
              </>
            )}
          </div>
          
          {/* Footer */}
          <div style={{
            padding: '15px 20px',
            borderTop: '2px solid #dee2e6',
            display: 'flex',
            justifyContent: 'flex-end',
            backgroundColor: '#f8f9fa'
          }}>
            <button
              onClick={() => setShowTripDetailsModal(false)}
              style={{
                padding: '10px 20px',
                backgroundColor: '#6c757d',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '14px',
                fontWeight: 'bold'
              }}
            >
              Cerrar
            </button>
          </div>
        </div>
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
      <h2 style={{ marginBottom: '20px' }}>Vista de Milestones con Pagos Yango</h2>

      <div style={{ marginBottom: '20px', borderBottom: '2px solid #dee2e6' }}>
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
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
            onClick={() => setActiveTab('range')}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: activeTab === 'range' ? '#007bff' : '#f8f9fa',
              color: activeTab === 'range' ? 'white' : '#333',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: activeTab === 'range' ? 'bold' : 'normal'
            }}
          >
            Rango Personalizado
          </button>
          <button
            onClick={() => setActiveTab('pending')}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: activeTab === 'pending' ? '#007bff' : '#f8f9fa',
              color: activeTab === 'pending' ? 'white' : '#333',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: activeTab === 'pending' ? 'bold' : 'normal'
            }}
          >
            Pagos Pendientes
          </button>
          <button
            onClick={() => setShowConfig(!showConfig)}
            style={{
              padding: '10px 20px',
              border: 'none',
              backgroundColor: showConfig ? '#28a745' : '#6c757d',
              color: 'white',
              cursor: 'pointer',
              borderTopLeftRadius: '4px',
              borderTopRightRadius: '4px',
              fontWeight: 'bold',
              marginLeft: 'auto'
            }}
          >
            {showConfig ? '✕ Cerrar Config' : '⚙️ Config Pagos Yango'}
          </button>
        </div>
      </div>
      
      {showConfig && (
        <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px', border: '2px solid #007bff' }}>
          <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Configuración de Pagos Yango</h3>
          {configLoading ? (
            <div className="loading">Cargando configuración...</div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#e9ecef' }}>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Milestone</th>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'left' }}>Periodo (días)</th>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Monto Individual</th>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>Monto Acumulativo</th>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>Activo</th>
                    <th style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {yangoConfigs
                    .filter(c => c.periodDays === 14)
                    .sort((a, b) => a.milestoneType - b.milestoneType)
                    .map((config) => {
                      const acumulativo = calcularMontoAcumulativoEsperado(config.milestoneType, config.periodDays);
                      const isEditing = editingConfig?.id === config.id;
                      
                      return (
                        <tr key={config.id} style={{ backgroundColor: 'white' }}>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6', fontWeight: 'bold' }}>
                            M{config.milestoneType}
                          </td>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6' }}>
                            {config.periodDays} días
                          </td>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right' }}>
                            {isEditing ? (
                              <input
                                type="number"
                                step="0.01"
                                value={editingConfig.amountYango}
                                onChange={(e) => setEditingConfig({...editingConfig, amountYango: parseFloat(e.target.value)})}
                                style={{ padding: '4px', width: '100px', textAlign: 'right' }}
                              />
                            ) : (
                              `S/ ${config.amountYango.toFixed(2)}`
                            )}
                          </td>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'right', fontWeight: 'bold', color: '#007bff' }}>
                            S/ {acumulativo.toFixed(2)}
                          </td>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>
                            {isEditing ? (
                              <input
                                type="checkbox"
                                checked={editingConfig.isActive}
                                onChange={(e) => setEditingConfig({...editingConfig, isActive: e.target.checked})}
                              />
                            ) : (
                              config.isActive ? '✓' : '✗'
                            )}
                          </td>
                          <td style={{ padding: '10px', border: '1px solid #dee2e6', textAlign: 'center' }}>
                            {isEditing ? (
                              <>
                                <button
                                  onClick={() => handleUpdateConfig(editingConfig)}
                                  style={{ padding: '4px 8px', marginRight: '5px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                                >
                                  Guardar
                                </button>
                                <button
                                  onClick={() => setEditingConfig(null)}
                                  style={{ padding: '4px 8px', backgroundColor: '#6c757d', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                                >
                                  Cancelar
                                </button>
                              </>
                            ) : (
                              <button
                                onClick={() => setEditingConfig({...config})}
                                style={{ padding: '4px 8px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                              >
                                Editar
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {error && (
        <div className="error" style={{ marginBottom: '15px', padding: '10px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
          <h2 style={{ margin: 0 }}>Milestones vs Pagos Yango</h2>
          <button
            onClick={handleRematch}
            disabled={rematchLoading}
            style={{
              padding: '10px 20px',
              backgroundColor: rematchLoading ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: rematchLoading ? 'not-allowed' : 'pointer',
              fontSize: '14px',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            {rematchLoading ? '⏳ Sincronizando...' : '🔄 Re-sincronizar Pagos Yango'}
          </button>
        </div>

        {rematchNotification && (
          <div style={{
            marginBottom: '15px',
            padding: '12px',
            backgroundColor: rematchNotification.includes('Error') ? '#f8d7da' : '#d4edda',
            color: rematchNotification.includes('Error') ? '#721c24' : '#155724',
            borderRadius: '4px',
            fontSize: '14px'
          }}>
            {rematchNotification}
          </div>
        )}

        {rematchProgress && rematchProgress.status === 'running' && (
          <div style={{
            marginBottom: '15px',
            padding: '12px',
            backgroundColor: '#e7f3ff',
            borderRadius: '4px',
            fontSize: '14px'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <span><strong>Progreso:</strong> {rematchProgress.processedTransactions} / {rematchProgress.totalTransactions} transacciones</span>
              <span><strong>{rematchProgress.progressPercentage}%</strong></span>
            </div>
            <div style={{
              width: '100%',
              height: '20px',
              backgroundColor: '#dee2e6',
              borderRadius: '10px',
              overflow: 'hidden'
            }}>
              <div style={{
                width: `${rematchProgress.progressPercentage}%`,
                height: '100%',
                backgroundColor: '#007bff',
                transition: 'width 0.3s ease'
              }} />
            </div>
          </div>
        )}

        {renderNavigation()}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px', marginBottom: '15px' }}>
          <select
            value={filters.milestoneType}
            onChange={(e) => setFilters({...filters, milestoneType: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Milestones</option>
            <option value="1">Milestone 1</option>
            <option value="5">Milestone 5</option>
            <option value="25">Milestone 25</option>
          </select>

          <select
            value={filters.paymentStatus}
            onChange={(e) => setFilters({...filters, paymentStatus: e.target.value})}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Todos los Estados</option>
            <option value="paid">Pagado</option>
            <option value="missing">Falta Pago</option>
            <option value="pending">Pendiente</option>
          </select>
        </div>

        <div style={{ display: 'flex', gap: '20px', alignItems: 'center', flexWrap: 'wrap', fontSize: '14px' }}>
          <div>
            <strong>Total Registros:</strong> {filteredData.length}
          </div>
          <div style={{ color: '#28a745' }}>
            <strong>Pagados:</strong> {totalPaid}
          </div>
          <div style={{ color: '#dc3545' }}>
            <strong>Falta Pago:</strong> {totalMissing}
          </div>
          <div style={{ color: '#ffc107' }}>
            <strong>Pendientes:</strong> {totalPending}
          </div>
          <div>
            <strong>Total Pagado:</strong> S/ {totalAmountPaid.toFixed(2)}
          </div>
          <div style={{ color: '#dc3545', fontWeight: 'bold' }}>
            <strong>Total por Pagar:</strong> S/ {totalAmountToPay.toFixed(2)}
          </div>
          {activeTab === 'pending' && (
            <div style={{ color: '#dc3545', fontWeight: 'bold' }}>
              <strong>Total Esperado Pendiente:</strong> S/ {
                filteredData.reduce((sum, item) => {
                  return sum + calcularMontoAcumulativoEsperado(item.milestoneType, item.periodDays);
                }, 0).toFixed(2)
              }
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="loading">Cargando datos...</div>
      ) : (
        renderTable()
      )}
      
      {renderTripDetailsModal()}
    </div>
  );
};

export default MilestonePaymentView;

