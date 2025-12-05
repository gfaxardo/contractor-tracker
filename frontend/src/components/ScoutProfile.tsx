import React, { useState, useEffect } from 'react';
import { api, ScoutProfile as ScoutProfileData, ScoutProfileUpdate } from '../services/api';

interface ScoutProfileProps {
  scoutId: string;
  onBack: () => void;
}

const ScoutProfile: React.FC<ScoutProfileProps> = ({ scoutId, onBack }) => {
  const [profile, setProfile] = useState<ScoutProfileData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState<ScoutProfileUpdate>({});

  useEffect(() => {
    cargarPerfil();
  }, [scoutId]);

  const cargarPerfil = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getScoutProfile(scoutId);
      setProfile(data);
      setFormData({
        email: data.email,
        phone: data.phone,
        address: data.address,
        notes: data.notes,
        startDate: data.startDate,
        status: data.status,
        contractType: data.contractType,
        workType: data.workType,
        paymentMethod: data.paymentMethod,
        bankAccount: data.bankAccount,
        commissionRate: data.commissionRate,
        isActive: data.isActive,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar el perfil');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!scoutId) return;

    setLoading(true);
    setError(null);
    try {
      const updatedProfile = await api.updateScoutProfile(scoutId, formData as ScoutProfileUpdate);
      setProfile(updatedProfile);
      setIsEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al actualizar el perfil');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    if (profile) {
      setFormData({
        email: profile.email,
        phone: profile.phone,
        address: profile.address,
        notes: profile.notes,
        startDate: profile.startDate,
        status: profile.status,
        contractType: profile.contractType,
        workType: profile.workType,
        paymentMethod: profile.paymentMethod,
        bankAccount: profile.bankAccount,
        commissionRate: profile.commissionRate,
        isActive: profile.isActive,
      });
    }
    setIsEditing(false);
  };

  const formatearFecha = (fecha: string | null) => {
    if (!fecha) return 'N/A';
    return new Date(fecha).toLocaleDateString('es-ES');
  };

  const formatearMonto = (monto: number | null) => {
    if (monto === null) return 'N/A';
    return new Intl.NumberFormat('es-PE', { 
      style: 'percent', 
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(monto);
  };

  if (loading && !profile) {
    return <div className="loading">Cargando perfil...</div>;
  }

  if (error && !profile) {
    return (
      <div className="card">
        <div className="error">Error: {error}</div>
        <button className="btn" onClick={onBack}>
          Volver a Gestión de Scouts
        </button>
      </div>
    );
  }

  if (!profile) {
    return null;
  }

  return (
    <div style={{ 
      backgroundColor: 'white', 
      padding: '20px', 
      borderRadius: '8px',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>Perfil del Scout: {profile.scoutName}</h2>
        <div style={{ display: 'flex', gap: '10px' }}>
          {!isEditing ? (
            <button
              onClick={() => setIsEditing(true)}
              className="btn"
            >
              Editar Perfil
            </button>
          ) : (
            <>
              <button
                onClick={handleSave}
                className="btn"
                disabled={loading}
              >
                {loading ? 'Guardando...' : 'Guardar'}
              </button>
              <button
                onClick={handleCancel}
                className="btn"
                style={{ backgroundColor: '#6b7280' }}
                disabled={loading}
              >
                Cancelar
              </button>
            </>
          )}
          <button
            onClick={onBack}
            className="btn"
            style={{ backgroundColor: '#6b7280' }}
          >
            Volver
          </button>
        </div>
      </div>

      {error && (
        <div className="error" style={{ marginBottom: '20px' }}>
          Error: {error}
        </div>
      )}

      {/* Información Básica */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px' 
      }}>
        <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Información Básica</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '15px' }}>
          <div>
            <strong>ID del Scout:</strong> {profile.scoutId}
          </div>
          <div>
            <strong>Nombre:</strong> {profile.scoutName}
          </div>
          <div>
            <strong>Driver ID:</strong> {profile.driverId || 'N/A'}
          </div>
          <div>
            <strong>Estado:</strong> 
            {isEditing ? (
              <select
                value={formData.isActive !== undefined ? (formData.isActive ? 'true' : 'false') : (profile.isActive ? 'true' : 'false')}
                onChange={(e) => setFormData({ ...formData, isActive: e.target.value === 'true' })}
                style={{ marginLeft: '10px', padding: '5px', borderRadius: '4px', border: '1px solid #ddd' }}
              >
                <option value="true">Activo</option>
                <option value="false">Inactivo</option>
              </select>
            ) : (
              <span style={{ color: profile.isActive ? '#28a745' : '#dc3545', fontWeight: 'bold' }}>
                {profile.isActive ? 'Activo' : 'Inactivo'}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Datos de Contacto */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px' 
      }}>
        <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Datos de Contacto</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '15px' }}>
          <div>
            <strong>Email:</strong>
            {isEditing ? (
              <input
                type="email"
                value={formData.email || ''}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 80px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="email@ejemplo.com"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.email || 'N/A'}</span>
            )}
          </div>
          <div>
            <strong>Teléfono:</strong>
            {isEditing ? (
              <input
                type="tel"
                value={formData.phone || ''}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 80px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="+51 999 999 999"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.phone || 'N/A'}</span>
            )}
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <strong>Dirección:</strong>
            {isEditing ? (
              <textarea
                value={formData.address || ''}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 80px)', borderRadius: '4px', border: '1px solid #ddd', minHeight: '60px' }}
                placeholder="Dirección completa"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.address || 'N/A'}</span>
            )}
          </div>
        </div>
      </div>

      {/* Datos Operacionales */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px' 
      }}>
        <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Datos Operacionales</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '15px' }}>
          <div>
            <strong>Fecha de Inicio:</strong>
            {isEditing ? (
              <input
                type="date"
                value={formData.startDate || ''}
                onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', borderRadius: '4px', border: '1px solid #ddd' }}
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{formatearFecha(profile.startDate)}</span>
            )}
          </div>
          <div>
            <strong>Estado:</strong>
            {isEditing ? (
              <input
                type="text"
                value={formData.status || ''}
                onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 80px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="activo, suspendido, etc."
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.status || 'N/A'}</span>
            )}
          </div>
          <div>
            <strong>Tipo de Contrato:</strong>
            {isEditing ? (
              <input
                type="text"
                value={formData.contractType || ''}
                onChange={(e) => setFormData({ ...formData, contractType: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 100px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="Tipo de contrato"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.contractType || 'N/A'}</span>
            )}
          </div>
          <div>
            <strong>Tipo de Trabajo:</strong>
            {isEditing ? (
              <select
                value={formData.workType || ''}
                onChange={(e) => setFormData({ ...formData, workType: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', borderRadius: '4px', border: '1px solid #ddd' }}
              >
                <option value="">Seleccionar...</option>
                <option value="PART_TIME">Part-Time</option>
                <option value="FULL_TIME">Full-Time</option>
              </select>
            ) : (
              <span style={{ marginLeft: '10px' }}>
                {profile.workType === 'PART_TIME' ? 'Part-Time' : 
                 profile.workType === 'FULL_TIME' ? 'Full-Time' : 'N/A'}
              </span>
            )}
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <strong>Notas:</strong>
            {isEditing ? (
              <textarea
                value={formData.notes || ''}
                onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 80px)', borderRadius: '4px', border: '1px solid #ddd', minHeight: '100px' }}
                placeholder="Notas y observaciones sobre el scout"
              />
            ) : (
              <div style={{ marginLeft: '10px', whiteSpace: 'pre-wrap' }}>
                {profile.notes || 'N/A'}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Configuración */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px' 
      }}>
        <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Configuración</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '15px' }}>
          <div>
            <strong>Método de Pago:</strong>
            {isEditing ? (
              <input
                type="text"
                value={formData.paymentMethod || ''}
                onChange={(e) => setFormData({ ...formData, paymentMethod: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 130px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="Transferencia, efectivo, etc."
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.paymentMethod || 'N/A'}</span>
            )}
          </div>
          <div>
            <strong>Cuenta Bancaria:</strong>
            {isEditing ? (
              <input
                type="text"
                value={formData.bankAccount || ''}
                onChange={(e) => setFormData({ ...formData, bankAccount: e.target.value })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 130px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="Número de cuenta"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{profile.bankAccount || 'N/A'}</span>
            )}
          </div>
          <div>
            <strong>Tasa de Comisión:</strong>
            {isEditing ? (
              <input
                type="number"
                step="0.0001"
                min="0"
                max="1"
                value={formData.commissionRate !== undefined && formData.commissionRate !== null ? formData.commissionRate : ''}
                onChange={(e) => setFormData({ ...formData, commissionRate: e.target.value ? parseFloat(e.target.value) : null })}
                style={{ marginLeft: '10px', padding: '5px', width: 'calc(100% - 140px)', borderRadius: '4px', border: '1px solid #ddd' }}
                placeholder="0.0000"
              />
            ) : (
              <span style={{ marginLeft: '10px' }}>{formatearMonto(profile.commissionRate)}</span>
            )}
          </div>
        </div>
      </div>

      {/* Métricas Calculadas (Solo Lectura) */}
      <div style={{ 
        marginBottom: '20px', 
        padding: '15px', 
        backgroundColor: '#e7f3ff', 
        borderRadius: '8px',
        border: '1px solid #b3d9ff'
      }}>
        <h3 style={{ marginTop: 0, marginBottom: '15px' }}>Métricas Calculadas</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '15px' }}>
          <div>
            <strong>Total de Registros:</strong> {profile.totalRegistrations ?? 0}
          </div>
          <div>
            <strong>Registros Matcheados:</strong> {profile.matchedRegistrations ?? 0}
          </div>
          <div>
            <strong>Total de Drivers Afiliados:</strong> {profile.totalDriversAffiliated ?? 0}
          </div>
          <div>
            <strong>Último Registro:</strong> {formatearFecha(profile.lastRegistrationDate)}
          </div>
        </div>
        <div style={{ marginTop: '15px', fontSize: '14px', color: '#666', fontStyle: 'italic' }}>
          * Estas métricas se calculan automáticamente a partir de los datos de ingesta y son de solo lectura.
        </div>
      </div>

      {/* Información del Sistema */}
      <div style={{ 
        padding: '15px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '8px',
        fontSize: '14px',
        color: '#666'
      }}>
        <strong>Fecha de Creación:</strong> {formatearFecha(profile.createdAt)} | 
        <strong> Última Actualización:</strong> {formatearFecha(profile.lastUpdated)}
      </div>
    </div>
  );
};

export default ScoutProfile;

