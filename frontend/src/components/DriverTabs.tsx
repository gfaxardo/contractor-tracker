import React from 'react';
import { DriverOnboarding } from '../services/api';

export type TabState = 'registrados' | 'conectados' | 'con-viajes';

interface DriverTabsProps {
  drivers: DriverOnboarding[];
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
}

const DriverTabs: React.FC<DriverTabsProps> = ({ drivers, activeTab, onTabChange }) => {
  const calcularContadores = () => {
    const tieneViajes = (d: DriverOnboarding): boolean => {
      const viajes = d.totalTrips14d ?? 0;
      if (viajes >= 1) return true;
      
      if (d.status14d === 'activo_con_viajes') return true;
      
      const tieneMilestone = (milestones?: any[]) => {
        return milestones && milestones.length > 0 && milestones.some(m => 
          m.milestoneType === 1 || m.milestoneType === 5 || m.milestoneType === 25
        );
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
      
      if (d.hasHistoricalConnection === true) return true;
      
      const diasConectados = d.diasConectados ?? 0;
      if (diasConectados > 0) return true;
      
      return false;
    };
    
    const registrados = drivers.filter(d => {
      return !tieneConexion(d) && !tieneViajes(d);
    });
    
    const conectados = drivers.filter(d => {
      return tieneConexion(d) && !tieneViajes(d);
    });
    
    const conViajes = drivers.filter(d => {
      return tieneViajes(d);
    });

    return {
      registrados: registrados.length,
      conectados: conectados.length,
      'con-viajes': conViajes.length
    };
  };

  const contadores = calcularContadores();

  return (
    <div className="driver-tabs">
      <button
        className={`tab-button ${activeTab === 'registrados' ? 'active' : ''}`}
        onClick={() => onTabChange('registrados')}
      >
        Registrados ({contadores.registrados})
      </button>
      <button
        className={`tab-button ${activeTab === 'conectados' ? 'active' : ''}`}
        onClick={() => onTabChange('conectados')}
      >
        Conectados ({contadores.conectados})
      </button>
      <button
        className={`tab-button ${activeTab === 'con-viajes' ? 'active' : ''}`}
        onClick={() => onTabChange('con-viajes')}
      >
        Con viajes ({contadores['con-viajes']})
      </button>
    </div>
  );
};

export default DriverTabs;

