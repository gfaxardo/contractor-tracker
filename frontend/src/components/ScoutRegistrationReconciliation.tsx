import React, { useState, useEffect } from 'react';
import { api, ScoutRegistrationReconciliationDTO, DriverByDate } from '../services/api';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

const ScoutRegistrationReconciliationComponent: React.FC = () => {
  const [registrations, setRegistrations] = useState<ScoutRegistrationReconciliationDTO[]>([]);
  const [drivers, setDrivers] = useState<DriverByDate[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingDrivers, setLoadingDrivers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [registrationSearchTerm, setRegistrationSearchTerm] = useState('');
  const [registrationDateFrom, setRegistrationDateFrom] = useState('');
  const [registrationDateTo, setRegistrationDateTo] = useState('');
  
  const [driverSearchTerm, setDriverSearchTerm] = useState('');
  const [driverDateFrom, setDriverDateFrom] = useState('');
  const [driverDateTo, setDriverDateTo] = useState('');
  
  const [selectedRegistration, setSelectedRegistration] = useState<ScoutRegistrationReconciliationDTO | null>(null);
  const [selectedDriver, setSelectedDriver] = useState<DriverByDate | null>(null);

  useEffect(() => {
    cargarRegistros().catch((err) => {
      console.error('Error no manejado al cargar registros:', err);
    });
  }, []);

  const cargarRegistros = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getUnmatchedScoutRegistrations();
      setRegistrations(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar registros de scouts');
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
    try {
      if (driverDateFrom && driverDateTo) {
        const fromDate = new Date(driverDateFrom);
        const toDate = new Date(driverDateTo);
        const allDrivers: DriverByDate[] = [];
        
        for (let d = new Date(fromDate); d <= toDate; d.setDate(d.getDate() + 1)) {
          const dateStr = d.toISOString().split('T')[0];
          try {
            const driversForDate = await api.getDriversByDate(dateStr, DEFAULT_PARK_ID);
            allDrivers.push(...driversForDate);
          } catch (err) {
            console.warn(`Error cargando drivers para ${dateStr}:`, err);
          }
        }
        
        const uniqueDrivers = Array.from(
          new Map(allDrivers.map(d => [d.driver_id, d])).values()
        );
        setDrivers(uniqueDrivers);
      } else if (driverDateFrom) {
        const driversForDate = await api.getDriversByDate(driverDateFrom, DEFAULT_PARK_ID);
        setDrivers(driversForDate);
      }
    } catch (err) {
      console.error('Error al cargar drivers:', err);
      setError('Error al cargar drivers');
    } finally {
      setLoadingDrivers(false);
    }
  };

  useEffect(() => {
    cargarDrivers().catch((err) => {
      console.error('Error no manejado al cargar drivers:', err);
    });
  }, [driverDateFrom, driverDateTo]);

  const sincronizarFechas = () => {
    setDriverDateFrom(registrationDateFrom);
    setDriverDateTo(registrationDateTo);
  };

  const handleAssignMatch = async () => {
    if (!selectedRegistration || !selectedDriver) return;

    try {
      await api.assignScoutRegistrationMatch(selectedRegistration.id, selectedDriver.driver_id);
      await cargarRegistros();
      setSelectedRegistration(null);
      setSelectedDriver(null);
      alert('Match asignado exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al asignar match');
    }
  };

  const filteredRegistrations = registrations.filter(reg => {
    if (registrationSearchTerm) {
      const term = registrationSearchTerm.toLowerCase();
      const matchesSearch = 
        reg.scoutName.toLowerCase().includes(term) ||
        (reg.driverName && reg.driverName.toLowerCase().includes(term)) ||
        (reg.driverPhone && reg.driverPhone.includes(term)) ||
        (reg.driverLicense && reg.driverLicense.toLowerCase().includes(term));
      if (!matchesSearch) return false;
    }
    
    if (registrationDateFrom) {
      const regDate = new Date(reg.registrationDate);
      const fromDate = new Date(registrationDateFrom);
      if (regDate < fromDate) return false;
    }
    
    if (registrationDateTo) {
      const regDate = new Date(reg.registrationDate);
      const toDate = new Date(registrationDateTo);
      toDate.setHours(23, 59, 59, 999);
      if (regDate > toDate) return false;
    }
    
    return true;
  });

  const filteredDrivers = drivers.filter(driver => {
    if (driverSearchTerm) {
      const term = driverSearchTerm.toLowerCase();
      const matchesSearch = 
        driver.driver_id.toLowerCase().includes(term) ||
        (driver.full_name && driver.full_name.toLowerCase().includes(term)) ||
        (driver.phone && driver.phone.includes(term)) ||
        (driver.license_number && driver.license_number.toLowerCase().includes(term));
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

  const highlightMatch = (registration: ScoutRegistrationReconciliationDTO, driver: DriverByDate) => {
    const regPhoneNormalized = registration.driverPhone?.replace(/\D/g, '') || '';
    const driverPhoneNormalized = driver.phone?.replace(/\D/g, '') || '';
    const phoneMatch = regPhoneNormalized && driverPhoneNormalized && 
                      regPhoneNormalized === driverPhoneNormalized;
    
    const regName = registration.driverName?.toLowerCase() || '';
    const driverName = driver.full_name?.toLowerCase() || '';
    const nameMatch = regName && driverName && 
                     (regName.includes(driverName) || driverName.includes(regName));
    
    const regLicense = registration.driverLicense?.trim().toUpperCase() || '';
    const driverLicense = driver.license_number?.trim().toUpperCase() || '';
    const licenseMatch = regLicense && driverLicense && regLicense === driverLicense;
    
    return phoneMatch || nameMatch || licenseMatch;
  };

  return (
    <div>
      {error && (
        <div className="error" style={{ marginBottom: '20px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <div>
          {selectedRegistration && selectedDriver && (
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
              ✓ Asignar Match: {selectedRegistration.driverName} → {selectedDriver.driver_id}
            </button>
          )}
          {(!selectedRegistration || !selectedDriver) && (
            <span style={{ color: '#6c757d' }}>
              Selecciona un registro de scout y un driver para asignar match
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
          disabled={!registrationDateFrom && !registrationDateTo}
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
          <h2 style={{ marginBottom: '15px', color: '#dc3545' }}>Registros de Scouts sin Match ({filteredRegistrations.length})</h2>
          
          <div style={{ marginBottom: '15px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <input
              type="text"
              placeholder="Buscar por scout, nombre, teléfono o licencia..."
              value={registrationSearchTerm}
              onChange={(e) => setRegistrationSearchTerm(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type="date"
                placeholder="Fecha desde"
                value={registrationDateFrom}
                onChange={(e) => setRegistrationDateFrom(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              <input
                type="date"
                placeholder="Fecha hasta"
                value={registrationDateTo}
                onChange={(e) => setRegistrationDateTo(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            {loading ? (
              <div className="loading">Cargando registros...</div>
            ) : (
              <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8f9fa', zIndex: 10 }}>
                  <tr>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Scout</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Nombre</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Teléfono</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Licencia</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Medio</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRegistrations.map((reg) => (
                    <tr 
                      key={reg.id}
                      onClick={() => setSelectedRegistration(reg)}
                      style={{ 
                        cursor: 'pointer',
                        backgroundColor: selectedRegistration?.id === reg.id ? '#e7f3ff' : 'white',
                        borderBottom: '1px solid #dee2e6'
                      }}
                      onMouseEnter={(e) => {
                        if (selectedRegistration?.id !== reg.id) {
                          e.currentTarget.style.backgroundColor = '#f8f9fa';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (selectedRegistration?.id !== reg.id) {
                          e.currentTarget.style.backgroundColor = 'white';
                        }
                      }}
                    >
                      <td style={{ padding: '8px' }}>
                        {selectedRegistration?.id === reg.id && '✓'}
                      </td>
                      <td style={{ padding: '8px' }}>{reg.scoutName}</td>
                      <td style={{ padding: '8px' }}>{reg.driverName || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{reg.driverPhone || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{reg.driverLicense || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{formatDate(reg.registrationDate)}</td>
                      <td style={{ padding: '8px' }}>{reg.acquisitionMedium || 'N/A'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
              placeholder="Buscar por driver_id, nombre, teléfono o licencia..."
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
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Licencia</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDrivers.map((driver) => {
                    const isHighlighted = selectedRegistration && highlightMatch(selectedRegistration, driver);
                    return (
                      <tr 
                        key={driver.driver_id}
                        onClick={() => setSelectedDriver(driver)}
                        style={{ 
                          cursor: 'pointer',
                          backgroundColor: selectedDriver?.driver_id === driver.driver_id 
                            ? '#e7f3ff' 
                            : isHighlighted 
                              ? '#fff3cd' 
                              : 'white',
                          borderBottom: '1px solid #dee2e6'
                        }}
                        onMouseEnter={(e) => {
                          if (selectedDriver?.driver_id !== driver.driver_id) {
                            e.currentTarget.style.backgroundColor = isHighlighted ? '#ffeaa7' : '#f8f9fa';
                          }
                        }}
                        onMouseLeave={(e) => {
                          if (selectedDriver?.driver_id !== driver.driver_id) {
                            e.currentTarget.style.backgroundColor = isHighlighted ? '#fff3cd' : 'white';
                          }
                        }}
                      >
                        <td style={{ padding: '8px' }}>
                          {selectedDriver?.driver_id === driver.driver_id && '✓'}
                          {isHighlighted && selectedDriver?.driver_id !== driver.driver_id && '⚠'}
                        </td>
                        <td style={{ padding: '8px' }}>{driver.driver_id}</td>
                        <td style={{ padding: '8px' }}>{driver.full_name || 'N/A'}</td>
                        <td style={{ padding: '8px' }}>{driver.phone || 'N/A'}</td>
                        <td style={{ padding: '8px' }}>{formatDate(driver.hire_date)}</td>
                        <td style={{ padding: '8px' }}>{driver.license_number || 'N/A'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

const ScoutRegistrationReconciliation = ScoutRegistrationReconciliationComponent;
export default ScoutRegistrationReconciliation;

