import React, { useState, useEffect } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import JobDetailModal from './JobDetailModal';
import { calculateSkillMatch } from '../utils/skillMatcher';
import { 
  Search, 
  MapPin, 
  Briefcase, 
  DollarSign, 
  Filter, 
  SlidersHorizontal, 
  RotateCcw, 
  ChevronLeft, 
  ChevronRight, 
  Sparkles,
  Building2,
  Clock
} from 'lucide-react';

export default function JobExplorer({ onOpenAuth }) {
  const { showToast, user } = useAuth();
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedJob, setSelectedJob] = useState(null);
  const [candidateSkills, setCandidateSkills] = useState([]);

  // Search & Filter State
  const [keyword, setKeyword] = useState('');
  const [location, setLocation] = useState('');
  const [employmentType, setEmploymentType] = useState('');
  const [experienceLevel, setExperienceLevel] = useState('');
  const [minSalary, setMinSalary] = useState('');
  const [sort, setSort] = useState('createdAt,desc');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    if (user && user.role === 'CANDIDATE') {
      api.candidate.getSkills()
        .then(res => setCandidateSkills(res || []))
        .catch(() => {});
    } else {
      setCandidateSkills([]);
    }
  }, [user]);

  const fetchJobs = async () => {
    setLoading(true);
    try {
      const backendSort = sort === 'skillMatch' ? 'createdAt,desc' : sort;
      const res = await api.jobs.list({
        keyword,
        location,
        employmentType,
        experienceLevel,
        minSalary: minSalary || undefined,
        page,
        size: 9,
        sort: backendSort,
      });

      let loadedJobs = [];
      if (res && res.content) {
        loadedJobs = res.content;
        setTotalPages(res.totalPages || 1);
        setTotalElements(res.totalElements || 0);
      } else if (Array.isArray(res)) {
        loadedJobs = res;
        setTotalPages(1);
        setTotalElements(res.length);
      }

      if (sort === 'skillMatch' && candidateSkills.length > 0) {
        loadedJobs = [...loadedJobs].sort((a, b) => {
          const scoreA = calculateSkillMatch(a.requiredSkills, candidateSkills).percentage;
          const scoreB = calculateSkillMatch(b.requiredSkills, candidateSkills).percentage;
          return scoreB - scoreA;
        });
      }

      setJobs(loadedJobs);
    } catch (err) {
      console.error('Failed to load jobs:', err);
      showToast('Could not load jobs', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs();
  }, [page, sort, candidateSkills.length]);

  const handleSearch = (e) => {
    e.preventDefault();
    setPage(0);
    fetchJobs();
  };

  const resetFilters = () => {
    setKeyword('');
    setLocation('');
    setEmploymentType('');
    setExperienceLevel('');
    setMinSalary('');
    setSort('createdAt,desc');
    setPage(0);
    setTimeout(fetchJobs, 50);
  };

  const formatSalary = (min, max) => {
    if (!min && !max) return 'Competitive';
    const fmt = (num) => new Intl.NumberFormat('en-US', { notation: 'compact', compactDisplay: 'short' }).format(num);
    if (min && max) return `$${fmt(min)} – $${fmt(max)}`;
    if (min) return `From $${fmt(min)}`;
    return `Up to $${fmt(max)}`;
  };

  return (
    <div style={{ maxWidth: '1280px', margin: '0 auto', padding: '0 20px 60px' }}>
      {/* Hero Section */}
      <section style={{
        textAlign: 'center',
        padding: '50px 20px 40px',
        position: 'relative',
        overflow: 'hidden'
      }}>
        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '8px',
          background: 'rgba(99, 102, 241, 0.12)',
          border: '1px solid rgba(99, 102, 241, 0.3)',
          padding: '6px 16px',
          borderRadius: '9999px',
          color: '#a5b4fc',
          fontSize: '0.84rem',
          fontWeight: 600,
          marginBottom: '18px'
        }}>
          <Sparkles size={15} />
          High-Performance Recruitment & Job Portal
        </div>

        <h1 style={{
          fontSize: 'clamp(2.2rem, 5vw, 3.4rem)',
          fontWeight: 800,
          lineHeight: '1.15',
          letterSpacing: '-0.03em',
          maxWidth: '850px',
          margin: '0 auto 16px'
        }}>
          Find Verified Tech Jobs With an <span className="gradient-text">Open Pipeline</span>
        </h1>

        <p style={{
          color: 'var(--text-muted)',
          fontSize: '1.1rem',
          maxWidth: '650px',
          margin: '0 auto 36px',
          lineHeight: '1.6'
        }}>
          Direct transparent hiring with real-time status transitions from application to offer.
        </p>

        {/* Stats strip */}
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '32px',
          flexWrap: 'wrap',
          marginBottom: '20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#38bdf8' }}>100%</span>
            <span style={{ fontSize: '0.82rem', color: 'var(--text-dim)' }}>Audited Pipeline</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#34d399' }}>Instant</span>
            <span style={{ fontSize: '0.82rem', color: 'var(--text-dim)' }}>Status Tracking</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#a78bfa' }}>Verified</span>
            <span style={{ fontSize: '0.82rem', color: 'var(--text-dim)' }}>Companies</span>
          </div>
        </div>
      </section>

      {/* Search & Filter Bar */}
      <section className="glass-panel" style={{ padding: '22px', marginBottom: '36px' }}>
        <form onSubmit={handleSearch} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '12px'
          }}>
            {/* Keyword */}
            <div style={{ position: 'relative' }}>
              <Search size={17} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '13px' }} />
              <input
                type="text"
                placeholder="Job title, keyword..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                style={{ paddingLeft: '38px' }}
              />
            </div>

            {/* Location */}
            <div style={{ position: 'relative' }}>
              <MapPin size={17} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '13px' }} />
              <input
                type="text"
                placeholder="Location (e.g. Pune, Remote)..."
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                style={{ paddingLeft: '38px' }}
              />
            </div>

            {/* Employment Type */}
            <select
              value={employmentType}
              onChange={(e) => setEmploymentType(e.target.value)}
            >
              <option value="">All Employment Types</option>
              <option value="FULL_TIME">Full Time</option>
              <option value="PART_TIME">Part Time</option>
              <option value="CONTRACT">Contract</option>
              <option value="INTERNSHIP">Internship</option>
            </select>

            {/* Experience Level */}
            <select
              value={experienceLevel}
              onChange={(e) => setExperienceLevel(e.target.value)}
            >
              <option value="">All Experience Levels</option>
              <option value="ENTRY_LEVEL">Entry Level</option>
              <option value="MID_LEVEL">Mid Level</option>
              <option value="SENIOR_LEVEL">Senior Level</option>
              <option value="LEAD">Lead</option>
              <option value="EXECUTIVE">Executive</option>
            </select>
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
            paddingTop: '8px',
            borderTop: '1px solid var(--border-subtle)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '0.84rem', color: 'var(--text-dim)' }}>Min Salary ($):</span>
                <input
                  type="number"
                  placeholder="e.g. 80000"
                  value={minSalary}
                  onChange={(e) => setMinSalary(e.target.value)}
                  style={{ width: '130px', padding: '6px 10px', fontSize: '0.85rem' }}
                />
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '0.84rem', color: 'var(--text-dim)' }}>Sort:</span>
                <select
                  value={sort}
                  onChange={(e) => setSort(e.target.value)}
                  style={{ width: '175px', padding: '6px 10px', fontSize: '0.85rem' }}
                >
                  {user?.role === 'CANDIDATE' && candidateSkills.length > 0 && (
                    <option value="skillMatch">✨ Best Skill Match</option>
                  )}
                  <option value="createdAt,desc">Newest First</option>
                  <option value="createdAt,asc">Oldest First</option>
                  <option value="salaryMax,desc">Highest Salary</option>
                  <option value="title,asc">Title (A-Z)</option>
                </select>
              </div>

              <button
                type="button"
                onClick={resetFilters}
                className="btn btn-ghost btn-sm"
              >
                <RotateCcw size={14} />
                Reset
              </button>
            </div>

            <button type="submit" className="btn btn-primary">
              <Search size={16} />
              Filter {totalElements > 0 ? `(${totalElements})` : ''} Jobs
            </button>
          </div>
        </form>
      </section>

      {/* Job Grid */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
          <div style={{ display: 'inline-block', animation: 'spin 1s linear infinite' }}>
            <RotateCcw size={32} color="var(--primary)" />
          </div>
          <p style={{ marginTop: '12px' }}>Loading open opportunities...</p>
        </div>
      ) : jobs.length === 0 ? (
        <div className="glass-panel" style={{ textAlign: 'center', padding: '60px 20px' }}>
          <Briefcase size={48} color="var(--text-dim)" style={{ margin: '0 auto 16px' }} />
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '6px' }}>No Published Jobs Found</h3>
          <p style={{ color: 'var(--text-muted)', maxWidth: '420px', margin: '0 auto 20px' }}>
            There are no active jobs matching your criteria right now. Try loosening your search filters or check back later!
          </p>
          <button onClick={resetFilters} className="btn btn-secondary btn-sm">
            Reset Filters
          </button>
        </div>
      ) : (
        <>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
            gap: '20px',
            marginBottom: '36px'
          }}>
            {jobs.map((job) => {
              const skillMatch = candidateSkills.length > 0
                ? calculateSkillMatch(job.requiredSkills, candidateSkills)
                : null;

              return (
              <div 
                key={job.id} 
                className="glass-panel glass-panel-hover"
                style={{
                  padding: '24px',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '16px',
                  cursor: 'pointer'
                }}
                onClick={() => setSelectedJob(job)}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <div style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '8px',
                        background: 'rgba(99, 102, 241, 0.15)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--primary)'
                      }}>
                        <Building2 size={18} />
                      </div>
                      <div>
                        <h4 style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                          {job.company?.name || 'Verified Partner'}
                        </h4>
                        <span style={{ fontSize: '0.74rem', color: 'var(--text-dim)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <MapPin size={12} />
                          {job.location || 'Remote'}
                        </span>
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      {skillMatch && user?.role === 'CANDIDATE' && (
                        <span
                          style={{
                            fontSize: '0.72rem',
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: '12px',
                            color: skillMatch.badgeColor,
                            backgroundColor: skillMatch.badgeBg,
                            border: `1px solid ${skillMatch.badgeBorder}`,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px'
                          }}
                        >
                          <Sparkles size={11} />
                          {skillMatch.percentage}% Match
                        </span>
                      )}
                      <span className="badge badge-primary" style={{ fontSize: '0.7rem' }}>
                        {job.employmentType?.replace('_', ' ')}
                      </span>
                    </div>
                  </div>

                  <h3 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '8px', color: 'var(--text-main)' }}>
                    {job.title}
                  </h3>

                  <p style={{
                    color: 'var(--text-dim)',
                    fontSize: '0.85rem',
                    lineHeight: '1.5',
                    marginBottom: '14px',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden'
                  }}>
                    {job.description}
                  </p>

                  {/* Skills tags */}
                  {job.requiredSkills && job.requiredSkills.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '12px' }}>
                      {job.requiredSkills.slice(0, 4).map((s, idx) => (
                        <span key={idx} className={`skill-chip ${s.mandatory ? 'mandatory' : ''}`} style={{ fontSize: '0.72rem', padding: '2px 8px' }}>
                          {s.skill?.name}
                        </span>
                      ))}
                      {job.requiredSkills.length > 4 && (
                        <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', alignSelf: 'center' }}>
                          +{job.requiredSkills.length - 4} more
                        </span>
                      )}
                    </div>
                  )}
                </div>

                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  paddingTop: '12px',
                  borderTop: '1px solid var(--border-subtle)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#34d399', fontWeight: 700, fontSize: '0.92rem' }}>
                    <DollarSign size={16} />
                    {formatSalary(job.salaryMin, job.salaryMax)}
                  </div>

                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedJob(job);
                    }}
                    className="btn btn-secondary btn-sm"
                  >
                    View Details
                  </button>
                </div>
              </div>
              );
            })}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px' }}>
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="btn btn-secondary btn-sm"
              >
                <ChevronLeft size={16} />
                Previous
              </button>
              <span style={{ fontSize: '0.88rem', color: 'var(--text-muted)' }}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="btn btn-secondary btn-sm"
              >
                Next
                <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      )}

      {/* Job Detail & Application Modal */}
      {selectedJob && (
        <JobDetailModal
          job={selectedJob}
          candidateSkills={candidateSkills}
          onClose={() => setSelectedJob(null)}
          onApplied={() => {
            fetchJobs();
          }}
          onOpenAuth={onOpenAuth}
        />
      )}
    </div>
  );
}
