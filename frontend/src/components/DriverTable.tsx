import React, { useState } from 'react';
import { DriverOnboarding, MilestoneInstance, YangoTransactionMatched } from '../services/api';
import { TabState } from './DriverTabs';
import { MilestoneType } from './MilestoneSubTabs';
import EvolutionMetrics from './EvolutionMetrics';

interface DriverTableProps {
  drivers: DriverOnboarding[];
  allDrivers?: DriverOnboarding[];
  allDriversGlobal?: DriverOnboarding[];
  totalDrivers?: number;
  activePeriod?: 7 | 14;
  activeTab?: TabState;
  activeMilestone?: MilestoneType;
  parkId?: string;
  onMilestoneClick?: (milestone: MilestoneInstance, driverName?: string) => void;
}

const DriverTable: React.FC<DriverTableProps> = ({ drivers, allDrivers, allDriversGlobal, totalDrivers, activePeriod = 14, activeTab, activeMilestone, parkId, onMilestoneClick }) => {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['embudo', 'metricas-principales']));
  
  const toggleSection = (section: string) => {
    setExpandedSections(prev => {
      const newSet = new Set(prev);
      if (newSet.has(section)) {
        newSet.delete(section);
      } else {
        newSet.add(section);
      }
      return newSet;
    });
  };

  const SeccionColapsable: React.FC<{
    id: string;
    titulo: string;
    icono?: string;
    children: React.ReactNode;
    defaultExpanded?: boolean;
    nivel?: 'primary' | 'secondary';
  }> = ({ id, titulo, icono, children, defaultExpanded = false, nivel = 'primary' }) => {
    const isExpanded = expandedSections.has(id) || defaultExpanded;
    const esPrimary = nivel === 'primary';
    
    return (
      <div style={{ 
        marginBottom: esPrimary ? '15px' : '10px',
        border: esPrimary ? '1px solid #e5e7eb' : '1px solid #f3f4f6',
        borderRadius: '6px',
        backgroundColor: esPrimary ? '#ffffff' : '#f9fafb',
        overflow: 'hidden'
      }}>
        <div
          onClick={() => toggleSection(id)}
          style={{
            padding: esPrimary ? '12px 16px' : '10px 14px',
            backgroundColor: esPrimary ? '#f8f9fa' : '#ffffff',
            borderBottom: isExpanded ? '1px solid #e5e7eb' : 'none',
            cursor: 'pointer',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            fontWeight: esPrimary ? '600' : '500',
            fontSize: esPrimary ? '14px' : '13px',
            color: '#1f2937',
            userSelect: 'none'
          }}
        >
          <span>
            {icono && <span style={{ marginRight: '8px' }}>{icono}</span>}
            {titulo}
          </span>
          <span style={{ fontSize: '18px', color: '#6b7280' }}>
            {isExpanded ? '▼' : '▶'}
          </span>
        </div>
        {isExpanded && (
          <div style={{ padding: esPrimary ? '16px' : '12px' }}>
            {children}
          </div>
        )}
      </div>
    );
  };
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('es-ES');
  };

  const formatWorkTime = (seconds: number | undefined | null): string => {
    if (seconds === null || seconds === undefined) {
      return 'N/A';
    }
    const hours = seconds / 3600;
    return `${hours.toFixed(1)} horas`;
  };

  const calcularDiferenciaFechas = (leadDate: string | undefined, hireDate: string): number | null => {
    if (!leadDate) return null;
    const lead = new Date(leadDate);
    const hire = new Date(hireDate);
    const diffTime = Math.abs(lead.getTime() - hire.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  const obtenerIconoFecha = (driver: DriverOnboarding) => {
    if (!driver.leadCreatedAt) return null;
    
    const diffDays = calcularDiferenciaFechas(driver.leadCreatedAt, driver.startDate);
    if (diffDays === null) return null;

    if (diffDays === 0) {
      return <span title="Fechas coinciden exactamente" style={{ color: 'green', fontSize: '18px' }}>✓</span>;
    } else if (diffDays <= 3) {
      return <span title={`Diferencia: ${diffDays} días`} style={{ color: '#ffc107', fontSize: '18px' }}>⚠</span>;
    } else {
      return <span title={`Diferencia: ${diffDays} días`} style={{ color: 'red', fontSize: '18px' }}>⚠</span>;
    }
  };

  const renderConexion = (driver: DriverOnboarding) => {
    const seConecto = driver.hasHistoricalConnection || (driver.totalOnlineTime14d && driver.totalOnlineTime14d > 0);
    
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

  const renderScout = (driver: DriverOnboarding) => {
    if (!driver.hasScoutRegistration || !driver.scoutName) {
      return (
        <span
          title="Sin scout asignado"
          style={{
            color: '#999',
            fontSize: '14px'
          }}
        >
          -
        </span>
      );
    }

    const tooltipParts = [driver.scoutName];
    if (driver.scoutRegistrationDate) {
      tooltipParts.push(`Registro: ${formatDate(driver.scoutRegistrationDate)}`);
    }
    if (driver.scoutMatchScore !== undefined) {
      tooltipParts.push(`Match: ${(driver.scoutMatchScore * 100).toFixed(1)}%`);
    }

    return (
      <span
        title={tooltipParts.join(' | ')}
        style={{
          color: '#007bff',
          fontSize: '14px',
          cursor: 'help'
        }}
      >
        {driver.scoutName}
      </span>
    );
  };

  const renderMilestoneColumna = (driver: DriverOnboarding, milestoneType: number) => {
    const milestones = activePeriod === 14 ? driver.milestones14d : driver.milestones7d;
    const transaccionesYango = activePeriod === 14 ? driver.yangoTransactions14d : undefined;
    
    const milestone = milestones?.find(m => m.milestoneType === milestoneType && m.periodDays === activePeriod);
    const transaccion = transaccionesYango?.find(t => {
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
      if (onMilestoneClick && milestone) {
        onMilestoneClick(milestone, driver.fullName);
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
      contenido = (
        <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          <span style={{ color: '#28a745', fontSize: '14px' }}>✓</span>
          <span style={{ fontSize: '12px' }}>💰</span>
        </span>
      );
      estilo = {
        cursor: 'pointer',
        padding: '4px 8px',
        borderRadius: '4px',
        backgroundColor: '#e8f5e9',
        border: '1px solid #28a745'
      };
    } else if (porPagar) {
      titulo += ' - Alcanzado pero NO pagado (Por pagar)';
      contenido = (
        <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          <span style={{ color: '#28a745', fontSize: '14px' }}>✓</span>
          <span style={{ color: '#ff9800', fontSize: '14px' }}>⚠</span>
        </span>
      );
      estilo = {
        cursor: 'pointer',
        padding: '4px 8px',
        borderRadius: '4px',
        backgroundColor: '#fff3e0',
        border: '1px solid #ff9800'
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
        border: '1px solid #ffc107'
      };
    } else {
      titulo += ' - No alcanzado ni pagado';
      contenido = (
        <span style={{ color: '#999', fontSize: '14px' }}>-</span>
      );
      estilo = {
        padding: '4px 8px',
        textAlign: 'center'
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

  const tieneConexion = (d: DriverOnboarding): boolean => {
    if (d.status14d === 'conecto_sin_viajes' || d.status14d === 'activo_con_viajes') {
      return true;
    }
    const tiempoTrabajo = d.sumWorkTimeSeconds ?? 0;
    if (tiempoTrabajo >= 1) return true;
    const tiempoOnline = d.totalOnlineTime14d ?? 0;
    if (tiempoOnline > 0) return true;
    if (d.hasHistoricalConnection === true) return true;
    const diasConectados = d.diasConectados ?? 0;
    if (diasConectados > 0) return true;
    return false;
  };

  const tieneViajes = (d: DriverOnboarding): boolean => {
    const viajes = d.totalTrips14d ?? 0;
    if (viajes >= 1) return true;
    if (d.status14d === 'activo_con_viajes') return true;
    const tieneMilestone = (milestones?: MilestoneInstance[]) => {
      return milestones && milestones.length > 0 && milestones.some(m => 
        m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
      );
    };
    return tieneMilestone(d.milestones14d) || tieneMilestone(d.milestones7d);
  };

  const alcanzoMilestone = (d: DriverOnboarding, milestoneType: number, period: number): boolean => {
    const milestones14 = d.milestones14d || [];
    const milestones7 = d.milestones7d || [];
    const todosMilestones = [...milestones14, ...milestones7];
    
    if (todosMilestones.some(m => m.milestoneType === milestoneType)) {
      return true;
    }
    
    const totalViajes = d.totalTrips14d || 0;
    if (milestoneType === 1 && totalViajes >= 1) return true;
    if (milestoneType === 5 && totalViajes >= 5) return true;
    if (milestoneType === 25 && totalViajes >= 25) return true;
    
    return false;
  };

  const calcularMetricasPorEstado = (driversParaEstadisticas: DriverOnboarding[]) => {
    const soloRegistro = driversParaEstadisticas.filter(d => !tieneConexion(d) && !tieneViajes(d));
    const conectoSinViajes = driversParaEstadisticas.filter(d => tieneConexion(d) && !tieneViajes(d));
    const activoConViajes = driversParaEstadisticas.filter(d => tieneViajes(d));

    return {
      soloRegistro: soloRegistro.length,
      conectoSinViajes: conectoSinViajes.length,
      activoConViajes: activoConViajes.length
    };
  };

  const calcularTasasConversion = (driversParaEstadisticas: DriverOnboarding[]) => {
    const total = driversParaEstadisticas.length;
    if (total === 0) {
      return {
        registroAConexion: 0,
        conexionAViaje: 0,
        alcanzo1Viaje: 0,
        alcanzo5Viajes: 0,
        alcanzo25Viajes: 0
      };
    }

    const seConectaron = driversParaEstadisticas.filter(d => tieneConexion(d)).length;
    const tuvieronViajes = driversParaEstadisticas.filter(d => tieneViajes(d)).length;
    const alcanzo1 = driversParaEstadisticas.filter(d => alcanzoMilestone(d, 1, activePeriod)).length;
    const alcanzo5 = driversParaEstadisticas.filter(d => alcanzoMilestone(d, 5, activePeriod)).length;
    const alcanzo25 = driversParaEstadisticas.filter(d => alcanzoMilestone(d, 25, activePeriod)).length;

    return {
      registroAConexion: seConectaron > 0 ? (seConectaron / total) * 100 : 0,
      conexionAViaje: seConectaron > 0 ? (tuvieronViajes / seConectaron) * 100 : 0,
      alcanzo1Viaje: (alcanzo1 / total) * 100,
      alcanzo5Viajes: (alcanzo5 / total) * 100,
      alcanzo25Viajes: (alcanzo25 / total) * 100
    };
  };

  const calcularTiemposPromedio = (driversParaEstadisticas: DriverOnboarding[]) => {
    const tiemposRC = driversParaEstadisticas
      .filter(d => d.diasRegistroAConexion !== undefined && d.diasRegistroAConexion !== null)
      .map(d => d.diasRegistroAConexion!);
    
    const tiemposCV = driversParaEstadisticas
      .filter(d => d.diasConexionAViaje !== undefined && d.diasConexionAViaje !== null)
      .map(d => d.diasConexionAViaje!);
    
    const tiemposV25 = driversParaEstadisticas
      .filter(d => d.diasPrimerViajeA25Viajes !== undefined && d.diasPrimerViajeA25Viajes !== null)
      .map(d => d.diasPrimerViajeA25Viajes!);

    return {
      promedioRC: tiemposRC.length > 0 ? tiemposRC.reduce((a, b) => a + b, 0) / tiemposRC.length : 0,
      promedioCV: tiemposCV.length > 0 ? tiemposCV.reduce((a, b) => a + b, 0) / tiemposCV.length : 0,
      promedioV25: tiemposV25.length > 0 ? tiemposV25.reduce((a, b) => a + b, 0) / tiemposV25.length : 0
    };
  };

  const calcularMetricasPorScout = (driversParaEstadisticas: DriverOnboarding[]) => {
    const scoutsMap = new Map<string, DriverOnboarding[]>();
    
    driversParaEstadisticas.forEach(driver => {
      if (driver.hasScoutRegistration && driver.scoutName) {
        const scoutName = driver.scoutName;
        if (!scoutsMap.has(scoutName)) {
          scoutsMap.set(scoutName, []);
        }
        scoutsMap.get(scoutName)!.push(driver);
      }
    });

    const metricasScouts = Array.from(scoutsMap.entries()).map(([scoutName, driversScout]) => {
      const total = driversScout.length;
      const seConectaron = driversScout.filter(d => tieneConexion(d)).length;
      const tuvieronViajes = driversScout.filter(d => tieneViajes(d)).length;
      const alcanzo1 = driversScout.filter(d => alcanzoMilestone(d, 1, activePeriod)).length;
      const alcanzo5 = driversScout.filter(d => alcanzoMilestone(d, 5, activePeriod)).length;
      const alcanzo25 = driversScout.filter(d => alcanzoMilestone(d, 25, activePeriod)).length;

      const tiemposRC = driversScout
        .filter(d => d.diasRegistroAConexion !== undefined && d.diasRegistroAConexion !== null)
        .map(d => d.diasRegistroAConexion!);
      const promedioRC = tiemposRC.length > 0 ? tiemposRC.reduce((a, b) => a + b, 0) / tiemposRC.length : 0;

      const tiemposCV = driversScout
        .filter(d => d.diasConexionAViaje !== undefined && d.diasConexionAViaje !== null)
        .map(d => d.diasConexionAViaje!);
      const promedioCV = tiemposCV.length > 0 ? tiemposCV.reduce((a, b) => a + b, 0) / tiemposCV.length : 0;

      const tasaRC = total > 0 ? (seConectaron / total) * 100 : 0;
      const tasaCV = seConectaron > 0 ? (tuvieronViajes / seConectaron) * 100 : 0;
      const tasa1Viaje = total > 0 ? (alcanzo1 / total) * 100 : 0;
      const tasa5Viajes = total > 0 ? (alcanzo5 / total) * 100 : 0;
      const tasa25Viajes = total > 0 ? (alcanzo25 / total) * 100 : 0;

      const eficiencia = (tasaRC * 0.2) + (tasaCV * 0.3) + (tasa1Viaje * 0.2) + (tasa5Viajes * 0.2) + (tasa25Viajes * 0.1);

      return {
        scoutName,
        total,
        seConectaron,
        tuvieronViajes,
        alcanzo1,
        alcanzo5,
        alcanzo25,
        tasaRC,
        tasaCV,
        tasa1Viaje,
        tasa5Viajes,
        tasa25Viajes,
        promedioRC,
        promedioCV,
        eficiencia
      };
    });

    return metricasScouts.sort((a, b) => b.eficiencia - a.eficiencia);
  };

  const calcularMetricasPorCanal = (driversParaEstadisticas: DriverOnboarding[]) => {
    const canalesMap = new Map<string, DriverOnboarding[]>();
    
    driversParaEstadisticas.forEach(driver => {
      const canal = driver.channel || 'Sin Canal';
      if (!canalesMap.has(canal)) {
        canalesMap.set(canal, []);
      }
      canalesMap.get(canal)!.push(driver);
    });

    const metricasCanales = Array.from(canalesMap.entries()).map(([canal, driversCanal]) => {
      const total = driversCanal.length;
      const seConectaron = driversCanal.filter(d => tieneConexion(d)).length;
      const tuvieronViajes = driversCanal.filter(d => tieneViajes(d)).length;
      const alcanzo1 = driversCanal.filter(d => alcanzoMilestone(d, 1, activePeriod)).length;
      const alcanzo5 = driversCanal.filter(d => alcanzoMilestone(d, 5, activePeriod)).length;
      const alcanzo25 = driversCanal.filter(d => alcanzoMilestone(d, 25, activePeriod)).length;

      return {
        canal,
        total,
        seConectaron,
        tuvieronViajes,
        alcanzo1,
        alcanzo5,
        alcanzo25,
        tasaRC: total > 0 ? (seConectaron / total) * 100 : 0,
        tasaCV: seConectaron > 0 ? (tuvieronViajes / seConectaron) * 100 : 0,
        tasa1Viaje: total > 0 ? (alcanzo1 / total) * 100 : 0,
        tasa5Viajes: total > 0 ? (alcanzo5 / total) * 100 : 0,
        tasa25Viajes: total > 0 ? (alcanzo25 / total) * 100 : 0
      };
    });

    return metricasCanales.sort((a, b) => b.total - a.total);
  };

  const calcularEmbudoGlobal = (driversGlobal: DriverOnboarding[]) => {
    const total = driversGlobal.length;
    if (total === 0) {
      return {
        total: 0,
        conectados: 0,
        conViajes: 0,
        alcanzo1: 0,
        alcanzo5: 0,
        alcanzo25: 0,
        porcentajeConectados: 0,
        porcentajeConViajes: 0,
        porcentaje1Viaje: 0,
        porcentaje5Viajes: 0,
        porcentaje25Viajes: 0
      };
    }

    const conectados = driversGlobal.filter(d => tieneConexion(d)).length;
    const conViajes = driversGlobal.filter(d => tieneViajes(d)).length;
    const alcanzo1 = driversGlobal.filter(d => alcanzoMilestone(d, 1, activePeriod)).length;
    const alcanzo5 = driversGlobal.filter(d => alcanzoMilestone(d, 5, activePeriod)).length;
    const alcanzo25 = driversGlobal.filter(d => alcanzoMilestone(d, 25, activePeriod)).length;

    return {
      total,
      conectados,
      conViajes,
      alcanzo1,
      alcanzo5,
      alcanzo25,
      porcentajeConectados: (conectados / total) * 100,
      porcentajeConViajes: (conViajes / total) * 100,
      porcentaje1Viaje: (alcanzo1 / total) * 100,
      porcentaje5Viajes: (alcanzo5 / total) * 100,
      porcentaje25Viajes: (alcanzo25 / total) * 100
    };
  };

  const calcularEstadisticasConversion = () => {
    const driversParaEstadisticas = allDriversGlobal && allDriversGlobal.length > 0 ? allDriversGlobal : (allDrivers && allDrivers.length > 0 ? allDrivers : drivers);
    const totalDriversCount = totalDrivers !== undefined ? totalDrivers : driversParaEstadisticas.length;
    
    const totalTiempoTrabajado = driversParaEstadisticas.reduce((sum, driver) => {
      return sum + (driver.sumWorkTimeSeconds || 0);
    }, 0);
    
    const tiempoPromedioPorDriver = driversParaEstadisticas.length > 0 ? totalTiempoTrabajado / driversParaEstadisticas.length : 0;
    
    const totalViajes = driversParaEstadisticas.reduce((sum, driver) => {
      return sum + (driver.totalTrips14d || 0);
    }, 0);
    
    const tiempoTotalOnline = driversParaEstadisticas.reduce((sum, driver) => {
      return sum + (driver.totalOnlineTime14d || 0);
    }, 0);

    const driversActivos = driversParaEstadisticas.filter(d => tieneViajes(d));
    const viajesPromedioActivos = driversActivos.length > 0 
      ? driversActivos.reduce((sum, d) => sum + (d.totalTrips14d || 0), 0) / driversActivos.length 
      : 0;

    const diasActivosArray = driversParaEstadisticas
      .filter(d => d.diasActivos !== undefined && d.diasActivos !== null)
      .map(d => d.diasActivos!);
    const diasActivosPromedio = diasActivosArray.length > 0 
      ? diasActivosArray.reduce((sum, val) => sum + val, 0) / diasActivosArray.length 
      : 0;

    const diasConectadosArray = driversParaEstadisticas
      .filter(d => d.diasConectados !== undefined && d.diasConectados !== null)
      .map(d => d.diasConectados!);
    const diasConectadosPromedio = diasConectadosArray.length > 0 
      ? diasConectadosArray.reduce((sum, val) => sum + val, 0) / diasConectadosArray.length 
      : 0;

    const tasaConversionConexion = diasConectadosPromedio > 0 
      ? (diasActivosPromedio / diasConectadosPromedio) * 100 
      : 0;

    const metricasEstado = calcularMetricasPorEstado(driversParaEstadisticas);
    const tasasConversion = calcularTasasConversion(driversParaEstadisticas);
    const tiemposPromedio = calcularTiemposPromedio(driversParaEstadisticas);
    const metricasScouts = calcularMetricasPorScout(driversParaEstadisticas);
    const metricasCanales = calcularMetricasPorCanal(driversParaEstadisticas);

    return {
      totalDrivers: totalDriversCount,
      totalTiempoTrabajado,
      tiempoPromedioPorDriver,
      totalViajes,
      tiempoTotalOnline,
      viajesPromedioActivos,
      diasActivosPromedio,
      diasConectadosPromedio,
      tasaConversionConexion,
      metricasEstado,
      tasasConversion,
      tiemposPromedio,
      metricasScouts,
      metricasCanales
    };
  };

  if (drivers.length === 0) {
    return <div className="loading">No se encontraron resultados</div>;
  }

  const estadisticas = calcularEstadisticasConversion();
  
  const obtenerColorTasa = (tasa: number): string => {
    if (tasa >= 70) return '#28a745';
    if (tasa >= 40) return '#ffc107';
    return '#dc3545';
  };

  const obtenerTituloContextual = () => {
    if (activeTab === 'registrados') {
      return 'Estadísticas - Drivers Registrados';
    } else if (activeTab === 'conectados') {
      return 'Estadísticas - Drivers Conectados';
    } else if (activeTab === 'con-viajes') {
      if (activeMilestone) {
        return `Estadísticas - Drivers con ${activeMilestone} Viaje${activeMilestone > 1 ? 's' : ''} (${activePeriod}d)`;
      }
      return 'Estadísticas - Drivers con Viajes';
    }
    return 'Estadísticas Históricas';
  };

  const embudoGlobal = allDriversGlobal && allDriversGlobal.length > 0 
    ? calcularEmbudoGlobal(allDriversGlobal) 
    : null;

  return (
    <>
      <h2>Resultados ({totalDrivers !== undefined ? totalDrivers : drivers.length} drivers)</h2>
      
      {embudoGlobal && (
        <SeccionColapsable 
          id="embudo" 
          titulo="Vista Global - Embudo de Conversión Completo" 
          icono="📊"
          defaultExpanded={true}
          nivel="primary"
        >
          <div style={{ 
            backgroundColor: '#f0fdf4', 
            padding: '12px', 
            borderRadius: '6px',
            border: '1px solid #86efac'
          }}>
            <div style={{ 
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
              gap: '8px',
              alignItems: 'center'
            }}>
              <div style={{
                backgroundColor: '#dcfce7',
                padding: '12px',
                borderRadius: '6px',
                border: '2px solid #86efac',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '11px', color: '#166534', marginBottom: '4px', fontWeight: '600' }}>
                  TOTAL REGISTRADOS
                </div>
                <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#166534' }}>
                  {embudoGlobal.total}
                </div>
                <div style={{ fontSize: '10px', color: '#166534', marginTop: '3px' }}>
                  100%
                </div>
              </div>

              <div style={{ textAlign: 'center', fontSize: '18px', color: '#86efac' }}>→</div>

              <div style={{
                backgroundColor: '#dbeafe',
                padding: '12px',
                borderRadius: '6px',
                border: '2px solid #93c5fd',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '11px', color: '#1e40af', marginBottom: '4px', fontWeight: '600' }}>
                  CONECTADOS
                </div>
                <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#1e40af' }}>
                  {embudoGlobal.conectados}
                </div>
                <div style={{ fontSize: '10px', color: '#1e40af', marginTop: '3px' }}>
                  {embudoGlobal.porcentajeConectados.toFixed(1)}%
                </div>
              </div>

              <div style={{ textAlign: 'center', fontSize: '18px', color: '#93c5fd' }}>→</div>

              <div style={{
                backgroundColor: '#fef3c7',
                padding: '12px',
                borderRadius: '6px',
                border: '2px solid #fde68a',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '11px', color: '#92400e', marginBottom: '4px', fontWeight: '600' }}>
                  CON VIAJES
                </div>
                <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#92400e' }}>
                  {embudoGlobal.conViajes}
                </div>
                <div style={{ fontSize: '10px', color: '#92400e', marginTop: '3px' }}>
                  {embudoGlobal.porcentajeConViajes.toFixed(1)}%
                </div>
              </div>

              <div style={{ textAlign: 'center', fontSize: '18px', color: '#fde68a' }}>→</div>

              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '6px'
              }}>
                <div style={{
                  backgroundColor: '#fce7f3',
                  padding: '10px',
                  borderRadius: '4px',
                  border: '1px solid #f9a8d4',
                  textAlign: 'center'
                }}>
                  <div style={{ fontSize: '10px', color: '#9f1239', marginBottom: '3px', fontWeight: '600' }}>
                    1 VIAJE
                  </div>
                  <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#9f1239' }}>
                    {embudoGlobal.alcanzo1}
                  </div>
                  <div style={{ fontSize: '9px', color: '#9f1239', marginTop: '2px' }}>
                    {embudoGlobal.porcentaje1Viaje.toFixed(1)}%
                  </div>
                </div>

                <div style={{
                  backgroundColor: '#f3e8ff',
                  padding: '10px',
                  borderRadius: '4px',
                  border: '1px solid #c4b5fd',
                  textAlign: 'center'
                }}>
                  <div style={{ fontSize: '10px', color: '#6b21a8', marginBottom: '3px', fontWeight: '600' }}>
                    5 VIAJES
                  </div>
                  <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#6b21a8' }}>
                    {embudoGlobal.alcanzo5}
                  </div>
                  <div style={{ fontSize: '9px', color: '#6b21a8', marginTop: '2px' }}>
                    {embudoGlobal.porcentaje5Viajes.toFixed(1)}%
                  </div>
                </div>

                <div style={{
                  backgroundColor: '#e0e7ff',
                  padding: '10px',
                  borderRadius: '4px',
                  border: '1px solid #a5b4fc',
                  textAlign: 'center'
                }}>
                  <div style={{ fontSize: '10px', color: '#3730a3', marginBottom: '3px', fontWeight: '600' }}>
                    25 VIAJES
                  </div>
                  <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#3730a3' }}>
                    {embudoGlobal.alcanzo25}
                  </div>
                  <div style={{ fontSize: '9px', color: '#3730a3', marginTop: '2px' }}>
                    {embudoGlobal.porcentaje25Viajes.toFixed(1)}%
                  </div>
                </div>
              </div>
            </div>
          </div>
        </SeccionColapsable>
      )}

      {allDriversGlobal && allDriversGlobal.length > 0 && (
        <SeccionColapsable 
          id="evolucion" 
          titulo="Evolución Temporal" 
          icono="📈"
          nivel="primary"
        >
          <EvolutionMetrics parkId={parkId || allDriversGlobal[0]?.parkId} />
        </SeccionColapsable>
      )}

      <SeccionColapsable 
        id="metricas-principales" 
        titulo={obtenerTituloContextual()} 
        icono="📊"
        defaultExpanded={true}
        nivel="primary"
      >
        <SeccionColapsable id="metricas-basicas" titulo="Métricas Principales" nivel="secondary">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px' }}>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Total Drivers</div>
              <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#1f2937' }}>{estadisticas.totalDrivers}</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Total Viajes</div>
              <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#1f2937' }}>{estadisticas.totalViajes}</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Viajes Promedio</div>
              <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#1f2937' }}>{estadisticas.viajesPromedioActivos.toFixed(1)}</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Tiempo Trabajado</div>
              <div style={{ fontSize: '16px', fontWeight: 'bold', color: '#1f2937' }}>{formatWorkTime(estadisticas.totalTiempoTrabajado)}</div>
            </div>
          </div>
        </SeccionColapsable>

        <SeccionColapsable id="distribucion-estado" titulo="Distribución por Estado" nivel="secondary">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '10px' }}>
            <div style={{ backgroundColor: '#fee2e2', padding: '10px', borderRadius: '4px', border: '1px solid #fecaca', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#991b1b', marginBottom: '4px' }}>Solo Registro</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#991b1b' }}>{estadisticas.metricasEstado.soloRegistro}</div>
              <div style={{ fontSize: '10px', color: '#991b1b', marginTop: '3px' }}>
                {estadisticas.totalDrivers > 0 ? ((estadisticas.metricasEstado.soloRegistro / estadisticas.totalDrivers) * 100).toFixed(1) : 0}%
              </div>
            </div>
            <div style={{ backgroundColor: '#fef3c7', padding: '10px', borderRadius: '4px', border: '1px solid #fde68a', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#92400e', marginBottom: '4px' }}>Conectó Sin Viajes</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#92400e' }}>{estadisticas.metricasEstado.conectoSinViajes}</div>
              <div style={{ fontSize: '10px', color: '#92400e', marginTop: '3px' }}>
                {estadisticas.totalDrivers > 0 ? ((estadisticas.metricasEstado.conectoSinViajes / estadisticas.totalDrivers) * 100).toFixed(1) : 0}%
              </div>
            </div>
            <div style={{ backgroundColor: '#d1fae5', padding: '10px', borderRadius: '4px', border: '1px solid #a7f3d0', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#065f46', marginBottom: '4px' }}>Activo Con Viajes</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#065f46' }}>{estadisticas.metricasEstado.activoConViajes}</div>
              <div style={{ fontSize: '10px', color: '#065f46', marginTop: '3px' }}>
                {estadisticas.totalDrivers > 0 ? ((estadisticas.metricasEstado.activoConViajes / estadisticas.totalDrivers) * 100).toFixed(1) : 0}%
              </div>
            </div>
          </div>
        </SeccionColapsable>

        <SeccionColapsable id="tasas-conversion" titulo="Tasas de Conversión" nivel="secondary">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px' }}>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>R → C</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasasConversion.registroAConexion) }}>
                {estadisticas.tasasConversion.registroAConexion.toFixed(1)}%
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Registro→Conexión</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>C → V</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasasConversion.conexionAViaje) }}>
                {estadisticas.tasasConversion.conexionAViaje.toFixed(1)}%
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Conexión→Viaje</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>1 Viaje</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasasConversion.alcanzo1Viaje) }}>
                {estadisticas.tasasConversion.alcanzo1Viaje.toFixed(1)}%
              </div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>5 Viajes</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasasConversion.alcanzo5Viajes) }}>
                {estadisticas.tasasConversion.alcanzo5Viajes.toFixed(1)}%
              </div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>25 Viajes</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasasConversion.alcanzo25Viajes) }}>
                {estadisticas.tasasConversion.alcanzo25Viajes.toFixed(1)}%
              </div>
            </div>
          </div>
        </SeccionColapsable>

        <SeccionColapsable id="tiempos-promedio" titulo="Tiempos Promedio de Conversión" nivel="secondary">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px' }}>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>R → C (días)</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1f2937' }}>
                {estadisticas.tiemposPromedio.promedioRC.toFixed(1)}
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Registro→Conexión</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>C → V (días)</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1f2937' }}>
                {estadisticas.tiemposPromedio.promedioCV.toFixed(1)}
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Conexión→Viaje</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>V → 25T (días)</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1f2937' }}>
                {estadisticas.tiemposPromedio.promedioV25.toFixed(1)}
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Viaje→25 Viajes</div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Días Activos</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1f2937' }}>
                {estadisticas.diasActivosPromedio.toFixed(1)}
              </div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>Días Conectados</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1f2937' }}>
                {estadisticas.diasConectadosPromedio.toFixed(1)}
              </div>
            </div>
            <div style={{ backgroundColor: 'white', padding: '10px', borderRadius: '4px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)', textAlign: 'center' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>% Conv. Conexión</div>
              <div style={{ fontSize: '18px', fontWeight: 'bold', color: obtenerColorTasa(estadisticas.tasaConversionConexion) }}>
                {estadisticas.tasaConversionConexion.toFixed(1)}%
              </div>
              <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '3px' }}>Activos/Conectados</div>
            </div>
          </div>
        </SeccionColapsable>

        {estadisticas.metricasScouts.length > 0 && (
          <SeccionColapsable id="metricas-scouts" titulo={`Métricas por Scout (Top 10) - ${estadisticas.metricasScouts.length} scouts`} icono="👥" nivel="secondary">
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '11px', backgroundColor: 'white', borderRadius: '4px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#e0f2fe' }}>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>Rank</th>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>Scout</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>Total</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>R→C %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>C→V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>1V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>5V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>25V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>R→C (d)</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>C→V (d)</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #bae6fd', fontSize: '11px' }}>Efic.</th>
                  </tr>
                </thead>
                <tbody>
                  {estadisticas.metricasScouts.slice(0, 10).map((scout, index) => (
                    <tr key={scout.scoutName} style={{ borderBottom: '1px solid #e5e7eb' }}>
                      <td style={{ padding: '8px', fontWeight: 'bold', color: index < 3 ? '#dc2626' : '#6b7280', fontSize: '11px' }}>#{index + 1}</td>
                      <td style={{ padding: '8px', fontWeight: '500', fontSize: '11px' }}>{scout.scoutName}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{scout.total}</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(scout.tasaRC), fontSize: '11px' }}>{scout.tasaRC.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(scout.tasaCV), fontSize: '11px' }}>{scout.tasaCV.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(scout.tasa1Viaje), fontSize: '11px' }}>{scout.tasa1Viaje.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(scout.tasa5Viajes), fontSize: '11px' }}>{scout.tasa5Viajes.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(scout.tasa25Viajes), fontSize: '11px' }}>{scout.tasa25Viajes.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{scout.promedioRC.toFixed(1)}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{scout.promedioCV.toFixed(1)}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontWeight: 'bold', color: obtenerColorTasa(scout.eficiencia), fontSize: '11px' }}>
                        {scout.eficiencia.toFixed(1)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </SeccionColapsable>
        )}

        {estadisticas.metricasCanales.length > 0 && (
          <SeccionColapsable id="metricas-canales" titulo={`Métricas por Canal - ${estadisticas.metricasCanales.length} canales`} icono="📡" nivel="secondary">
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '11px', backgroundColor: 'white', borderRadius: '4px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#ede9fe' }}>
                    <th style={{ padding: '8px', textAlign: 'left', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>Canal</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>Total</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>R→C %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>C→V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>1V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>5V %</th>
                    <th style={{ padding: '8px', textAlign: 'center', borderBottom: '2px solid #ddd6fe', fontSize: '11px' }}>25V %</th>
                  </tr>
                </thead>
                <tbody>
                  {estadisticas.metricasCanales.map((canal) => (
                    <tr key={canal.canal} style={{ borderBottom: '1px solid #e5e7eb' }}>
                      <td style={{ padding: '8px', fontWeight: '500', fontSize: '11px' }}>{canal.canal}</td>
                      <td style={{ padding: '8px', textAlign: 'center', fontSize: '11px' }}>{canal.total}</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(canal.tasaRC), fontSize: '11px' }}>{canal.tasaRC.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(canal.tasaCV), fontSize: '11px' }}>{canal.tasaCV.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(canal.tasa1Viaje), fontSize: '11px' }}>{canal.tasa1Viaje.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(canal.tasa5Viajes), fontSize: '11px' }}>{canal.tasa5Viajes.toFixed(1)}%</td>
                      <td style={{ padding: '8px', textAlign: 'center', color: obtenerColorTasa(canal.tasa25Viajes), fontSize: '11px' }}>{canal.tasa25Viajes.toFixed(1)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </SeccionColapsable>
        )}
      </SeccionColapsable>

      <table className="drivers-table">
        <thead>
          <tr>
            <th>Driver ID</th>
            <th>Nombre Completo</th>
            <th>Teléfono</th>
            <th>Fecha Contratación</th>
            <th>Número de Licencia</th>
            <th>Canal</th>
            <th>Scout</th>
            <th>Fecha Match</th>
            <th>C</th>
            <th>Ctot</th>
            <th>Conexión</th>
            <th>R→C</th>
            <th>C→V</th>
            <th>V→25T</th>
            <th>Días Act</th>
            <th>Días Con</th>
            <th>% Conv</th>
            <th>Calidad</th>
            <th>1 Viaje</th>
            <th>5 Viajes</th>
            <th>25 Viajes</th>
          </tr>
        </thead>
        <tbody>
          {drivers.map((driver) => (
            <tr key={driver.driverId}>
              <td>{driver.driverId}</td>
              <td>{driver.fullName || 'N/A'}</td>
              <td>{driver.phone || 'N/A'}</td>
              <td>{formatDate(driver.startDate)}</td>
              <td>{driver.licenseNumber || 'N/A'}</td>
              <td>{driver.channel || 'N/A'}</td>
              <td style={{ textAlign: 'center' }}>
                {renderScout(driver)}
              </td>
              <td style={{ textAlign: 'center' }}>
                {obtenerIconoFecha(driver)}
              </td>
              <td>{driver.hasHistoricalConnection ? 'C' : ''}</td>
              <td>{formatWorkTime(driver.sumWorkTimeSeconds)}</td>
              <td style={{ textAlign: 'center' }}>
                {renderConexion(driver)}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.diasRegistroAConexion !== undefined && driver.diasRegistroAConexion !== null ? (
                  <span title="Días desde Registro (hire_date) hasta Primera Conexión">{driver.diasRegistroAConexion}</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.diasConexionAViaje !== undefined && driver.diasConexionAViaje !== null ? (
                  <span title="Días desde Primera Conexión hasta Primer Viaje (0 si tuvo viajes el mismo día de la primera conexión)">{driver.diasConexionAViaje}</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.diasPrimerViajeA25Viajes !== undefined && driver.diasPrimerViajeA25Viajes !== null ? (
                  <span title="Días desde Primer Viaje hasta alcanzar 25 viajes acumulados">{driver.diasPrimerViajeA25Viajes}</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.diasActivos !== undefined && driver.diasActivos !== null ? (
                  <span title="Días con al menos 1 viaje">{driver.diasActivos}</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.diasConectados !== undefined && driver.diasConectados !== null ? (
                  <span title="Días con conexión">{driver.diasConectados}</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {driver.tasaConversionConexion !== undefined && driver.tasaConversionConexion !== null ? (
                  <span title="Tasa de conversión: días activos / días conectados">{driver.tasaConversionConexion.toFixed(1)}%</span>
                ) : (
                  <span style={{ color: '#999' }}>-</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                {(() => {
                  const flags = [];
                  if (driver.matchScoreBajo) flags.push('Score bajo');
                  if (driver.matchManual) flags.push('Manual');
                  if (driver.tieneInconsistencias) flags.push('Inconsistente');
                  if (flags.length > 0) {
                    return (
                      <span
                        title={flags.join(', ')}
                        style={{
                          color: driver.tieneInconsistencias ? '#dc3545' : driver.matchScoreBajo ? '#ffc107' : '#007bff',
                          fontSize: '12px',
                          cursor: 'help'
                        }}
                      >
                        ⚠
                      </span>
                    );
                  }
                  if (driver.tieneLead && driver.tieneScout) {
                    return (
                      <span
                        title="Match completo: Lead y Scout"
                        style={{ color: '#28a745', fontSize: '14px' }}
                      >
                        ✓
                      </span>
                    );
                  }
                  return <span style={{ color: '#999', fontSize: '14px' }}>-</span>;
                })()}
              </td>
              <td style={{ textAlign: 'center' }}>
                {renderMilestoneColumna(driver, 1)}
              </td>
              <td style={{ textAlign: 'center' }}>
                {renderMilestoneColumna(driver, 5)}
              </td>
              <td style={{ textAlign: 'center' }}>
                {renderMilestoneColumna(driver, 25)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
};

export default DriverTable;

