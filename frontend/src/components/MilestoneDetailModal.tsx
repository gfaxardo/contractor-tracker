import React from 'react';
import { MilestoneInstance } from '../services/api';

interface MilestoneDetailModalProps {
  milestone: MilestoneInstance | null;
  driverName?: string;
  onClose: () => void;
}

const MilestoneDetailModal: React.FC<MilestoneDetailModalProps> = ({ milestone, driverName, onClose }) => {
  if (!milestone) {
    return null;
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  };

  const formatDateTime = (dateString: string) => {
    return new Date(dateString).toLocaleString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className="modal-overlay" onClick={onClose} style={{
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
        maxWidth: '800px',
        maxHeight: '80vh',
        overflow: 'auto',
        width: '90%'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h2>Detalle de Instancia - {milestone.milestoneType} Viaje{milestone.milestoneType > 1 ? 's' : ''} ({milestone.periodDays} días)</h2>
          <button onClick={onClose} style={{
            background: 'none',
            border: 'none',
            fontSize: '24px',
            cursor: 'pointer',
            color: '#666'
          }}>×</button>
        </div>

        {driverName && (
          <div style={{ marginBottom: '15px' }}>
            <strong>Driver:</strong> {driverName}
          </div>
        )}

        <div style={{ marginBottom: '20px' }}>
          <div><strong>Driver ID:</strong> {milestone.driverId}</div>
          <div><strong>Viajes Totales:</strong> {milestone.tripCount}</div>
          <div><strong>Fecha de Cumplimiento:</strong> {formatDateTime(milestone.fulfillmentDate)}</div>
          <div><strong>Fecha de Cálculo:</strong> {formatDateTime(milestone.calculationDate)}</div>
        </div>

        {milestone.tripDetails && milestone.tripDetails.length > 0 && (
          <div>
            <h3 style={{ marginBottom: '15px' }}>Viajes Considerados:</h3>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #ddd' }}>
                  <th style={{ padding: '10px', textAlign: 'left' }}>Día desde Hire Date</th>
                  <th style={{ padding: '10px', textAlign: 'left' }}>Fecha</th>
                  <th style={{ padding: '10px', textAlign: 'right' }}>Viajes</th>
                </tr>
              </thead>
              <tbody>
                {milestone.tripDetails.map((trip, index) => (
                  <tr key={index} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: '10px' }}>Día {trip.dayFromHireDate}</td>
                    <td style={{ padding: '10px' }}>{formatDate(trip.date)}</td>
                    <td style={{ padding: '10px', textAlign: 'right' }}>{trip.tripCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {(!milestone.tripDetails || milestone.tripDetails.length === 0) && (
          <div style={{ padding: '20px', textAlign: 'center', color: '#666' }}>
            No hay detalles de viajes disponibles para esta instancia.
          </div>
        )}
      </div>
    </div>
  );
};

export default MilestoneDetailModal;












