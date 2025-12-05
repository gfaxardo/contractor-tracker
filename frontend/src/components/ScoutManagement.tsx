import React, { useState, useEffect } from 'react';
import { api, Scout } from '../services/api';
import ScoutProfile from './ScoutProfile';

const ScoutManagement: React.FC = () => {
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingScout, setEditingScout] = useState<Scout | null>(null);
  const [newScoutName, setNewScoutName] = useState('');
  const [selectedScoutId, setSelectedScoutId] = useState<string | null>(null);

  useEffect(() => {
    cargarScouts().catch((err) => {
      console.error('Error no manejado al cargar scouts:', err);
    });
  }, []);

  const cargarScouts = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getScouts();
      setScouts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar scouts');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateScout = async () => {
    if (!newScoutName.trim()) {
      setError('El nombre del scout es requerido');
      return;
    }

    try {
      await api.createScout(newScoutName);
      setNewScoutName('');
      await cargarScouts();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al crear scout');
    }
  };

  const handleUpdateScout = async (scout: Scout) => {
    try {
      await api.updateScout(scout.scoutId, scout.scoutName, scout.driverId || undefined, scout.isActive);
      setEditingScout(null);
      await cargarScouts();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al actualizar scout');
    }
  };

  if (selectedScoutId) {
    return (
      <ScoutProfile 
        scoutId={selectedScoutId} 
        onBack={() => {
          setSelectedScoutId(null);
          cargarScouts();
        }} 
      />
    );
  }

  return (
    <div className="card">
      <h2 className="card-title">Gestión de Scouts</h2>

      {error && (
        <div className="error">
          Error: {error}
        </div>
      )}

      <div className="filter-panel">
        <h3 style={{ marginBottom: '12px', fontSize: '16px', fontWeight: '600' }}>Crear Nuevo Scout</h3>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="Nombre del scout"
            value={newScoutName}
            onChange={(e) => setNewScoutName(e.target.value)}
            className="form-group input"
            style={{ flex: 1 }}
          />
          <button
            onClick={handleCreateScout}
            className="btn"
          >
            Crear Scout
          </button>
        </div>
      </div>

      {loading ? (
        <div className="loading">Cargando scouts...</div>
      ) : (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Nombre</th>
                <th>Driver ID</th>
                <th>Activo</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {scouts.map((scout) => (
                <tr key={scout.scoutId}>
                {editingScout?.scoutId === scout.scoutId ? (
                  <>
                    <td>{scout.scoutId}</td>
                    <td>
                      <input
                        type="text"
                        value={editingScout.scoutName}
                        onChange={(e) => setEditingScout({ ...editingScout, scoutName: e.target.value })}
                        className="form-group input"
                        style={{ padding: '6px', width: '100%' }}
                      />
                    </td>
                    <td>
                      <input
                        type="text"
                        value={editingScout.driverId || ''}
                        onChange={(e) => setEditingScout({ ...editingScout, driverId: e.target.value || null })}
                        className="form-group input"
                        style={{ padding: '6px', width: '100%' }}
                      />
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        checked={editingScout.isActive}
                        onChange={(e) => setEditingScout({ ...editingScout, isActive: e.target.checked })}
                      />
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                          onClick={() => handleUpdateScout(editingScout)}
                          className="btn"
                          style={{ padding: '6px 12px', fontSize: '13px' }}
                        >
                          Guardar
                        </button>
                        <button
                          onClick={() => setEditingScout(null)}
                          className="btn"
                          style={{ padding: '6px 12px', fontSize: '13px', backgroundColor: '#6b7280' }}
                        >
                          Cancelar
                        </button>
                      </div>
                    </td>
                  </>
                ) : (
                  <>
                    <td>{scout.scoutId}</td>
                    <td>{scout.scoutName}</td>
                    <td>{scout.driverId || 'N/A'}</td>
                    <td>{scout.isActive ? 'Sí' : 'No'}</td>
                    <td>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                          onClick={() => setSelectedScoutId(scout.scoutId)}
                          className="btn"
                          style={{ padding: '6px 12px', fontSize: '13px', backgroundColor: '#2563eb' }}
                        >
                          Ver Perfil
                        </button>
                        <button
                          onClick={() => setEditingScout(scout)}
                          className="btn"
                          style={{ padding: '6px 12px', fontSize: '13px' }}
                        >
                          Editar
                        </button>
                      </div>
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </div>
  );
};

export default ScoutManagement;

