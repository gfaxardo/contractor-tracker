import React, { useState, useEffect } from 'react';
import { api, Scout, ScoutPaymentInstance } from '../services/api';

const ScoutPaymentInstances: React.FC = () => {
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [selectedScoutId, setSelectedScoutId] = useState<string>('');
  const [fechaDesde, setFechaDesde] = useState('');
  const [fechaHasta, setFechaHasta] = useState('');
  const [instances, setInstances] = useState<ScoutPaymentInstance[]>([]);
  const [selectedInstances, setSelectedInstances] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    cargarScouts().catch((err) => {
      console.error('Error al cargar scouts:', err);
    });
  }, []);

  useEffect(() => {
    if (selectedScoutId) {
      cargarInstancias().catch((err) => {
        console.error('Error al cargar instancias:', err);
      });
    }
  }, [selectedScoutId, fechaDesde, fechaHasta]);

  const cargarScouts = async () => {
    try {
      const data = await api.getScouts();
      setScouts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar scouts');
    }
  };

  const cargarInstancias = async () => {
    if (!selectedScoutId) return;
    
    setLoading(true);
    setError(null);
    try {
      const data = await api.getScoutPaymentInstances(
        selectedScoutId,
        fechaDesde || undefined,
        fechaHasta || undefined
      );
      setInstances(data);
      setSelectedInstances(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar instancias');
    } finally {
      setLoading(false);
    }
  };

  const handleCalcular = async () => {
    if (!selectedScoutId) {
      setError('Selecciona un scout');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await api.calculateScoutPaymentInstances(
        selectedScoutId,
        fechaDesde || undefined,
        fechaHasta || undefined
      );
      setInstances(data);
      setSelectedInstances(new Set());
      alert(`Se calcularon ${data.length} instancias`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al calcular instancias');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSelection = (instanceId: number) => {
    const newSelected = new Set(selectedInstances);
    if (newSelected.has(instanceId)) {
      newSelected.delete(instanceId);
    } else {
      newSelected.add(instanceId);
    }
    setSelectedInstances(newSelected);
  };

  const handleToggleAll = () => {
    if (selectedInstances.size === instances.filter(i => i.status === 'pending').length) {
      setSelectedInstances(new Set());
    } else {
      const pendingIds = instances.filter(i => i.status === 'pending').map(i => i.id);
      setSelectedInstances(new Set(pendingIds));
    }
  };

  const handlePagarSeleccionadas = async () => {
    if (selectedInstances.size === 0) {
      setError('Selecciona al menos una instancia para pagar');
      return;
    }

    if (!selectedScoutId) {
      setError('Selecciona un scout');
      return;
    }

    try {
      await api.payScoutPaymentInstances(selectedScoutId, Array.from(selectedInstances));
      await cargarInstancias();
      alert('Instancias pagadas exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al pagar instancias');
    }
  };

  const handlePagarTodas = async () => {
    if (!selectedScoutId) {
      setError('Selecciona un scout');
      return;
    }

    if (!confirm('¿Estás seguro de pagar todas las instancias pendientes?')) {
      return;
    }

    try {
      await api.payAllScoutPaymentInstances(selectedScoutId);
      await cargarInstancias();
      alert('Todas las instancias pendientes fueron pagadas exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al pagar todas las instancias');
    }
  };

  const pendingInstances = instances.filter(i => i.status === 'pending');
  const totalAmount = pendingInstances.reduce((sum, i) => sum + i.amount, 0);
  const selectedAmount = instances
    .filter(i => selectedInstances.has(i.id))
    .reduce((sum, i) => sum + i.amount, 0);

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <h2 style={{ marginBottom: '20px' }}>Instancias de Pago a Scouts</h2>

      {error && (
        <div className="error" style={{ marginBottom: '15px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ marginBottom: '10px' }}>Filtros</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '10px', marginBottom: '10px' }}>
          <select
            value={selectedScoutId}
            onChange={(e) => setSelectedScoutId(e.target.value)}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          >
            <option value="">Selecciona un scout</option>
            {scouts.map(scout => (
              <option key={scout.scoutId} value={scout.scoutId}>{scout.scoutName}</option>
            ))}
          </select>
          
          <input
            type="date"
            placeholder="Fecha desde"
            value={fechaDesde}
            onChange={(e) => setFechaDesde(e.target.value)}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          />
          
          <input
            type="date"
            placeholder="Fecha hasta"
            value={fechaHasta}
            onChange={(e) => setFechaHasta(e.target.value)}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          />
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={handleCalcular}
            disabled={loading || !selectedScoutId}
            className="btn"
          >
            {loading ? 'Calculando...' : '🔄 Calcular Instancias'}
          </button>
          {pendingInstances.length > 0 && (
            <>
              <button
                onClick={handlePagarSeleccionadas}
                disabled={selectedInstances.size === 0}
                className="btn"
                style={{ backgroundColor: '#28a745', color: 'white' }}
              >
                💰 Pagar Seleccionadas ({selectedInstances.size})
              </button>
              <button
                onClick={handlePagarTodas}
                className="btn"
                style={{ backgroundColor: '#17a2b8', color: 'white' }}
              >
                💵 Pagar Todas Pendientes ({pendingInstances.length})
              </button>
            </>
          )}
        </div>
      </div>

      {pendingInstances.length > 0 && (
        <div style={{ 
          marginBottom: '20px', 
          padding: '15px', 
          backgroundColor: '#d4edda', 
          border: '1px solid #c3e6cb',
          borderRadius: '4px'
        }}>
          <p><strong>Total Pendiente:</strong> S/ {totalAmount.toFixed(2)} ({pendingInstances.length} instancias)</p>
          {selectedInstances.size > 0 && (
            <p><strong>Seleccionado:</strong> S/ {selectedAmount.toFixed(2)} ({selectedInstances.size} instancias)</p>
          )}
        </div>
      )}

      {loading ? (
        <div className="loading">Cargando instancias...</div>
      ) : (
        <div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8f9fa' }}>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>
                  <input
                    type="checkbox"
                    checked={pendingInstances.length > 0 && selectedInstances.size === pendingInstances.length}
                    onChange={handleToggleAll}
                    disabled={pendingInstances.length === 0}
                  />
                </th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Driver</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Milestone</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Monto</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha Registro</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Fecha Cumplimiento</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Elegibilidad</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Estado</th>
              </tr>
            </thead>
            <tbody>
              {instances.map((instance) => (
                <tr 
                  key={instance.id} 
                  style={{ 
                    borderBottom: '1px solid #dee2e6',
                    backgroundColor: instance.status === 'pending' ? 'white' : '#f8f9fa',
                    opacity: instance.status === 'cancelled' ? 0.6 : 1
                  }}
                >
                  <td style={{ padding: '12px' }}>
                    {instance.status === 'pending' && (
                      <input
                        type="checkbox"
                        checked={selectedInstances.has(instance.id)}
                        onChange={() => handleToggleSelection(instance.id)}
                      />
                    )}
                  </td>
                  <td style={{ padding: '12px' }}>{instance.driverName || instance.driverId}</td>
                  <td style={{ padding: '12px' }}>{instance.milestoneType} viaje{instance.milestoneType > 1 ? 's' : ''}</td>
                  <td style={{ padding: '12px' }}>S/ {instance.amount.toFixed(2)}</td>
                  <td style={{ padding: '12px' }}>
                    {new Date(instance.registrationDate).toLocaleDateString('es-ES')}
                  </td>
                  <td style={{ padding: '12px' }}>
                    {new Date(instance.milestoneFulfillmentDate).toLocaleDateString('es-ES')}
                  </td>
                  <td style={{ padding: '12px' }}>
                    <span style={{ 
                      color: instance.eligibilityVerified && instance.status !== 'cancelled' ? '#28a745' : '#dc3545',
                      fontWeight: 'bold'
                    }}>
                      {instance.eligibilityVerified && instance.status !== 'cancelled' ? '✓ Elegible' : '✗ No Elegible'}
                    </span>
                    {instance.eligibilityReason && (
                      <div style={{ fontSize: '10px', color: '#6c757d', marginTop: '4px' }}>
                        {instance.eligibilityReason}
                      </div>
                    )}
                  </td>
                  <td style={{ padding: '12px' }}>
                    <span style={{ 
                      color: instance.status === 'paid' ? '#28a745' : instance.status === 'pending' ? '#ffc107' : '#dc3545',
                      fontWeight: 'bold'
                    }}>
                      {instance.status === 'paid' ? 'Pagado' : instance.status === 'pending' ? 'Pendiente' : 'Cancelado'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {instances.length === 0 && selectedScoutId && (
            <p style={{ marginTop: '20px', color: '#6c757d', textAlign: 'center' }}>
              No hay instancias pendientes. Haz clic en "Calcular Instancias" para generar nuevas.
            </p>
          )}

          {!selectedScoutId && (
            <p style={{ marginTop: '20px', color: '#6c757d', textAlign: 'center' }}>
              Selecciona un scout para ver sus instancias de pago
            </p>
          )}
        </div>
      )}
    </div>
  );
};

export default ScoutPaymentInstances;


