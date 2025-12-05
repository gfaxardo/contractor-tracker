import React, { useState, useEffect } from 'react';
import { api, LeadMatch, DriverByDate } from '../services/api';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

const LeadReconciliation: React.FC = () => {
  const [leads, setLeads] = useState<LeadMatch[]>([]);
  const [drivers, setDrivers] = useState<DriverByDate[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingDrivers, setLoadingDrivers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Filtros lado izquierdo (Leads)
  const [leadSearchTerm, setLeadSearchTerm] = useState('');
  const [leadDateFrom, setLeadDateFrom] = useState('');
  const [leadDateTo, setLeadDateTo] = useState('');
  
  // Filtros lado derecho (Drivers)
  const [driverSearchTerm, setDriverSearchTerm] = useState('');
  const [driverDateFrom, setDriverDateFrom] = useState('');
  const [driverDateTo, setDriverDateTo] = useState('');
  
  // Selección
  const [selectedLead, setSelectedLead] = useState<LeadMatch | null>(null);
  const [selectedDriver, setSelectedDriver] = useState<DriverByDate | null>(null);

  useEffect(() => {
    cargarLeads().catch((err) => {
      console.error('Error no manejado al cargar leads:', err);
    });
  }, []);

  const cargarLeads = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getUnmatchedLeads();
      setLeads(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar leads');
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
      // Si hay rango de fechas, cargar drivers de todas las fechas en el rango
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
        
        // Eliminar duplicados por driver_id
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
    setDriverDateFrom(leadDateFrom);
    setDriverDateTo(leadDateTo);
  };

  const handleAssignMatch = async () => {
    if (!selectedLead || !selectedDriver) return;

    try {
      await api.assignManualMatch(selectedLead.externalId, selectedDriver.driver_id);
      await cargarLeads();
      setSelectedLead(null);
      setSelectedDriver(null);
      alert('Match asignado exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al asignar match');
    }
  };

  const handleDiscard = async (externalId: string) => {
    if (!window.confirm('¿Está seguro de descartar este lead?')) {
      return;
    }

    try {
      await api.discardLead(externalId);
      await cargarLeads();
      setSelectedLead(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al descartar lead');
    }
  };

  const filteredLeads = leads.filter(lead => {
    if (leadSearchTerm) {
      const term = leadSearchTerm.toLowerCase();
      const matchesSearch = 
        lead.externalId.toLowerCase().includes(term) ||
        (lead.leadFirstName && lead.leadFirstName.toLowerCase().includes(term)) ||
        (lead.leadLastName && lead.leadLastName.toLowerCase().includes(term)) ||
        (lead.leadPhone && lead.leadPhone.includes(term));
      if (!matchesSearch) return false;
    }
    
    if (leadDateFrom) {
      const leadDate = new Date(lead.leadCreatedAt);
      const fromDate = new Date(leadDateFrom);
      if (leadDate < fromDate) return false;
    }
    
    if (leadDateTo) {
      const leadDate = new Date(lead.leadCreatedAt);
      const toDate = new Date(leadDateTo);
      toDate.setHours(23, 59, 59, 999);
      if (leadDate > toDate) return false;
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

  const getFullName = (lead: LeadMatch) => {
    if (lead.leadFirstName && lead.leadLastName) {
      return `${lead.leadFirstName} ${lead.leadLastName}`;
    }
    return lead.leadFirstName || lead.leadLastName || 'N/A';
  };

  const highlightMatch = (lead: LeadMatch, driver: DriverByDate) => {
    const leadPhoneNormalized = lead.leadPhone?.replace(/\D/g, '') || '';
    const driverPhoneNormalized = driver.phone?.replace(/\D/g, '') || '';
    const phoneMatch = leadPhoneNormalized && driverPhoneNormalized && 
                      leadPhoneNormalized === driverPhoneNormalized;
    
    const leadName = getFullName(lead).toLowerCase();
    const driverName = driver.full_name?.toLowerCase() || '';
    const nameMatch = leadName && driverName && 
                     (leadName.includes(driverName) || driverName.includes(leadName));
    
    return phoneMatch || nameMatch;
  };

  return (
    <div>
      {error && (
        <div className="error" style={{ marginBottom: '20px' }}>
          Error: {error}
        </div>
      )}

      {/* Barra de acciones globales */}
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
          {selectedLead && selectedDriver && (
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
              ✓ Asignar Match: {selectedLead.externalId} → {selectedDriver.driver_id}
            </button>
          )}
          {(!selectedLead || !selectedDriver) && (
            <span style={{ color: '#6c757d' }}>
              Selecciona un lead y un driver para asignar match
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
          disabled={!leadDateFrom && !leadDateTo}
        >
          🔄 Sincronizar Fechas
        </button>
      </div>

      {/* Vista Dividida */}
      <div className="reconciliation-grid">
        {/* LADO IZQUIERDO: Leads sin match */}
        <div style={{ 
          backgroundColor: 'white', 
          padding: '15px', 
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }}>
          <h2 style={{ marginBottom: '15px', color: '#dc3545' }}>Leads sin Match ({filteredLeads.length})</h2>
          
          {/* Filtros Leads */}
          <div style={{ marginBottom: '15px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <input
              type="text"
              placeholder="Buscar por external_id, nombre o teléfono..."
              value={leadSearchTerm}
              onChange={(e) => setLeadSearchTerm(e.target.value)}
              style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type="date"
                placeholder="Fecha desde"
                value={leadDateFrom}
                onChange={(e) => setLeadDateFrom(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              <input
                type="date"
                placeholder="Fecha hasta"
                value={leadDateTo}
                onChange={(e) => setLeadDateTo(e.target.value)}
                style={{ flex: 1, padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
            </div>
          </div>

          {/* Tabla Leads */}
          <div style={{ flex: 1, overflowY: 'auto' }}>
            {loading ? (
              <div className="loading">Cargando leads...</div>
            ) : (
              <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8f9fa', zIndex: 10 }}>
                  <tr>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>External ID</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Nombre</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Teléfono</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredLeads.map((lead) => (
                    <tr 
                      key={lead.externalId}
                      onClick={() => setSelectedLead(lead)}
                      style={{ 
                        cursor: 'pointer',
                        backgroundColor: selectedLead?.externalId === lead.externalId ? '#e7f3ff' : 'white',
                        borderBottom: '1px solid #dee2e6'
                      }}
                      onMouseEnter={(e) => {
                        if (selectedLead?.externalId !== lead.externalId) {
                          e.currentTarget.style.backgroundColor = '#f8f9fa';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (selectedLead?.externalId !== lead.externalId) {
                          e.currentTarget.style.backgroundColor = 'white';
                        }
                      }}
                    >
                      <td style={{ padding: '8px' }}>
                        {selectedLead?.externalId === lead.externalId && '✓'}
                      </td>
                      <td style={{ padding: '8px' }}>{lead.externalId}</td>
                      <td style={{ padding: '8px' }}>{getFullName(lead)}</td>
                      <td style={{ padding: '8px' }}>{lead.leadPhone || 'N/A'}</td>
                      <td style={{ padding: '8px' }}>{formatDate(lead.leadCreatedAt)}</td>
                      <td style={{ padding: '8px' }}>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDiscard(lead.externalId);
                          }}
                          style={{ 
                            padding: '4px 8px', 
                            backgroundColor: '#dc3545', 
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '11px'
                          }}
                        >
                          Descartar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* LADO DERECHO: Drivers registrados */}
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
          
          {/* Filtros Drivers */}
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

          {/* Tabla Drivers */}
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
                    const isHighlighted = selectedLead && highlightMatch(selectedLead, driver);
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

export default LeadReconciliation;
