import React, { useState } from 'react';
import { api } from '../services/api';

interface ScoutRegistrationUploadResult {
  total: number;
  matched: number;
  unmatched: number;
  message: string;
  timestamp: string;
}

const ScoutRegistrationUpload: React.FC<{ onUploadComplete?: () => void }> = ({ onUploadComplete }) => {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<ScoutRegistrationUploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdateTime, setLastUpdateTime] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setResult(null);
      setError(null);
    }
  };

  const handleUpload = async () => {
    if (!file) {
      setError('Por favor selecciona un archivo CSV');
      return;
    }

    setUploading(true);
    setError(null);
    setResult(null);

    try {
      const uploadResult = await api.uploadScoutRegistrations(file);
      setResult(uploadResult);
      setLastUpdateTime(new Date().toLocaleString('es-ES'));
      if (onUploadComplete) {
        onUploadComplete();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al subir el archivo');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="card">
      {lastUpdateTime && (
        <div className="update-banner">
          <strong>Última Actualización: {lastUpdateTime}</strong>
        </div>
      )}
      
      <h2 className="card-title">Subir Archivo CSV de Registros de Scouts</h2>
      
      <div style={{ marginBottom: '16px' }}>
        <input
          type="file"
          accept=".csv"
          onChange={handleFileChange}
          disabled={uploading}
          className="file-input"
        />
      </div>

      <button
        onClick={handleUpload}
        disabled={!file || uploading}
        className="btn"
        style={{ marginBottom: '16px' }}
      >
        {uploading ? 'Subiendo...' : 'Subir CSV'}
      </button>

      {error && (
        <div className="error">
          Error: {error}
        </div>
      )}

      {result && (
        <div className="success-message">
          <h3 style={{ marginBottom: '12px', fontSize: '16px', fontWeight: '600' }}>Resultado del Procesamiento</h3>
          <div className="result-grid">
            <div><strong>Total Registros:</strong> {result.total}</div>
            <div><strong>Matcheados:</strong> {result.matched}</div>
            <div><strong>Sin Match:</strong> {result.unmatched}</div>
            <div><strong>Mensaje:</strong> {result.message}</div>
            <div><strong>Timestamp:</strong> {new Date(result.timestamp).toLocaleString('es-ES')}</div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ScoutRegistrationUpload;

