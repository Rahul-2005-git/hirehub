import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api';
import { 
  Briefcase, 
  User, 
  LogOut, 
  LogIn, 
  UserPlus, 
  Shield, 
  FileText, 
  Building2, 
  Activity,
  PlusCircle
} from 'lucide-react';
import NotificationBell from './NotificationBell';

export default function Navbar({ activeTab, setActiveTab, onOpenAuth }) {
  const { user, role, isAuthenticated, logout } = useAuth();
  const [backendHealth, setBackendHealth] = useState(null);

  useEffect(() => {
    // Check backend health on mount
    api.health()
      .then((data) => setBackendHealth(data?.status || 'UP'))
      .catch(() => setBackendHealth('OFFLINE'));
  }, []);

  return (
    <header className="glass-panel" style={{
      position: 'sticky',
      top: 0,
      zIndex: 100,
      borderRadius: 0,
      borderTop: 'none',
      borderLeft: 'none',
      borderRight: 'none',
      padding: '14px 24px',
      marginBottom: '28px'
    }}>
      <div style={{
        maxWidth: '1280px',
        margin: '0 auto',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px'
      }}>
        {/* Brand */}
        <div 
          onClick={() => setActiveTab('jobs')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            cursor: 'pointer'
          }}
        >
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '10px',
            background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 16px rgba(99, 102, 241, 0.4)'
          }}>
            <Briefcase size={22} color="#ffffff" />
          </div>
          <div>
            <span style={{ fontSize: '1.35rem', fontWeight: 800, letterSpacing: '-0.02em' }} className="gradient-text">
              HireHub
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '-2px' }}>
              <span style={{
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                backgroundColor: backendHealth === 'UP' ? '#10b981' : '#f59e0b'
              }} />
              <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
                API {backendHealth || 'Checking...'}
              </span>
            </div>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <button
            onClick={() => setActiveTab('jobs')}
            className={`btn btn-sm ${activeTab === 'jobs' ? 'btn-primary' : 'btn-ghost'}`}
          >
            <Briefcase size={16} />
            Explore Jobs
          </button>

          {role === 'CANDIDATE' && (
            <>
              <button
                onClick={() => setActiveTab('applications')}
                className={`btn btn-sm ${activeTab === 'applications' ? 'btn-primary' : 'btn-ghost'}`}
              >
                <FileText size={16} />
                My Applications
              </button>
              <button
                onClick={() => setActiveTab('profile')}
                className={`btn btn-sm ${activeTab === 'profile' ? 'btn-primary' : 'btn-ghost'}`}
              >
                <User size={16} />
                My Profile
              </button>
            </>
          )}

          {role === 'RECRUITER' && (
            <>
              <button
                onClick={() => setActiveTab('recruiter-jobs')}
                className={`btn btn-sm ${activeTab === 'recruiter-jobs' ? 'btn-primary' : 'btn-ghost'}`}
              >
                <Building2 size={16} />
                Recruiter ATS
              </button>
              <button
                onClick={() => setActiveTab('post-job')}
                className={`btn btn-sm ${activeTab === 'post-job' ? 'btn-primary' : 'btn-ghost'}`}
              >
                <PlusCircle size={16} />
                Post a Job
              </button>
            </>
          )}

          {role === 'ADMIN' && (
            <button
              onClick={() => setActiveTab('admin')}
              className={`btn btn-sm ${activeTab === 'admin' ? 'btn-primary' : 'btn-ghost'}`}
            >
              <Shield size={16} />
              Admin Moderation
            </button>
          )}
        </nav>

        {/* Auth / Profile Area */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {isAuthenticated ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <NotificationBell />
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
                <span style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-main)' }}>
                  {user?.fullName || user?.email}
                </span>
                <span className={`badge ${
                  role === 'ADMIN' ? 'badge-danger' : role === 'RECRUITER' ? 'badge-warning' : 'badge-primary'
                }`} style={{ fontSize: '0.68rem', padding: '1px 6px' }}>
                  {role}
                </span>
              </div>
              <button onClick={logout} className="btn btn-secondary btn-sm" title="Log Out">
                <LogOut size={15} />
                <span>Logout</span>
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <button 
                onClick={() => onOpenAuth('login')} 
                className="btn btn-secondary btn-sm"
              >
                <LogIn size={15} />
                <span>Sign In</span>
              </button>
              <button 
                onClick={() => onOpenAuth('register')} 
                className="btn btn-primary btn-sm"
              >
                <UserPlus size={15} />
                <span>Create Account</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
