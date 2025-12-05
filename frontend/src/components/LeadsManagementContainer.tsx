import React, { useState, useEffect } from 'react';
import Cabinet from './Cabinet';
import LeadReprocess from './LeadReprocess';

type LeadsManagementTab = 'cabinet' | 'reprocess';

interface LeadsManagementContainerProps {
  initialTab?: LeadsManagementTab;
}

const LeadsManagementContainer: React.FC<LeadsManagementContainerProps> = ({ initialTab = 'cabinet' }) => {
  const [activeTab, setActiveTab] = useState<LeadsManagementTab>(initialTab);

  useEffect(() => {
    if (initialTab && initialTab !== activeTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, activeTab]);

  const tabs = [
    { id: 'cabinet' as LeadsManagementTab, label: 'Cabinet' },
    { id: 'reprocess' as LeadsManagementTab, label: 'Reprocesamiento' }
  ];

  return (
    <div className="leads-management-container">
      <div className="container-header">
        <h1>Gestión de Leads</h1>
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
        {activeTab === 'cabinet' && <Cabinet />}
        {activeTab === 'reprocess' && <LeadReprocess />}
      </div>
    </div>
  );
};

export default LeadsManagementContainer;

