import React, { useState, useEffect, useRef } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import ResumeViewerModal from './ResumeViewerModal';
import { 
  User, 
  Briefcase, 
  Code, 
  FileText, 
  CheckCircle, 
  Clock, 
  Trash2, 
  Plus, 
  ExternalLink, 
  AlertTriangle,
  History,
  ChevronDown,
  ChevronUp,
  XCircle,
  Building2,
  UploadCloud,
  Eye,
  Loader2
} from 'lucide-react';

const PIPELINE_STAGES = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'SELECTED'];

export default function CandidatePortal({ defaultTab = 'applications' }) {
  const { showToast } = useAuth();
  const [activeSubTab, setActiveSubTab] = useState(defaultTab); // 'applications' | 'profile' | 'skills'
  const [loading, setLoading] = useState(true);

  // Profile State
  const [profile, setProfile] = useState({
    headline: '',
    experienceYears: 0,
    currentLocation: '',
    resumeUrl: '',
    resumeFileName: ''
  });
  const [savingProfile, setSavingProfile] = useState(false);
  const [uploadingResume, setUploadingResume] = useState(false);
  const [showResumeViewer, setShowResumeViewer] = useState(false);
  const fileInputRef = useRef(null);

  const handleResumeFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.name.toLowerCase().endsWith('.pdf')) {
      showToast('Only PDF documents are allowed', 'error');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      showToast('File size must be under 10MB', 'error');
      return;
    }

    setUploadingResume(true);
    try {
      const updated = await api.candidate.uploadResume(file);
      setProfile(prev => ({
        ...prev,
        resumeUrl: updated.resumeUrl,
        resumeFileName: updated.resumeFileName
      }));
      showToast('Resume uploaded successfully!', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to upload resume', 'error');
    } finally {
      setUploadingResume(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteResume = async () => {
    if (!confirm('Are you sure you want to remove your resume?')) return;
    setUploadingResume(true);
    try {
      await api.candidate.deleteResume();
      setProfile(prev => ({
        ...prev,
        resumeUrl: '',
        resumeFileName: ''
      }));
      showToast('Resume removed', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to delete resume', 'error');
    } finally {
      setUploadingResume(false);
    }
  };

  // Skills State
  const [skills, setSkills] = useState([]);
  const [newSkillName, setNewSkillName] = useState('');
  const [newSkillProficiency, setNewSkillProficiency] = useState('INTERMEDIATE');
  const [newSkillYears, setNewSkillYears] = useState(1);
  const [addingSkill, setAddingSkill] = useState(false);

  // Applications State
  const [applications, setApplications] = useState([]);
  const [expandedHistoryAppId, setExpandedHistoryAppId] = useState(null);
  const [historyData, setHistoryData] = useState({});
  const [loadingHistory, setLoadingHistory] = useState(false);

  const loadAllData = async () => {
    setLoading(true);
    try {
      const [profRes, skillsRes, appsRes] = await Promise.allSettled([
        api.candidate.getProfile(),
        api.candidate.getSkills(),
        api.candidate.getApplications()
      ]);

      if (profRes.status === 'fulfilled' && profRes.value) {
        setProfile({
          headline: profRes.value.headline || '',
          experienceYears: profRes.value.experienceYears || 0,
          currentLocation: profRes.value.currentLocation || '',
          resumeUrl: profRes.value.resumeUrl || '',
          resumeFileName: profRes.value.resumeFileName || ''
        });
      }
      if (skillsRes.status === 'fulfilled' && Array.isArray(skillsRes.value)) {
        setSkills(skillsRes.value);
      }
      if (appsRes.status === 'fulfilled' && Array.isArray(appsRes.value)) {
        setApplications(appsRes.value);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAllData();
  }, []);

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      await api.candidate.updateProfile(profile);
      showToast('Profile updated successfully!', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to update profile', 'error');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleAddSkill = async (e) => {
    e.preventDefault();
    if (!newSkillName.trim()) return;
    setAddingSkill(true);
    try {
      await api.candidate.addSkill({
        skillName: newSkillName.trim().toLowerCase(),
        proficiency: newSkillProficiency,
        yearsOfExperience: parseInt(newSkillYears, 10) || 1
      });
      setNewSkillName('');
      showToast('Skill added!', 'success');
      const updatedSkills = await api.candidate.getSkills();
      setSkills(updatedSkills || []);
    } catch (err) {
      showToast(err.message || 'Failed to add skill', 'error');
    } finally {
      setAddingSkill(false);
    }
  };

  const handleDeleteSkill = async (skillId) => {
    try {
      await api.candidate.deleteSkill(skillId);
      setSkills((prev) => prev.filter((s) => s.id !== skillId));
      showToast('Skill removed', 'info');
    } catch (err) {
      showToast(err.message || 'Failed to delete skill', 'error');
    }
  };

  const handleWithdraw = async (appId) => {
    if (!confirm('Are you sure you want to withdraw this application? This action cannot be reversed.')) return;
    try {
      await api.candidate.withdrawApplication(appId);
      showToast('Application withdrawn', 'info');
      const updated = await api.candidate.getApplications();
      setApplications(updated || []);
    } catch (err) {
      showToast(err.message || 'Could not withdraw application', 'error');
    }
  };

  const toggleHistory = async (appId) => {
    if (expandedHistoryAppId === appId) {
      setExpandedHistoryAppId(null);
      return;
    }
    setExpandedHistoryAppId(appId);
    if (!historyData[appId]) {
      setLoadingHistory(true);
      try {
        const hist = await api.candidate.getApplicationHistory(appId);
        setHistoryData((prev) => ({ ...prev, [appId]: hist || [] }));
      } catch (err) {
        showToast('Failed to load application history', 'error');
      } finally {
        setLoadingHistory(false);
      }
    }
  };

  const getStageIndex = (status) => {
    return PIPELINE_STAGES.indexOf(status);
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '0 20px 60px' }}>
      {/* Tab Nav */}
      <div style={{
        display: 'flex',
        gap: '12px',
        borderBottom: '1px solid var(--border-subtle)',
        paddingBottom: '16px',
        marginBottom: '28px',
        flexWrap: 'wrap'
      }}>
        <button
          onClick={() => setActiveSubTab('applications')}
          className={`btn ${activeSubTab === 'applications' ? 'btn-primary' : 'btn-ghost'}`}
        >
          <FileText size={18} />
          My Applications ({applications.length})
        </button>
        <button
          onClick={() => setActiveSubTab('profile')}
          className={`btn ${activeSubTab === 'profile' ? 'btn-primary' : 'btn-ghost'}`}
        >
          <User size={18} />
          Profile Details
        </button>
        <button
          onClick={() => setActiveSubTab('skills')}
          className={`btn ${activeSubTab === 'skills' ? 'btn-primary' : 'btn-ghost'}`}
        >
          <Code size={18} />
          Skills & Technologies ({skills.length})
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '50px 0', color: 'var(--text-muted)' }}>
          Loading candidate data...
        </div>
      ) : activeSubTab === 'applications' ? (
        <div>
          {applications.length === 0 ? (
            <div className="glass-panel" style={{ textAlign: 'center', padding: '50px 20px' }}>
              <FileText size={44} color="var(--text-dim)" style={{ margin: '0 auto 14px' }} />
              <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '6px' }}>No Applications Yet</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                You haven't submitted any job applications yet. Go to Explore Jobs and find your match!
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {applications.map((app) => {
                const currentStageIdx = getStageIndex(app.status);
                const isRejected = app.status === 'REJECTED';
                const isWithdrawn = app.status === 'WITHDRAWN';

                return (
                  <div key={app.id} className="glass-panel" style={{ padding: '24px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '14px', marginBottom: '20px' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <Building2 size={15} />
                            {app.job?.companyName || 'Employer'}
                          </span>
                          <span style={{ color: 'var(--text-dim)' }}>•</span>
                          <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                            Applied on {new Date(app.appliedAt || app.createdAt || Date.now()).toLocaleDateString()}
                          </span>
                        </div>
                        <h3 style={{ fontSize: '1.35rem', fontWeight: 800 }}>{app.job?.title}</h3>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span className={`badge ${
                          isRejected || isWithdrawn ? 'badge-danger' : app.status === 'SELECTED' ? 'badge-success' : 'badge-primary'
                        }`} style={{ fontSize: '0.8rem', padding: '5px 12px' }}>
                          {app.status?.replace('_', ' ')}
                        </span>

                        {!isRejected && !isWithdrawn && app.status !== 'SELECTED' && (
                          <button
                            onClick={() => handleWithdraw(app.id)}
                            className="btn btn-danger btn-sm"
                            title="Withdraw application"
                          >
                            Withdraw
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Pipeline Visual Stepper */}
                    {!isRejected && !isWithdrawn ? (
                      <div style={{
                        background: 'rgba(0,0,0,0.2)',
                        padding: '16px 20px',
                        borderRadius: 'var(--radius-sm)',
                        border: '1px solid var(--border-subtle)',
                        marginBottom: '18px'
                      }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative' }}>
                          {PIPELINE_STAGES.map((stage, idx) => {
                            const isCompleted = idx <= currentStageIdx;
                            const isCurrent = idx === currentStageIdx;
                            return (
                              <div key={stage} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 1, flex: 1 }}>
                                <div style={{
                                  width: '28px',
                                  height: '28px',
                                  borderRadius: '50%',
                                  background: isCompleted ? 'var(--primary)' : 'var(--bg-input)',
                                  border: `2px solid ${isCompleted ? '#a5b4fc' : 'var(--border-subtle)'}`,
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.75rem',
                                  fontWeight: 700,
                                  color: isCompleted ? '#ffffff' : 'var(--text-dim)',
                                  boxShadow: isCurrent ? '0 0 12px var(--primary-glow)' : 'none',
                                  marginBottom: '6px'
                                }}>
                                  {isCompleted ? '✓' : idx + 1}
                                </div>
                                <span style={{
                                  fontSize: '0.72rem',
                                  fontWeight: isCurrent ? 700 : 500,
                                  color: isCurrent ? '#ffffff' : isCompleted ? 'var(--text-muted)' : 'var(--text-dim)',
                                  textAlign: 'center'
                                }}>
                                  {stage.replace('_', ' ')}
                                </span>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ) : (
                      <div style={{
                        padding: '12px 16px',
                        borderRadius: 'var(--radius-sm)',
                        background: 'var(--danger-bg)',
                        border: '1px solid rgba(239, 68, 68, 0.25)',
                        color: '#fca5a5',
                        fontSize: '0.85rem',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        marginBottom: '16px'
                      }}>
                        <XCircle size={16} />
                        Application status: <strong>{app.status}</strong>.
                      </div>
                    )}

                    {/* Cover Letter excerpt */}
                    {app.coverLetter && (
                      <div style={{ fontSize: '0.86rem', color: 'var(--text-dim)', fontStyle: 'italic', marginBottom: '14px' }}>
                        "{app.coverLetter}"
                      </div>
                    )}

                    {/* History Accordion Toggle */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <button
                        onClick={() => toggleHistory(app.id)}
                        className="btn btn-ghost btn-sm"
                        style={{ fontSize: '0.8rem' }}
                      >
                        <History size={14} />
                        Audit History
                        {expandedHistoryAppId === app.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                      </button>
                    </div>

                    {expandedHistoryAppId === app.id && (
                      <div style={{
                        marginTop: '12px',
                        padding: '14px',
                        borderRadius: 'var(--radius-sm)',
                        background: 'rgba(15, 23, 42, 0.6)',
                        border: '1px solid var(--border-subtle)'
                      }}>
                        <h5 style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: '10px' }}>
                          Status Transition History
                        </h5>
                        {loadingHistory ? (
                          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>Loading log...</span>
                        ) : (!historyData[app.id] || historyData[app.id].length === 0) ? (
                          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>No prior transitions logged yet.</span>
                        ) : (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {historyData[app.id].map((h, i) => (
                              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem' }}>
                                <span>
                                  <strong>{h.fromStatus ? `${h.fromStatus} ➔ ` : ''}{h.toStatus}</strong>
                                  {h.note && <span style={{ color: 'var(--text-dim)', marginLeft: '8px' }}>({h.note})</span>}
                                </span>
                                <span style={{ color: 'var(--text-dim)' }}>
                                  {new Date(h.changedAt || Date.now()).toLocaleString()}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      ) : activeSubTab === 'profile' ? (
        <div className="glass-panel" style={{ maxWidth: '720px', padding: '32px' }}>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '6px' }}>Candidate Profile</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', marginBottom: '24px' }}>
            A complete profile gives recruiters direct insight when you submit job applications.
          </p>

          <form onSubmit={handleSaveProfile} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div>
              <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                Professional Headline
              </label>
              <input
                type="text"
                placeholder="e.g. Senior Full Stack Engineer | Java & React"
                value={profile.headline}
                onChange={(e) => setProfile({ ...profile, headline: e.target.value })}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Years of Experience
                </label>
                <input
                  type="number"
                  min={0}
                  max={50}
                  value={profile.experienceYears}
                  onChange={(e) => setProfile({ ...profile, experienceYears: parseInt(e.target.value, 10) || 0 })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Current Location
                </label>
                <input
                  type="text"
                  placeholder="e.g. Pune, India"
                  value={profile.currentLocation}
                  onChange={(e) => setProfile({ ...profile, currentLocation: e.target.value })}
                />
              </div>
            </div>

            {/* Modern Resume Upload & Preview Section */}
            <div style={{ marginTop: '10px', marginBottom: '10px' }}>
              <label style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-main)', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <FileText size={16} color="var(--primary)" />
                Resume Document (PDF)
              </label>

              <input
                type="file"
                ref={fileInputRef}
                accept=".pdf,application/pdf"
                style={{ display: 'none' }}
                onChange={handleResumeFileSelect}
              />

              {profile.resumeUrl ? (
                <div
                  style={{
                    backgroundColor: 'rgba(30, 41, 59, 0.7)',
                    border: '1px solid rgba(99, 102, 241, 0.3)',
                    borderRadius: '12px',
                    padding: '16px 20px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '12px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <div
                      style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '10px',
                        backgroundColor: 'rgba(99, 102, 241, 0.15)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--primary)',
                      }}
                    >
                      <FileText size={22} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, color: 'var(--text-main)', fontSize: '0.95rem' }}>
                        {profile.resumeFileName || 'Resume.pdf'}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <CheckCircle size={12} /> Ready for applications
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button
                      type="button"
                      onClick={() => setShowResumeViewer(true)}
                      className="btn btn-secondary"
                      style={{ padding: '8px 14px', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '6px' }}
                    >
                      <Eye size={15} />
                      Preview PDF
                    </button>
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={uploadingResume}
                      className="btn btn-secondary"
                      style={{ padding: '8px 14px', fontSize: '0.85rem' }}
                    >
                      {uploadingResume ? 'Uploading...' : 'Replace'}
                    </button>
                    <button
                      type="button"
                      onClick={handleDeleteResume}
                      disabled={uploadingResume}
                      className="btn-ghost"
                      style={{ padding: '8px', color: '#f87171' }}
                      title="Delete resume"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ) : (
                <div
                  onClick={() => fileInputRef.current?.click()}
                  style={{
                    border: '2px dashed rgba(255, 255, 255, 0.15)',
                    borderRadius: '12px',
                    padding: '30px 20px',
                    textAlign: 'center',
                    cursor: 'pointer',
                    backgroundColor: 'rgba(15, 23, 42, 0.4)',
                    transition: 'all 0.2s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'var(--primary)';
                    e.currentTarget.style.backgroundColor = 'rgba(99, 102, 241, 0.05)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.15)';
                    e.currentTarget.style.backgroundColor = 'rgba(15, 23, 42, 0.4)';
                  }}
                >
                  {uploadingResume ? (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px', color: 'var(--text-muted)' }}>
                      <Loader2 size={32} style={{ animation: 'spin 1s linear infinite' }} />
                      <span>Uploading resume PDF...</span>
                    </div>
                  ) : (
                    <>
                      <UploadCloud size={36} color="var(--primary)" style={{ margin: '0 auto 10px auto' }} />
                      <div style={{ fontWeight: 600, color: 'var(--text-main)', fontSize: '0.95rem', marginBottom: '4px' }}>
                        Click to upload your Resume (PDF)
                      </div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                        Supports PDF up to 10MB • Automatically attached to all future applications
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '10px' }}>
              <button type="submit" disabled={savingProfile} className="btn btn-primary">
                {savingProfile ? 'Saving Changes...' : 'Save Profile'}
              </button>
            </div>
          </form>
        </div>
      ) : (
        /* Skills SubTab */
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '24px' }}>
          {/* Add Skill Form */}
          <div className="glass-panel" style={{ padding: '28px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '6px' }}>Add Tech Skill</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '20px' }}>
              Add verified skills to match with job requirements automatically.
            </p>

            <form onSubmit={handleAddSkill} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Skill Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. java, react, postgresql, docker"
                  value={newSkillName}
                  onChange={(e) => setNewSkillName(e.target.value)}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Proficiency Level
                </label>
                <select
                  value={newSkillProficiency}
                  onChange={(e) => setNewSkillProficiency(e.target.value)}
                >
                  <option value="BEGINNER">Beginner</option>
                  <option value="INTERMEDIATE">Intermediate</option>
                  <option value="ADVANCED">Advanced</option>
                  <option value="EXPERT">Expert</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Years of Experience
                </label>
                <input
                  type="number"
                  min={1}
                  max={40}
                  value={newSkillYears}
                  onChange={(e) => setNewSkillYears(e.target.value)}
                />
              </div>

              <button type="submit" disabled={addingSkill} className="btn btn-primary" style={{ marginTop: '6px' }}>
                <Plus size={16} />
                {addingSkill ? 'Adding...' : 'Add Skill'}
              </button>
            </form>
          </div>

          {/* Current Skills Cloud */}
          <div className="glass-panel" style={{ padding: '28px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '6px' }}>My Skills ({skills.length})</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '20px' }}>
              Skills registered on your candidate profile.
            </p>

            {skills.length === 0 ? (
              <div style={{ color: 'var(--text-dim)', fontSize: '0.88rem' }}>
                No skills added yet. Use the form on the left to add your technologies.
              </div>
            ) : (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                {skills.map((s) => (
                  <div
                    key={s.skill?.id}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '8px',
                      background: 'rgba(99, 102, 241, 0.1)',
                      border: '1px solid rgba(99, 102, 241, 0.3)',
                      padding: '8px 12px',
                      borderRadius: '8px'
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 600, color: '#e0e7ff', fontSize: '0.88rem' }}>
                        {s.skill?.name}
                      </div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)' }}>
                        {s.proficiency} • {s.yearsOfExperience} yrs
                      </div>
                    </div>
                    <button
                      onClick={() => handleDeleteSkill(s.skill?.id)}
                      className="btn-ghost"
                      style={{ padding: '4px', color: '#f87171' }}
                      title="Remove skill"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Resume Viewer Modal */}
      {showResumeViewer && profile.resumeUrl && (
        <ResumeViewerModal
          resumeUrl={profile.resumeUrl}
          candidateName={profile.headline || 'Candidate'}
          onClose={() => setShowResumeViewer(false)}
        />
      )}
    </div>
  );
}
