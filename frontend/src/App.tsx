import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import FilterForm from './components/FilterForm';
import DriverTable from './components/DriverTable';
import DriverTabs, { TabState } from './components/DriverTabs';
import MilestoneSubTabs, { MilestoneType, PeriodDays } from './components/MilestoneSubTabs';
import MilestoneDetailModal from './components/MilestoneDetailModal';
import ReconciliationDashboard from './components/ReconciliationDashboard';
import LeadsManagementContainer from './components/LeadsManagementContainer';
import ScoutsContainer from './components/ScoutsContainer';
import UnifiedLeadsManagement from './components/UnifiedLeadsManagement';
import UnifiedYangoTransactions from './components/UnifiedYangoTransactions';
import UnifiedScoutRegistrations from './components/UnifiedScoutRegistrations';
import AuditLogs from './components/AuditLogs';
import AppLayout from './components/AppLayout';
import Pagination from './components/Pagination';
import Login from './components/Login';
import MilestonePaymentView from './components/MilestonePaymentView';
import { useAuth } from './contexts/AuthContext';
import { api, DriverOnboarding, OnboardingFilters, MilestoneInstance, PaginatedResponse } from './services/api';
import { getCurrentWeekISO, formatWeekISO, getWeekRange } from './utils/weekUtils';
import './App.css';

const DEFAULT_PARK_ID = '08e20910d81d42658d4334d3f6d10ac0';

type Page = 'main' | 'reconciliation-leads' | 'reconciliation-transactions' | 'reconciliation-scouts' | 'reconciliation-dashboard' | 
            'cabinet' | 'reprocess' | 'scouts-upload' | 'scouts-reconciliation' | 'scouts-transactions' | 
            'scouts-management' | 'scouts-liquidation' | 'scouts-instances' | 'scouts-config' | 'scouts-affiliation' | 'audit' | 'milestones-payment-view';

