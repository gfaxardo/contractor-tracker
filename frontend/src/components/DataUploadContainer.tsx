import React, { useState, useEffect } from 'react';
import LeadUpload from './LeadUpload';
import YangoTransactionUpload from './YangoTransactionUpload';
import ScoutRegistrationUpload from './ScoutRegistrationUpload';

type DataUploadTab = 'leads' | 'yango-transactions' | 'scout-registrations';

interface DataUploadContainerProps {
  initialTab?: DataUploadTab;
  onUploadComplete?: () => void;
}

const DataUploadContainer: React.FC<DataUploadContainerProps> = ({ 
  initialTab = 'leads',
  onUploadComplete 
}) => {
  const [activeTab, setActiveTab] = useState<DataUploadTab>(initialTab);

  useEffect(() => {
    if (initialTab && initialTab !== activeTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, activeTab]);

  const tabs = [
    { id: 'leads' as DataUploadTab, label: 'Leads' },
    { id: 'yango-transactions' as DataUploadTab, label: 'Transacciones Yango' },
    { id: 'scout-registrations' as DataUploadTab, label: 'Registros de Scouts' }
  ];

  return (
    <div className="data-upload-container">
      <div className="container-header">
        <h1>Carga de Datos</h1>
      </div>

      <div className="tabs-container">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="tab-content">
        {activeTab === 'leads' && (
          <LeadUpload onUploadComplete={onUploadComplete} />
        )}
        {activeTab === 'yango-transactions' && (
          <YangoTransactionUpload onUploadComplete={onUploadComplete} />
        )}
        {activeTab === 'scout-registrations' && (
          <ScoutRegistrationUpload onUploadComplete={onUploadComplete} />
        )}
      </div>
    </div>
  );
};

export default DataUploadContainer;

