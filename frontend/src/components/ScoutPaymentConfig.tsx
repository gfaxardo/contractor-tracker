import React, { useState, useEffect } from 'react';
import { api, ScoutPaymentConfig as ScoutPaymentConfigType } from '../services/api';

const ScoutPaymentConfigComponent: React.FC = () => {
  const [configs, setConfigs] = useState<ScoutPaymentConfigType[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingConfig, setEditingConfig] = useState<ScoutPaymentConfigType | null>(null);

  useEffect(() => {
    cargarConfiguracion().catch((err) => {
      console.error('Error al cargar configuración:', err);
      setError(err instanceof Error ? err.message : 'Error al cargar configuración');
    });
  }, []);

  const cargarConfiguracion = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getScoutPaymentConfig();
      setConfigs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar configuración');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateConfig = async (config: ScoutPaymentConfigType) => {
    try {
      await api.updateScoutPaymentConfig(
        config.id,
        config.amountScout,
        config.paymentDays,
        config.isActive,
        config.minRegistrationsRequired,
        config.minConnectionSeconds
      );
      setEditingConfig(null);
      await cargarConfiguracion();
      alert('Configuración actualizada exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al actualizar configuración');
    }
  };

  if (loading) {
    return <div className="loading">Cargando configuración...</div>;
  }

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <h2 style={{ marginBottom: '20px' }}>Configuración de Pagos a Scouts</h2>

      {error && (
        <div className="error" style={{ marginBottom: '15px' }}>
          Error: {error}
        </div>
      )}

      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ backgroundColor: '#f8f9fa' }}>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Milestone</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Monto Scout (PEN)</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Días de Pago</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Min. Registros</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Min. Segundos</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Activo</th>
            <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {configs.map((config) => (
            <tr key={config.id} style={{ borderBottom: '1px solid #dee2e6' }}>
              {editingConfig?.id === config.id ? (
                <>
                  <td style={{ padding: '12px' }}>Milestone {config.milestoneType}</td>
                  <td style={{ padding: '12px' }}>
                    <input
                      type="number"
                      step="0.01"
                      value={editingConfig.amountScout}
                      onChange={(e) => setEditingConfig({ ...editingConfig, amountScout: parseFloat(e.target.value) })}
                      style={{ padding: '4px', width: '100px' }}
                    />
                  </td>
                  <td style={{ padding: '12px' }}>
                    <input
                      type="number"
                      value={editingConfig.paymentDays}
                      onChange={(e) => setEditingConfig({ ...editingConfig, paymentDays: parseInt(e.target.value) })}
                      style={{ padding: '4px', width: '80px' }}
                    />
                  </td>
                  <td style={{ padding: '12px' }}>
                    <input
                      type="number"
                      value={editingConfig.minRegistrationsRequired || 8}
                      onChange={(e) => setEditingConfig({ ...editingConfig, minRegistrationsRequired: parseInt(e.target.value) })}
                      style={{ padding: '4px', width: '80px' }}
                    />
                  </td>
                  <td style={{ padding: '12px' }}>
                    <input
                      type="number"
                      value={editingConfig.minConnectionSeconds || 1}
                      onChange={(e) => setEditingConfig({ ...editingConfig, minConnectionSeconds: parseInt(e.target.value) })}
                      style={{ padding: '4px', width: '80px' }}
                    />
                  </td>
                  <td style={{ padding: '12px' }}>
                    <input
                      type="checkbox"
                      checked={editingConfig.isActive}
                      onChange={(e) => setEditingConfig({ ...editingConfig, isActive: e.target.checked })}
                    />
                  </td>
                  <td style={{ padding: '12px' }}>
                    <button
                      onClick={() => handleUpdateConfig(editingConfig)}
                      className="btn"
                      style={{ marginRight: '5px', padding: '4px 8px', fontSize: '12px' }}
                    >
                      Guardar
                    </button>
                    <button
                      onClick={() => setEditingConfig(null)}
                      style={{ padding: '4px 8px', fontSize: '12px' }}
                    >
                      Cancelar
                    </button>
                  </td>
                </>
              ) : (
                <>
                  <td style={{ padding: '12px' }}>Milestone {config.milestoneType}</td>
                  <td style={{ padding: '12px' }}>S/ {config.amountScout.toFixed(2)}</td>
                  <td style={{ padding: '12px' }}>{config.paymentDays} días</td>
                  <td style={{ padding: '12px' }}>{config.minRegistrationsRequired || 8}</td>
                  <td style={{ padding: '12px' }}>{config.minConnectionSeconds || 1}</td>
                  <td style={{ padding: '12px' }}>{config.isActive ? 'Sí' : 'No'}</td>
                  <td style={{ padding: '12px' }}>
                    <button
                      onClick={() => setEditingConfig(config)}
                      className="btn"
                      style={{ padding: '4px 8px', fontSize: '12px' }}
                    >
                      Editar
                    </button>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {configs.length === 0 && (
        <p style={{ marginTop: '20px', color: '#6c757d' }}>
          No hay configuración disponible. Asegúrate de que existan registros en la base de datos.
        </p>
      )}
    </div>
  );
};

export default ScoutPaymentConfigComponent;

