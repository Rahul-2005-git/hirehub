import React, { createContext, useContext, useState, useEffect } from 'react';
import { api, getToken, setToken } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setTokenState] = useState(getToken());
  const [loading, setLoading] = useState(true);
  const [toasts, setToasts] = useState([]);

  const showToast = (message, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const refreshUser = async () => {
    try {
      if (!getToken()) {
        setUser(null);
        return;
      }
      const me = await api.auth.getMe();
      setUser(me);
    } catch (err) {
      console.warn('Failed to fetch authenticated user:', err);
      // If token expired/invalid, clear session
      if (err.status === 401) {
        logout();
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshUser();
  }, [token]);

  const login = async (credentials) => {
    try {
      const res = await api.auth.login(credentials);
      // In HireHub, login response contains accessToken / token
      const jwt = res?.accessToken || res?.token || res;
      setToken(jwt);
      setTokenState(jwt);
      showToast('Logged in successfully!', 'success');
      await refreshUser();
      return true;
    } catch (err) {
      showToast(err.message || 'Login failed', 'error');
      throw err;
    }
  };

  const register = async (payload) => {
    try {
      await api.auth.register(payload);
      showToast('Account created! Please log in.', 'success');
      return true;
    } catch (err) {
      showToast(err.message || 'Registration failed', 'error');
      throw err;
    }
  };

  const logout = () => {
    setToken(null);
    setTokenState(null);
    setUser(null);
    showToast('Logged out', 'info');
  };

  const role = user?.role || null;

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        role,
        isAuthenticated: !!user,
        loading,
        login,
        register,
        logout,
        refreshUser,
        toasts,
        showToast,
        removeToast,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
