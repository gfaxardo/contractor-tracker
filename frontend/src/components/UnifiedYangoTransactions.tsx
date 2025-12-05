import React, { useState, useEffect } from 'react';
import YangoTransactionUpload from './YangoTransactionUpload';
import YangoTransactionReconciliation from './YangoTransactionReconciliation';
import UploadMetadataBanner from './UploadMetadataBanner';
import UploadMetadataPanel from './UploadMetadataPanel';
import { api, UploadMetadata } from '../services/api';

const UnifiedYangoTransactions: React.FC = () => {
  const [metadata, setMetadata] = useState<UploadMetadata | null>(null);
  const [loadingMetadata, setLoadingMetadata] = useState(true);
  const [activeSection, setActiveSection] = useState<'upload' | 'reconciliation'>('upload');

  useEffect(() => {
    cargarMetadata();
  }, []);

  const cargarMetadata = async () => {
    setLoadingMetadata(true);
    try {
      const data = await api.getYangoTransactionsUploadMetadata();
      setMetadata(data);
    } catch (err) {
      console.error('Error al cargar metadata:', err);
    } finally {
      setLoadingMetadata(false);
    }
  };

  const handleUploadComplete = () => {
    cargarMetadata();
  };

  return (
    <div>
      <div className="container-header">
        <h1>Transacciones Yango vs Drivers</h1>
      </div>

      <UploadMetadataBanner metadata={metadata} loading={loadingMetadata} />

      <UploadMetadataPanel metadata={metadata} loading={loadingMetadata} />

      <div className="tabs-container" style={{ marginBottom: '20px' }}>
        <button
          className={`tab-button ${activeSection === 'upload' ? 'active' : ''}`}
          onClick={() => setActiveSection('upload')}
        >
          Carga de Datos
        </button>
        <button
          className={`tab-button ${activeSection === 'reconciliation' ? 'active' : ''}`}
          onClick={() => setActiveSection('reconciliation')}
        >
          Conciliación
        </button>
      </div>

      <div className="tab-content">
        {activeSection === 'upload' && <YangoTransactionUpload onUploadComplete={handleUploadComplete} />}
        {activeSection === 'reconciliation' && <YangoTransactionReconciliation />}
      </div>
    </div>
  );
};

export default UnifiedYangoTransactions;







