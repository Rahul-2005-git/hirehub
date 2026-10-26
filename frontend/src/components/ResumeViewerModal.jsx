import React, { useEffect, useState } from 'react';
import { X, Download, ExternalLink, Loader2, AlertCircle, FileText } from 'lucide-react';
import { api } from '../api';

export default function ResumeViewerModal({ resumeUrl, candidateName, onClose }) {
  const [blobUrl, setBlobUrl] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    let createdUrl = null;

    async function loadResume() {
      if (!resumeUrl) {
        setError('No resume document attached.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        if (resumeUrl.startsWith('http://') || resumeUrl.startsWith('https://')) {
          // External link
          setBlobUrl(resumeUrl);
        } else {
          // Internal API resume
          createdUrl = await api.resumes.getResumeBlobUrl(resumeUrl);
          if (active) {
            setBlobUrl(createdUrl);
          }
        }
      } catch (err) {
        if (active) {
          setError(err.message || 'Failed to load resume preview.');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadResume();

    return () => {
      active = false;
      if (createdUrl && createdUrl.startsWith('blob:')) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [resumeUrl]);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(8px)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        animation: 'fadeIn 0.2s ease-out',
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '900px',
          height: '90vh',
          backgroundColor: '#0f172a',
          borderRadius: '16px',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 24px',
            borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#1e293b',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                backgroundColor: 'rgba(59, 130, 246, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#60a5fa',
              }}
            >
              <FileText size={20} />
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 600, color: '#f8fafc' }}>
                {candidateName ? `${candidateName}'s Resume` : 'Resume Document'}
              </h3>
              <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>PDF Viewer & Document Preview</span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {blobUrl && (
              <a
                href={blobUrl}
                download={`${(candidateName || 'resume').replace(/\s+/g, '_')}_Resume.pdf`}
                target="_blank"
                rel="noreferrer"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 14px',
                  borderRadius: '8px',
                  backgroundColor: '#2563eb',
                  color: '#ffffff',
                  fontSize: '0.85rem',
                  fontWeight: 500,
                  textDecoration: 'none',
                  transition: 'background-color 0.15s ease',
                }}
              >
                <Download size={15} />
                Download
              </a>
            )}

            {blobUrl && (
              <a
                href={blobUrl}
                target="_blank"
                rel="noreferrer"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 14px',
                  borderRadius: '8px',
                  backgroundColor: 'rgba(255, 255, 255, 0.08)',
                  color: '#cbd5e1',
                  fontSize: '0.85rem',
                  fontWeight: 500,
                  textDecoration: 'none',
                }}
              >
                <ExternalLink size={15} />
                Open Tab
              </a>
            )}

            <button
              onClick={onClose}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#94a3b8',
                cursor: 'pointer',
                padding: '8px',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div style={{ flex: 1, backgroundColor: '#020617', position: 'relative', overflow: 'hidden' }}>
          {loading && (
            <div
              style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                color: '#94a3b8',
              }}
            >
              <Loader2 size={36} className="animate-spin" style={{ animation: 'spin 1s linear infinite' }} />
              <p style={{ margin: 0, fontSize: '0.95rem' }}>Loading PDF resume document...</p>
            </div>
          )}

          {error && !loading && (
            <div
              style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                padding: '24px',
                textAlign: 'center',
              }}
            >
              <AlertCircle size={40} color="#f87171" />
              <h4 style={{ margin: 0, color: '#f8fafc', fontSize: '1.1rem' }}>Unable to display preview</h4>
              <p style={{ margin: 0, color: '#94a3b8', maxWidth: '400px', fontSize: '0.9rem' }}>{error}</p>
              {resumeUrl && (
                <a
                  href={resumeUrl}
                  target="_blank"
                  rel="noreferrer"
                  style={{
                    marginTop: '8px',
                    padding: '8px 16px',
                    borderRadius: '8px',
                    backgroundColor: '#3b82f6',
                    color: 'white',
                    textDecoration: 'none',
                    fontSize: '0.85rem',
                  }}
                >
                  Try direct link
                </a>
              )}
            </div>
          )}

          {blobUrl && !loading && (
            <iframe
              src={blobUrl}
              title="Resume Preview"
              style={{
                width: '100%',
                height: '100%',
                border: 'none',
                backgroundColor: '#1e293b',
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
