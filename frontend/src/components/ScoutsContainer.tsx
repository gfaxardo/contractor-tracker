import React, { useState, useEffect } from 'react';
import YangoTransactionUpload from './YangoTransactionUpload';
import YangoTransactionReconciliation from './YangoTransactionReconciliation';
import ScoutTransactions from './ScoutTransactions';
import ScoutManagement from './ScoutManagement';
import ScoutLiquidation from './ScoutLiquidation';
import ScoutPaymentConfigComponent from './ScoutPaymentConfig';
import ScoutPaymentInstances from './ScoutPaymentInstances';
import ScoutRegistrationUpload from './ScoutRegistrationUpload';
import ScoutAffiliationControl from './ScoutAffiliationControl';

type ScoutsTab = 'upload' | 'reconciliation' | 'transactions' | 'management' | 'liquidation' | 'instances' | 'config' | 'affiliation-control';

interface ScoutsContainerProps {
  initialTab?: ScoutsTab;
}

const ScoutsContainer: React.FC<ScoutsContainerProps> = ({ initialTab = 'upload' }) => {
  const [activeTab, setActiveTab] = useState<ScoutsTab>(initialTab);

  useEffect(() => {
    if (initialTab && initialTab !== activeTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, activeTab]);

  const tabs = [
    { id: 'upload' as ScoutsTab, label: 'Subir Transacciones' },
    { id: 'reconciliation' as ScoutsTab, label: 'Conciliación' },
    { id: 'transactions' as ScoutsTab, label: 'Transacciones' },
    { id: 'management' as ScoutsTab, label: 'Gestión' },
    { id: 'liquidation' as ScoutsTab, label: 'Liquidaciones' },
    { id: 'instances' as ScoutsTab, label: 'Instancias de Pago' },
    { id: 'config' as ScoutsTab, label: 'Configuración' },
    { id: 'affiliation-control' as ScoutsTab, label: 'Control de Afiliaciones' }
  ];

  return (
    <div className="scouts-container">
      <div className="container-header">
        <h1>Scouts</h1>
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
        {activeTab === 'upload' && <YangoTransactionUpload />}
        {activeTab === 'reconciliation' && <YangoTransactionReconciliation />}
        {activeTab === 'transactions' && <ScoutTransactions />}
        {activeTab === 'management' && <ScoutManagement />}
        {activeTab === 'liquidation' && <ScoutLiquidation />}
        {activeTab === 'instances' && <ScoutPaymentInstances />}
        {activeTab === 'config' && <ScoutPaymentConfigComponent />}
        {activeTab === 'affiliation-control' && (
          <div>
            <ScoutRegistrationUpload />
            <ScoutAffiliationControl />
          </div>
        )}
      </div>
    </div>
  );
};

export default ScoutsContainer;

