import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { api, LeadCabinetDTO, MilestoneInstance, DriverByDate } from '../services/api';
import MilestoneDetailModal from './MilestoneDetailModal';
import Pagination from './Pagination';
import { getCurrentWeekISO, formatWeekISO, getWeekRange, getPreviousWeek, getNextWeek } from '../utils/weekUtils';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

const Cabinet: React.FC = () => {
  const [leads, setLeads] = useState<LeadCabinetDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [weekISO, setWeekISO] = useState(getCurrentWeekISO());
  const [matchStatus, setMatchStatus] = useState<'matched' | 'unmatched' | 'all'>('all');
  const [driverStatus, setDriverStatus] = useState<'solo_registro' | 'conecto_sin_viajes' | 'activo_con_viajes' | 'all'>('all');
  const [milestoneType, setMilestoneType] = useState<number | undefined>(undefined);
  const [milestonePeriod, setMilestonePeriod] = useState<number | undefined>(undefined);
  const [search, setSearch] = useState('');
  const [includeDiscarded, setIncludeDiscarded] = useState(false);
  const [hasYangoTransaction, setHasYangoTransaction] = useState<string>('');
  const [yangoMilestoneType, setYangoMilestoneType] = useState<string>('');
  
  const [selectedLead, setSelectedLead] = useState<LeadCabinetDTO | null>(null);
  const [selectedMilestone, setSelectedMilestone] = useState<MilestoneInstance | null>(null);
  const [showReassignModal, setShowReassignModal] = useState(false);
  const [reassignDrivers, setReassignDrivers] = useState<DriverByDate[]>([]);
  const [loadingDrivers, setLoadingDrivers] = useState(false);
  const [driverSearchTerm, setDriverSearchTerm] = useState('');
  const [driverDateFrom, setDriverDateFrom] = useState('');
  const [driverDateTo, setDriverDateTo] = useState('');
  const [selectedDriver, setSelectedDriver] = useState<DriverByDate | null>(null);
  
  const [showScoutReconcileModal, setShowScoutReconcileModal] = useState(false);
  const [scoutSuggestions, setScoutSuggestions] = useState<any[]>([]);
  const [loadingScoutSuggestions, setLoadingScoutSuggestions] = useState(false);
  const [selectedScoutRegistration, setSelectedScoutRegistration] = useState<any | null>(null);
  
  const [currentPage, setCurrentTablePage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const cargarLeads = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getLeadsCabinet({
        weekISO: weekISO || undefined,
        dateFrom: weekISO ? undefined : (dateFrom || undefined),
        dateTo: weekISO ? undefined : (dateTo || undefined),
        matchStatus: matchStatus !== 'all' ? matchStatus : undefined,
        driverStatus: driverStatus !== 'all' ? driverStatus : undefined,
        milestoneType,
        milestonePeriod,
        search: search.trim() || undefined,
        includeDiscarded
      });
      setLeads(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar leads');
    } finally {
      setLoading(false);
    }
  }, [weekISO, dateFrom, dateTo, matchStatus, driverStatus, milestoneType, milestonePeriod, search, includeDiscarded]);

  useEffect(() => {
    cargarLeads().catch((err) => {
      console.error('Error no manejado al cargar leads:', err);
    });
  }, [cargarLeads]);

  const cargarDrivers = async () => {
    if (!driverDateFrom && !driverDateTo) {
      setReassignDrivers([]);
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
        setReassignDrivers(uniqueDrivers);
      } else if (driverDateFrom) {
        const driversForDate = await api.getDriversByDate(driverDateFrom, DEFAULT_PARK_ID);
        setReassignDrivers(driversForDate);
      }
    } catch (err) {
      console.error('Error al cargar drivers:', err);
    } finally {
      setLoadingDrivers(false);
    }
  };

  useEffect(() => {
    if (showReassignModal) {
      cargarDrivers().catch((err) => {
        console.error('Error no manejado al cargar drivers:', err);
      });
    }
  }, [showReassignModal, driverDateFrom, driverDateTo]);

  const handleReassignMatch = async () => {
    if (!selectedLead || !selectedDriver) return;

    try {
      await api.assignManualMatch(selectedLead.externalId, selectedDriver.driver_id);
      await cargarLeads();
      setShowReassignModal(false);
      setSelectedLead(null);
      setSelectedDriver(null);
      alert('Match reasignado exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al reasignar match');
    }
  };

  const cargarSugerenciasScout = async (externalId: string) => {
    setLoadingScoutSuggestions(true);
    try {
      const sugerencias = await api.getScoutSuggestionsForLead(externalId);
      setScoutSuggestions(sugerencias);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar sugerencias');
    } finally {
      setLoadingScoutSuggestions(false);
    }
  };

  const handleAssignScout = async () => {
    if (!selectedLead || !selectedScoutRegistration) return;

    try {
      await api.assignScoutToLead(selectedLead.externalId, selectedScoutRegistration.id);
      await cargarLeads();
      setShowScoutReconcileModal(false);
      setSelectedLead(null);
      setSelectedScoutRegistration(null);
      setScoutSuggestions([]);
      alert('Scout registration asignado exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al asignar scout registration');
    }
  };

  const formatDate = (dateString: string | null | undefined) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleDateString('es-ES');
  };

  const getFullName = (lead: LeadCabinetDTO) => {
    if (lead.leadFirstName && lead.leadLastName) {
      return `${lead.leadFirstName} ${lead.leadLastName}`;
    }
    return lead.leadFirstName || lead.leadLastName || 'N/A';
  };

  const getStatusLabel = (status: string | null) => {
    if (!status) return 'N/A';
    const labels: Record<string, string> = {
      'solo_registro': 'Solo Registro',
      'conecto_sin_viajes': 'Conectó Sin Viajes',
      'activo_con_viajes': 'Activo Con Viajes'
    };
    return labels[status] || status;
  };

  const getStatusClass = (status: string | null) => {
    if (!status) return '';
    return `status-${status}`;
  };

  const renderConexionCabinet = (lead: LeadCabinetDTO) => {
    const seConecto = (lead.sumWorkTimeSeconds && lead.sumWorkTimeSeconds > 0) || 
                      (lead.totalTrips14d && lead.totalTrips14d > 0);
    
    if (seConecto) {
      return (
        <span
          title="Driver se conectó"
          style={{
            color: '#28a745',
            fontSize: '16px',
            fontWeight: 'bold'
          }}
        >
          ✓
        </span>
      );
    }
    return (
      <span
        title="Driver no se conectó"
        style={{
          color: '#999',
          fontSize: '14px'
        }}
      >
        -
      </span>
    );
  };

  const renderMilestoneColumnaCabinet = (lead: LeadCabinetDTO, milestoneType: number) => {
    if (!lead.milestones) {
      lead.milestones = [];
    }
    
    const milestone = lead.milestones.find(m => m.milestoneType === milestoneType && m.periodDays === 14);
    const transaccion = lead.yangoTransactions14d?.find(t => {
      if (t.milestoneType === milestoneType) {
        if (milestone) {
          return t.milestoneInstanceId === milestone.id || 
                 (t.milestoneInstance && t.milestoneInstance.id === milestone.id);
        }
        return true;
      }
      return false;
    });

    const tieneMilestone = !!milestone;
    const tieneTransaccion = !!transaccion;
    const porPagar = tieneMilestone && !tieneTransaccion;

    const handleClick = (event: React.MouseEvent) => {
      event.stopPropagation();
      if (milestone) {
        setSelectedMilestone(milestone);
      }
    };

    let contenido: React.ReactNode;
    let estilo: React.CSSProperties;
    let titulo = `${milestoneType} viaje${milestoneType > 1 ? 's' : ''}`;

    if (tieneMilestone && tieneTransaccion) {
      titulo += ` - Alcanzado y Pagado: S/ ${transaccion.amountYango.toFixed(2)}`;
      if (transaccion.transactionDate) {
        const fecha = new Date(transaccion.transactionDate).toLocaleDateString('es-ES');
        titulo += ` (${fecha})`;
      }
      if (milestone) {
        titulo += ` - Instancia ID: ${milestone.id}`;
      }
      contenido = (
        <span style={{ display: 'flex', alignItems: 'center', gap: '3px', justifyContent: 'center' }}>
          <span style={{ color: '#28a745', fontSize: '14px' }}>✓</span>
          <span style={{ fontSize: '12px' }}>💰</span>
        </span>
      );
      estilo = {
        cursor: 'pointer',
        padding: '4px 8px',
        borderRadius: '4px',
        backgroundColor: '#e8f5e9',
        border: '1px solid #28a745',
        display: 'inline-block'
      };
    } else if (porPagar) {
      titulo += ' - Alcanzado pero NO pagado (Por pagar)';
      if (milestone) {
        titulo += ` - Instancia ID: ${milestone.id}`;
      }
      contenido = (
        <span style={{ display: 'flex', alignItems: 'center', gap: '3px', justifyContent: 'center' }}>
          <span style={{ color: '#28a745', fontSize: '14px' }}>✓</span>
          <span style={{ color: '#ff9800', fontSize: '14px' }}>⚠</span>
        </span>
      );
      estilo = {
        cursor: 'pointer',
        padding: '4px 8px',
        borderRadius: '4px',
        backgroundColor: '#fff3e0',
        border: '1px solid #ff9800',
        display: 'inline-block'
      };
    } else if (tieneTransaccion && !tieneMilestone) {
      titulo += ` - Pagado pero NO alcanzado: S/ ${transaccion.amountYango.toFixed(2)} (Posible error)`;
      contenido = (
        <span style={{ fontSize: '12px' }}>💰</span>
      );
      estilo = {
        padding: '4px 8px',
        borderRadius: '4px',
        backgroundColor: '#fff9c4',
        border: '1px solid #ffc107',
        display: 'inline-block'
      };
    } else {
      titulo += ' - No alcanzado ni pagado';
      contenido = (
        <span style={{ color: '#999', fontSize: '14px' }}>-</span>
      );
      estilo = {
        padding: '4px 8px',
        textAlign: 'center',
        display: 'inline-block'
      };
    }

    return (
      <div
        onClick={handleClick}
        title={titulo}
        style={estilo}
      >
        {contenido}
      </div>
    );
  };

  const filteredReassignDrivers = reassignDrivers.filter(driver => {
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

  const filteredLeads = useMemo(() => {
    let filtered = leads;
    
    if (hasYangoTransaction) {
      if (hasYangoTransaction === 'with') {
        filtered = filtered.filter(lead => 
          lead.yangoTransactions14d && lead.yangoTransactions14d.length > 0
        );
      } else if (hasYangoTransaction === 'without') {
        filtered = filtered.filter(lead => 
          !lead.yangoTransactions14d || lead.yangoTransactions14d.length === 0
        );
      } else if (hasYangoTransaction === 'pending') {
        filtered = filtered.filter(lead => {
          if (!lead.milestones || lead.milestones.length === 0) return false;
          
          return lead.milestones.some(m => {
            if (m.periodDays !== 14) return false;
            const tieneTransaccion = lead.yangoTransactions14d?.some(t => 
              t.milestoneType === m.milestoneType && 
              (t.milestoneInstanceId === m.id || 
               (t.milestoneInstance && t.milestoneInstance.id === m.id))
            );
            return !tieneTransaccion;
          });
        });
      }
    }
    
    if (yangoMilestoneType) {
      const milestoneTypeNum = parseInt(yangoMilestoneType);
      filtered = filtered.filter(lead => 
        lead.yangoTransactions14d && lead.yangoTransactions14d.some(t => t.milestoneType === milestoneTypeNum)
      );
    }
    
    return filtered;
  }, [leads, hasYangoTransaction, yangoMilestoneType]);

  const leadsPaginados = filteredLeads.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  useEffect(() => {
    setCurrentTablePage(1);
  }, [weekISO, dateFrom, dateTo, matchStatus, driverStatus, milestoneType, milestonePeriod, search, includeDiscarded, hasYangoTransaction, yangoMilestoneType]);

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
        borderRadius: '8px' 
      }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '15px', marginBottom: '15px' }}>
          <div style={{ minWidth: '280px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Semana ISO:</label>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <button 
                type="button"
                onClick={() => {
                  const prevWeek = getPreviousWeek(weekISO);
                  setWeekISO(prevWeek);
                  setDateFrom('');
                  setDateTo('');
                }}
                disabled={loading}
                style={{ padding: '4px 8px', border: '1px solid #ddd', borderRadius: '4px', cursor: 'pointer', backgroundColor: 'white' }}
              >
                ←
              </button>
              <input
                type="text"
                value={weekISO}
                onChange={(e) => {
                  const value = e.target.value;
                  if (value === '') {
                    setWeekISO('');
                    return;
                  }
                  if (value.match(/^\d{4}-W\d{2}$/)) {
                    // Validar que la semana sea válida (1-53)
                    const [yearStr, weekStr] = value.split('-W');
                    const year = parseInt(yearStr, 10);
                    const week = parseInt(weekStr, 10);
                    
                    if (!isNaN(year) && !isNaN(week) && week >= 1 && week <= 53 && year >= 2000 && year <= 2100) {
                      setWeekISO(value);
                      setDateFrom('');
                      setDateTo('');
                    }
                  }
                }}
                placeholder="2024-W01"
                pattern="\d{4}-W\d{2}"
                style={{ flex: 1, padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              <button 
                type="button"
                onClick={() => {
                  const nextWeek = getNextWeek(weekISO);
                  setWeekISO(nextWeek);
                  setDateFrom('');
                  setDateTo('');
                }}
                disabled={loading}
                style={{ padding: '4px 8px', border: '1px solid #ddd', borderRadius: '4px', cursor: 'pointer', backgroundColor: 'white' }}
              >
                →
              </button>
            </div>
            {weekISO && (
              <div style={{ fontSize: '11px', color: '#666', marginTop: '4px' }}>
                {formatWeekISO(weekISO)} ({(() => {
                  const range = getWeekRange(weekISO);
                  return `${range.start.toLocaleDateString('es-ES')} - ${range.end.toLocaleDateString('es-ES')}`;
                })()})
              </div>
            )}
          </div>
          <div style={{ minWidth: '200px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Desde:</label>
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value);
                if (e.target.value) {
                  setWeekISO('');
                }
              }}
              disabled={!!weekISO}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px', opacity: weekISO ? 0.6 : 1 }}
            />
          </div>
          <div style={{ minWidth: '200px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Fecha Hasta:</label>
            <input
              type="date"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value);
                if (e.target.value) {
                  setWeekISO('');
                }
              }}
              disabled={!!weekISO}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px', opacity: weekISO ? 0.6 : 1 }}
            />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Estado Match:</label>
            <select
              value={matchStatus}
              onChange={(e) => setMatchStatus(e.target.value as any)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="all">Todos</option>
              <option value="matched">Matcheados</option>
              <option value="unmatched">No Matcheados</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Estado Driver:</label>
            <select
              value={driverStatus}
              onChange={(e) => setDriverStatus(e.target.value as any)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="all">Todos</option>
              <option value="solo_registro">Solo Registro</option>
              <option value="conecto_sin_viajes">Conectó Sin Viajes</option>
              <option value="activo_con_viajes">Activo Con Viajes</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Tipo Milestone:</label>
            <select
              value={milestoneType || ''}
              onChange={(e) => setMilestoneType(e.target.value ? parseInt(e.target.value) : undefined)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="1">1 Viaje</option>
              <option value="5">5 Viajes</option>
              <option value="25">25 Viajes</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Período Milestone:</label>
            <select
              value={milestonePeriod || ''}
              onChange={(e) => setMilestonePeriod(e.target.value ? parseInt(e.target.value) : undefined)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="7">7 Días</option>
              <option value="14">14 Días</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Transacciones Yango:</label>
            <select
              value={hasYangoTransaction}
              onChange={(e) => setHasYangoTransaction(e.target.value)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="with">Con transacciones pagadas</option>
              <option value="without">Sin transacciones pagadas</option>
              <option value="pending">Instancias por pagar</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Tipo Milestone Pagado:</label>
            <select
              value={yangoMilestoneType}
              onChange={(e) => setYangoMilestoneType(e.target.value)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            >
              <option value="">Todos</option>
              <option value="1">1 Viaje</option>
              <option value="5">5 Viajes</option>
              <option value="25">25 Viajes</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Búsqueda:</label>
            <input
              type="text"
              placeholder="External ID, nombre o teléfono..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
              <input
                type="checkbox"
                checked={includeDiscarded}
                onChange={(e) => setIncludeDiscarded(e.target.checked)}
              />
              <span>Incluir Descartados</span>
            </label>
          </div>
        </div>
      </div>

      {loading && (
        <div className="loading">Cargando leads...</div>
      )}

      {!loading && (
        <div style={{ 
          marginBottom: '15px', 
          padding: '10px 15px', 
          backgroundColor: '#e7f3ff', 
          borderRadius: '6px',
          border: '1px solid #b3d9ff',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div style={{ fontWeight: 'bold', fontSize: '14px', color: '#0056b3' }}>
            Total de leads encontrados: <span style={{ fontSize: '16px' }}>{filteredLeads.length}</span>
            {filteredLeads.length !== leads.length && (
              <span style={{ fontSize: '12px', color: '#666', marginLeft: '10px' }}>
                (de {leads.length} total)
              </span>
            )}
          </div>
          {filteredLeads.length > 0 && (
            <div style={{ fontSize: '12px', color: '#666' }}>
              Mostrando {((currentPage - 1) * itemsPerPage) + 1} - {Math.min(currentPage * itemsPerPage, filteredLeads.length)} de {filteredLeads.length}
            </div>
          )}
        </div>
      )}

      {!loading && leads.length > 0 && (
        <>
          <div style={{ overflowX: 'auto', marginBottom: '20px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
              <thead style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                <tr>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>External ID</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Lead</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Match</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Scout Registration</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Driver</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Estado Driver</th>
                  <th style={{ padding: '10px', textAlign: 'center', borderBottom: '2px solid #dee2e6' }}>Conexión</th>
                  <th style={{ padding: '10px', textAlign: 'center', borderBottom: '2px solid #dee2e6' }}>1 Viaje</th>
                  <th style={{ padding: '10px', textAlign: 'center', borderBottom: '2px solid #dee2e6' }}>5 Viajes</th>
                  <th style={{ padding: '10px', textAlign: 'center', borderBottom: '2px solid #dee2e6' }}>25 Viajes</th>
                  <th style={{ padding: '10px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {leadsPaginados.map((lead) => (
                  <tr key={lead.externalId} style={{ borderBottom: '1px solid #dee2e6' }}>
                    <td style={{ padding: '10px' }}>{lead.externalId}</td>
                    <td style={{ padding: '10px' }}>
                      <div><strong>{getFullName(lead)}</strong></div>
                      <div>{lead.leadPhone || 'N/A'}</div>
                      <div style={{ fontSize: '11px', color: '#666' }}>{formatDate(lead.leadCreatedAt)}</div>
                    </td>
                    <td style={{ padding: '10px' }}>
                      {lead.driverId ? (
                        <div>
                          <div style={{ color: '#28a745', fontWeight: 'bold' }}>✓ Matcheado</div>
                          <div>Score: {lead.matchScore?.toFixed(2) || 'N/A'}</div>
                          <div>{lead.isManual ? 'Manual' : 'Automático'}</div>
                        </div>
                      ) : (
                        <div style={{ color: '#dc3545' }}>✗ No Matcheado</div>
                      )}
                      {lead.isDiscarded && (
                        <div style={{ color: '#dc3545', fontSize: '11px' }}>Descartado</div>
                      )}
                    </td>
                    <td style={{ padding: '10px' }}>
                      {lead.scoutRegistrationId ? (
                        <div>
                          <div style={{ color: '#28a745', fontWeight: 'bold' }}>✓ Matcheado</div>
                          <div>{lead.scoutName || lead.scoutId || 'N/A'}</div>
                          {lead.scoutMatchScore && (
                            <div style={{ fontSize: '11px' }}>Score: {lead.scoutMatchScore.toFixed(2)}</div>
                          )}
                          {lead.scoutRegistrationDate && (
                            <div style={{ fontSize: '11px', color: '#666' }}>{formatDate(lead.scoutRegistrationDate)}</div>
                          )}
                        </div>
                      ) : (
                        <div>
                          <div style={{ color: '#dc3545' }}>✗ Sin match</div>
                          <button
                            onClick={() => {
                              setSelectedLead(lead);
                              setShowScoutReconcileModal(true);
                              cargarSugerenciasScout(lead.externalId);
                            }}
                            style={{
                              padding: '4px 8px',
                              backgroundColor: '#17a2b8',
                              color: 'white',
                              border: 'none',
                              borderRadius: '4px',
                              cursor: 'pointer',
                              fontSize: '11px',
                              marginTop: '4px'
                            }}
                          >
                            Conciliar
                          </button>
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '10px' }}>
                      {lead.driverId ? (
                        <div>
                          <div><strong>{lead.driverFullName || 'N/A'}</strong></div>
                          <div>{lead.driverPhone || 'N/A'}</div>
                          <div style={{ fontSize: '11px', color: '#666' }}>{formatDate(lead.hireDate || undefined)}</div>
                        </div>
                      ) : (
                        <div style={{ color: '#999' }}>N/A</div>
                      )}
                    </td>
                    <td style={{ padding: '10px' }}>
                      {lead.driverStatus ? (
                        <span className={getStatusClass(lead.driverStatus)} style={{ 
                          padding: '4px 8px', 
                          borderRadius: '4px',
                          fontSize: '11px',
                          display: 'inline-block'
                        }}>
                          {getStatusLabel(lead.driverStatus)}
                        </span>
                      ) : (
                        <span style={{ color: '#999' }}>N/A</span>
                      )}
                      {lead.driverStatus && (
                        <div style={{ fontSize: '11px', color: '#666', marginTop: '4px' }}>
                          Viajes: {lead.totalTrips14d || 0}
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '10px', textAlign: 'center' }}>
                      {renderConexionCabinet(lead)}
                    </td>
                    <td style={{ padding: '10px', textAlign: 'center' }}>
                      {renderMilestoneColumnaCabinet(lead, 1)}
                    </td>
                    <td style={{ padding: '10px', textAlign: 'center' }}>
                      {renderMilestoneColumnaCabinet(lead, 5)}
                    </td>
                    <td style={{ padding: '10px', textAlign: 'center' }}>
                      {renderMilestoneColumnaCabinet(lead, 25)}
                    </td>
                    <td style={{ padding: '10px' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
                        <button
                          onClick={() => {
                            setSelectedLead(lead);
                            setShowReassignModal(true);
                            setDriverDateFrom(dateFrom);
                            setDriverDateTo(dateTo);
                          }}
                          style={{
                            padding: '4px 8px',
                            backgroundColor: '#ffc107',
                            color: 'black',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '11px'
                          }}
                        >
                          Reasignar
                        </button>
                        {lead.driverId && (
                          <button
                            onClick={() => {
                              window.location.href = `/?driverId=${lead.driverId}`;
                            }}
                            style={{
                              padding: '4px 8px',
                              backgroundColor: '#28a745',
                              color: 'white',
                              border: 'none',
                              borderRadius: '4px',
                              cursor: 'pointer',
                              fontSize: '11px'
                            }}
                          >
                            Ver Driver
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          
          <Pagination
            currentPage={currentPage}
            totalPages={Math.ceil(leads.length / itemsPerPage)}
            totalItems={leads.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentTablePage}
            onItemsPerPageChange={setItemsPerPage}
            itemLabel="leads"
          />
        </>
      )}

      {!loading && leads.length === 0 && (
        <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
          No se encontraron leads con los filtros seleccionados.
        </div>
      )}

      {selectedMilestone && (
        <MilestoneDetailModal
          milestone={selectedMilestone}
          driverName={selectedLead?.driverFullName}
          onClose={() => setSelectedMilestone(null)}
        />
      )}

      {showReassignModal && selectedLead && (
        <div className="modal-overlay" onClick={() => {
          setShowReassignModal(false);
          setSelectedLead(null);
          setSelectedDriver(null);
        }} style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1000
        }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{
            backgroundColor: 'white',
            padding: '20px',
            borderRadius: '8px',
            maxWidth: '900px',
            maxHeight: '80vh',
            overflow: 'auto',
            width: '90%'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2>Reasignar Match para Lead: {selectedLead.externalId}</h2>
              <button onClick={() => {
                setShowReassignModal(false);
                setSelectedLead(null);
                setSelectedDriver(null);
              }} style={{
                background: 'none',
                border: 'none',
                fontSize: '24px',
                cursor: 'pointer',
                color: '#666'
              }}>×</button>
            </div>

            <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
              <h3>Lead Actual:</h3>
              <div><strong>Nombre:</strong> {getFullName(selectedLead)}</div>
              <div><strong>Teléfono:</strong> {selectedLead.leadPhone || 'N/A'}</div>
              <div><strong>Fecha Creación:</strong> {formatDate(selectedLead.leadCreatedAt)}</div>
              {selectedLead.driverId && (
                <div><strong>Driver Actual:</strong> {selectedLead.driverFullName || selectedLead.driverId}</div>
              )}
            </div>

            <div style={{ marginBottom: '15px' }}>
              <h3>Seleccionar Nuevo Driver:</h3>
              <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
                <input
                  type="text"
                  placeholder="Buscar driver..."
                  value={driverSearchTerm}
                  onChange={(e) => setDriverSearchTerm(e.target.value)}
                  style={{ flex: 1, padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                />
                <input
                  type="date"
                  placeholder="Fecha desde"
                  value={driverDateFrom}
                  onChange={(e) => setDriverDateFrom(e.target.value)}
                  style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                />
                <input
                  type="date"
                  placeholder="Fecha hasta"
                  value={driverDateTo}
                  onChange={(e) => setDriverDateTo(e.target.value)}
                  style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                />
              </div>
            </div>

            <div style={{ maxHeight: '400px', overflowY: 'auto', border: '1px solid #ddd', borderRadius: '4px' }}>
              {loadingDrivers ? (
                <div className="loading">Cargando drivers...</div>
              ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                  <thead style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                    <tr>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Driver ID</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Nombre</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Teléfono</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredReassignDrivers.map((driver) => (
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

            <div style={{ marginTop: '20px', display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                onClick={() => {
                  setShowReassignModal(false);
                  setSelectedLead(null);
                  setSelectedDriver(null);
                }}
                style={{
                  padding: '10px 20px',
                  backgroundColor: '#6c757d',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
              >
                Cancelar
              </button>
              <button
                onClick={handleReassignMatch}
                disabled={!selectedDriver}
                style={{
                  padding: '10px 20px',
                  backgroundColor: selectedDriver ? '#28a745' : '#ccc',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: selectedDriver ? 'pointer' : 'not-allowed'
                }}
              >
                Reasignar Match
              </button>
            </div>
          </div>
        </div>
      )}

      {showScoutReconcileModal && selectedLead && (
        <div className="modal-overlay" onClick={() => {
          setShowScoutReconcileModal(false);
          setSelectedLead(null);
          setSelectedScoutRegistration(null);
          setScoutSuggestions([]);
        }} style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1000
        }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{
            backgroundColor: 'white',
            padding: '20px',
            borderRadius: '8px',
            maxWidth: '900px',
            maxHeight: '80vh',
            overflow: 'auto',
            width: '90%'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2>Conciliar Scout Registration para Lead: {selectedLead.externalId}</h2>
              <button onClick={() => {
                setShowScoutReconcileModal(false);
                setSelectedLead(null);
                setSelectedScoutRegistration(null);
                setScoutSuggestions([]);
              }} style={{
                background: 'none',
                border: 'none',
                fontSize: '24px',
                cursor: 'pointer',
                color: '#666'
              }}>×</button>
            </div>

            <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
              <h3>Lead Actual:</h3>
              <div><strong>Nombre:</strong> {getFullName(selectedLead)}</div>
              <div><strong>Teléfono:</strong> {selectedLead.leadPhone || 'N/A'}</div>
              <div><strong>Fecha Creación:</strong> {formatDate(selectedLead.leadCreatedAt)}</div>
            </div>

            <div style={{ marginBottom: '15px' }}>
              <h3>Sugerencias de Scout Registrations:</h3>
              <button
                onClick={() => cargarSugerenciasScout(selectedLead.externalId)}
                disabled={loadingScoutSuggestions}
                style={{
                  padding: '8px 16px',
                  backgroundColor: '#17a2b8',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: loadingScoutSuggestions ? 'not-allowed' : 'pointer',
                  marginBottom: '10px'
                }}
              >
                {loadingScoutSuggestions ? 'Cargando...' : 'Buscar Sugerencias'}
              </button>
            </div>

            <div style={{ maxHeight: '400px', overflowY: 'auto', border: '1px solid #ddd', borderRadius: '4px' }}>
              {loadingScoutSuggestions ? (
                <div className="loading">Cargando sugerencias...</div>
              ) : scoutSuggestions.length === 0 ? (
                <div style={{ padding: '20px', textAlign: 'center', color: '#666' }}>
                  No se encontraron sugerencias. Haz clic en "Buscar Sugerencias" para buscar.
                </div>
              ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                  <thead style={{ backgroundColor: '#f8f9fa', position: 'sticky', top: 0 }}>
                    <tr>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Sel</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Scout</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Nombre Driver</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha Registro</th>
                      <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {scoutSuggestions.map((suggestion) => (
                      <tr 
                        key={suggestion.id}
                        onClick={() => setSelectedScoutRegistration(suggestion)}
                        style={{ 
                          cursor: 'pointer',
                          backgroundColor: selectedScoutRegistration?.id === suggestion.id ? '#e7f3ff' : 'white',
                          borderBottom: '1px solid #dee2e6'
                        }}
                      >
                        <td style={{ padding: '8px' }}>
                          {selectedScoutRegistration?.id === suggestion.id && '✓'}
                        </td>
                        <td style={{ padding: '8px' }}>{suggestion.scout_name || suggestion.scout_id || 'N/A'}</td>
                        <td style={{ padding: '8px' }}>{suggestion.driver_name || 'N/A'}</td>
                        <td style={{ padding: '8px' }}>{formatDate(suggestion.registration_date)}</td>
                        <td style={{ padding: '8px' }}>
                          {suggestion.total_score ? (suggestion.total_score * 100).toFixed(1) + '%' : 'N/A'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div style={{ marginTop: '20px', display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                onClick={() => {
                  setShowScoutReconcileModal(false);
                  setSelectedLead(null);
                  setSelectedScoutRegistration(null);
                  setScoutSuggestions([]);
                }}
                style={{
                  padding: '10px 20px',
                  backgroundColor: '#6c757d',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
              >
                Cancelar
              </button>
              <button
                onClick={handleAssignScout}
                disabled={!selectedScoutRegistration}
                style={{
                  padding: '10px 20px',
                  backgroundColor: selectedScoutRegistration ? '#28a745' : '#ccc',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: selectedScoutRegistration ? 'pointer' : 'not-allowed'
                }}
              >
                Asignar Scout Registration
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Cabinet;

