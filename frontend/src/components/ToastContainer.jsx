import React from 'react';
import { useAuth } from '../context/AuthContext';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

export default function ToastContainer() {
  const { toasts, removeToast } = useAuth();

  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="toast-container">
      {toasts.map((t) => (
        <div key={t.id} className={`toast ${t.type || 'info'}`}>
          {t.type === 'success' && <CheckCircle2 size={18} color="var(--success)" />}
          {t.type === 'error' && <AlertCircle size={18} color="var(--danger)" />}
          {t.type === 'info' && <Info size={18} color="var(--info)" />}
          <div style={{ flex: 1, fontSize: '0.88rem' }}>{t.message}</div>
          <button 
            onClick={() => removeToast(t.id)} 
            className="btn-ghost" 
            style={{ padding: '2px', color: 'var(--text-dim)' }}
          >
            <X size={15} />
          </button>
        </div>
      ))}
    </div>
  );
}
