import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '../types';

interface JwtPayload {
  sub: string;
  roles: string[];
  exp: number;
}

function decodeToken(token: string): JwtPayload | null {
  try {
    const base64 = token.split('.')[1];
    const decoded = JSON.parse(atob(base64));
    return decoded;
  } catch {
    return null;
  }
}

interface AuthContextType {
  user: User | null;
  accessToken: string | null;
  login: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isSeller: boolean;
  isClient: boolean;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      const payload = decodeToken(token);
      if (payload && payload.exp * 1000 > Date.now()) {
        setAccessToken(token);
        setUser({
          id: parseInt(payload.sub),
          username: '',
          email: '',
          role: payload.roles[0] as User['role'],
        });
      } else {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
      }
    }
  }, []);

  const login = (token: string, refresh: string) => {
    localStorage.setItem('accessToken', token);
    localStorage.setItem('refreshToken', refresh);
    setAccessToken(token);
    const payload = decodeToken(token);
    if (payload) {
      setUser({
        id: parseInt(payload.sub),
        username: '',
        email: '',
        role: payload.roles[0] as User['role'],
      });
    }
  };

  const logout = () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      import('../api/axios').then(({ default: api }) => {
        api.post('/auth/logout', { refreshToken }).catch(() => {});
      });
    }
    localStorage.clear();
    setUser(null);
    setAccessToken(null);
  };

  return (
    <AuthContext.Provider value={{
      user,
      accessToken,
      login,
      logout,
      isAuthenticated: !!user,
      isAdmin: user?.role === 'ROLE_ADMIN',
      isSeller: user?.role === 'ROLE_SELLER',
      isClient: user?.role === 'ROLE_CLIENT',
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
