import React, { useState, useEffect } from 'react';
import LeadReconciliation from './LeadReconciliation';
import YangoTransactionReconciliation from './YangoTransactionReconciliation';
import ReconciliationDashboard from './ReconciliationDashboard';
import ScoutRegistrationReconciliation from './ScoutRegistrationReconciliation';

type ReconciliationTab = 'leads' | 'transactions' | 'dashboard' | 'scouts';

interface ReconciliationContainerProps {
  initialTab?: ReconciliationTab;
}

const ReconciliationContainer: React.FC<ReconciliationContainerProps> = ({ initialTab = 'leads' }) => {
  const [activeTab, setActiveTab] = useState<ReconciliationTab>(initialTab);

  useEffect(() => {
    if (initialTab && initialTab !== activeTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, activeTab]);

  const tabs = [
    { id: 'leads' as ReconciliationTab, label: 'Leads vs Drivers' },
    { id: 'transactions' as ReconciliationTab, label: 'Transacciones vs Drivers' },
    { id: 'scouts' as ReconciliationTab, label: 'Scouts vs Drivers' },
    { id: 'dashboard' as ReconciliationTab, label: 'Dashboard' }
  ];

  return (
    <div className="reconciliation-container">
      <div className="container-header">
        <h1>Conciliación</h1>
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
        {activeTab === 'leads' && <LeadReconciliation />}
        {activeTab === 'transactions' && <YangoTransactionReconciliation />}
        {activeTab === 'scouts' && <ScoutRegistrationReconciliation />}
        {activeTab === 'dashboard' && <ReconciliationDashboard />}
      </div>
    </div>
  );
};

export default ReconciliationContainer;

