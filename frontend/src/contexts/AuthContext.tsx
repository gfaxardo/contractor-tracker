import React, { createContext, useContext, useState, useEffect, useMemo, useCallback, ReactNode } from 'react';

interface AuthUser {
  username: string;
  email?: string;
  nombre?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe ser usado dentro de AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storedToken = localStorage.getItem('authToken');
    const storedUser = localStorage.getItem('authUser');
    
    if (storedToken && storedUser) {
      setToken(storedToken);
      try {
        setUser(JSON.parse(storedUser));
      } catch (e) {
        console.error('Error al parsear usuario almacenado', e);
        localStorage.removeItem('authToken');
        localStorage.removeItem('authUser');
      }
    }
    setLoading(false);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    // Usar la misma lógica de detección automática que api.ts
    const getApiBaseUrl = (): string => {
      if (import.meta.env.VITE_API_BASE_URL) {
        return import.meta.env.VITE_API_BASE_URL;
      }
      if (typeof window !== 'undefined') {
        const hostname = window.location.hostname;
        if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '10.10.5.17') {
          return 'http://localhost:8090/api';
        }
        if (hostname === '5.161.86.63') {
          return 'http://5.161.86.63:8090/api';
        }
      }
      return 'http://localhost:8090/api';
    };
    const API_BASE_URL = getApiBaseUrl();
    
    try {
      const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        let errorMessage = 'Error al iniciar sesión';
        try {
          const errorData = await response.json();
          errorMessage = errorData.message || errorData.error || errorMessage;
        } catch (e) {
          errorMessage = `Error ${response.status}: ${response.statusText}`;
        }
        throw new Error(errorMessage);
      }

      const data = await response.json();

      if (!data.success) {
        throw new Error(data.message || 'Error al iniciar sesión');
      }

      if (!data.token) {
        throw new Error('No se recibió token de autenticación');
      }

      setToken(data.token);
      setUser(data.user);
      localStorage.setItem('authToken', data.token);
      localStorage.setItem('authUser', JSON.stringify(data.user));
    } catch (error) {
      console.error('Error en login:', error);
      throw error;
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
  }, []);

  const isAuthenticated = useMemo(() => !!token, [token]);

  const value = useMemo(() => ({
    user,
    token,
    isAuthenticated,
    login,
    logout,
    loading,
  }), [user, token, isAuthenticated, login, logout, loading]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

