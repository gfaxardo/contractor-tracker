import React from 'react';
import { formatWeekISO, formatWeekRange, getPreviousWeek, getNextWeek } from '../utils/weekUtils';

interface FilterFormProps {
  parkId: string;
  startDateFrom: string;
  startDateTo: string;
  channel: string;
  weekISO: string;
  lastUpdated?: string;
  hasYangoTransaction?: string;
  yangoMilestoneType?: string;
  onParkIdChange: (value: string) => void;
  onStartDateFromChange: (value: string) => void;
  onStartDateToChange: (value: string) => void;
  onChannelChange: (value: string) => void;
  onWeekISOChange: (value: string) => void;
  onHasYangoTransactionChange?: (value: string) => void;
  onYangoMilestoneTypeChange?: (value: string) => void;
  onSearch: () => void;
  loading: boolean;
}

const FilterForm: React.FC<FilterFormProps> = ({
  parkId,
  startDateFrom,
  startDateTo,
  channel,
  weekISO,
  lastUpdated,
  hasYangoTransaction,
  yangoMilestoneType,
  onParkIdChange,
  onStartDateFromChange,
  onStartDateToChange,
  onChannelChange,
  onWeekISOChange,
  onHasYangoTransactionChange,
  onYangoMilestoneTypeChange,
  onSearch,
  loading
}) => {
  const handlePreviousWeek = () => {
    const prevWeek = getPreviousWeek(weekISO);
    onWeekISOChange(prevWeek);
  };

  const handleNextWeek = () => {
    const nextWeek = getNextWeek(weekISO);
    onWeekISOChange(nextWeek);
  };

  const handleWeekISOInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    if (value.match(/^\d{4}-W\d{2}$/)) {
      // Validar que la semana sea válida (1-53)
      const [yearStr, weekStr] = value.split('-W');
      const year = parseInt(yearStr, 10);
      const week = parseInt(weekStr, 10);
      
      if (!isNaN(year) && !isNaN(week) && week >= 1 && week <= 53 && year >= 2000 && year <= 2100) {
        onWeekISOChange(value);
      }
    } else if (value === '') {
      // Permitir borrar el campo
      onWeekISOChange('');
    }
  };

  return (
    <div className="filter-section">
      <h2>Filtros de Búsqueda</h2>
      <div className="filter-form">
        <div className="form-group">
          <label htmlFor="parkId">Park ID *</label>
          <input
            id="parkId"
            type="text"
            value={parkId}
            onChange={(e) => onParkIdChange(e.target.value)}
            placeholder="08e20910d81d42658d4334d3f6d10ac0"
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="weekISO">Semana ISO</label>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <button 
              type="button"
              onClick={handlePreviousWeek}
              disabled={loading}
              style={{ padding: '4px 8px' }}
            >
              ←
            </button>
            <input
              id="weekISO"
              type="text"
              value={weekISO}
              onChange={handleWeekISOInputChange}
              placeholder="2024-W01"
              pattern="\d{4}-W\d{2}"
              style={{ flex: 1 }}
            />
            <button 
              type="button"
              onClick={handleNextWeek}
              disabled={loading}
              style={{ padding: '4px 8px' }}
            >
              →
            </button>
          </div>
          {weekISO && (
            <div style={{ fontSize: '0.9em', color: '#666', marginTop: '4px' }}>
              {formatWeekISO(weekISO)} ({formatWeekRange(weekISO)})
            </div>
          )}
        </div>
        
        <div className="form-group">
          <label htmlFor="startDateFrom">Fecha Inicio (Desde)</label>
          <input
            id="startDateFrom"
            type="date"
            value={startDateFrom}
            onChange={(e) => onStartDateFromChange(e.target.value)}
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="startDateTo">Fecha Fin (Hasta)</label>
          <input
            id="startDateTo"
            type="date"
            value={startDateTo}
            onChange={(e) => onStartDateToChange(e.target.value)}
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="channel">Canal</label>
          <select
            id="channel"
            value={channel}
            onChange={(e) => onChannelChange(e.target.value)}
            style={{ width: '100%', padding: '8px' }}
          >
            <option value="">Todos</option>
            <option value="cabinet">Cabinet</option>
            <option value="otros">Otros</option>
          </select>
        </div>
        
        {onHasYangoTransactionChange && (
          <div className="form-group">
            <label htmlFor="hasYangoTransaction">Transacciones Yango</label>
            <select
              id="hasYangoTransaction"
              value={hasYangoTransaction || ''}
              onChange={(e) => onHasYangoTransactionChange(e.target.value)}
              style={{ width: '100%', padding: '8px' }}
            >
              <option value="">Todos</option>
              <option value="with">Con transacciones pagadas</option>
              <option value="without">Sin transacciones pagadas</option>
              <option value="pending">Instancias por pagar</option>
            </select>
          </div>
        )}
        
        {onYangoMilestoneTypeChange && (
          <div className="form-group">
            <label htmlFor="yangoMilestoneType">Tipo Milestone Pagado</label>
            <select
              id="yangoMilestoneType"
              value={yangoMilestoneType || ''}
              onChange={(e) => onYangoMilestoneTypeChange(e.target.value)}
              style={{ width: '100%', padding: '8px' }}
            >
              <option value="">Todos</option>
              <option value="1">1 Viaje</option>
              <option value="5">5 Viajes</option>
              <option value="25">25 Viajes</option>
            </select>
          </div>
        )}
      </div>
      
      {lastUpdated && (
        <div style={{ marginTop: '10px', padding: '10px', backgroundColor: '#f0f0f0', borderRadius: '4px', fontSize: '0.9em' }}>
          <strong>Última actualización:</strong> {new Date(lastUpdated).toLocaleString('es-ES')}
        </div>
      )}
      
      <button 
        className="btn" 
        onClick={onSearch} 
        disabled={loading || !parkId.trim()}
      >
        {loading ? 'Buscando...' : 'Buscar'}
      </button>
    </div>
  );
};

export default FilterForm;

