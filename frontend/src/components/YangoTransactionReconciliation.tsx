import React, { useState, useEffect } from 'react';
import { api, YangoTransactionGroup, DriverByDate, MilestoneInstance } from '../services/api';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

const YangoTransactionReconciliation: React.FC = () => {
  const [transactionGroups, setTransactionGroups] = useState<YangoTransactionGroup[]>([]);
  const [drivers, setDrivers] = useState<DriverByDate[]>([]);
  const [milestones, setMilestones] = useState<MilestoneInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingDrivers, setLoadingDrivers] = useState(false);
  const [loadingMilestones, setLoadingMilestones] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [transactionSearchTerm, setTransactionSearchTerm] = useState('');
  const [transactionDateFrom, setTransactionDateFrom] = useState('');
  const [transactionDateTo, setTransactionDateTo] = useState('');
  
  const [driverSearchTerm, setDriverSearchTerm] = useState('');
  const [driverDateFrom, setDriverDateFrom] = useState('');
  const [driverDateTo, setDriverDateTo] = useState('');
  
  const [selectedTransactions, setSelectedTransactions] = useState<Set<number>>(new Set());
  const [selectedDriver, setSelectedDriver] = useState<DriverByDate | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [reprocessing, setReprocessing] = useState(false);
  const [reprocessResult, setReprocessResult] = useState<string | null>(null);
  const [cleaning, setCleaning] = useState(false);
  const [lastUpdateTime, setLastUpdateTime] = useState<string | null>(null);

  useEffect(() => {
    cargarTransacciones().catch((err) => {
      console.error('Error no manejado al cargar transacciones:', err);
    });
  }, []);

  const cargarTransacciones = async () => {
    setLoading(true);
    setError(null);
    try {
      const grupos = await api.getUnmatchedYangoTransactions();
      setTransactionGroups(grupos);
      // Expandir todos los grupos por defecto
      const allGroupKeys = new Set(grupos.map(g => g.driverNameFromComment || `single-${g.transactions[0]?.id}`));
      setExpandedGroups(allGroupKeys);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar transacciones');
      console.error('Error al cargar transacciones:', err);
    } finally {
      setLoading(false);
    }
  };

  const cargarDrivers = async () => {
    if (!driverDateFrom && !driverDateTo) {
      setDrivers([]);
      return;
    }

    setLoadingDrivers(true);
    setError(null);
    try {
      if (driverDateFrom && driverDateTo) {
        const fromDate = new Date(driverDateFrom);
        const toDate = new Date(driverDateTo);
        const allDrivers: DriverByDate[] = [];
        
        // Iterar día por día
        for (let d = new Date(fromDate); d <= toDate; d.setDate(d.getDate() + 1)) {
          const dateStr = d.toISOString().split('T')[0];
          try {
            const driversForDate = await api.getYangoDriversByDate(dateStr, DEFAULT_PARK_ID);
            if (driversForDate && Array.isArray(driversForDate)) {
              // Mapear los datos del backend al formato esperado
              const mappedDrivers = driversForDate.map((driver: any) => ({
                driver_id: driver.driver_id || driver.driverId || '',
                full_name: driver.full_name || driver.fullName || '',
                phone: driver.phone || '',
                hire_date: driver.hire_date || driver.hireDate || dateStr,
                license_number: driver.license_number || driver.licenseNumber || ''
              }));
              allDrivers.push(...mappedDrivers);
              console.log(`Cargados ${mappedDrivers.length} drivers para ${dateStr}`);
            } else {
              console.warn(`Respuesta inesperada para ${dateStr}:`, driversForDate);
            }
          } catch (err) {
            console.warn(`Error cargando drivers para ${dateStr}:`, err);
          }
        }
        
        const uniqueDrivers = Array.from(
          new Map(allDrivers.map(d => [d.driver_id, d])).values()
        );
        setDrivers(uniqueDrivers);
        console.log(`Cargados ${uniqueDrivers.length} drivers únicos del rango ${driverDateFrom} a ${driverDateTo}`);
      } else if (driverDateFrom) {
        const driversForDate = await api.getYangoDriversByDate(driverDateFrom, DEFAULT_PARK_ID);
        console.log('Respuesta de getYangoDriversByDate:', driversForDate);
        if (driversForDate && Array.isArray(driversForDate)) {
          // Mapear los datos del backend al formato esperado
          const mappedDrivers = driversForDate.map((driver: any) => ({
            driver_id: driver.driver_id || driver.driverId || '',
            full_name: driver.full_name || driver.fullName || '',
            phone: driver.phone || '',
            hire_date: driver.hire_date || driver.hireDate || driverDateFrom,
            license_number: driver.license_number || driver.licenseNumber || ''
          }));
          setDrivers(mappedDrivers);
          console.log(`Cargados ${mappedDrivers.length} drivers para ${driverDateFrom}`, mappedDrivers);
        } else {
          console.warn('Respuesta inesperada de getYangoDriversByDate:', driversForDate);
          setDrivers([]);
        }
      }
    } catch (err) {
      console.error('Error al cargar drivers:', err);
      setError('Error al cargar drivers: ' + (err instanceof Error ? err.message : 'Error desconocido'));
      setDrivers([]);
    } finally {
      setLoadingDrivers(false);
    }
  };

  useEffect(() => {
    cargarDrivers().catch((err) => {
      console.error('Error no manejado al cargar drivers:', err);
    });
  }, [driverDateFrom, driverDateTo]);
  
  // Cargar drivers automáticamente al montar el componente si no hay fechas
  useEffect(() => {
    if (!driverDateFrom && !driverDateTo) {
      const today = new Date();
      const thirtyDaysAgo = new Date(today);
      thirtyDaysAgo.setDate(today.getDate() - 30);
      
      setDriverDateFrom(thirtyDaysAgo.toISOString().split('T')[0]);
      setDriverDateTo(today.toISOString().split('T')[0]);
    }
  }, []); // Solo ejecutar una vez al montar

  useEffect(() => {
    if (selectedDriver) {
      cargarMilestonesParaDriver(selectedDriver.driver_id);
    } else {
      setMilestones([]);
    }
  }, [selectedDriver]);

  const cargarMilestonesParaDriver = async (driverId: string) => {
    setLoadingMilestones(true);
    try {
      const driverMilestones = await api.getDriverMilestones(driverId);
      setMilestones(driverMilestones || []);
    } catch (err) {
      console.error('Error al cargar milestones:', err);
      setMilestones([]);
    } finally {
      setLoadingMilestones(false);
    }
  };

  const sincronizarFechas = () => {
    setDriverDateFrom(transactionDateFrom);
    setDriverDateTo(transactionDateTo);
  };

  const toggleGroup = (groupKey: string) => {
    const newExpanded = new Set(expandedGroups);
    if (newExpanded.has(groupKey)) {
      newExpanded.delete(groupKey);
    } else {
      newExpanded.add(groupKey);
    }
    setExpandedGroups(newExpanded);
  };

  const toggleTransactionSelection = (transactionId: number) => {
    const newSelected = new Set(selectedTransactions);
    if (newSelected.has(transactionId)) {
      newSelected.delete(transactionId);
    } else {
      newSelected.add(transactionId);
    }
    setSelectedTransactions(newSelected);
  };

  const selectAllInGroup = (group: YangoTransactionGroup) => {
    const newSelected = new Set(selectedTransactions);
    group.transactions.forEach(t => newSelected.add(t.id));
    setSelectedTransactions(newSelected);
  };

  const deselectAllInGroup = (group: YangoTransactionGroup) => {
    const newSelected = new Set(selectedTransactions);
    group.transactions.forEach(t => newSelected.delete(t.id));
    setSelectedTransactions(newSelected);
  };

  const handleAssignMatch = async () => {
    if (selectedTransactions.size === 0 || !selectedDriver) {
      alert('Por favor selecciona al menos una transacción y un driver');
      return;
    }

    const transactionIds = Array.from(selectedTransactions);
    const milestoneIds = milestones.length > 0 ? milestones.map(m => m.id) : undefined;

    try {
      await api.assignBatchTransactionMatch(transactionIds, selectedDriver.driver_id, milestoneIds);
      setLastUpdateTime(new Date().toLocaleString('es-ES'));
      await cargarTransacciones();
      setSelectedTransactions(new Set());
      setSelectedDriver(null);
      setMilestones([]);
      alert(`Match asignado exitosamente a ${transactionIds.length} transacción(es)`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al asignar match');
      alert('Error al asignar match: ' + (err instanceof Error ? err.message : 'Error desconocido'));
    }
  };

  const filteredGroups = transactionGroups.filter(group => {
    const groupTransactions = group.transactions;
    
    if (transactionSearchTerm) {
      const term = transactionSearchTerm.toLowerCase();
      const matchesSearch = groupTransactions.some(trans => 
        trans.driverNameFromComment?.toLowerCase().includes(term) ||
        trans.comment?.toLowerCase().includes(term)
      );
      if (!matchesSearch) return false;
    }
    
    if (transactionDateFrom || transactionDateTo) {
      const matchesDate = groupTransactions.some(trans => {
        const transDate = new Date(trans.transactionDate);
        if (transactionDateFrom) {
          const fromDate = new Date(transactionDateFrom);
          if (transDate < fromDate) return false;
        }
        if (transactionDateTo) {
          const toDate = new Date(transactionDateTo);
          toDate.setHours(23, 59, 59, 999);
          if (transDate > toDate) return false;
        }
        return true;
      });
      if (!matchesDate) return false;
    }
    
    return true;
  });

  const filteredDrivers = drivers.filter(driver => {
    if (driverSearchTerm) {
      const term = driverSearchTerm.toLowerCase();
      const matchesSearch = 
        driver.driver_id.toLowerCase().includes(term) ||
        (driver.full_name && driver.full_name.toLowerCase().includes(term)) ||
        (driver.phone && driver.phone.includes(term));
      if (!matchesSearch) return false;
    }
    
    if (driverDateFrom) {
      const driverDate = new Date(driver.hire_date);
      const fromDate = new Date(driverDateFrom);
      if (driverDate < fromDate) return false;
    }
    
    if (driverDateTo) {
      const driverDate = new Date(driver.hire_date);
      const toDate = new Date(driverDateTo);
      toDate.setHours(23, 59, 59, 999);
      if (driverDate > toDate) return false;
    }
    
    return true;
  });

  const formatDate = (dateString: string | null) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleDateString('es-ES');
  };

  const getGroupKey = (group: YangoTransactionGroup): string => {
    return group.driverNameFromComment || `single-${group.transactions[0]?.id}`;
  };

  const handleReprocess = async () => {
    if (!window.confirm('¿Deseas reprocesar todas las transacciones sin match con el algoritmo mejorado?')) {
      return;
    }
    
    setReprocessing(true);
    setReprocessResult(null);
    setError(null);
    
    try {
      const result = await api.reprocessUnmatchedYangoTransactions();
      setReprocessResult(
        `Reprocesadas ${result.totalTransactions} transacciones: ` +
        `${result.matchedCount} matcheadas, ${result.unmatchedCount} sin match`
      );
      setLastUpdateTime(new Date().toLocaleString('es-ES'));
      
      await cargarTransacciones();
      
      alert(result.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al reprocesar transacciones');
      alert('Error al reprocesar: ' + (err instanceof Error ? err.message : 'Error desconocido'));
    } finally {
      setReprocessing(false);
    }
  };

  const handleCleanupDuplicates = async () => {
    if (!window.confirm('¿Deseas limpiar transacciones duplicadas? Esto eliminará los duplicados manteniendo el más reciente.')) {
      return;
    }
    
    setCleaning(true);
    setError(null);
    
    try {
      const result = await api.cleanupYangoDuplicates();
      setLastUpdateTime(new Date().toLocaleString('es-ES'));
      alert(`Limpieza completada: ${result.deleted} duplicados eliminados de ${result.totalDuplicates} encontrados`);
      await cargarTransacciones();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al limpiar duplicados');
      alert('Error al limpiar: ' + (err instanceof Error ? err.message : 'Error desconocido'));
    } finally {
      setCleaning(false);
    }
  };

  return (
    <div>
      {lastUpdateTime && (
        <div style={{
          backgroundColor: '#fff3cd',
          border: '2px solid #dc3545',
          borderRadius: '4px',
          padding: '10px 15px',
          marginBottom: '20px',
          textAlign: 'center'
        }}>
          <strong style={{ color: '#dc3545', fontSize: '16px' }}>
            Última Actualización: {lastUpdateTime}
          </strong>
        </div>
      )}

      {error && (
        <div className="error" style={{ marginBottom: '20px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ 
        marginBottom: '20px', 
        display: 'flex', 
        gap: '10px', 
        alignItems: 'center',
        flexWrap: 'wrap'
      }}>
        <button
          onClick={handleReprocess}
          disabled={reprocessing || loading}
          className="btn"
          style={{
            backgroundColor: '#17a2b8',
            color: 'white',
            padding: '10px 20px',
            fontSize: '16px',
            fontWeight: 'bold',
            cursor: reprocessing || loading ? 'not-allowed' : 'pointer',
            opacity: reprocessing || loading ? 0.6 : 1,
            border: 'none',
            borderRadius: '4px'
          }}
        >
          {reprocessing ? '⏳ Reprocesando...' : '🔄 Reprocesar Transacciones Sin Match'}
        </button>
        
        {reprocessResult && (
          <span style={{ color: '#28a745', fontWeight: 'bold' }}>
            {reprocessResult}
          </span>
        )}

        <button
          onClick={handleCleanupDuplicates}
          disabled={cleaning || loading}
          className="btn"
          style={{
            backgroundColor: '#ffc107',
            color: 'black',
            padding: '10px 20px',
            fontSize: '16px',
            fontWeight: 'bold',
            cursor: cleaning || loading ? 'not-allowed' : 'pointer',
            opacity: cleaning || loading ? 0.6 : 1,
            border: 'none',
            borderRadius: '4px'
          }}
        >
          {cleaning ? '🧹 Limpiando Duplicados...' : '🧹 Limpiar Duplicados Existentes'}
        </button>
      </div>

      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '10px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          {selectedTransactions.size > 0 && selectedDriver && (
            <button
              onClick={handleAssignMatch}
              className="btn"
              style={{ 
                backgroundColor: '#28a745', 
                color: 'white',
                padding: '10px 20px',
                fontSize: '16px',
                fontWeight: 'bold'
              }}
            >
              ✓ Matchear {selectedTransactions.size} transacción(es) → {selectedDriver.driver_id}
            </button>
          )}
          {(!selectedTransactions.size || !selectedDriver) && (
            <span style={{ color: '#6c757d' }}>
              Selecciona transacciones y un driver para asignar match
            </span>
          )}
          {selectedTransactions.size > 0 && (
            <span style={{ color: '#6c757d', fontSize: '14px' }}>
              ({selectedTransactions.size} transacción(es) seleccionada(s))
            </span>
          )}
        </div>
        <button
          onClick={sincronizarFechas}
          className="btn"
          style={{ 
            backgroundColor: '#007bff', 
            color: 'white',
            padding: '8px 16px'
          }}
          disabled={!transactionDateFrom && !transactionDateTo}
        >
          🔄 Sincronizar Fechas
        </button>
      </div>

      <div className="reconciliation-grid">
        <div style={{ 
          backgroundColor: 'white', 
          padding: '15px', 
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }}>
          <h2 style={{ marginBottom: '15px', color: '#dc3545' }}>
            Transacciones sin Match ({filteredGroups.reduce((sum, g) => sum + g.count, 0)})
          </h2>
          
          <div style={{ marginBottom: '15px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <input
              type="text"
              placeholder="Buscar por nombre del driver o comentario..."
              value={transactionSearchTerm}
              onChange={(e) => setTransactionSearchTerm(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type="date"
                placeholder="Fecha desde"
                value={transactionDateFrom}
                onChange={(e) => setTransactionDateFrom(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              <input
                type="date"
                placeholder="Fecha hasta"
                value={transactionDateTo}
                onChange={(e) => setTransactionDateTo(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            {loading ? (
              <div className="loading">Cargando transacciones...</div>
            ) : (
              <div>
                {filteredGroups.map((group) => {
                  const groupKey = getGroupKey(group);
                  const isExpanded = expandedGroups.has(groupKey);
                  const groupSelectedCount = group.transactions.filter(t => selectedTransactions.has(t.id)).length;
                  const allSelected = group.transactions.length > 0 && groupSelectedCount === group.transactions.length;
                  
                  return (
                    <div key={groupKey} style={{ marginBottom: '10px', border: '1px solid #dee2e6', borderRadius: '4px' }}>
                      <div 
                        style={{ 
                          padding: '10px', 
                          backgroundColor: allSelected ? '#e7f3ff' : '#f8f9fa',
                          cursor: 'pointer',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center'
                        }}
                        onClick={() => toggleGroup(groupKey)}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <span>{isExpanded ? '▼' : '▶'}</span>
                          <strong>
                            {group.driverNameFromComment || 'Sin nombre de driver'}
                          </strong>
                          <span style={{ 
                            backgroundColor: '#007bff', 
                            color: 'white', 
                            padding: '2px 8px', 
                            borderRadius: '12px',
                            fontSize: '12px'
                          }}>
                            {group.count} transacción(es)
                          </span>
                          {groupSelectedCount > 0 && (
                            <span style={{ 
                              backgroundColor: '#28a745', 
                              color: 'white', 
                              padding: '2px 8px', 
                              borderRadius: '12px',
                              fontSize: '12px'
                            }}>
                              {groupSelectedCount} seleccionada(s)
                            </span>
                          )}
                        </div>
                        <div>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              if (allSelected) {
                                deselectAllInGroup(group);
                              } else {
                                selectAllInGroup(group);
                              }
                            }}
                            style={{ 
                              padding: '4px 8px', 
                              fontSize: '12px',
                              marginRight: '5px',
                              backgroundColor: allSelected ? '#dc3545' : '#28a745',
                              color: 'white',
                              border: 'none',
                              borderRadius: '4px',
                              cursor: 'pointer'
                            }}
                          >
                            {allSelected ? 'Deseleccionar todo' : 'Seleccionar todo'}
                          </button>
                        </div>
                      </div>
                      
                      {isExpanded && (
                        <div style={{ padding: '10px' }}>
                          <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                            <thead>
                              <tr style={{ backgroundColor: '#f8f9fa' }}>
                                <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                                <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>ID</th>
                                <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Milestone</th>
                                <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                                <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Monto</th>
                              </tr>
                            </thead>
                            <tbody>
                              {group.transactions.map((trans) => (
                                <tr 
                                  key={trans.id}
                                  onClick={() => toggleTransactionSelection(trans.id)}
                                  style={{ 
                                    cursor: 'pointer',
                                    backgroundColor: selectedTransactions.has(trans.id) ? '#e7f3ff' : 'white',
                                    borderBottom: '1px solid #dee2e6'
                                  }}
                                >
                                  <td style={{ padding: '8px' }}>
                                    {selectedTransactions.has(trans.id) && '✓'}
                                  </td>
                                  <td style={{ padding: '8px' }}>{trans.id}</td>
                                  <td style={{ padding: '8px' }}>{trans.milestoneType || 'N/A'}</td>
                                  <td style={{ padding: '8px' }}>{formatDate(trans.transactionDate)}</td>
                                  <td style={{ padding: '8px' }}>{trans.amountYango || 'N/A'}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div style={{ 
          backgroundColor: 'white', 
          padding: '15px', 
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }}>
          <h2 style={{ marginBottom: '15px', color: '#28a745' }}>Drivers Registrados ({filteredDrivers.length})</h2>
          
          <div style={{ marginBottom: '15px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <input
              type="text"
              placeholder="Buscar por driver_id, nombre o teléfono..."
              value={driverSearchTerm}
              onChange={(e) => setDriverSearchTerm(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type="date"
                placeholder="Fecha desde"
                value={driverDateFrom}
                onChange={(e) => setDriverDateFrom(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              <input
                type="date"
                placeholder="Fecha hasta"
                value={driverDateTo}
                onChange={(e) => setDriverDateTo(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            {loadingDrivers ? (
              <div className="loading">Cargando drivers...</div>
            ) : (
              <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8f9fa', zIndex: 10 }}>
                  <tr>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Driver ID</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Nombre</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Teléfono</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDrivers.map((driver) => (
                    <tr 
                      key={driver.driver_id}
                      onClick={() => setSelectedDriver(driver)}
                      style={{ 
                        cursor: 'pointer',
                        backgroundColor: selectedDriver?.driver_id === driver.driver_id ? '#e7f3ff' : 'white',
                        borderBottom: '1px solid #dee2e6'
                      }}
                    >
                      <td style={{ padding: '8px' }}>
                        {selectedDriver?.driver_id === driver.driver_id && '✓'}
                      </td>
                      <td style={{ padding: '8px' }}>{driver.driver_id}</td>
                      <td style={{ padding: '8px' }}>{driver.full_name || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{driver.phone || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{formatDate(driver.hire_date)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
          
          {selectedDriver && (
            <div style={{ marginTop: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
              <h3 style={{ marginBottom: '10px' }}>Milestones de {selectedDriver.full_name}</h3>
              {loadingMilestones ? (
                <div className="loading">Cargando milestones...</div>
              ) : milestones.length > 0 ? (
                <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ backgroundColor: '#e9ecef' }}>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Tipo</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Período</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                    </tr>
                  </thead>
                  <tbody>
                    {milestones.map((milestone) => (
                      <tr key={milestone.id} style={{ borderBottom: '1px solid #dee2e6' }}>
                        <td style={{ padding: '8px' }}>{milestone.milestoneType} viajes</td>
                        <td style={{ padding: '8px' }}>{milestone.periodDays} días</td>
                        <td style={{ padding: '8px' }}>{formatDate(milestone.fulfillmentDate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p style={{ color: '#6c757d' }}>No hay milestones disponibles para este driver</p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default YangoTransactionReconciliation;
