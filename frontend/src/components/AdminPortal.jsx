import React, { useState, useEffect } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { 
  ShieldCheck, 
  Building2, 
  CheckCircle, 
  XCircle, 
  ExternalLink, 
  Activity, 
  Server, 
  Database,
  RefreshCw 
} from 'lucide-react';

export default function AdminPortal() {
  const { showToast } = useAuth();
  const [pendingCompanies, setPendingCompanies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [decidingId, setDecidingId] = useState(null);
  const [systemHealth, setSystemHealth] = useState(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [compsRes, healthRes] = await Promise.allSettled([
        api.admin.getPendingCompanies(),
        api.health()
      ]);

      if (compsRes.status === 'fulfilled' && compsRes.value) {
        // Paged or array
        const list = compsRes.value.content || compsRes.value;
        setPendingCompanies(Array.isArray(list) ? list : []);
      }
      if (healthRes.status === 'fulfilled') {
        setSystemHealth(healthRes.value);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleDecision = async (companyId, decision) => {
    setDecidingId(companyId);
    try {
      await api.admin.decideCompany(companyId, decision);
      showToast(`Company has been ${decision.toLowerCase()}!`, 'success');
      setPendingCompanies((prev) => prev.filter((c) => c.id !== companyId));
    } catch (err) {
      showToast(err.message || 'Failed to process company decision', 'error');
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '0 20px 60px' }}>
      {/* Header */}
      <div className="glass-panel" style={{ padding: '28px', marginBottom: '32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '42px',
              height: '42px',
              borderRadius: '10px',
              background: 'rgba(239, 68, 68, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--danger)'
            }}>
              <ShieldCheck size={24} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.45rem', fontWeight: 800 }}>Admin Moderation & Diagnostics</h2>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.86rem' }}>
                Manage company verifications, system health, and recruitment platform integrity.
              </p>
            </div>
          </div>

          <button onClick={loadData} className="btn btn-secondary btn-sm">
            <RefreshCw size={15} />
            Refresh
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
        {/* Pending Company Approvals */}
        <div className="glass-panel" style={{ padding: '28px', gridColumn: 'span 2' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>
              Pending Company Approvals ({pendingCompanies.length})
            </h3>
            <span className="badge badge-warning">Awaiting Decision</span>
          </div>

          {loading ? (
            <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)' }}>
              Checking verification queue...
            </div>
          ) : pendingCompanies.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--text-dim)' }}>
              <CheckCircle size={36} color="var(--success)" style={{ margin: '0 auto 10px', opacity: 0.8 }} />
              <p>No companies currently pending verification. You're all caught up!</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {pendingCompanies.map((c) => (
                <div key={c.id} style={{
                  background: 'rgba(15, 23, 42, 0.7)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '18px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '12px'
                }}>
                  <div>
                    <h4 style={{ fontSize: '1.05rem', fontWeight: 700 }}>{c.name}</h4>
                    {c.website && (
                      <a 
                        href={c.website} 
                        target="_blank" 
                        rel="noreferrer" 
                        style={{ fontSize: '0.82rem', color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}
                      >
                        {c.website}
                        <ExternalLink size={12} />
                      </a>
                    )}
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)', marginTop: '4px' }}>
                      Registered on {new Date(c.createdAt || Date.now()).toLocaleDateString()}
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      disabled={decidingId === c.id}
                      onClick={() => handleDecision(c.id, 'APPROVED')}
                      className="btn btn-success btn-sm"
                    >
                      <CheckCircle size={15} />
                      Approve
                    </button>
                    <button
                      disabled={decidingId === c.id}
                      onClick={() => handleDecision(c.id, 'REJECTED')}
                      className="btn btn-danger btn-sm"
                    >
                      <XCircle size={15} />
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Backend Diagnostics Card */}
        <div className="glass-panel" style={{ padding: '28px' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '16px' }}>System Diagnostics</h3>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Server size={18} color="var(--primary)" />
              <div>
                <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Backend Engine</div>
                <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>Spring Boot 3.5.5 (Java 23)</div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Database size={18} color="#34d399" />
              <div>
                <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Database Connection</div>
                <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>PostgreSQL 18 (Localhost:5432)</div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Activity size={18} color="#38bdf8" />
              <div>
                <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>API Status</div>
                <div style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--success)' }}>
                  {systemHealth?.status || 'ONLINE'}
                </div>
              </div>
            </div>

            <hr style={{ borderColor: 'var(--border-subtle)', margin: '8px 0' }} />

            <div>
              <a
                href="http://localhost:8080/swagger-ui.html"
                target="_blank"
                rel="noreferrer"
                className="btn btn-secondary btn-sm"
                style={{ width: '100%' }}
              >
                <ExternalLink size={14} />
                Open Backend Swagger UI
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