function App() {
  const { isAuthenticated, loading: authLoading, token } = useAuth();
  const [currentPage, setCurrentPage] = useState<Page>('main');
  const [parkId, setParkId] = useState(DEFAULT_PARK_ID);
  const [startDateFrom, setStartDateFrom] = useState('');
  const [startDateTo, setStartDateTo] = useState('');
  const [channel, setChannel] = useState('');
  const [weekISO, setWeekISO] = useState(getCurrentWeekISO());
  const [hasYangoTransaction, setHasYangoTransaction] = useState('');
  const [yangoMilestoneType, setYangoMilestoneType] = useState('');
  const [drivers, setDrivers] = useState<DriverOnboarding[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pagination, setPagination] = useState<{ total: number; hasMore: boolean; currentPage: number } | null>(null);
  const [activeTab, setActiveTab] = useState<TabState>('registrados');
  const [lastUpdated, setLastUpdated] = useState<string | undefined>(undefined);
  const [activeMilestone, setActiveMilestone] = useState<MilestoneType>(1);
  const [activePeriod, setActivePeriod] = useState<PeriodDays>(14);
  const [selectedMilestone, setSelectedMilestone] = useState<MilestoneInstance | null>(null);
  const [selectedDriverName, setSelectedDriverName] = useState<string | undefined>(undefined);
  const [loadingMilestones, setLoadingMilestones] = useState(false);
  const [milestoneTotals, setMilestoneTotals] = useState<{ milestone1: number; milestone5: number; milestone25: number } | null>(null);
  const [currentTablePage, setCurrentTablePage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const handleNavigate = (page: string) => {
    setCurrentPage(page as Page);
  };

  const handleSearch = useCallback(async () => {
    if (!isAuthenticated || !token) return;
    setLoading(true);
    setError(null);
    setDrivers([]);
    setPagination(null);
    
    try {
      const filters: OnboardingFilters = {
        parkId: parkId.trim() || DEFAULT_PARK_ID,
        startDateFrom: startDateFrom || undefined,
        startDateTo: startDateTo || undefined,
        channel: channel.trim() || undefined,
        weekISO: weekISO || undefined,
        page: 0,
        size: 50
      };
      
      const results: PaginatedResponse<DriverOnboarding> = await api.getOnboarding14d(filters);
      setDrivers(results.data);
      setPagination({
        total: results.total,
        hasMore: results.hasMore,
        currentPage: results.page
      });
      setLoading(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al buscar drivers');
      setDrivers([]);
      setLoading(false);
    }
  }, [parkId, startDateFrom, startDateTo, channel, weekISO, isAuthenticated, token]);
  
  const handleLoadMore = useCallback(async () => {
    if (!isAuthenticated || !token || !pagination || !pagination.hasMore || loadingMore) return;
    
    setLoadingMore(true);
    try {
      const nextPage = pagination.currentPage + 1;
      const filters: OnboardingFilters = {
        parkId: parkId.trim() || DEFAULT_PARK_ID,
        startDateFrom: startDateFrom || undefined,
        startDateTo: startDateTo || undefined,
        channel: channel.trim() || undefined,
        weekISO: weekISO || undefined,
        page: nextPage,
        size: 50
      };
      
      const results: PaginatedResponse<DriverOnboarding> = await api.getOnboarding14d(filters);
      setDrivers(prev => [...prev, ...results.data]);
      setPagination({
        total: results.total,
        hasMore: results.hasMore,
        currentPage: results.page
      });
      setLoadingMore(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar más drivers');
      setLoadingMore(false);
    }
  }, [parkId, startDateFrom, startDateTo, channel, weekISO, isAuthenticated, token, pagination, loadingMore]);
  
  const cargarMilestones = useCallback(async (driversList: DriverOnboarding[]) => {
    if (driversList.length === 0 || loadingMilestones) return;
    
    setLoadingMilestones(true);
    try {
      const driverIds = driversList.map(d => d.driverId);
      const [milestones14d, milestones7d] = await Promise.all([
        api.getMilestonesBatch(driverIds, 14),
        api.getMilestonesBatch(driverIds, 7)
      ]);
      
      setDrivers(prevDrivers => {
        return prevDrivers.map(driver => {
          const milestones14dList = milestones14d[driver.driverId];
          const milestones7dList = milestones7d[driver.driverId];
          
          if (milestones14dList || milestones7dList) {
            return {
              ...driver,
              milestones14d: milestones14dList || driver.milestones14d,
              milestones7d: milestones7dList || driver.milestones7d
            };
          }
          return driver;
        });
      });
    } catch (err) {
      console.warn('Error al cargar milestones, continuando sin ellos:', err);
    } finally {
      setLoadingMilestones(false);
    }
  }, [loadingMilestones]);

  useEffect(() => {
    if (!isAuthenticated) return;
    handleSearch().catch((err) => {
      console.error('Error no manejado en handleSearch:', err);
    });
  }, [parkId, startDateFrom, startDateTo, channel, weekISO, handleSearch, isAuthenticated]);

  const cargarUltimaActualizacion = useCallback(async () => {
    if (!isAuthenticated || !token) return;
    try {
      const status = await api.getProcessingStatus();
      setLastUpdated(status.lastUpdated);
    } catch (err) {
      console.error('Error al cargar última actualización:', err);
    }
  }, [isAuthenticated, token]);

  useEffect(() => {
    if (!isAuthenticated) return;
    cargarUltimaActualizacion().catch((err) => {
      console.error('Error no manejado al cargar última actualización:', err);
    });
  }, [cargarUltimaActualizacion, isAuthenticated]);

  const [milestoneDriverIds, setMilestoneDriverIds] = useState<Set<string>>(new Set());
  const cargandoRef = useRef(false);
  const driversRef = useRef(drivers);

  useEffect(() => {
    driversRef.current = drivers;
  }, [drivers]);

  useEffect(() => {
    if (!isAuthenticated || cargandoRef.current) return;
    
    const cargarTotalesYMilestones = async () => {
      if (activeTab === 'con-viajes') {
        try {
          let fechaFrom = startDateFrom || undefined;
          let fechaTo = startDateTo || undefined;
          
          if (!fechaFrom && !fechaTo && weekISO) {
            const weekRange = getWeekRange(weekISO);
            fechaFrom = weekRange.start.toISOString().split('T')[0];
            fechaTo = weekRange.end.toISOString().split('T')[0];
          }
          
          const resultado = await api.getMilestonesByPeriod(
            activePeriod, 
            parkId || DEFAULT_PARK_ID, 
            activeMilestone,
            fechaFrom,
            fechaTo
          );
          if (resultado) {
            if (resultado.totals) {
              setMilestoneTotals(resultado.totals);
            }
            if (resultado.milestones && resultado.milestones.length > 0) {
              const driverIdsSet = new Set(resultado.milestones.map((m: MilestoneInstance) => m.driverId));
              setMilestoneDriverIds(driverIdsSet);
              
              const driversActuales = driversRef.current;
              const driversCoincidentes = driversActuales.filter(d => driverIdsSet.has(d.driverId));
              
              if (driversCoincidentes.length < driverIdsSet.size) {
                const driverIdsFaltantes = Array.from(driverIdsSet).filter(id => !driversActuales.some(d => d.driverId === id));
                if (driverIdsFaltantes.length > 0 && !cargandoRef.current) {
                  console.warn(`Hay ${driverIdsFaltantes.length} drivers faltantes, pero el endpoint /by-ids no está disponible. Los drivers ya cargados se mostrarán.`);
                  console.warn(`Para cargar todos los drivers, ajusta los filtros de búsqueda inicial (fechas de contratación) para incluir más drivers.`);
                }
              }
              
              if (driversCoincidentes.length > 0) {
                await cargarMilestones(driversCoincidentes);
              }
            } else {
              setMilestoneDriverIds(new Set());
            }
          }
        } catch (err) {
          console.warn('Error al cargar totales de milestones:', err);
          setMilestoneTotals(null);
          setMilestoneDriverIds(new Set());
        }
      }
    };
    cargarTotalesYMilestones().catch((err) => {
      console.error('Error no manejado al cargar totales y milestones:', err);
    });
  }, [activeTab, activePeriod, activeMilestone, parkId, startDateFrom, startDateTo, weekISO, isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated || cargandoRef.current || activeTab !== 'con-viajes' || loadingMilestones) return;
    
    const recargarMilestonesSiEsNecesario = async () => {
      const driversActuales = driversRef.current;
      if (!driversActuales || driversActuales.length === 0) return;
      
      const driversConViajes = driversActuales.filter(d => {
        const viajes = d.totalTrips14d ?? 0;
        if (viajes >= 1) return true;
        
        const tieneMilestone = (milestones?: MilestoneInstance[]) => {
          return milestones && milestones.length > 0 && milestones.some(m => 
            m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
          );
        };
        
        return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
      });
      
      if (driversConViajes.length === 0) return;
      
      const driversSinMilestones = driversConViajes.filter(driver => {
        const milestones = activePeriod === 14 ? driver.milestones14d : driver.milestones7d;
        return !milestones || milestones.length === 0;
      });
      
      if (driversSinMilestones.length > 0) {
        await cargarMilestones(driversSinMilestones);
      }
    };
    
    recargarMilestonesSiEsNecesario().catch((err) => {
      console.error('Error no manejado al recargar milestones:', err);
    });
  }, [activeMilestone, activePeriod, activeTab, cargarMilestones, loadingMilestones, isAuthenticated]);

  const handleTabChange = useCallback((tab: TabState) => {
    setActiveTab(tab);
    
    if (tab === 'con-viajes' && drivers.length > 0) {
      const driversConViajes = drivers.filter(d => {
        const viajes = d.totalTrips14d ?? 0;
        if (viajes >= 1) return true;
        
        const tieneMilestone = (milestones?: MilestoneInstance[]) => {
          return milestones && milestones.length > 0 && milestones.some(m => 
            m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
          );
        };
        
        return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
      });
      
      const tieneMilestones = driversConViajes.some(d => d.milestones14d || d.milestones7d);
      if (!tieneMilestones && driversConViajes.length > 0) {
        cargarMilestones(driversConViajes).catch((err) => {
          console.error('Error no manejado al cargar milestones:', err);
        });
      }
    }
  }, [drivers, cargarMilestones]);

  const filtrarDriversPorTab = useMemo(() => {
    if (!drivers || drivers.length === 0) {
      return [];
    }

    const tieneViajes = (d: DriverOnboarding): boolean => {
      const viajes = d.totalTrips14d ?? 0;
      if (viajes >= 1) return true;
      
      if (d.status14d === 'activo_con_viajes') return true;
      
      const tieneMilestone = (milestones?: MilestoneInstance[]): boolean => {
        return !!(milestones && milestones.length > 0 && milestones.some(m => 
          m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
        ));
      };
      
      return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
    };
    
    const tieneConexion = (d: DriverOnboarding): boolean => {
      if (d.status14d === 'conecto_sin_viajes' || d.status14d === 'activo_con_viajes') {
        return true;
      }
      
      const tiempoTrabajo = d.sumWorkTimeSeconds ?? 0;
      if (tiempoTrabajo >= 1) return true;
      
      const tiempoOnline = d.totalOnlineTime14d ?? 0;
      if (tiempoOnline > 0) return true;
      
      if (d.hasHistoricalConnection) return true;
      
      const diasConectados = d.diasConectados ?? 0;
      if (diasConectados > 0) return true;
      
      return false;
    };

    let filtered: DriverOnboarding[] = [];
    
    switch (activeTab) {
      case 'registrados':
        filtered = drivers.filter(d => {
          return !tieneConexion(d) && !tieneViajes(d);
        });
        break;
      case 'conectados':
        filtered = drivers.filter(d => {
          return tieneConexion(d) && !tieneViajes(d);
        });
        break;
      case 'con-viajes':
        if (milestoneDriverIds.size > 0) {
          filtered = drivers.filter(driver => milestoneDriverIds.has(driver.driverId));
        } else {
          filtered = drivers.filter(d => tieneViajes(d));
          
          filtered = filtered.filter(driver => {
            const milestones = activePeriod === 14 ? driver.milestones14d : driver.milestones7d;
            if (!milestones || milestones.length === 0) {
              return false;
            }
            return milestones.some(m => m.milestoneType === activeMilestone);
          });
        }
        break;
      default:
        filtered = drivers;
    }
    
    if (hasYangoTransaction) {
        if (hasYangoTransaction === 'with') {
            filtered = filtered.filter(d => 
                d.yangoTransactions14d && d.yangoTransactions14d.length > 0
            );
        } else if (hasYangoTransaction === 'without') {
            filtered = filtered.filter(d => 
                !d.yangoTransactions14d || d.yangoTransactions14d.length === 0
            );
        } else if (hasYangoTransaction === 'pending') {
            filtered = filtered.filter(d => {
                const milestones = activePeriod === 14 ? d.milestones14d : d.milestones7d;
                if (!milestones || milestones.length === 0) return false;
                
                const transacciones = activePeriod === 14 ? d.yangoTransactions14d : undefined;
                
                return milestones.some(m => {
                    if (m.periodDays !== activePeriod) return false;
                    const tieneTransaccion = transacciones?.some(t => 
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
        filtered = filtered.filter(d => 
            d.yangoTransactions14d && d.yangoTransactions14d.some(t => t.milestoneType === milestoneTypeNum)
        );
    }
    
    return filtered;
  }, [drivers, activeTab, activeMilestone, activePeriod, milestoneDriverIds, hasYangoTransaction, yangoMilestoneType]);

  const totalItemsParaPaginacion = useMemo(() => {
    if (activeTab === 'con-viajes' && milestoneTotals) {
      if (activeMilestone === 1) return milestoneTotals.milestone1;
      if (activeMilestone === 5) return milestoneTotals.milestone5;
      if (activeMilestone === 25) return milestoneTotals.milestone25;
    }
    return filtrarDriversPorTab.length;
  }, [activeTab, activeMilestone, milestoneTotals, filtrarDriversPorTab.length]);

  const driversPaginados = useMemo(() => {
    const startIndex = (currentTablePage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    return filtrarDriversPorTab.slice(startIndex, endIndex);
  }, [filtrarDriversPorTab, currentTablePage, itemsPerPage]);

  useEffect(() => {
    if (!isAuthenticated) return;
    setCurrentTablePage(1);
  }, [activeTab, activeMilestone, activePeriod, isAuthenticated]);
  
  const handleMilestoneClick = useCallback((milestone: MilestoneInstance, driverName?: string) => {
    setSelectedMilestone(milestone);
    setSelectedDriverName(driverName);
  }, []);
  
  const handleCloseModal = useCallback(() => {
    setSelectedMilestone(null);
    setSelectedDriverName(undefined);
  }, []);

  const renderPageContent = () => {
    if (currentPage === 'reconciliation-leads') {
      return <UnifiedLeadsManagement />;
    }
    
    if (currentPage === 'reconciliation-transactions') {
      return <UnifiedYangoTransactions />;
    }
    
    if (currentPage === 'reconciliation-scouts') {
      return <UnifiedScoutRegistrations />;
    }
    
    if (currentPage === 'reconciliation-dashboard') {
      return <ReconciliationDashboard />;
    }

    if (currentPage === 'cabinet' || currentPage === 'reprocess') {
      const tab = currentPage === 'cabinet' ? 'cabinet' : 'reprocess';
      return <LeadsManagementContainer initialTab={tab} />;
    }

    if (currentPage.startsWith('scouts-')) {
      let tab: 'upload' | 'reconciliation' | 'transactions' | 'management' | 'liquidation' | 'instances' | 'config' | 'affiliation-control' = 'upload';
      const scoutPage = currentPage.replace('scouts-', '');
      if (scoutPage === 'reconciliation') tab = 'reconciliation';
      else if (scoutPage === 'transactions') tab = 'transactions';
      else if (scoutPage === 'management') tab = 'management';
      else if (scoutPage === 'liquidation') tab = 'liquidation';
      else if (scoutPage === 'instances') tab = 'instances';
      else if (scoutPage === 'config') tab = 'config';
      else if (scoutPage === 'affiliation') tab = 'affiliation-control';
      return <ScoutsContainer initialTab={tab} />;
    }

    if (currentPage === 'audit') {
      return <AuditLogs />;
    }

    if (currentPage === 'milestones-payment-view') {
      return <MilestonePaymentView />;
    }

    return (
      <div>
        <div className="container-header">
          <h1>Dashboard</h1>
          <p>Análisis de Onboarding de Drivers (Histórico Completo)</p>
          <p>Semana: {formatWeekISO(weekISO)}</p>
        </div>

        {activeTab === 'con-viajes' && (
          <div style={{ marginTop: '10px', marginBottom: '10px' }}>
            <button 
              onClick={async () => {
                try {
                  setLoading(true);
                  const result = await api.calculateMilestones(
                    parkId, 
                    activePeriod, 
                    activeMilestone,
                    startDateFrom || undefined,
                    startDateTo || undefined
                  );
                  if (result.success) {
                    const fechaInfo = (startDateFrom && startDateTo) ? ` para conductores con hire_date entre ${startDateFrom} y ${startDateTo}` : '';
                    alert(`Cálculo de milestones iniciado para ${activePeriod} días${activeMilestone ? ` (tipo ${activeMilestone})` : ''}${fechaInfo}. Los resultados aparecerán en unos momentos.`);
                  }
                  setTimeout(async () => {
                    try {
                      await handleSearch();
                      if (drivers.length > 0) {
                        const driversConViajes = drivers.filter(d => {
                          const viajes = d.totalTrips14d ?? 0;
                          if (viajes >= 1) return true;
                          
                          const tieneMilestone = (milestones?: MilestoneInstance[]) => {
                            return milestones && milestones.length > 0 && milestones.some(m => 
                              m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
                            );
                          };
                          
                          return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
                        });
                        await cargarMilestones(driversConViajes);
                        try {
                          let fechaFrom = startDateFrom || undefined;
                          let fechaTo = startDateTo || undefined;
                          
                          if (!fechaFrom && !fechaTo && weekISO) {
                            const weekRange = getWeekRange(weekISO);
                            fechaFrom = weekRange.start.toISOString().split('T')[0];
                            fechaTo = weekRange.end.toISOString().split('T')[0];
                          }
                          
                          const resultado = await api.getMilestonesByPeriod(
                            activePeriod, 
                            parkId || DEFAULT_PARK_ID,
                            undefined,
                            fechaFrom,
                            fechaTo
                          );
                          if (resultado && resultado.totals) {
                            setMilestoneTotals(resultado.totals);
                          }
                        } catch (err) {
                          console.warn('Error al cargar totales:', err);
                        }
                      }
                    } catch (err) {
                      console.error('Error no manejado en setTimeout:', err);
                    }
                  }, 3000);
                } catch (err) {
                  alert('Error al calcular milestones: ' + (err instanceof Error ? err.message : 'Error desconocido'));
                } finally {
                  setLoading(false);
                }
              }}
              disabled={loading}
              className="btn"
              style={{ 
                padding: '8px 16px', 
                backgroundColor: '#ffc107', 
                color: 'black', 
                fontWeight: 'bold'
              }}
            >
              {loading ? 'Calculando...' : `🔄 Calcular Milestones (${activePeriod}d${activeMilestone ? ` - Tipo ${activeMilestone}` : ''})`}
            </button>
          </div>
        )}

        <FilterForm
          parkId={parkId}
          startDateFrom={startDateFrom}
          startDateTo={startDateTo}
          channel={channel}
          weekISO={weekISO}
          lastUpdated={lastUpdated}
          hasYangoTransaction={hasYangoTransaction}
          yangoMilestoneType={yangoMilestoneType}
          onParkIdChange={setParkId}
          onStartDateFromChange={setStartDateFrom}
          onStartDateToChange={setStartDateTo}
          onChannelChange={setChannel}
          onWeekISOChange={setWeekISO}
          onHasYangoTransactionChange={setHasYangoTransaction}
          onYangoMilestoneTypeChange={setYangoMilestoneType}
          onSearch={handleSearch}
          loading={loading}
        />

        {error && (
          <div className="error">
            Error: {error}
          </div>
        )}

        {loading && (
          <div className="loading">Cargando datos...</div>
        )}

        {!loading && drivers.length > 0 && (
          <div className="results-section">
            <DriverTabs 
              drivers={drivers} 
              activeTab={activeTab}
              onTabChange={handleTabChange}
            />
            {activeTab === 'con-viajes' && (
              <MilestoneSubTabs
                drivers={drivers.filter(d => {
                  const viajes = d.totalTrips14d ?? 0;
                  if (viajes >= 1) return true;
                  
                  const tieneMilestone = (milestones?: MilestoneInstance[]) => {
                    return milestones && milestones.length > 0 && milestones.some(m => 
                      m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
                    );
                  };
                  
                  return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
                })}
                activeMilestone={activeMilestone}
                activePeriod={activePeriod}
                onMilestoneChange={setActiveMilestone}
                onPeriodChange={setActivePeriod}
                totals={milestoneTotals}
              />
            )}
            <DriverTable 
              drivers={driversPaginados}
              allDrivers={filtrarDriversPorTab}
              allDriversGlobal={drivers}
              totalDrivers={totalItemsParaPaginacion > filtrarDriversPorTab.length ? totalItemsParaPaginacion : filtrarDriversPorTab.length}
              activePeriod={activePeriod}
              activeTab={activeTab}
              activeMilestone={activeTab === 'con-viajes' ? activeMilestone : undefined}
              parkId={parkId}
              onMilestoneClick={handleMilestoneClick}
            />
            {pagination && pagination.hasMore && (
              <div style={{ textAlign: 'center', marginTop: '20px', marginBottom: '20px' }}>
                <button
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  style={{
                    padding: '12px 24px',
                    fontSize: '16px',
                    backgroundColor: loadingMore ? '#ccc' : '#2563eb',
                    color: 'white',
                    border: 'none',
                    borderRadius: '6px',
                    cursor: loadingMore ? 'not-allowed' : 'pointer',
                    fontWeight: '600'
                  }}
                >
                  {loadingMore ? 'Cargando...' : `Cargar más (${pagination.total - drivers.length} restantes)`}
                </button>
                <div style={{ marginTop: '8px', color: '#666', fontSize: '14px' }}>
                  Mostrando {drivers.length} de {pagination.total} drivers
                </div>
              </div>
            )}
            {filtrarDriversPorTab.length > 0 && (
              <Pagination
                currentPage={currentTablePage}
                totalPages={Math.ceil((totalItemsParaPaginacion > filtrarDriversPorTab.length ? totalItemsParaPaginacion : filtrarDriversPorTab.length) / itemsPerPage)}
                totalItems={totalItemsParaPaginacion > filtrarDriversPorTab.length ? totalItemsParaPaginacion : filtrarDriversPorTab.length}
                itemsPerPage={itemsPerPage}
                onPageChange={setCurrentTablePage}
                onItemsPerPageChange={setItemsPerPage}
                itemLabel="conductores"
              />
            )}
          </div>
        )}
        
        {selectedMilestone && (
          <MilestoneDetailModal
            milestone={selectedMilestone}
            driverName={selectedDriverName}
            onClose={handleCloseModal}
          />
        )}
      </div>
    );
  };

  if (authLoading) {
    return <div className="loading">Cargando...</div>;
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  return (
    <AppLayout currentPage={currentPage} onNavigate={handleNavigate}>
      {renderPageContent()}
    </AppLayout>
  );
}

export default App;
