// Detectar automáticamente el entorno
const getApiBaseUrl = (): string => {
  // Si hay variable de entorno, usarla
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  
  // Detectar si estamos en localhost o en producción
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    
    // Si estamos en localhost o 127.0.0.1, usar localhost
    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '10.10.5.17') {
      return 'http://localhost:8090/api';
    }
    
    // Si estamos en la IP de producción, usar esa IP
    if (hostname === '5.161.86.63') {
      return 'http://5.161.86.63:8090/api';
    }
  }
  
  // Por defecto, usar localhost para desarrollo
  return 'http://localhost:8090/api';
};

const API_BASE_URL = getApiBaseUrl();

const obtenerToken = (): string | null => {
  return localStorage.getItem('authToken');
};

const obtenerHeaders = (includeAuth: boolean = true, isFormData: boolean = false): HeadersInit => {
  const headers: HeadersInit = {};
  
  if (!isFormData) {
    headers['Content-Type'] = 'application/json';
  }
  
  if (includeAuth) {
    const token = obtenerToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  
  return headers;
};

const manejarRespuesta = async (response: Response): Promise<Response> => {
  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
    window.location.href = '/';
    throw new Error('Sesión expirada o sin permisos. Por favor, inicia sesión nuevamente.');
  }
  return response;
};

export interface DayActivity {
  dayNumber: number;
  activityDate: string;
  tripsDay: number;
  onlineTime: number;
  connectedFlag: boolean;
}

export interface MilestoneTripDetail {
  date: string;
  tripCount: number;
  dayFromHireDate: number;
}

export interface MilestoneInstance {
  id: number;
  driverId: string;
  parkId: string;
  milestoneType: number;
  periodDays: number;
  fulfillmentDate: string;
  calculationDate: string;
  tripCount: number;
  tripDetails?: MilestoneTripDetail[];
}

export interface MilestonePaymentViewItem {
  driverId: string;
  driverName: string;
  driverPhone: string | null;
  hireDate: string;
  milestoneInstanceId: number;
  milestoneType: number;
  periodDays: number;
  fulfillmentDate: string;
  tripCount: number;
  yangoTransactionId: number | null;
  amountYango: number | null;
  yangoPaymentDate: string | null;
  hasPayment: boolean;
  paymentStatus: 'paid' | 'missing' | 'pending';
  hasLeadMatch?: boolean;
}

export interface YangoRematchProgress {
  status: 'pending' | 'running' | 'completed' | 'failed';
  totalTransactions: number;
  processedTransactions: number;
  progressPercentage: number;
  error?: string;
}

export interface YangoPaymentConfig {
  id: number;
  milestoneType: number;
  amountYango: number;
  periodDays: number;
  isActive: boolean;
  createdAt: string;
  lastUpdated: string;
}

export interface YangoTransactionMatched {
  id: number;
  transactionDate: string;
  milestoneType?: number;
  amountYango: number;
  milestoneInstanceId?: number | null;
  milestoneInstance?: MilestoneInstance;
}

export interface DriverOnboarding {
  driverId: string;
  parkId: string;
  channel: string | null;
  fullName?: string;
  phone?: string;
  startDate: string;
  licenseNumber?: string;
  status14d: string;
  totalTrips14d: number;
  totalOnlineTime14d: number;
  sumWorkTimeSeconds?: number;
  hasHistoricalConnection?: boolean;
  leadCreatedAt?: string;
  matchScore?: number;
  scoutId?: string;
  scoutName?: string;
  scoutRegistrationDate?: string;
  scoutMatchScore?: number;
  hasScoutRegistration?: boolean;
  diasRegistroAConexion?: number;
  diasConexionAViaje?: number;
  diasPrimerViajeA25Viajes?: number;
  primeraConexionDate?: string;
  primerViajeDate?: string;
  diasActivos?: number;
  diasConectados?: number;
  tasaConversionConexion?: number;
  tieneLead?: boolean;
  tieneScout?: boolean;
  matchScoreBajo?: boolean;
  matchManual?: boolean;
  tieneInconsistencias?: boolean;
  diaSemanaRegistro?: string;
  semanaMesRegistro?: number;
  semanaISORegistro?: string;
  days: DayActivity[];
  milestones14d?: MilestoneInstance[];
  milestones7d?: MilestoneInstance[];
  yangoTransactions14d?: YangoTransactionMatched[];
}

export interface LeadMatch {
  externalId: string;
  driverId: string | null;
  leadCreatedAt: string;
  hireDate: string | null;
  dateMatch: boolean;
  matchScore: number;
  isManual: boolean;
  isDiscarded: boolean;
  driverFullName?: string;
  driverPhone?: string;
  leadPhone?: string;
  leadFirstName?: string;
  leadLastName?: string;
}

