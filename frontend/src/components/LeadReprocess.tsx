import React, { useState } from 'react';
import { api, LeadReprocessConfig, LeadProcessingResult } from '../services/api';

const LeadReprocess: React.FC = () => {
  const [config, setConfig] = useState<LeadReprocessConfig>({
    timeMarginDays: 3,
    matchByPhone: true,
    matchByName: true,
    matchThreshold: 0.5,
    nameSimilarityThreshold: 0.5,
    phoneSimilarityThreshold: 0.7,
    enableFuzzyMatching: true,
    minWordsMatch: 2,
    ignoreSecondLastName: false,
    reprocessScope: 'unmatched'
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<LeadProcessingResult | null>(null);
  const [lastUpdateTime, setLastUpdateTime] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const processingResult = await api.reprocessLeads(config);
      setResult(processingResult);
      setLastUpdateTime(new Date().toLocaleString('es-ES'));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al reprocesar leads');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {lastUpdateTime && (
        <div style={{
          backgroundColor: '#fff3cd',
          border: '2px solid #dc3545',
          borderRadius: '4px',
          padding: '10px 15px',
          marginBottom: '20px',
          textAlign: 'center'
        }}>
          <strong style={{ color: '#dc3545', fontSize: '16px' }}>
            Última Actualización: {lastUpdateTime}
          </strong>
        </div>
      )}

      {error && (
        <div className="error">
          Error: {error}
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ maxWidth: '100%', width: '100%', margin: '0 auto' }}>
        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label htmlFor="timeMarginDays">Margen de Tiempo (días)</label>
          <input
            id="timeMarginDays"
            type="number"
            min="1"
            max="30"
            value={config.timeMarginDays || 3}
            onChange={(e) => setConfig({ ...config, timeMarginDays: parseInt(e.target.value) || 3 })}
            style={{ width: '100%', padding: '8px' }}
          />
          <small>Días de diferencia permitidos entre lead_created_at y hire_date</small>
        </div>

        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label>
            <input
              type="checkbox"
              checked={config.matchByPhone || false}
              onChange={(e) => setConfig({ ...config, matchByPhone: e.target.checked })}
              style={{ marginRight: '8px' }}
            />
            Match por Teléfono
          </label>
        </div>

        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label>
            <input
              type="checkbox"
              checked={config.matchByName || false}
              onChange={(e) => setConfig({ ...config, matchByName: e.target.checked })}
              style={{ marginRight: '8px' }}
            />
            Match por Nombre
          </label>
        </div>

        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label htmlFor="matchThreshold">Umbral de Coincidencia (0.0 - 1.0)</label>
          <input
            id="matchThreshold"
            type="number"
            min="0"
            max="1"
            step="0.1"
            value={config.matchThreshold || 0.5}
            onChange={(e) => setConfig({ ...config, matchThreshold: parseFloat(e.target.value) || 0.5 })}
            style={{ width: '100%', padding: '8px' }}
          />
          <small>Score mínimo requerido para considerar un match (0.5 = al menos un campo, 1.0 = ambos campos)</small>
        </div>

        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label>
            <input
              type="checkbox"
              checked={config.enableFuzzyMatching !== false}
              onChange={(e) => setConfig({ ...config, enableFuzzyMatching: e.target.checked })}
              style={{ marginRight: '8px' }}
            />
            Matching Flexible (habilita similitud de nombres y teléfonos)
          </label>
        </div>

        {config.enableFuzzyMatching !== false && (
          <>
            <div className="form-group" style={{ marginBottom: '20px' }}>
              <label htmlFor="nameSimilarityThreshold">
                Umbral de Similitud de Nombres (0.0 - 1.0)
                <span style={{ marginLeft: '10px', fontSize: '0.9em', color: '#666' }}>
                  {config.nameSimilarityThreshold || 0.5}
                </span>
              </label>
              <input
                id="nameSimilarityThreshold"
                type="range"
                min="0"
                max="1"
                step="0.05"
                value={config.nameSimilarityThreshold || 0.5}
                onChange={(e) => setConfig({ ...config, nameSimilarityThreshold: parseFloat(e.target.value) })}
                style={{ width: '100%', padding: '8px' }}
              />
              <small>Umbral mínimo de similitud Jaccard para considerar match de nombres (0.5 = 50% de palabras comunes)</small>
            </div>

            <div className="form-group" style={{ marginBottom: '20px' }}>
              <label htmlFor="phoneSimilarityThreshold">
                Umbral de Similitud de Teléfonos (0.0 - 1.0)
                <span style={{ marginLeft: '10px', fontSize: '0.9em', color: '#666' }}>
                  {config.phoneSimilarityThreshold || 0.7}
                </span>
              </label>
              <input
                id="phoneSimilarityThreshold"
                type="range"
                min="0"
                max="1"
                step="0.05"
                value={config.phoneSimilarityThreshold || 0.7}
                onChange={(e) => setConfig({ ...config, phoneSimilarityThreshold: parseFloat(e.target.value) })}
                style={{ width: '100%', padding: '8px' }}
              />
              <small>Umbral mínimo de similitud para considerar match de teléfonos (0.7 = tolera 1-2 dígitos diferentes)</small>
            </div>

            <div className="form-group" style={{ marginBottom: '20px' }}>
              <label htmlFor="minWordsMatch">Mínimo de Palabras Coincidentes</label>
              <input
                id="minWordsMatch"
                type="number"
                min="1"
                max="5"
                value={config.minWordsMatch || 2}
                onChange={(e) => setConfig({ ...config, minWordsMatch: parseInt(e.target.value) || 2 })}
                style={{ width: '100%', padding: '8px' }}
              />
              <small>Número mínimo de palabras que deben coincidir entre nombres (default: 2)</small>
            </div>

            <div className="form-group" style={{ marginBottom: '20px' }}>
              <label>
                <input
                  type="checkbox"
                  checked={config.ignoreSecondLastName || false}
                  onChange={(e) => setConfig({ ...config, ignoreSecondLastName: e.target.checked })}
                  style={{ marginRight: '8px' }}
                />
                Ignorar Segundo Apellido
              </label>
              <small style={{ display: 'block', marginTop: '5px', color: '#666' }}>
                Si está marcado, solo compara nombre y primer apellido (ignora segundo apellido)
              </small>
            </div>
          </>
        )}

        <div className="form-group" style={{ marginBottom: '20px' }}>
          <label htmlFor="reprocessScope">Ámbito de Reprocesamiento</label>
          <select
            id="reprocessScope"
            value={config.reprocessScope || 'unmatched'}
            onChange={(e) => setConfig({ ...config, reprocessScope: e.target.value as 'all' | 'unmatched' | 'discarded' })}
            style={{ width: '100%', padding: '8px' }}
          >
            <option value="unmatched">Solo leads sin match</option>
            <option value="all">Todos los leads</option>
            <option value="discarded">Solo leads descartados</option>
          </select>
          <small>Selecciona qué leads reprocesar</small>
        </div>

        <button
          type="submit"
          className="btn"
          disabled={loading || (!config.matchByPhone && !config.matchByName)}
          style={{ width: '100%', padding: '12px' }}
        >
          {loading ? 'Reprocesando...' : 'Reprocesar Leads'}
        </button>
      </form>

      {result && (
        <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#f0f0f0', borderRadius: '8px' }}>
          <h3>Resultado del Reprocesamiento</h3>
          <p><strong>Total Leads:</strong> {result.totalLeads}</p>
          <p><strong>Matcheados:</strong> {result.matchedCount}</p>
          <p><strong>Sin Match:</strong> {result.unmatchedCount}</p>
          <p><strong>Descartados:</strong> {result.discardedCount}</p>
          <p><strong>Última Actualización:</strong> {new Date(result.lastUpdated).toLocaleString('es-ES')}</p>
          <p><strong>Mensaje:</strong> {result.message}</p>
        </div>
      )}
    </div>
  );
};

export default LeadReprocess;

