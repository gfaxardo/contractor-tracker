import React from 'react';
import { UploadMetadata } from '../services/api';

interface UploadMetadataPanelProps {
  metadata: UploadMetadata | null;
  loading?: boolean;
}

const UploadMetadataPanel: React.FC<UploadMetadataPanelProps> = ({ metadata, loading }) => {
  if (loading) {
    return (
      <div className="card" style={{ marginBottom: '20px' }}>
        <div className="loading">Cargando información...</div>
      </div>
    );
  }

  if (!metadata) {
    return null;
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return 'No disponible';
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
    <div className="card" style={{ marginBottom: '20px', backgroundColor: '#f8f9fa' }}>
      <h3 style={{ marginBottom: '16px', fontSize: '18px', fontWeight: '600', color: '#333' }}>
        Información del Origen de Datos
      </h3>
      
      <div style={{ marginBottom: '20px', padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
        <div style={{ fontSize: '14px', color: '#666', marginBottom: '8px' }}>
          <strong>Tipo:</strong> {metadata.sourceDescription.title}
        </div>
        <div style={{ fontSize: '14px', color: '#666', marginBottom: '8px' }}>
          <strong>Fuente:</strong> {metadata.sourceDescription.source}
        </div>
        {metadata.sourceDescription.url && (
          <div style={{ fontSize: '14px', color: '#666', marginBottom: '8px' }}>
            <strong>URL:</strong>{' '}
            <a href={metadata.sourceDescription.url} target="_blank" rel="noopener noreferrer" 
               style={{ color: '#1976d2', textDecoration: 'underline' }}>
              {metadata.sourceDescription.url}
            </a>
          </div>
        )}
        <div style={{ fontSize: '14px', color: '#666', marginTop: '12px', padding: '12px', 
                     backgroundColor: '#f0f0f0', borderRadius: '4px', fontStyle: 'italic' }}>
          {metadata.sourceDescription.details}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
        <div style={{ padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
          <div style={{ fontSize: '12px', color: '#666', marginBottom: '4px' }}>Total de Registros</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#333' }}>
            {metadata.totalRecords.toLocaleString('es-ES')}
          </div>
        </div>
        
        <div style={{ padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
          <div style={{ fontSize: '12px', color: '#666', marginBottom: '4px' }}>Matcheados</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#28a745' }}>
            {metadata.matchedCount.toLocaleString('es-ES')}
          </div>
        </div>
        
        <div style={{ padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
          <div style={{ fontSize: '12px', color: '#666', marginBottom: '4px' }}>Sin Match</div>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#dc3545' }}>
            {metadata.unmatchedCount.toLocaleString('es-ES')}
          </div>
        </div>
        
        <div style={{ padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
          <div style={{ fontSize: '12px', color: '#666', marginBottom: '4px' }}>Última Actualización</div>
          <div style={{ fontSize: '14px', fontWeight: '600', color: '#333' }}>
            {formatDateTime(metadata.lastUploadDate)}
          </div>
        </div>
      </div>

      {metadata.dataDateFrom && metadata.dataDateTo && (
        <div style={{ marginTop: '16px', padding: '12px', backgroundColor: 'white', borderRadius: '6px' }}>
          <div style={{ fontSize: '12px', color: '#666', marginBottom: '4px' }}>
            Rango de Fechas de los Datos
          </div>
          <div style={{ fontSize: '16px', fontWeight: '600', color: '#333' }}>
            Desde: {formatDate(metadata.dataDateFrom)} - Hasta: {formatDate(metadata.dataDateTo)}
          </div>
        </div>
      )}
    </div>
  );
};

export default UploadMetadataPanel;