export interface LeadCabinetDTO {
  externalId: string;
  driverId: string | null;
  leadCreatedAt: string;
  hireDate: string | null;
  dateMatch: boolean;
  matchScore: number;
  isManual: boolean;
  isDiscarded: boolean;
  driverFullName?: string;
  driverPhone?: string;
  leadPhone?: string;
  leadFirstName?: string;
  leadLastName?: string;
  driverStatus: string | null;
  totalTrips14d: number;
  sumWorkTimeSeconds?: number | null;
  milestones?: MilestoneInstance[];
  yangoTransactions14d?: YangoTransactionMatched[];
  scoutRegistrationId?: number | null;
  scoutMatchScore?: number | null;
  scoutName?: string | null;
  scoutId?: string | null;
  scoutRegistrationDate?: string | null;
}

export interface ReconciliationSummary {
  period: string;
  periodType: 'day' | 'week';
  totals: {
    registrados: number;
    porCabinet: number;
    porOtrosMedios: number;
    conectados: number;
    conViajes7d: number;
    conViajes14d: number;
    conMilestone1: number;
    conMilestone5: number;
    conMilestone25: number;
    conPagoYango: number;
  };
  byScout: Array<{
    scoutId: string;
    scoutName: string;
    count: number;
    registrados: number;
    conectados: number;
    conViajes: number;
    conMilestones: number;
    conPago: number;
  }>;
  conversionMetrics: {
    tasaConexion: number;
    tasaActivacion: number;
    tasaMilestone1: number;
    tasaMilestone5: number;
    tasaMilestone25: number;
    tasaPagoYango: number;
  };
  inconsistencies: {
    sinMatch: number;
    sinPago: number;
    pagoSinMilestone: number;
    milestoneSinPago: number;
  };
  lastUpdated: string;
}

export interface DriverByDate {
  driver_id: string;
  full_name: string;
  phone: string;
  hire_date: string;
  license_number: string;
}

export interface ScoutRegistrationReconciliationDTO {
  id: number;
  scoutId: string;
  scoutName: string;
  registrationDate: string;
  driverLicense: string | null;
  driverName: string;
  driverPhone: string | null;
  acquisitionMedium: string | null;
  driverId: string | null;
  matchScore: number | null;
  isMatched: boolean;
}

export interface LeadProcessingResult {
  totalLeads: number;
  matchedCount: number;
  unmatchedCount: number;
  discardedCount: number;
  lastUpdated: string;
  message: string;
  dataDateFrom?: string | null;
  dataDateTo?: string | null;
}

export interface UploadMetadata {
  lastUploadDate: string;
  dataDateFrom: string | null;
  dataDateTo: string | null;
  totalRecords: number;
  matchedCount: number;
  unmatchedCount: number;
  sourceDescription: {
    title: string;
    source: string;
    url?: string | null;
    details: string;
  };
}

export interface LeadReprocessConfig {
  timeMarginDays?: number;
  matchByPhone?: boolean;
  matchByName?: boolean;
  matchThreshold?: number;
  nameSimilarityThreshold?: number;
  phoneSimilarityThreshold?: number;
  enableFuzzyMatching?: boolean;
  minWordsMatch?: number;
  ignoreSecondLastName?: boolean;
  reprocessScope?: 'all' | 'unmatched' | 'discarded';
}

export interface ProcessingStatus {
  lastUpdated: string;
}

export interface OnboardingFilters {
  parkId?: string;
  startDateFrom?: string;
  startDateTo?: string;
  channel?: string;
  weekISO?: string;
  page?: number;
  size?: number;
}

export interface PaginatedResponse<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
  hasMore: boolean;
  totalPages: number;
}

export interface EvolutionMetrics {
  period: string;
  totalDrivers: number;
  soloRegistro: number;
  conectoSinViajes: number;
  activoConViajes: number;
  tasaRegistroAConexion: number;
  tasaConexionAViaje: number;
  tasaAlcanzo1Viaje: number;
  tasaAlcanzo5Viajes: number;
  tasaAlcanzo25Viajes: number;
  promedioDiasRegistroAConexion: number;
  promedioDiasConexionAViaje: number;
  promedioDiasPrimerViajeA25Viajes: number;
  totalViajes: number;
  promedioViajesPorActivo: number;
}

export interface Scout {
  scoutId: string;
  scoutName: string;
  driverId?: string | null;
  isActive: boolean;
  createdAt: string;
  lastUpdated: string;
}

export interface ScoutProfile {
  scoutId: string;
  scoutName: string;
  driverId: string | null;
  isActive: boolean;
  email: string | null;
  phone: string | null;
  address: string | null;
  notes: string | null;
  startDate: string | null;
  status: string | null;
  contractType: string | null;
  workType: string | null;
  paymentMethod: string | null;
  bankAccount: string | null;
  commissionRate: number | null;
  createdAt: string;
  lastUpdated: string;
  totalRegistrations: number | null;
  matchedRegistrations: number | null;
  totalDriversAffiliated: number | null;
  lastRegistrationDate: string | null;
}

export interface ScoutProfileUpdate {
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  notes?: string | null;
  startDate?: string | null;
  status?: string | null;
  contractType?: string | null;
  workType?: string | null;
  paymentMethod?: string | null;
  bankAccount?: string | null;
  commissionRate?: number | null;
  isActive?: boolean;
}

