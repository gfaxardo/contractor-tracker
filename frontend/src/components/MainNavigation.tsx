import React from 'react';

interface MainNavigationProps {
  isOpen: boolean;
  onClose: () => void;
  currentPage: string;
  onNavigate: (page: string) => void;
}

interface NavItem {
  id: string;
  label: string;
  icon?: string;
  children?: NavItem[];
}

const MainNavigation: React.FC<MainNavigationProps> = ({ isOpen, currentPage, onNavigate }) => {
  const navItems: NavItem[] = [
    {
      id: 'main',
      label: 'Dashboard',
      icon: '🏠'
    },
    {
      id: 'reconciliation',
      label: 'Conciliación',
      icon: '🔄',
      children: [
        { id: 'reconciliation-leads', label: 'Leads vs Drivers' },
        { id: 'reconciliation-transactions', label: 'Transacciones vs Drivers' },
        { id: 'reconciliation-scouts', label: 'Registros de Scouts vs Drivers' },
        { id: 'reconciliation-dashboard', label: 'Dashboard de Métricas' },
        { id: 'milestones-payment-view', label: 'Milestones vs Pagos Yango' }
      ]
    },
    {
      id: 'leads-management',
      label: 'Gestión de Leads',
      icon: '📋',
      children: [
        { id: 'cabinet', label: 'Cabinet' },
        { id: 'reprocess', label: 'Reprocesamiento' }
      ]
    },
    {
      id: 'scouts',
      label: 'Scouts',
      icon: '👥',
      children: [
        { id: 'scouts-upload', label: 'Subir Transacciones' },
        { id: 'scouts-reconciliation', label: 'Conciliación' },
        { id: 'scouts-transactions', label: 'Transacciones' },
        { id: 'scouts-management', label: 'Gestión' },
        { id: 'scouts-liquidation', label: 'Liquidaciones' },
        { id: 'scouts-instances', label: 'Instancias de Pago' },
        { id: 'scouts-config', label: 'Configuración' },
        { id: 'scouts-affiliation', label: 'Control de Afiliaciones' }
      ]
    },
    {
      id: 'audit',
      label: 'Auditoría',
      icon: '📋'
    }
  ];

  const [expandedItems, setExpandedItems] = React.useState<Set<string>>(() => {
    const initial = new Set<string>();
    if (currentPage.startsWith('reconciliation-') || currentPage === 'milestones-payment-view') {
      initial.add('reconciliation');
    } else if (currentPage === 'cabinet' || currentPage === 'reprocess') {
      initial.add('leads-management');
    } else if (currentPage.startsWith('scouts-')) {
      initial.add('scouts');
    }
    if (currentPage === 'audit') {
      initial.add('audit');
    }
    return initial;
  });

  React.useEffect(() => {
    const newExpanded = new Set<string>();
    if (currentPage.startsWith('reconciliation-') || currentPage === 'milestones-payment-view') {
      newExpanded.add('reconciliation');
    } else if (currentPage === 'cabinet' || currentPage === 'reprocess') {
      newExpanded.add('leads-management');
    } else if (currentPage.startsWith('scouts-')) {
      newExpanded.add('scouts');
    } else if (currentPage === 'audit') {
      newExpanded.add('audit');
    }
    setExpandedItems(prev => {
      const updated = new Set(prev);
      newExpanded.forEach(item => updated.add(item));
      return updated;
    });
  }, [currentPage]);

  const toggleExpand = (itemId: string) => {
    const newExpanded = new Set(expandedItems);
    if (newExpanded.has(itemId)) {
      newExpanded.delete(itemId);
    } else {
      newExpanded.add(itemId);
    }
    setExpandedItems(newExpanded);
  };

  const handleItemClick = (item: NavItem) => {
    if (item.children) {
      toggleExpand(item.id);
    } else {
      onNavigate(item.id);
    }
  };

  const isItemActive = (item: NavItem): boolean => {
    if (item.id === currentPage) return true;
    if (item.children) {
      return item.children.some(child => child.id === currentPage);
    }
    return false;
  };

  const isChildActive = (child: NavItem): boolean => {
    return child.id === currentPage;
  };

  return (
    <nav className={`main-navigation ${isOpen ? 'open' : ''}`}>
      <div className="nav-content">
        {navItems.map((item) => (
          <div key={item.id} className="nav-section">
            <div
              className={`nav-item ${isItemActive(item) ? 'active' : ''} ${item.children ? 'has-children' : ''}`}
              onClick={() => handleItemClick(item)}
            >
              {item.icon && <span className="nav-icon">{item.icon}</span>}
              <span className="nav-label">{item.label}</span>
              {item.children && (
                <span className={`nav-arrow ${expandedItems.has(item.id) ? 'expanded' : ''}`}>
                  ▶
                </span>
              )}
            </div>
            {item.children && expandedItems.has(item.id) && (
              <div className="nav-children">
                {item.children.map((child) => (
                  <div
                    key={child.id}
                    className={`nav-child ${isChildActive(child) ? 'active' : ''}`}
                    onClick={() => onNavigate(child.id)}
                  >
                    {child.label}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </nav>
  );
};

export default MainNavigation;

