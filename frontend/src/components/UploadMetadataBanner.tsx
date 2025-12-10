import React from 'react';
import { UploadMetadata } from '../services/api';

interface UploadMetadataBannerProps {
  metadata: UploadMetadata | null;
  loading?: boolean;
}

const UploadMetadataBanner: React.FC<UploadMetadataBannerProps> = ({ metadata, loading }) => {
  if (loading || !metadata) {
    return null;
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  };

  const formatDateTime = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div style={{
      backgroundColor: '#e3f2fd',
      border: '2px solid #2196f3',
      borderRadius: '8px',
      padding: '16px 20px',
      marginBottom: '20px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div style={{ flex: '1', minWidth: '200px' }}>
          <div style={{ fontSize: '14px', color: '#666', marginBottom: '4px' }}>
            Última Carga de Datos
          </div>
          <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1976d2' }}>
            {formatDateTime(metadata.lastUploadDate)}
          </div>
        </div>
        
        {metadata.dataDateFrom && metadata.dataDateTo && (
          <div style={{ flex: '1', minWidth: '200px', textAlign: 'right' }}>
            <div style={{ fontSize: '14px', color: '#666', marginBottom: '4px' }}>
              Rango de Datos Cargados
            </div>
            <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1976d2' }}>
              {formatDate(metadata.dataDateFrom)} - {formatDate(metadata.dataDateTo)}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default UploadMetadataBanner;








