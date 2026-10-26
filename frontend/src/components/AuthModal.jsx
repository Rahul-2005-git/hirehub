import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { X, User, Lock, Mail, CheckCircle2, Briefcase, GraduationCap } from 'lucide-react';

export default function AuthModal({ initialMode = 'login', onClose }) {
  const { login, register } = useAuth();
  const [mode, setMode] = useState(initialMode); // 'login' | 'register'
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Form State
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState('CANDIDATE'); // 'CANDIDATE' | 'RECRUITER'

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (mode === 'login') {
        await login({ email, password });
        onClose();
      } else {
        await register({ email, password, fullName, role });
        // Switch to login tab on successful register
        setMode('login');
      }
    } catch (err) {
      setError(err.message || 'Authentication error. Please check your inputs.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ padding: '32px' }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
          <div>
            <h2 style={{ fontSize: '1.45rem', fontWeight: 700 }}>
              {mode === 'login' ? 'Welcome Back' : 'Create your HireHub Account'}
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', marginTop: '4px' }}>
              {mode === 'login' 
                ? 'Sign in to access your jobs, applications, and pipeline' 
                : 'Join top talent and forward-thinking hiring teams'}
            </p>
          </div>
          <button onClick={onClose} className="btn-ghost" style={{ padding: '6px', borderRadius: '50%' }}>
            <X size={20} />
          </button>
        </div>

        {/* Tab switch */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          background: 'rgba(0,0,0,0.25)',
          borderRadius: 'var(--radius-sm)',
          padding: '4px',
          marginBottom: '24px'
        }}>
          <button
            type="button"
            onClick={() => { setMode('login'); setError(''); }}
            style={{
              padding: '8px',
              borderRadius: '6px',
              fontSize: '0.9rem',
              fontWeight: 600,
              background: mode === 'login' ? 'var(--primary)' : 'transparent',
              color: mode === 'login' ? '#ffffff' : 'var(--text-dim)',
            }}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setMode('register'); setError(''); }}
            style={{
              padding: '8px',
              borderRadius: '6px',
              fontSize: '0.9rem',
              fontWeight: 600,
              background: mode === 'register' ? 'var(--primary)' : 'transparent',
              color: mode === 'register' ? '#ffffff' : 'var(--text-dim)',
            }}
          >
            Create Account
          </button>
        </div>

        {error && (
          <div style={{
            background: 'var(--danger-bg)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            color: 'var(--danger)',
            padding: '10px 14px',
            borderRadius: 'var(--radius-sm)',
            fontSize: '0.85rem',
            marginBottom: '18px'
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {mode === 'register' && (
            <>
              {/* Role selector */}
              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', display: 'block' }}>
                  I want to join as:
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div
                    onClick={() => setRole('CANDIDATE')}
                    style={{
                      border: `1px solid ${role === 'CANDIDATE' ? 'var(--primary)' : 'var(--border-subtle)'}`,
                      background: role === 'CANDIDATE' ? 'var(--primary-glow)' : 'var(--bg-input)',
                      padding: '12px',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      transition: 'all 0.2s'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, color: role === 'CANDIDATE' ? '#a5b4fc' : 'var(--text-main)' }}>
                      <GraduationCap size={18} />
                      Candidate
                    </div>
                    <span style={{ fontSize: '0.74rem', color: 'var(--text-dim)' }}>
                      Search jobs & apply
                    </span>
                  </div>

                  <div
                    onClick={() => setRole('RECRUITER')}
                    style={{
                      border: `1px solid ${role === 'RECRUITER' ? 'var(--secondary)' : 'var(--border-subtle)'}`,
                      background: role === 'RECRUITER' ? 'rgba(139, 92, 246, 0.15)' : 'var(--bg-input)',
                      padding: '12px',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      transition: 'all 0.2s'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, color: role === 'RECRUITER' ? '#c4b5fd' : 'var(--text-main)' }}>
                      <Briefcase size={18} />
                      Recruiter
                    </div>
                    <span style={{ fontSize: '0.74rem', color: 'var(--text-dim)' }}>
                      Post jobs & hire talent
                    </span>
                  </div>
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Full Name
                </label>
                <div style={{ position: 'relative' }}>
                  <User size={16} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
                  <input
                    type="text"
                    required
                    placeholder="e.g. Jane Doe"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    style={{ paddingLeft: '38px' }}
                  />
                </div>
              </div>
            </>
          )}

          <div>
            <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
              Work or Personal Email
            </label>
            <div style={{ position: 'relative' }}>
              <Mail size={16} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
              <input
                type="email"
                required
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{ paddingLeft: '38px' }}
              />
            </div>
          </div>

          <div>
            <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={16} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
              <input
                type="password"
                required
                minLength={8}
                placeholder="At least 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{ paddingLeft: '38px' }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', padding: '12px', marginTop: '8px' }}
          >
            {loading ? 'Please wait...' : mode === 'login' ? 'Sign In to Account' : 'Register Now'}
          </button>
        </form>

        <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.82rem', color: 'var(--text-dim)' }}>
          {mode === 'login' ? (
            <>
              Don't have an account yet?{' '}
              <span 
                onClick={() => { setMode('register'); setError(''); }} 
                style={{ color: 'var(--primary)', cursor: 'pointer', fontWeight: 600 }}
              >
                Sign up
              </span>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <span 
                onClick={() => { setMode('login'); setError(''); }} 
                style={{ color: 'var(--primary)', cursor: 'pointer', fontWeight: 600 }}
              >
                Sign in
              </span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
