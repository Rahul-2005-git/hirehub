import React, { useState, useEffect } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import ResumeViewerModal from './ResumeViewerModal';
import RecruiterAnalytics from './RecruiterAnalytics';
import { calculateSkillMatch } from '../utils/skillMatcher';
import { 
  Building2, 
  Briefcase, 
  Users, 
  Plus, 
  CheckCircle, 
  XCircle, 
  AlertCircle, 
  ExternalLink, 
  Clock, 
  Trash2, 
  Eye, 
  X,
  Send,
  User,
  MapPin,
  DollarSign,
  FileText,
  Sparkles,
  Check,
  BarChart3
} from 'lucide-react';

export default function RecruiterPortal({ defaultAction = 'list' }) {
  const { showToast } = useAuth();
  const [recruiterData, setRecruiterData] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [allApplications, setAllApplications] = useState([]);
  const [activeTab, setActiveTab] = useState('jobs'); // 'jobs' | 'analytics'
  const [loading, setLoading] = useState(true);

  // Company Form Modal
  const [showCompanyModal, setShowCompanyModal] = useState(false);
  const [companyName, setCompanyName] = useState('');
  const [companyWebsite, setCompanyWebsite] = useState('');
  const [creatingCompany, setCreatingCompany] = useState(false);

  // Create Job Modal
  const [showJobModal, setShowJobModal] = useState(defaultAction === 'create');
  const [newJob, setNewJob] = useState({
    title: '',
    description: '',
    location: '',
    employmentType: 'FULL_TIME',
    experienceLevel: 'MID_LEVEL',
    salaryMin: '',
    salaryMax: '',
    skills: []
  });
  const [skillInput, setSkillInput] = useState('');
  const [skillMandatory, setSkillMandatory] = useState(true);
  const [submittingJob, setSubmittingJob] = useState(false);

  // ATS Applicant Drawer / Modal
  const [selectedJobForApplicants, setSelectedJobForApplicants] = useState(null);
  const [applicants, setApplicants] = useState([]);
  const [loadingApplicants, setLoadingApplicants] = useState(false);
  const [updatingAppId, setUpdatingAppId] = useState(null);
  const [statusUpdate, setStatusUpdate] = useState({ status: 'UNDER_REVIEW', note: '' });
  const [activeResumeViewer, setActiveResumeViewer] = useState(null);
  const [sortApplicantsByMatch, setSortApplicantsByMatch] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const [recruiterRes, jobsRes] = await Promise.allSettled([
        api.recruiter.getMe(),
        api.jobs.getMyCreatedJobs()
      ]);

      if (recruiterRes.status === 'fulfilled') {
        setRecruiterData(recruiterRes.value);
      }
      if (jobsRes.status === 'fulfilled') {
        const jobData = jobsRes.value;
        const jobList = Array.isArray(jobData) ? jobData : (jobData?.content || []);
        setJobs(jobList);

        // Fetch applications across all jobs for the analytics dashboard
        if (jobList.length > 0) {
          Promise.allSettled(jobList.map(j => api.recruiter.getJobApplicants(j.id)))
            .then(appResults => {
              const combined = [];
              appResults.forEach(r => {
                if (r.status === 'fulfilled' && r.value) {
                  const items = Array.isArray(r.value) ? r.value : (r.value.content || []);
                  combined.push(...items);
                }
              });
              setAllApplications(combined);
            })
            .catch(() => {});
        }
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

  const handleCreateCompany = async (e) => {
    e.preventDefault();
    setCreatingCompany(true);
    try {
      await api.recruiter.createCompany({ name: companyName, website: companyWebsite });
      showToast('Company profile created! Pending Admin approval.', 'success');
      setShowCompanyModal(false);
      loadData();
    } catch (err) {
      showToast(err.message || 'Failed to create company', 'error');
    } finally {
      setCreatingCompany(false);
    }
  };

  const handleAddSkillToJob = () => {
    if (!skillInput.trim()) return;
    setNewJob((prev) => ({
      ...prev,
      skills: [...prev.skills, { skillName: skillInput.trim().toLowerCase(), mandatory: skillMandatory }]
    }));
    setSkillInput('');
  };

  const handleRemoveSkillFromJob = (index) => {
    setNewJob((prev) => ({
      ...prev,
      skills: prev.skills.filter((_, i) => i !== index)
    }));
  };

  const handleCreateJob = async (e) => {
    e.preventDefault();
    setSubmittingJob(true);
    try {
      const payload = {
        title: newJob.title,
        description: newJob.description,
        location: newJob.location,
        employmentType: newJob.employmentType,
        experienceLevel: newJob.experienceLevel,
        salaryMin: newJob.salaryMin ? parseInt(newJob.salaryMin, 10) : undefined,
        salaryMax: newJob.salaryMax ? parseInt(newJob.salaryMax, 10) : undefined,
        skills: newJob.skills
      };
      await api.jobs.create(payload);
      showToast('Draft job created successfully!', 'success');
      setShowJobModal(false);
      setNewJob({
        title: '',
        description: '',
        location: '',
        employmentType: 'FULL_TIME',
        experienceLevel: 'MID_LEVEL',
        salaryMin: '',
        salaryMax: '',
        skills: []
      });
      loadData();
    } catch (err) {
      showToast(err.message || 'Failed to create job', 'error');
    } finally {
      setSubmittingJob(false);
    }
  };

  const handlePublishJob = async (jobId) => {
    try {
      await api.jobs.publish(jobId);
      showToast('Job published to public board!', 'success');
      loadData();
    } catch (err) {
      showToast(err.message || 'Could not publish job. Is your company approved?', 'error');
    }
  };

  const handleCloseJob = async (jobId) => {
    try {
      await api.jobs.close(jobId);
      showToast('Job marked as CLOSED', 'info');
      loadData();
    } catch (err) {
      showToast(err.message || 'Could not close job', 'error');
    }
  };

  const handleDeleteJob = async (jobId) => {
    if (!confirm('Are you sure you want to delete this draft job?')) return;
    try {
      await api.jobs.delete(jobId);
      showToast('Job deleted', 'info');
      loadData();
    } catch (err) {
      showToast(err.message || 'Only DRAFT jobs can be deleted', 'error');
    }
  };

  const openApplicantsDrawer = async (job) => {
    setSelectedJobForApplicants(job);
    setLoadingApplicants(true);
    try {
      const res = await api.recruiter.getJobApplicants(job.id);
      setApplicants(res || []);
    } catch (err) {
      showToast('Failed to load applicants', 'error');
    } finally {
      setLoadingApplicants(false);
    }
  };

  const handleUpdateApplicantStatus = async (appId) => {
    try {
      await api.recruiter.updateApplicantStatus(appId, statusUpdate);
      showToast(`Applicant moved to ${statusUpdate.status}`, 'success');
      setUpdatingAppId(null);
      // Reload applicants
      const res = await api.recruiter.getJobApplicants(selectedJobForApplicants.id);
      setApplicants(res || []);
    } catch (err) {
      showToast(err.message || 'Failed to update applicant status', 'error');
    }
  };

  return (
    <div style={{ maxWidth: '1280px', margin: '0 auto', padding: '0 20px 60px' }}>
      {/* Recruiter Header & Company Status */}
      <div className="glass-panel" style={{ padding: '28px', marginBottom: '32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '20px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
              <div style={{
                width: '40px',
                height: '40px',
                borderRadius: '8px',
                background: 'rgba(139, 92, 246, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--secondary)'
              }}>
                <Building2 size={20} />
              </div>
              <div>
                <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
                  {recruiterData?.company?.name || 'Recruiter Workspace'}
                </h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.84rem' }}>
                  {recruiterData?.company?.name ? (
                    <span className={`badge ${
                      recruiterData?.company?.status === 'APPROVED' ? 'badge-success' : 'badge-warning'
                    }`}>
                      Company: {recruiterData?.company?.status || 'PENDING'}
                    </span>
                  ) : (
                    <span style={{ color: 'var(--text-dim)' }}>
                      No company linked yet
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            {!recruiterData?.company?.name && (
              <button onClick={() => setShowCompanyModal(true)} className="btn btn-secondary">
                <Building2 size={16} />
                Register Company
              </button>
            )}
            <button onClick={() => setShowJobModal(true)} className="btn btn-primary">
              <Plus size={16} />
              Create Job Posting
            </button>
          </div>
        </div>

        {recruiterData?.company?.status === 'PENDING' && (
          <div style={{
            marginTop: '18px',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--warning-bg)',
            border: '1px solid rgba(245, 158, 11, 0.25)',
            color: '#fde68a',
            fontSize: '0.86rem',
            display: 'flex',
            alignItems: 'center',
            gap: '10px'
          }}>
            <AlertCircle size={18} />
            <span>
              Your company is currently <strong>PENDING approval</strong> by an administrator. You can create DRAFT jobs now, and publish them once approved.
            </span>
          </div>
        )}
      </div>

      {/* Recruiter Workspace Tabs */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '24px' }}>
        <button
          type="button"
          onClick={() => setActiveTab('jobs')}
          className={activeTab === 'jobs' ? 'btn btn-primary' : 'btn btn-secondary'}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <Briefcase size={16} />
          Active Job Postings ({jobs.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('analytics')}
          className={activeTab === 'analytics' ? 'btn btn-primary' : 'btn btn-secondary'}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <BarChart3 size={16} />
          Recruitment Analytics & Insights
        </button>
      </div>

      {activeTab === 'analytics' ? (
        <RecruiterAnalytics jobs={jobs} allApplications={allApplications} />
      ) : (
        /* Posted Jobs Table */
        <div className="glass-panel" style={{ padding: '28px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700 }}>Active Job Postings</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              Manage hiring requirements, stages, and incoming applications.
            </p>
          </div>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
            {jobs.length} jobs created
          </span>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
            Loading your jobs...
          </div>
        ) : jobs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px 20px', color: 'var(--text-dim)' }}>
            <Briefcase size={40} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
            <p>You haven't posted any jobs yet.</p>
            <button onClick={() => setShowJobModal(true)} className="btn btn-secondary btn-sm" style={{ marginTop: '12px' }}>
              Create Your First Job
            </button>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                  <th style={{ padding: '12px 16px' }}>Title & Location</th>
                  <th style={{ padding: '12px 16px' }}>Type</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px' }}>Created</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                    <td style={{ padding: '14px 16px' }}>
                      <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{job.title}</div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>{job.location || 'Remote'}</div>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <span className="badge badge-primary" style={{ fontSize: '0.7rem' }}>
                        {job.employmentType?.replace('_', ' ')}
                      </span>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <span className={`badge ${
                        job.status === 'PUBLISHED' ? 'badge-success' : job.status === 'CLOSED' ? 'badge-danger' : 'badge-warning'
                      }`}>
                        {job.status}
                      </span>
                    </td>
                    <td style={{ padding: '14px 16px', color: 'var(--text-dim)', fontSize: '0.82rem' }}>
                      {new Date(job.createdAt || Date.now()).toLocaleDateString()}
                    </td>
                    <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        <button
                          onClick={() => openApplicantsDrawer(job)}
                          className="btn btn-secondary btn-sm"
                          title="View Applicants"
                        >
                          <Users size={14} />
                          Applicants
                        </button>

                        {job.status === 'DRAFT' && (
                          <button
                            onClick={() => handlePublishJob(job.id)}
                            className="btn btn-success btn-sm"
                            title="Publish to public board"
                          >
                            Publish
                          </button>
                        )}

                        {job.status === 'PUBLISHED' && (
                          <button
                            onClick={() => handleCloseJob(job.id)}
                            className="btn btn-danger btn-sm"
                            title="Close job"
                          >
                            Close
                          </button>
                        )}

                        {job.status === 'DRAFT' && (
                          <button
                            onClick={() => handleDeleteJob(job.id)}
                            className="btn btn-ghost btn-sm"
                            style={{ color: '#f87171' }}
                            title="Delete draft"
                          >
                            <Trash2 size={14} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      )}

      {/* Create Company Modal */}
      {showCompanyModal && (
        <div className="modal-overlay" onClick={() => setShowCompanyModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h3 style={{ fontSize: '1.3rem', fontWeight: 700 }}>Register Hiring Company</h3>
              <button onClick={() => setShowCompanyModal(false)} className="btn-ghost" style={{ padding: '6px' }}>
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreateCompany} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Company Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Acme Technologies"
                  value={companyName}
                  onChange={(e) => setCompanyName(e.target.value)}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Company Website URL
                </label>
                <input
                  type="url"
                  placeholder="https://acme.example.com"
                  value={companyWebsite}
                  onChange={(e) => setCompanyWebsite(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button type="button" onClick={() => setShowCompanyModal(false)} className="btn btn-secondary">
                  Cancel
                </button>
                <button type="submit" disabled={creatingCompany} className="btn btn-primary">
                  {creatingCompany ? 'Submitting...' : 'Register Company'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create Job Modal */}
      {showJobModal && (
        <div className="modal-overlay" onClick={() => setShowJobModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '680px', padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h3 style={{ fontSize: '1.35rem', fontWeight: 700 }}>Create New Job Posting</h3>
              <button onClick={() => setShowJobModal(false)} className="btn-ghost" style={{ padding: '6px' }}>
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleCreateJob} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Job Title
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Staff Backend Engineer"
                  value={newJob.title}
                  onChange={(e) => setNewJob({ ...newJob, title: e.target.value })}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div>
                  <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                    Employment Type
                  </label>
                  <select
                    value={newJob.employmentType}
                    onChange={(e) => setNewJob({ ...newJob, employmentType: e.target.value })}
                  >
                    <option value="FULL_TIME">Full Time</option>
                    <option value="PART_TIME">Part Time</option>
                    <option value="CONTRACT">Contract</option>
                    <option value="INTERNSHIP">Internship</option>
                  </select>
                </div>

                <div>
                  <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                    Experience Level
                  </label>
                  <select
                    value={newJob.experienceLevel}
                    onChange={(e) => setNewJob({ ...newJob, experienceLevel: e.target.value })}
                  >
                    <option value="ENTRY_LEVEL">Entry Level</option>
                    <option value="MID_LEVEL">Mid Level</option>
                    <option value="SENIOR_LEVEL">Senior Level</option>
                    <option value="LEAD">Lead</option>
                    <option value="EXECUTIVE">Executive</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '14px' }}>
                <div>
                  <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                    Location
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. Pune / Remote"
                    value={newJob.location}
                    onChange={(e) => setNewJob({ ...newJob, location: e.target.value })}
                  />
                </div>

                <div>
                  <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                    Min Salary ($)
                  </label>
                  <input
                    type="number"
                    placeholder="e.g. 100000"
                    value={newJob.salaryMin}
                    onChange={(e) => setNewJob({ ...newJob, salaryMin: e.target.value })}
                  />
                </div>

                <div>
                  <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                    Max Salary ($)
                  </label>
                  <input
                    type="number"
                    placeholder="e.g. 150000"
                    value={newJob.salaryMax}
                    onChange={(e) => setNewJob({ ...newJob, salaryMax: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Job Description
                </label>
                <textarea
                  rows={4}
                  required
                  placeholder="Outline responsibilities, team impact, and day-to-day work..."
                  value={newJob.description}
                  onChange={(e) => setNewJob({ ...newJob, description: e.target.value })}
                />
              </div>

              {/* Skills required */}
              <div>
                <label style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '6px', display: 'block' }}>
                  Required Skills
                </label>
                <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                  <input
                    type="text"
                    placeholder="e.g. java, spring-boot, react"
                    value={skillInput}
                    onChange={(e) => setSkillInput(e.target.value)}
                  />
                  <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.82rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                    <input
                      type="checkbox"
                      checked={skillMandatory}
                      onChange={(e) => setSkillMandatory(e.target.checked)}
                      style={{ width: 'auto' }}
                    />
                    Mandatory
                  </label>
                  <button type="button" onClick={handleAddSkillToJob} className="btn btn-secondary btn-sm">
                    Add
                  </button>
                </div>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {newJob.skills.map((s, idx) => (
                    <span key={idx} className={`skill-chip ${s.mandatory ? 'mandatory' : ''}`}>
                      {s.skillName} {s.mandatory && '★'}
                      <button type="button" onClick={() => handleRemoveSkillFromJob(idx)} style={{ background: 'none', color: 'inherit' }}>
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                <button type="button" onClick={() => setShowJobModal(false)} className="btn btn-secondary">
                  Cancel
                </button>
                <button type="submit" disabled={submittingJob} className="btn btn-primary">
                  {submittingJob ? 'Saving...' : 'Save Draft Job'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Applicant Tracking Modal */}
      {selectedJobForApplicants && (
        <div className="modal-overlay" onClick={() => setSelectedJobForApplicants(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '820px', padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
              <div>
                <h3 style={{ fontSize: '1.35rem', fontWeight: 800 }}>
                  Applicants for {selectedJobForApplicants.title}
                </h3>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.84rem' }}>
                  {applicants.length} total applicant(s)
                </p>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                {applicants.length > 1 && (
                  <button
                    onClick={() => setSortApplicantsByMatch(prev => !prev)}
                    className={sortApplicantsByMatch ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm"}
                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                  >
                    <Sparkles size={14} />
                    {sortApplicantsByMatch ? "Sorted by Best Match" : "Sort by Skill Match"}
                  </button>
                )}
                <button onClick={() => setSelectedJobForApplicants(null)} className="btn-ghost" style={{ padding: '6px' }}>
                  <X size={20} />
                </button>
              </div>
            </div>

            {loadingApplicants ? (
              <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                Loading applicants...
              </div>
            ) : applicants.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '50px 20px', color: 'var(--text-dim)' }}>
                <Users size={36} style={{ margin: '0 auto 10px', opacity: 0.5 }} />
                <p>No candidates have applied to this job yet.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {(sortApplicantsByMatch
                  ? [...applicants].sort((a, b) => {
                      const matchA = calculateSkillMatch(selectedJobForApplicants?.requiredSkills || [], a.candidateSkills || []).percentage;
                      const matchB = calculateSkillMatch(selectedJobForApplicants?.requiredSkills || [], b.candidateSkills || []).percentage;
                      return matchB - matchA;
                    })
                  : applicants
                ).map((app) => {
                  const applicantMatch = calculateSkillMatch(
                    selectedJobForApplicants?.requiredSkills || [],
                    app.candidateSkills || []
                  );

                  return (
                  <div key={app.id} style={{
                    background: 'rgba(15, 23, 42, 0.7)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '18px'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '10px', marginBottom: '8px' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontWeight: 700, fontSize: '1rem', color: 'var(--text-main)' }}>
                            {app.candidate?.fullName || `Candidate #${app.candidate?.id}`}
                          </span>
                          <span className={`badge ${
                            app.status === 'SELECTED' ? 'badge-success' : app.status === 'REJECTED' ? 'badge-danger' : 'badge-primary'
                          }`}>
                            {app.status?.replace('_', ' ')}
                          </span>

                          {/* Skill Match Score Badge */}
                          <span
                            style={{
                              fontSize: '0.74rem',
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: '12px',
                              color: applicantMatch.badgeColor,
                              backgroundColor: applicantMatch.badgeBg,
                              border: `1px solid ${applicantMatch.badgeBorder}`,
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px'
                            }}
                          >
                            <Sparkles size={11} />
                            {applicantMatch.percentage}% Match
                          </span>
                        </div>
                        <div style={{ fontSize: '0.82rem', color: 'var(--text-dim)', marginTop: '2px' }}>
                          Applied on {new Date(app.appliedAt || app.updatedAt || Date.now()).toLocaleDateString()}
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        {app.resumeUrl && (
                          <button
                            onClick={() => setActiveResumeViewer({
                              resumeUrl: app.resumeUrl,
                              candidateName: app.candidate?.fullName || 'Candidate'
                            })}
                            className="btn btn-secondary btn-sm"
                            style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
                          >
                            <FileText size={14} color="var(--primary)" />
                            View Resume
                          </button>
                        )}

                        {/* Move Status Button */}
                        {app.status !== 'SELECTED' && app.status !== 'REJECTED' && app.status !== 'WITHDRAWN' && (
                          <button
                            onClick={() => {
                              setUpdatingAppId(app.id);
                              setStatusUpdate({ status: 'UNDER_REVIEW', note: '' });
                            }}
                            className="btn btn-secondary btn-sm"
                          >
                            Update Stage
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Matched and Missing Skills tags */}
                    {(applicantMatch.matchedSkills.length > 0 || applicantMatch.missingSkills.length > 0) && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '12px' }}>
                        {applicantMatch.matchedSkills.map((s, idx) => (
                          <span
                            key={`m-${idx}`}
                            style={{
                              fontSize: '0.72rem',
                              backgroundColor: 'rgba(16, 185, 129, 0.12)',
                              color: '#34d399',
                              border: '1px solid rgba(16, 185, 129, 0.25)',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px'
                            }}
                          >
                            <Check size={11} />
                            {s.name}
                          </span>
                        ))}
                        {applicantMatch.missingSkills.filter(s => s.mandatory).map((s, idx) => (
                          <span
                            key={`mis-${idx}`}
                            style={{
                              fontSize: '0.72rem',
                              backgroundColor: 'rgba(239, 68, 68, 0.1)',
                              color: '#fca5a5',
                              border: '1px solid rgba(239, 68, 68, 0.25)',
                              padding: '2px 8px',
                              borderRadius: '4px'
                            }}
                          >
                            Missing: {s.name} ★
                          </span>
                        ))}
                      </div>
                    )}

                    {app.coverLetter && (
                      <div style={{
                        fontSize: '0.85rem',
                        color: 'var(--text-muted)',
                        background: 'rgba(0,0,0,0.2)',
                        padding: '10px 14px',
                        borderRadius: '6px',
                        marginBottom: '12px'
                      }}>
                        "{app.coverLetter}"
                      </div>
                    )}

                    {/* Status change form */}
                    {updatingAppId === app.id && (
                      <div style={{
                        marginTop: '12px',
                        padding: '14px',
                        borderRadius: '8px',
                        background: 'rgba(30, 41, 59, 0.9)',
                        border: '1px solid var(--border-active)'
                      }}>
                        <h5 style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' }}>
                          Advance Applicant in Hiring Pipeline
                        </h5>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '10px', marginBottom: '10px' }}>
                          <select
                            value={statusUpdate.status}
                            onChange={(e) => setStatusUpdate({ ...statusUpdate, status: e.target.value })}
                          >
                            <option value="UNDER_REVIEW">Under Review</option>
                            <option value="SHORTLISTED">Shortlisted</option>
                            <option value="INTERVIEW">Interview Scheduled</option>
                            <option value="SELECTED">Selected / Hired</option>
                            <option value="REJECTED">Rejected</option>
                          </select>
                          <input
                            type="text"
                            placeholder="Feedback / Reviewer Note (e.g. Passed coding test)"
                            value={statusUpdate.note}
                            onChange={(e) => setStatusUpdate({ ...statusUpdate, note: e.target.value })}
                          />
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                          <button onClick={() => setUpdatingAppId(null)} className="btn btn-ghost btn-sm">
                            Cancel
                          </button>
                          <button onClick={() => handleUpdateApplicantStatus(app.id)} className="btn btn-primary btn-sm">
                            Confirm Change
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Candidate Resume Preview Modal */}
      {activeResumeViewer && (
        <ResumeViewerModal
          resumeUrl={activeResumeViewer.resumeUrl}
          candidateName={activeResumeViewer.candidateName}
          onClose={() => setActiveResumeViewer(null)}
        />
      )}
    </div>
  );
}