export interface YangoTransaction {
  id: number;
  transactionDate: string;
  scoutId: string;
  driverId?: string | null;
  driverNameFromComment?: string;
  milestoneType?: number;
  amountYango: number;
  amountIndicator: number;
  comment?: string;
  categoryId?: string;
  category?: string;
  document?: string;
  initiatedBy?: string;
  milestoneInstanceId?: number | null;
  matchConfidence?: number;
  isMatched: boolean;
  createdAt: string;
  lastUpdated: string;
}

export interface YangoTransactionGroup {
  driverNameFromComment: string | null;
  transactions: YangoTransaction[];
  count: number;
}

export interface YangoTransactionFilters {
  scoutId?: string;
  dateFrom?: string;
  dateTo?: string;
  milestoneType?: number;
  isMatched?: boolean;
}

export interface YangoTransactionProcessingResult {
  totalTransactions: number;
  matchedCount: number;
  unmatchedCount: number;
  lastUpdated: string;
  message: string;
  dataDateFrom?: string | null;
  dataDateTo?: string | null;
}

export interface ScoutPaymentConfig {
  id: number;
  milestoneType: number;
  amountScout: number;
  paymentDays: number;
  isActive: boolean;
  minRegistrationsRequired?: number;
  minConnectionSeconds?: number;
  createdAt: string;
  lastUpdated: string;
}

export interface ScoutPaymentInstance {
  id: number;
  scoutId: string;
  scoutName?: string;
  driverId: string;
  driverName?: string;
  milestoneType: number;
  milestoneInstanceId?: number;
  amount: number;
  registrationDate: string;
  milestoneFulfillmentDate: string;
  eligibilityVerified: boolean;
  eligibilityReason?: string;
  status: 'pending' | 'paid' | 'cancelled';
  paymentId?: number;
  createdAt: string;
  lastUpdated: string;
}

export interface ScoutPayment {
  id: number;
  scoutId: string;
  paymentPeriodStart: string;
  paymentPeriodEnd: string;
  totalAmount: number;
  transactionsCount: number;
  status: 'pending' | 'paid' | 'cancelled';
  paidAt?: string | null;
  createdAt: string;
  lastUpdated: string;
}

export interface ScoutLiquidationCalculation {
  scoutId: string;
  fechaInicio: string;
  fechaFin: string;
  totalAmount: number;
  transactionsCount: number;
  milestoneCounts: Record<number, number>;
}

export interface DriverWeeklyInfo {
  driverId: string;
  driverName: string;
  driverPhone: string | null;
  registrationDate: string;
  hireDate: string;
  hasConnection: boolean;
  reachedMilestone1: boolean;
  reachedMilestone5: boolean;
  reachedMilestone25: boolean;
  milestone1Date: string | null;
  milestone5Date: string | null;
  milestone25Date: string | null;
  scoutReached8Registrations: boolean;
  isEligible: boolean;
  eligibilityReason: string;
  amount: number;
  status: 'pending' | 'paid' | 'cancelled' | 'partial_paid' | 'all_paid';
  instanceId: number | null;
  
  // Campos individuales por milestone
  milestone1Status?: string | null;
  milestone1Amount?: number;
  milestone1InstanceId?: number | null;
  
  milestone5Status?: string | null;
  milestone5Amount?: number;
  milestone5InstanceId?: number | null;
  
  milestone25Status?: string | null;
  milestone25Amount?: number;
  milestone25InstanceId?: number | null;
  
  // Estados de expiración para milestones no alcanzados
  milestone1ExpirationStatus?: 'in_progress' | 'expired' | null;
  milestone5ExpirationStatus?: 'in_progress' | 'expired' | null;
  milestone25ExpirationStatus?: 'in_progress' | 'expired' | null;
}

export interface ScoutWeeklyView {
  scoutId: string;
  scoutName: string;
  drivers: DriverWeeklyInfo[];
}

