import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api';
import { calculateSkillMatch } from '../utils/skillMatcher';
import { 
  X, 
  Building2, 
  MapPin, 
  Briefcase, 
  DollarSign, 
  Calendar, 
  Send, 
  CheckCircle2, 
  AlertCircle,
  ExternalLink,
  Sparkles,
  Check
} from 'lucide-react';

export default function JobDetailModal({ job, candidateSkills = [], onClose, onApplied, onOpenAuth }) {
  const { isAuthenticated, role, showToast } = useAuth();
  const [coverLetter, setCoverLetter] = useState('');
  const [applying, setApplying] = useState(false);
  const [showApplyBox, setShowApplyBox] = useState(false);
  const [error, setError] = useState('');

  if (!job) return null;

  const formatSalary = (min, max) => {
    if (!min && !max) return 'Salary Undisclosed';
    const fmt = (num) => new Intl.NumberFormat('en-US', { notation: 'compact', compactDisplay: 'short' }).format(num);
    if (min && max) return `$${fmt(min)} – $${fmt(max)}`;
    if (min) return `From $${fmt(min)}`;
    return `Up to $${fmt(max)}`;
  };

  const handleApply = async (e) => {
    e.preventDefault();
    if (!isAuthenticated) {
      onOpenAuth('login');
      return;
    }
    if (role !== 'CANDIDATE') {
      showToast('Only candidate accounts can apply to jobs.', 'error');
      return;
    }

    setApplying(true);
    setError('');

    try {
      await api.candidate.applyToJob(job.id, { coverLetter });
      showToast('Application submitted successfully!', 'success');
      if (onApplied) onApplied();
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to submit application. Make sure your profile is complete.');
    } finally {
      setApplying(false);
    }
  };

  const matchResult = (role === 'CANDIDATE' && candidateSkills.length > 0 && job.requiredSkills)
    ? calculateSkillMatch(job.requiredSkills, candidateSkills)
    : null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '720px', padding: '32px' }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <span className="badge badge-primary">{job.employmentType?.replace('_', ' ')}</span>
              <span className="badge badge-warning">{job.experienceLevel?.replace('_', ' ')}</span>
              <span className="badge badge-success">{job.status}</span>
              {matchResult && (
                <span
                  style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    padding: '3px 10px',
                    borderRadius: '12px',
                    color: matchResult.badgeColor,
                    backgroundColor: matchResult.badgeBg,
                    border: `1px solid ${matchResult.badgeBorder}`,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px'
                  }}
                >
                  <Sparkles size={12} />
                  {matchResult.percentage}% Match
                </span>
              )}
            </div>
            <h2 style={{ fontSize: '1.6rem', fontWeight: 800 }}>{job.title}</h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '6px' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Building2 size={16} />
                {job.company?.name || 'Company'}
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <MapPin size={16} />
                {job.location || 'Remote'}
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#34d399', fontWeight: 600 }}>
                <DollarSign size={16} />
                {formatSalary(job.salaryMin, job.salaryMax)}
              </span>
            </div>
          </div>
          <button onClick={onClose} className="btn-ghost" style={{ padding: '6px', borderRadius: '50%' }}>
            <X size={20} />
          </button>
        </div>

        <hr style={{ borderColor: 'var(--border-subtle)', margin: '16px 0' }} />

        {/* Smart Skill Compatibility Analysis Card */}
        {matchResult && (
          <div
            style={{
              backgroundColor: 'rgba(15, 23, 42, 0.65)',
              border: `1px solid ${matchResult.badgeBorder}`,
              borderRadius: '12px',
              padding: '18px 20px',
              marginBottom: '24px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Sparkles size={18} color={matchResult.badgeColor} />
                <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#f8fafc' }}>
                  Smart Skill Compatibility
                </span>
              </div>
              <span
                style={{
                  fontSize: '0.85rem',
                  fontWeight: 800,
                  color: matchResult.badgeColor,
                  backgroundColor: matchResult.badgeBg,
                  padding: '3px 10px',
                  borderRadius: '16px',
                  border: `1px solid ${matchResult.badgeBorder}`,
                }}
              >
                {matchResult.percentage}% Compatible
              </span>
            </div>

            {/* Progress Bar */}
            <div
              style={{
                width: '100%',
                height: '6px',
                backgroundColor: 'rgba(255, 255, 255, 0.08)',
                borderRadius: '3px',
                overflow: 'hidden',
                marginBottom: '14px',
              }}
            >
              <div
                style={{
                  width: `${matchResult.percentage}%`,
                  height: '100%',
                  backgroundColor: matchResult.badgeColor,
                  borderRadius: '3px',
                  transition: 'width 0.4s ease',
                }}
              />
            </div>

            {/* Skills Breakdown */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
              {/* Matched */}
              <div>
                <div style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 600, marginBottom: '6px' }}>
                  Matched Skills ({matchResult.matchedSkills.length})
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {matchResult.matchedSkills.length > 0 ? (
                    matchResult.matchedSkills.map((s, i) => (
                      <span
                        key={i}
                        style={{
                          fontSize: '0.74rem',
                          backgroundColor: 'rgba(16, 185, 129, 0.15)',
                          color: '#34d399',
                          border: '1px solid rgba(16, 185, 129, 0.3)',
                          padding: '3px 8px',
                          borderRadius: '6px',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <Check size={11} />
                        {s.name}
                      </span>
                    ))
                  ) : (
                    <span style={{ fontSize: '0.74rem', color: '#94a3b8' }}>None matched</span>
                  )}
                </div>
              </div>

              {/* Missing */}
              {matchResult.missingSkills.length > 0 && (
                <div>
                  <div style={{ fontSize: '0.75rem', color: '#f59e0b', fontWeight: 600, marginBottom: '6px' }}>
                    Skills to Acquire ({matchResult.missingSkills.length})
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {matchResult.missingSkills.map((s, i) => (
                      <span
                        key={i}
                        style={{
                          fontSize: '0.74rem',
                          backgroundColor: 'rgba(255, 255, 255, 0.04)',
                          color: s.mandatory ? '#fca5a5' : '#94a3b8',
                          border: `1px solid ${s.mandatory ? 'rgba(239, 68, 68, 0.3)' : 'rgba(255, 255, 255, 0.08)'}`,
                          padding: '3px 8px',
                          borderRadius: '6px',
                        }}
                      >
                        {s.name} {s.mandatory && '★ Required'}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Required & Optional Skills */}
        {job.requiredSkills && job.requiredSkills.length > 0 && (
          <div style={{ marginBottom: '24px' }}>
            <h4 style={{ fontSize: '0.92rem', color: 'var(--text-muted)', marginBottom: '10px' }}>Required & Preferred Skills</h4>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {job.requiredSkills.map((s, idx) => (
                <span key={idx} className={`skill-chip ${s.mandatory ? 'mandatory' : ''}`}>
                  {s.skill?.name} {s.mandatory && '★ Required'}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Job Description */}
        <div style={{ marginBottom: '28px' }}>
          <h4 style={{ fontSize: '0.92rem', color: 'var(--text-muted)', marginBottom: '10px' }}>Job Description</h4>
          <div style={{ 
            color: '#e2e8f0', 
            fontSize: '0.95rem', 
            lineHeight: '1.7', 
            whiteSpace: 'pre-line',
            background: 'rgba(0,0,0,0.2)',
            padding: '18px',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-subtle)'
          }}>
            {job.description || 'No description provided.'}
          </div>
        </div>

        {/* Apply Section */}
        {error && (
          <div style={{
            background: 'var(--danger-bg)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            color: 'var(--danger)',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            fontSize: '0.88rem',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <AlertCircle size={18} />
            {error}
          </div>
        )}

        {!showApplyBox ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
            <button onClick={onClose} className="btn btn-secondary">
              Close
            </button>
            {job.status === 'PUBLISHED' && (
              <button 
                onClick={() => {
                  if (!isAuthenticated) onOpenAuth('login');
                  else setShowApplyBox(true);
                }} 
                className="btn btn-primary"
              >
                <Send size={16} />
                Apply for this Position
              </button>
            )}
          </div>
        ) : (
          <form onSubmit={handleApply} style={{ 
            background: 'rgba(15, 23, 42, 0.6)', 
            padding: '20px', 
            borderRadius: 'var(--radius-md)', 
            border: '1px solid var(--border-active)' 
          }}>
            <h4 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '8px' }}>
              Submit Your Application
            </h4>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
              Your profile headline, skills, and resume link will automatically be attached.
            </p>
            <div style={{ marginBottom: '16px' }}>
              <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                Cover Letter / Pitch (Optional)
              </label>
              <textarea
                rows={4}
                placeholder="Introduce yourself and explain why you're a great fit for this position..."
                value={coverLetter}
                onChange={(e) => setCoverLetter(e.target.value)}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button 
                type="button" 
                onClick={() => setShowApplyBox(false)} 
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button 
                type="submit" 
                disabled={applying} 
                className="btn btn-primary btn-sm"
              >
                {applying ? 'Submitting...' : 'Confirm Application'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
