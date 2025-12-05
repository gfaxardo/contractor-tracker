import React, { useState, useEffect } from 'react';
import { api, Scout, ScoutLiquidationCalculation, ScoutPayment } from '../services/api';

const ScoutLiquidation: React.FC = () => {
  const [scouts, setScouts] = useState<Scout[]>([]);
  const [selectedScoutId, setSelectedScoutId] = useState<string>('');
  const [fechaInicio, setFechaInicio] = useState('');
  const [fechaFin, setFechaFin] = useState('');
  const [calculo, setCalculo] = useState<ScoutLiquidationCalculation | null>(null);
  const [payments, setPayments] = useState<ScoutPayment[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    cargarScouts().catch((err) => {
      console.error('Error no manejado al cargar scouts:', err);
    });
  }, []);

  useEffect(() => {
    if (selectedScoutId) {
      cargarPagos().catch((err) => {
        console.error('Error no manejado al cargar pagos:', err);
      });
    }
  }, [selectedScoutId]);

  const cargarScouts = async () => {
    try {
      const data = await api.getScouts();
      setScouts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar scouts');
    }
  };

  const cargarPagos = async () => {
    try {
      const data = await api.getScoutPayments(selectedScoutId);
      setPayments(data);
    } catch (err) {
      console.error('Error al cargar pagos:', err);
    }
  };

  const handleCalcular = async () => {
    if (!selectedScoutId || !fechaInicio || !fechaFin) {
      setError('Selecciona un scout y un rango de fechas');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const resultado = await api.calculateScoutLiquidation(selectedScoutId, fechaInicio, fechaFin);
      setCalculo(resultado);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al calcular liquidación');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerarPago = async () => {
    if (!selectedScoutId || !fechaInicio || !fechaFin) {
      setError('Selecciona un scout y un rango de fechas');
      return;
    }

    try {
      await api.generateScoutPayment(selectedScoutId, fechaInicio, fechaFin);
      setCalculo(null);
      await cargarPagos();
      alert('Pago generado exitosamente');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al generar pago');
    }
  };

  const handleMarcarComoPagado = async (paymentId: number) => {
    try {
      await api.markScoutPaymentAsPaid(paymentId);
      await cargarPagos();
      alert('Pago marcado como pagado');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al marcar pago');
    }
  };

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <h2 style={{ marginBottom: '20px' }}>Liquidación de Scouts</h2>

      {error && (
        <div className="error" style={{ marginBottom: '15px' }}>
          Error: {error}
        </div>
      )}

      <div style={{ marginBottom: '20px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ marginBottom: '10px' }}>Calcular Liquidación</h3>
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
            placeholder="Fecha inicio"
            value={fechaInicio}
            onChange={(e) => setFechaInicio(e.target.value)}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          />
          
          <input
            type="date"
            placeholder="Fecha fin"
            value={fechaFin}
            onChange={(e) => setFechaFin(e.target.value)}
            style={{ padding: '8px', fontSize: '14px', border: '1px solid #ddd', borderRadius: '4px' }}
          />
        </div>
        <button
          onClick={handleCalcular}
          disabled={loading || !selectedScoutId || !fechaInicio || !fechaFin}
          className="btn"
          style={{ marginRight: '10px' }}
        >
          {loading ? 'Calculando...' : 'Calcular'}
        </button>
        {calculo && (
          <button
            onClick={handleGenerarPago}
            className="btn"
            style={{ backgroundColor: '#28a745', color: 'white' }}
          >
            Generar Pago
          </button>
        )}
      </div>

      {calculo && (
        <div style={{ 
          marginBottom: '20px', 
          padding: '15px', 
          backgroundColor: '#d4edda', 
          border: '1px solid #c3e6cb',
          borderRadius: '4px'
        }}>
          <h3 style={{ marginBottom: '10px' }}>Resultado del Cálculo</h3>
          <p><strong>Total a Pagar:</strong> S/ {Number(calculo.totalAmount).toFixed(2)}</p>
          <p><strong>Transacciones:</strong> {calculo.transactionsCount}</p>
          <p><strong>Milestones:</strong></p>
          <ul>
            {Object.entries(calculo.milestoneCounts || {}).map(([milestone, count]) => (
              <li key={milestone}>Milestone {milestone}: {count}</li>
            ))}
          </ul>
        </div>
      )}

      <div>
        <h3 style={{ marginBottom: '10px' }}>Historial de Pagos</h3>
        {selectedScoutId ? (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8f9fa' }}>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>ID</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Período</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Monto</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Transacciones</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Estado</th>
                <th style={{ padding: '12px', textAlign: 'left', borderBottom: '2px solid #dee2e6' }}>Acción</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.id} style={{ borderBottom: '1px solid #dee2e6' }}>
                  <td style={{ padding: '12px' }}>{payment.id}</td>
                  <td style={{ padding: '12px' }}>
                    {new Date(payment.paymentPeriodStart).toLocaleDateString('es-ES')} - {new Date(payment.paymentPeriodEnd).toLocaleDateString('es-ES')}
                  </td>
                  <td style={{ padding: '12px' }}>S/ {payment.totalAmount.toFixed(2)}</td>
                  <td style={{ padding: '12px' }}>{payment.transactionsCount}</td>
                  <td style={{ padding: '12px' }}>
                    <span style={{ 
                      color: payment.status === 'paid' ? '#28a745' : payment.status === 'pending' ? '#ffc107' : '#dc3545',
                      fontWeight: 'bold'
                    }}>
                      {payment.status === 'paid' ? 'Pagado' : payment.status === 'pending' ? 'Pendiente' : 'Cancelado'}
                    </span>
                  </td>
                  <td style={{ padding: '12px' }}>
                    {payment.status === 'pending' && (
                      <button
                        onClick={() => handleMarcarComoPagado(payment.id)}
                        className="btn"
                        style={{ padding: '4px 8px', fontSize: '12px' }}
                      >
                        Marcar como Pagado
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p>Selecciona un scout para ver su historial de pagos</p>
        )}
      </div>
    </div>
  );
};

export default ScoutLiquidation;

