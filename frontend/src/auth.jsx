import { createContext, useCallback, useContext, useState } from 'react';
import { api, clearSession, getStoredUser, setSession } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser());

  const login = async (email, password) => {
    const data = await api.login(email, password);
    setSession(data);
    setUser(data.user);
    return data.user;
  };

  const register = async (payload) => {
    const data = await api.register(payload);
    setSession(data);
    setUser(data.user);
    return data.user;
  };

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  /** Reload the full profile (balance, bio) from the server. */
  const refresh = useCallback(async () => {
    try {
      const profile = await api.me();
      localStorage.setItem('auction.user', JSON.stringify(profile));
      setUser(profile);
      return profile;
    } catch (e) {
      if (e.status === 401) logout();
      throw e;
    }
  }, [logout]);

  return (
    <AuthContext.Provider value={{ user, login, register, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