export const api = {
  async getOnboarding14d(filters: OnboardingFilters): Promise<PaginatedResponse<DriverOnboarding>> {
    const params = new URLSearchParams();
    
    if (filters.parkId) {
      params.append('parkId', filters.parkId);
    }
    if (filters.startDateFrom) {
      params.append('startDateFrom', filters.startDateFrom);
    }
    if (filters.startDateTo) {
      params.append('startDateTo', filters.startDateTo);
    }
    if (filters.channel) {
      params.append('channel', filters.channel);
    }
    if (filters.weekISO) {
      params.append('weekISO', filters.weekISO);
    }
    if (filters.page !== undefined) {
      params.append('page', filters.page.toString());
    }
    if (filters.size !== undefined) {
      params.append('size', filters.size.toString());
    }
    
    const url = `${API_BASE_URL}/drivers/onboarding-14d${params.toString() ? '?' + params.toString() : ''}`;
    console.log('Frontend - Enviando petición con weekISO:', filters.weekISO, 'page:', filters.page, 'size:', filters.size, 'URL:', url);
    
    const response = await fetch(url, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: 'Error desconocido' }));
      throw new Error(error.error || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async uploadLeadsCSV(file: File): Promise<LeadProcessingResult> {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch(`${API_BASE_URL}/leads/upload`, {
      method: 'POST',
      headers: obtenerHeaders(true, true),
      body: formData
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getUnmatchedLeads(): Promise<LeadMatch[]> {
    const response = await fetch(`${API_BASE_URL}/leads/unmatched`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async assignManualMatch(externalId: string, driverId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/leads/manual-match`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ externalId, driverId })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async discardLead(externalId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/leads/discard`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ externalId })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async reprocessLeads(config: LeadReprocessConfig): Promise<LeadProcessingResult> {
    const response = await fetch(`${API_BASE_URL}/leads/reprocess`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify(config)
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getProcessingStatus(): Promise<ProcessingStatus> {
    const response = await fetch(`${API_BASE_URL}/leads/processing-status`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getDriversByDate(date: string, parkId?: string): Promise<DriverByDate[]> {
    const params = new URLSearchParams();
    params.append('date', date);
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const response = await fetch(`${API_BASE_URL}/leads/drivers-by-date?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getLeadsCabinet(filters: {
    dateFrom?: string;
    dateTo?: string;
    weekISO?: string;
    matchStatus?: 'matched' | 'unmatched' | 'all';
    driverStatus?: 'solo_registro' | 'conecto_sin_viajes' | 'activo_con_viajes' | 'all';
    milestoneType?: number;
    milestonePeriod?: number;
    search?: string;
    includeDiscarded?: boolean;
  }): Promise<LeadCabinetDTO[]> {
    const params = new URLSearchParams();
    
    if (filters.weekISO) {
      params.append('weekISO', filters.weekISO);
    } else {
      if (filters.dateFrom) {
        params.append('dateFrom', filters.dateFrom);
      }
      if (filters.dateTo) {
        params.append('dateTo', filters.dateTo);
      }
    }
    if (filters.matchStatus) {
      params.append('matchStatus', filters.matchStatus);
    }
    if (filters.driverStatus) {
      params.append('driverStatus', filters.driverStatus);
    }
    if (filters.milestoneType !== undefined) {
      params.append('milestoneType', filters.milestoneType.toString());
    }
    if (filters.milestonePeriod !== undefined) {
      params.append('milestonePeriod', filters.milestonePeriod.toString());
    }
    if (filters.search) {
      params.append('search', filters.search);
    }
    if (filters.includeDiscarded !== undefined) {
      params.append('includeDiscarded', filters.includeDiscarded.toString());
    }
    
    const response = await fetch(`${API_BASE_URL}/leads/cabinet?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getDriverMilestones(driverId: string): Promise<MilestoneInstance[]> {
    const response = await fetch(`${API_BASE_URL}/milestones/driver/${driverId}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestoneDetail(driverId: string, milestoneType: number, periodDays: number): Promise<MilestoneInstance | null> {
    const response = await fetch(`${API_BASE_URL}/milestones/driver/${driverId}/instance/${milestoneType}/${periodDays}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      if (response.status === 404) {
        return null;
      }
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonesByPeriod(periodDays: number, parkId?: string, milestoneType?: number, startDateFrom?: string, startDateTo?: string): Promise<{ totals: { milestone1: number; milestone5: number; milestone25: number }; milestones: MilestoneInstance[]; periodDays: number }> {
    const params = new URLSearchParams();
    if (parkId) {
      params.append('parkId', parkId);
    }
    if (milestoneType !== undefined) {
      params.append('milestoneType', milestoneType.toString());
    }
    if (startDateFrom) {
      params.append('hireDateFrom', startDateFrom);
    }
    if (startDateTo) {
      params.append('hireDateTo', startDateTo);
    }
    
    const url = `${API_BASE_URL}/milestones/period/${periodDays}${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonesBatch(driverIds: string[], periodDays: number = 14): Promise<Record<string, MilestoneInstance[]>> {
    if (!driverIds || driverIds.length === 0) {
      return {};
    }
    
    try {
      const response = await fetch(`${API_BASE_URL}/milestones/batch?periodDays=${periodDays}`, {
        method: 'POST',
        headers: obtenerHeaders(),
        body: JSON.stringify(driverIds)
      });
      
      await manejarRespuesta(response);
      
      if (!response.ok) {
        if (response.status === 404 || response.status === 500) {
          return {};
        }
        const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
        throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
      }
      
      return response.json();
    } catch (err) {
      console.warn('Error al obtener milestones batch:', err);
      return {};
    }
  },

  async calculateMilestones(parkId?: string, periodDays?: number, milestoneType?: number, hireDateFrom?: string, hireDateTo?: string): Promise<{ success: boolean; message: string; jobId?: string; periodDays?: number; milestoneType?: number }> {
    const params = new URLSearchParams();
    
    if (parkId) {
      params.append('parkId', parkId);
    }
    if (periodDays !== undefined) {
      params.append('periodDays', periodDays.toString());
    }
    if (milestoneType !== undefined) {
      params.append('milestoneType', milestoneType.toString());
    }
    if (hireDateFrom) {
      params.append('hireDateFrom', hireDateFrom);
    }
    if (hireDateTo) {
      params.append('hireDateTo', hireDateTo);
    }
    
    const url = `${API_BASE_URL}/milestones/calculate${params.toString() ? '?' + params.toString() : ''}`;
    
    const response = await fetch(url, {
      method: 'POST',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestoneProgress(jobId: string): Promise<{ status: string; totalDrivers: number; processedDrivers: number; milestone1Count: number; milestone5Count: number; milestone25Count: number; periodType?: string; error?: string; progressPercentage: number } | null> {
    const response = await fetch(`${API_BASE_URL}/milestones/progress/${jobId}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      if (response.status === 404) {
        return null;
      }
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async clearMilestoneProgress(jobId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/milestones/progress/${jobId}`, {
      method: 'DELETE',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async getDriversByIds(driverIds: string[], parkId?: string): Promise<DriverOnboarding[]> {
    if (!driverIds || driverIds.length === 0) {
      return [];
    }

    const params = new URLSearchParams();
    if (parkId) {
      params.append('parkId', parkId);
    }

    const url = `${API_BASE_URL}/drivers/by-ids${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify(driverIds)
    });

    await manejarRespuesta(response);

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }

    return response.json();
  },

  async uploadYangoTransactions(file: File): Promise<YangoTransactionProcessingResult> {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const response = await fetch(`${API_BASE_URL}/yango-transactions/upload`, {
        method: 'POST',
        headers: obtenerHeaders(true, true),
        body: formData
      });
      
      await manejarRespuesta(response);
      
      if (!response.ok) {
        let errorMessage = `Error ${response.status}: ${response.statusText}`;
        try {
          const error = await response.json();
          errorMessage = error.message || error.error || errorMessage;
        } catch (e) {
          const text = await response.text().catch(() => '');
          if (text) {
            errorMessage = text;
          }
        }
        throw new Error(errorMessage);
      }
      
      const result = await response.json();
      if (result.data) {
        return result.data;
      }
      return result;
    } catch (err) {
      if (err instanceof Error) {
        throw err;
      }
      throw new Error('Error desconocido al subir el archivo');
    }
  },

  async getYangoTransactions(filters?: YangoTransactionFilters): Promise<YangoTransaction[]> {
    const params = new URLSearchParams();
    
    if (filters?.scoutId) {
      params.append('scoutId', filters.scoutId);
    }
    if (filters?.dateFrom) {
      params.append('dateFrom', filters.dateFrom);
    }
    if (filters?.dateTo) {
      params.append('dateTo', filters.dateTo);
    }
    if (filters?.milestoneType !== undefined) {
      params.append('milestoneType', filters.milestoneType.toString());
    }
    if (filters?.isMatched !== undefined) {
      params.append('isMatched', filters.isMatched.toString());
    }
    
    const url = `${API_BASE_URL}/yango-transactions${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getUnmatchedYangoTransactions(): Promise<YangoTransactionGroup[]> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/unmatched`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data || [];
  },

  async reprocessUnmatchedYangoTransactions(): Promise<{
    totalTransactions: number;
    matchedCount: number;
    unmatchedCount: number;
    message: string;
  }> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/reprocess`, {
      method: 'POST',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async cleanupYangoDuplicates(): Promise<{
    duplicateGroups: number;
    totalDuplicates: number;
    deleted: number;
    kept: number;
  }> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/cleanup-duplicates`, {
      method: 'POST',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async assignManualTransactionMatch(transactionId: number, driverId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/${transactionId}/match`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ driverId })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async assignBatchTransactionMatch(
    transactionIds: number[], 
    driverId: string, 
    milestoneInstanceIds?: number[]
  ): Promise<{ matchedCount: number }> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/batch-match`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ 
        transactionIds, 
        driverId,
        milestoneInstanceIds: milestoneInstanceIds || undefined
      })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return { matchedCount: result.matchedCount || 0 };
  },

  async getYangoDriversByDate(date: string, parkId?: string): Promise<DriverByDate[]> {
    const params = new URLSearchParams();
    params.append('date', date);
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const response = await fetch(`${API_BASE_URL}/yango-transactions/drivers-by-date?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScouts(): Promise<Scout[]> {
    const response = await fetch(`${API_BASE_URL}/scouts`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data || result;
  },

  async createScout(nombre: string): Promise<Scout> {
    const response = await fetch(`${API_BASE_URL}/scouts`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ nombre })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async updateScout(scoutId: string, nombre?: string, driverId?: string, isActive?: boolean): Promise<Scout> {
    const body: any = {};
    if (nombre !== undefined) body.nombre = nombre;
    if (driverId !== undefined) body.driverId = driverId;
    if (isActive !== undefined) body.isActive = isActive;
    
    const response = await fetch(`${API_BASE_URL}/scouts/${scoutId}`, {
      method: 'PUT',
      headers: obtenerHeaders(),
      body: JSON.stringify(body)
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutProfile(scoutId: string): Promise<ScoutProfile> {
    const response = await fetch(`${API_BASE_URL}/scouts/${scoutId}/profile`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async updateScoutProfile(scoutId: string, profile: ScoutProfileUpdate): Promise<ScoutProfile> {
    const response = await fetch(`${API_BASE_URL}/scouts/${scoutId}/profile`, {
      method: 'PUT',
      headers: obtenerHeaders(),
      body: JSON.stringify(profile)
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutDrivers(scoutId: string): Promise<DriverByDate[]> {
    const response = await fetch(`${API_BASE_URL}/scouts/${scoutId}/drivers`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutPaymentConfig(): Promise<ScoutPaymentConfig[]> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/config`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data || result;
  },

  async updateScoutPaymentConfig(id: number, amountScout?: number, paymentDays?: number, isActive?: boolean, minRegistrationsRequired?: number, minConnectionSeconds?: number): Promise<ScoutPaymentConfig> {
    const body: any = {};
    if (amountScout !== undefined) body.amountScout = amountScout;
    if (paymentDays !== undefined) body.paymentDays = paymentDays;
    if (isActive !== undefined) body.isActive = isActive;
    if (minRegistrationsRequired !== undefined) body.minRegistrationsRequired = minRegistrationsRequired;
    if (minConnectionSeconds !== undefined) body.minConnectionSeconds = minConnectionSeconds;
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/config/${id}`, {
      method: 'PUT',
      headers: obtenerHeaders(),
      body: JSON.stringify(body)
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async calculateScoutLiquidation(scoutId: string, fechaInicio: string, fechaFin: string): Promise<ScoutLiquidationCalculation> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/calculate`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ scoutId, fechaInicio, fechaFin })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async generateScoutPayment(scoutId: string, fechaInicio: string, fechaFin: string): Promise<ScoutPayment> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/generate`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ scoutId, fechaInicio, fechaFin })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutPayments(scoutId: string, status?: string): Promise<ScoutPayment[]> {
    const params = new URLSearchParams();
    params.append('scoutId', scoutId);
    if (status) {
      params.append('status', status);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-payments?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async markScoutPaymentAsPaid(paymentId: number): Promise<ScoutPayment> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/${paymentId}/mark-paid`, {
      method: 'POST',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutPaymentInstances(scoutId: string, fechaDesde?: string, fechaHasta?: string): Promise<ScoutPaymentInstance[]> {
    const params = new URLSearchParams();
    params.append('scoutId', scoutId);
    if (fechaDesde) {
      params.append('fechaDesde', fechaDesde);
    }
    if (fechaHasta) {
      params.append('fechaHasta', fechaHasta);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/pending?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async calculateScoutPaymentInstances(scoutId: string, fechaDesde?: string, fechaHasta?: string): Promise<ScoutPaymentInstance[]> {
    const body: any = { scoutId };
    if (fechaDesde) body.fechaDesde = fechaDesde;
    if (fechaHasta) body.fechaHasta = fechaHasta;
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/calculate`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify(body)
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async payScoutPaymentInstances(scoutId: string, instanceIds: number[]): Promise<ScoutPayment> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/pay`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ scoutId, instanceIds })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async payAllScoutPaymentInstances(scoutId: string): Promise<ScoutPayment> {
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/pay-all`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ scoutId })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutWeeklyPaymentView(weekISO: string, scoutId?: string): Promise<ScoutWeeklyView[]> {
    const params = new URLSearchParams();
    params.append('weekISO', weekISO);
    if (scoutId) {
      params.append('scoutId', scoutId);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/weekly-view?${params.toString()}`, {
      method: 'GET',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutDailyPaymentView(fecha: string, scoutId?: string): Promise<{ data: ScoutWeeklyView[]; fechaUsada: string; hizoFallback: boolean }> {
    const params = new URLSearchParams();
    params.append('fecha', fecha);
    if (scoutId) {
      params.append('scoutId', scoutId);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/daily-view?${params.toString()}`, {
      method: 'GET',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return {
      data: result.data,
      fechaUsada: result.fechaUsada || fecha,
      hizoFallback: result.hizoFallback || false
    };
  },

  async getScoutHistoricalPaymentView(meses: number, offset: number, limit: number, scoutId?: string): Promise<{ data: ScoutWeeklyView[]; total: number; hasMore: boolean }> {
    const params = new URLSearchParams();
    params.append('meses', meses.toString());
    params.append('offset', offset.toString());
    params.append('limit', limit.toString());
    if (scoutId) {
      params.append('scoutId', scoutId);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-payments/instances/historical-view?${params.toString()}`, {
      method: 'GET',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return {
      data: result.data,
      total: result.total || 0,
      hasMore: result.hasMore || false
    };
  },

  async uploadScoutRegistrations(file: File): Promise<any> {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch(`${API_BASE_URL}/scout-registrations/upload`, {
      method: 'POST',
      headers: obtenerHeaders(true, true),
      body: formData
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutAffiliationControl(filters: {
    scoutId?: string;
    weekISO?: string;
    fechaInicio?: string;
    fechaFin?: string;
    milestoneType?: number;
    isMatched?: boolean;
    hasYangoPayment?: boolean;
    acquisitionMedium?: string;
    driverName?: string;
    driverPhone?: string;
    amountMin?: number;
    amountMax?: number;
  }): Promise<{ data: any[]; count: number }> {
    const params = new URLSearchParams();
    if (filters.scoutId) {
      params.append('scoutId', filters.scoutId);
    }
    if (filters.weekISO) {
      params.append('weekISO', filters.weekISO);
    }
    if (filters.fechaInicio) {
      params.append('fechaInicio', filters.fechaInicio);
    }
    if (filters.fechaFin) {
      params.append('fechaFin', filters.fechaFin);
    }
    if (filters.milestoneType !== undefined) {
      params.append('milestoneType', filters.milestoneType.toString());
    }
    if (filters.isMatched !== undefined) {
      params.append('isMatched', filters.isMatched.toString());
    }
    if (filters.hasYangoPayment !== undefined) {
      params.append('hasYangoPayment', filters.hasYangoPayment.toString());
    }
    if (filters.acquisitionMedium) {
      params.append('acquisitionMedium', filters.acquisitionMedium);
    }
    if (filters.driverName) {
      params.append('driverName', filters.driverName);
    }
    if (filters.driverPhone) {
      params.append('driverPhone', filters.driverPhone);
    }
    if (filters.amountMin !== undefined) {
      params.append('amountMin', filters.amountMin.toString());
    }
    if (filters.amountMax !== undefined) {
      params.append('amountMax', filters.amountMax.toString());
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-registrations/control?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return {
      data: result.data || [],
      count: result.count || 0
    };
  },

  async getScoutRegistrationsByScout(scoutId: string, fechaInicio?: string, fechaFin?: string): Promise<any[]> {
    const params = new URLSearchParams();
    if (fechaInicio) {
      params.append('fechaInicio', fechaInicio);
    }
    if (fechaFin) {
      params.append('fechaFin', fechaFin);
    }
    
    const response = await fetch(`${API_BASE_URL}/scout-registrations/by-scout/${scoutId}?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data;
  },

  async getScoutSuggestionsForLead(externalId: string): Promise<any[]> {
    const response = await fetch(`${API_BASE_URL}/leads/${externalId}/scout-suggestions`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const result = await response.json();
    return result.data || [];
  },

  async assignScoutToLead(externalId: string, scoutRegistrationId: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/leads/${externalId}/assign-scout`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ scoutRegistrationId }),
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async getReconciliationSummary(filters: {
    periodType?: 'day' | 'week';
    dateFrom?: string;
    dateTo?: string;
    weekISO?: string;
    weekISOs?: string[];
    parkId?: string;
    scoutId?: string;
    channel?: string;
  }): Promise<ReconciliationSummary[]> {
    const params = new URLSearchParams();
    
    if (filters.periodType) {
      params.append('periodType', filters.periodType);
    }
    if (filters.dateFrom) {
      params.append('dateFrom', filters.dateFrom);
    }
    if (filters.dateTo) {
      params.append('dateTo', filters.dateTo);
    }
    if (filters.weekISO) {
      params.append('weekISO', filters.weekISO);
    }
    if (filters.weekISOs && filters.weekISOs.length > 0) {
      params.append('weekISOs', filters.weekISOs.join(','));
    }
    if (filters.parkId) {
      params.append('parkId', filters.parkId);
    }
    if (filters.scoutId) {
      params.append('scoutId', filters.scoutId);
    }
    if (filters.channel) {
      params.append('channel', filters.channel);
    }
    
    const response = await fetch(`${API_BASE_URL}/reconciliation/summary?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getDailyClosure(parkId?: string): Promise<ReconciliationSummary> {
    const params = new URLSearchParams();
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const url = `${API_BASE_URL}/reconciliation/daily-closure${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getDataLastUpdated(): Promise<{ lastUpdated: string }> {
    const response = await fetch(`${API_BASE_URL}/leads/processing-status`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getUnmatchedScoutRegistrations(): Promise<ScoutRegistrationReconciliationDTO[]> {
    const response = await fetch(`${API_BASE_URL}/scout-registrations/unmatched`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async assignScoutRegistrationMatch(registrationId: number, driverId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/scout-registrations/manual-match`, {
      method: 'POST',
      headers: obtenerHeaders(),
      body: JSON.stringify({ registrationId, driverId })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
  },

  async getLeadsUploadMetadata(): Promise<UploadMetadata> {
    const response = await fetch(`${API_BASE_URL}/leads/upload-metadata`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getYangoTransactionsUploadMetadata(): Promise<UploadMetadata> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/upload-metadata`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getScoutRegistrationsUploadMetadata(): Promise<UploadMetadata> {
    const response = await fetch(`${API_BASE_URL}/scout-registrations/upload-metadata`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getAuditLogs(filters?: {
    username?: string;
    endpoint?: string;
    method?: string;
    fechaDesde?: string;
    fechaHasta?: string;
    page?: number;
    size?: number;
  }): Promise<{
    content: AuditLog[];
    totalElements: number;
    totalPages: number;
    currentPage: number;
    size: number;
  }> {
    const params = new URLSearchParams();
    
    if (filters?.username) {
      params.append('username', filters.username);
    }
    if (filters?.endpoint) {
      params.append('endpoint', filters.endpoint);
    }
    if (filters?.method) {
      params.append('method', filters.method);
    }
    if (filters?.fechaDesde) {
      params.append('fechaDesde', filters.fechaDesde);
    }
    if (filters?.fechaHasta) {
      params.append('fechaHasta', filters.fechaHasta);
    }
    if (filters?.page !== undefined) {
      params.append('page', filters.page.toString());
    }
    if (filters?.size !== undefined) {
      params.append('size', filters.size.toString());
    }
    
    const response = await fetch(`${API_BASE_URL}/audit/logs?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getAuditStats(filters?: {
    fechaDesde?: string;
    fechaHasta?: string;
  }): Promise<{
    totalLogs: number;
    uniqueUsers: number;
    uniqueEndpoints: number;
    methodCounts: Record<string, number>;
    statusCounts: Record<string, number>;
  }> {
    const params = new URLSearchParams();
    
    if (filters?.fechaDesde) {
      params.append('fechaDesde', filters.fechaDesde);
    }
    if (filters?.fechaHasta) {
      params.append('fechaHasta', filters.fechaHasta);
    }
    
    const response = await fetch(`${API_BASE_URL}/audit/stats?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getEvolutionMetrics(parkId?: string, periodType: 'weeks' | 'months' = 'weeks', periods: number = 4): Promise<EvolutionMetrics[]> {
    const params = new URLSearchParams();
    
    if (parkId) {
      params.append('parkId', parkId);
    }
    params.append('periodType', periodType);
    params.append('periods', periods.toString());
    
    const response = await fetch(`${API_BASE_URL}/drivers/evolution?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: 'Error desconocido' }));
      throw new Error(error.error || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonePaymentViewWeekly(weekISO: string, parkId?: string): Promise<MilestonePaymentViewItem[]> {
    const params = new URLSearchParams();
    params.append('weekISO', weekISO);
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const response = await fetch(`${API_BASE_URL}/milestones/payment-view/weekly?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonePaymentViewDaily(fecha: string, parkId?: string): Promise<MilestonePaymentViewItem[]> {
    const params = new URLSearchParams();
    params.append('fecha', fecha);
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const response = await fetch(`${API_BASE_URL}/milestones/payment-view/daily?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonePaymentViewRange(fechaDesde: string, fechaHasta: string, parkId?: string): Promise<MilestonePaymentViewItem[]> {
    const params = new URLSearchParams();
    params.append('fechaDesde', fechaDesde);
    params.append('fechaHasta', fechaHasta);
    if (parkId) {
      params.append('parkId', parkId);
    }
    
    const response = await fetch(`${API_BASE_URL}/milestones/payment-view/range?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getMilestonePaymentViewPending(parkId?: string, milestoneType?: number, fechaDesde?: string, fechaHasta?: string): Promise<MilestonePaymentViewItem[]> {
    const params = new URLSearchParams();
    if (parkId) {
      params.append('parkId', parkId);
    }
    if (milestoneType !== undefined) {
      params.append('milestoneType', milestoneType.toString());
    }
    if (fechaDesde) {
      params.append('fechaDesde', fechaDesde);
    }
    if (fechaHasta) {
      params.append('fechaHasta', fechaHasta);
    }
    
    const response = await fetch(`${API_BASE_URL}/milestones/payment-view/pending?${params.toString()}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  },

  async getYangoPaymentConfig(): Promise<YangoPaymentConfig[]> {
    const response = await fetch(`${API_BASE_URL}/yango-payment-config`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return data.data || data;
  },

  async getYangoPaymentConfigByPeriod(periodDays: number): Promise<YangoPaymentConfig[]> {
    const response = await fetch(`${API_BASE_URL}/yango-payment-config/period/${periodDays}`, {
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return data.data || data;
  },

  async updateYangoPaymentConfig(id: number, amountYango: number, periodDays: number, isActive: boolean): Promise<YangoPaymentConfig> {
    const response = await fetch(`${API_BASE_URL}/yango-payment-config/${id}`, {
      method: 'PUT',
      headers: obtenerHeaders(),
      body: JSON.stringify({
        amountYango,
        periodDays,
        isActive
      })
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return data.data || data;
  },

  async rematchYangoTransactions(): Promise<{ jobId: string }> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/rematch-all`, {
      method: 'POST',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return { jobId: data.jobId };
  },

  async getYangoRematchProgress(jobId: string): Promise<YangoRematchProgress> {
    const response = await fetch(`${API_BASE_URL}/yango-transactions/rematch-progress/${jobId}`, {
      method: 'GET',
      headers: obtenerHeaders()
    });
    
    await manejarRespuesta(response);
    
    if (!response.ok) {
      if (response.status === 404) {
        throw new Error('Job no encontrado');
      }
      const error = await response.json().catch(() => ({ message: 'Error desconocido' }));
      throw new Error(error.message || `Error ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return data.data;
  }
};

export interface AuditLog {
  id: number;
  username: string;
  action: string;
  endpoint: string;
  method: string;
  requestBody?: string;
  responseStatus?: number;
  ipAddress?: string;
  userAgent?: string;
  timestamp: string;
  errorMessage?: string;
}

