import React, { useState } from 'react';
import MainNavigation from './MainNavigation';
import { useAuth } from '../contexts/AuthContext';

interface AppLayoutProps {
  children: React.ReactNode;
  currentPage: string;
  onNavigate: (page: string) => void;
}

const AppLayout: React.FC<AppLayoutProps> = ({ children, currentPage, onNavigate }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { logout, user } = useAuth();

  const toggleSidebar = () => {
    setSidebarOpen(!sidebarOpen);
  };

  const closeSidebar = () => {
    setSidebarOpen(false);
  };

  const handleNavigate = (page: string) => {
    onNavigate(page);
    closeSidebar();
  };

  return (
    <div className="app-layout">
      <header className="app-header">
        <button 
          className="hamburger-btn" 
          onClick={toggleSidebar}
          aria-label="Toggle menu"
        >
          <span className={`hamburger-icon ${sidebarOpen ? 'open' : ''}`}>
            <span></span>
            <span></span>
            <span></span>
          </span>
        </button>
        <h1 className="app-title">Contractor Tracker</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          {user && (
            <span style={{ color: '#666', fontSize: '14px' }}>
              {user.nombre || user.username}
            </span>
          )}
          <button
            onClick={logout}
            style={{
              padding: '8px 16px',
              backgroundColor: '#dc3545',
              color: 'white',
              border: 'none',
              borderRadius: '5px',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            Cerrar Sesión
          </button>
        </div>
      </header>

      <MainNavigation 
        isOpen={sidebarOpen} 
        onClose={closeSidebar}
        currentPage={currentPage}
        onNavigate={handleNavigate}
      />

      {sidebarOpen && (
        <div 
          className="sidebar-overlay" 
          onClick={closeSidebar}
        />
      )}

      <main className="app-main">
        {children}
      </main>
    </div>
  );
};

export default AppLayout;



