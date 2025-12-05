import React from 'react';
import { DriverOnboarding } from '../services/api';

export type MilestoneType = 1 | 5 | 25;
export type PeriodDays = 7 | 14;

interface MilestoneSubTabsProps {
  drivers: DriverOnboarding[];
  activeMilestone: MilestoneType;
  activePeriod: PeriodDays;
  onMilestoneChange: (milestone: MilestoneType) => void;
  onPeriodChange: (period: PeriodDays) => void;
  totals?: { milestone1: number; milestone5: number; milestone25: number } | null;
}

const MilestoneSubTabs: React.FC<MilestoneSubTabsProps> = ({
  drivers,
  activeMilestone,
  activePeriod,
  onMilestoneChange,
  onPeriodChange,
  totals
}) => {
  const filtrarDriversPorMilestone = (milestoneType: MilestoneType, periodDays: PeriodDays): DriverOnboarding[] => {
    return drivers.filter(driver => {
      const milestones = periodDays === 14 ? driver.milestones14d : driver.milestones7d;
      if (!milestones || milestones.length === 0) {
        return false;
      }
      const milestone = milestones.find(m => m.milestoneType === milestoneType);
      return milestone != null;
    });
  };

  const obtenerContador = (milestoneType: MilestoneType, periodDays: PeriodDays): number => {
    if (totals) {
      if (milestoneType === 1) return totals.milestone1;
      if (milestoneType === 5) return totals.milestone5;
      if (milestoneType === 25) return totals.milestone25;
    }
    return filtrarDriversPorMilestone(milestoneType, periodDays).length;
  };

  return (
    <div style={{ marginBottom: '20px' }}>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center', 
        marginBottom: '15px',
        flexWrap: 'wrap',
        gap: '10px'
      }}>
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <button
            className={`tab-button ${activeMilestone === 1 ? 'active' : ''}`}
            onClick={() => onMilestoneChange(1)}
            style={{
              padding: '8px 16px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              backgroundColor: activeMilestone === 1 ? '#dc3545' : 'white',
              color: activeMilestone === 1 ? 'white' : '#333',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            1 Viaje ({obtenerContador(1, activePeriod)})
          </button>
          <button
            className={`tab-button ${activeMilestone === 5 ? 'active' : ''}`}
            onClick={() => onMilestoneChange(5)}
            style={{
              padding: '8px 16px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              backgroundColor: activeMilestone === 5 ? '#dc3545' : 'white',
              color: activeMilestone === 5 ? 'white' : '#333',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            5 Viajes ({obtenerContador(5, activePeriod)})
          </button>
          <button
            className={`tab-button ${activeMilestone === 25 ? 'active' : ''}`}
            onClick={() => onMilestoneChange(25)}
            style={{
              padding: '8px 16px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              backgroundColor: activeMilestone === 25 ? '#dc3545' : 'white',
              color: activeMilestone === 25 ? 'white' : '#333',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            25 Viajes ({obtenerContador(25, activePeriod)})
          </button>
        </div>
        
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <span style={{ fontSize: '14px', fontWeight: 'bold' }}>Período:</span>
          <button
            onClick={() => onPeriodChange(14)}
            style={{
              padding: '8px 16px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              backgroundColor: activePeriod === 14 ? '#dc3545' : 'white',
              color: activePeriod === 14 ? 'white' : '#333',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: activePeriod === 14 ? 'bold' : 'normal'
            }}
          >
            14 Días
          </button>
          <button
            onClick={() => onPeriodChange(7)}
            style={{
              padding: '8px 16px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              backgroundColor: activePeriod === 7 ? '#dc3545' : 'white',
              color: activePeriod === 7 ? 'white' : '#333',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: activePeriod === 7 ? 'bold' : 'normal'
            }}
          >
            7 Días (Scouts)
          </button>
        </div>
      </div>
    </div>
  );
};

export default MilestoneSubTabs;

