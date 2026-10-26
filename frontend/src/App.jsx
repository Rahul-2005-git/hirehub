import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import AuthModal from './components/AuthModal';
import JobExplorer from './components/JobExplorer';
import CandidatePortal from './components/CandidatePortal';
import RecruiterPortal from './components/RecruiterPortal';
import AdminPortal from './components/AdminPortal';
import ToastContainer from './components/ToastContainer';
import { ExternalLink, Heart, Shield, Code2 } from 'lucide-react';

function MainApp() {
  const { role, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState('jobs');
  const [authModalMode, setAuthModalMode] = useState(null); // 'login' | 'register' | null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onOpenAuth={(mode) => setAuthModalMode(mode)}
      />

      <main style={{ flex: 1 }}>
        {activeTab === 'jobs' && (
          <JobExplorer onOpenAuth={(mode) => setAuthModalMode(mode)} />
        )}

        {activeTab === 'applications' && (
          <CandidatePortal defaultTab="applications" />
        )}

        {activeTab === 'profile' && (
          <CandidatePortal defaultTab="profile" />
        )}

        {activeTab === 'recruiter-jobs' && (
          <RecruiterPortal defaultAction="list" />
        )}

        {activeTab === 'post-job' && (
          <RecruiterPortal defaultAction="create" />
        )}

        {activeTab === 'admin' && (
          <AdminPortal />
        )}
      </main>

      {/* Footer */}
      <footer style={{
        borderTop: '1px solid var(--border-subtle)',
        background: 'rgba(10, 14, 23, 0.95)',
        padding: '30px 24px',
        marginTop: '60px',
        color: 'var(--text-dim)',
        fontSize: '0.85rem'
      }}>
        <div style={{
          maxWidth: '1280px',
          margin: '0 auto',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '16px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontWeight: 700, color: 'var(--text-main)' }}>HireHub</span>
            <span>—</span>
            <span>Spring Boot 3 + PostgreSQL + React ATS Platform</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <a
              href="http://localhost:8080/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--text-muted)' }}
            >
              <ExternalLink size={14} />
              Swagger API Docs
            </a>
            <a
              href="http://localhost:8080/actuator/health"
              target="_blank"
              rel="noreferrer"
              style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--text-muted)' }}
            >
              <ExternalLink size={14} />
              API Health
            </a>
          </div>
        </div>
      </footer>

      {/* Authentication Modal */}
      {authModalMode && (
        <AuthModal
          initialMode={authModalMode}
          onClose={() => setAuthModalMode(null)}
        />
      )}

      {/* Global Toasts */}
      <ToastContainer />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
}
