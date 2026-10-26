import React, { useState, useEffect, useRef } from 'react';
import { Bell, CheckCheck, X, Briefcase, FileCheck, Info, Sparkles } from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';

export default function NotificationBell() {
  const { isAuthenticated, token, showToast } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Fetch recent notifications & setup SSE connection
  useEffect(() => {
    if (!isAuthenticated) {
      setNotifications([]);
      setUnreadCount(0);
      return;
    }

    // 1. Initial fetch of recent history
    api.notifications.getRecent()
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setNotifications(list);
        setUnreadCount(list.length);
      })
      .catch((err) => {
        console.warn('Could not load recent notifications:', err.message);
      });

    // 2. Open Server-Sent Events (SSE) Stream
    const streamUrl = api.notifications.getStreamUrl();
    let eventSource;

    try {
      eventSource = new EventSource(streamUrl);

      eventSource.addEventListener('NOTIFICATION', (event) => {
        try {
          const newNotif = JSON.parse(event.data);
          setNotifications((prev) => [newNotif, ...prev]);
          setUnreadCount((c) => c + 1);

          // Toast notification in real-time
          if (showToast) {
            showToast(`${newNotif.title}: ${newNotif.message}`, 'info');
          }
        } catch (e) {
          console.error('Error parsing SSE notification payload:', e);
        }
      });

      eventSource.addEventListener('CONNECTED', (event) => {
        console.log('SSE notification stream established');
      });

      eventSource.onerror = (err) => {
        // SSE auto-reconnects on error; log softly
        console.debug('SSE connection state change / reconnecting');
      };
    } catch (e) {
      console.warn('EventSource initialization failed:', e);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [isAuthenticated, token]);

  const markAllAsRead = () => {
    setUnreadCount(0);
  };

  const getIconForType = (type) => {
    switch (type) {
      case 'NEW_APPLICATION':
        return <Briefcase size={16} color="#8b5cf6" />;
      case 'STATUS_CHANGE':
        return <FileCheck size={16} color="#10b981" />;
      default:
        return <Sparkles size={16} color="#38bdf8" />;
    }
  };

  const formatRelativeTime = (timestamp) => {
    if (!timestamp) return 'Just now';
    try {
      const date = new Date(timestamp);
      const diffSec = Math.floor((new Date() - date) / 1000);
      if (diffSec < 60) return 'Just now';
      if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
      if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
      return `${Math.floor(diffSec / 86400)}d ago`;
    } catch {
      return 'Recent';
    }
  };

  if (!isAuthenticated) return null;

  return (
    <div style={{ position: 'relative' }} ref={dropdownRef}>
      <button
        onClick={() => {
          setIsOpen(!isOpen);
          if (!isOpen && unreadCount > 0) {
            setUnreadCount(0);
          }
        }}
        className="btn btn-ghost btn-sm"
        style={{
          position: 'relative',
          padding: '8px 10px',
          borderRadius: '10px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
        title="Real-Time Notifications"
      >
        <Bell size={18} color={unreadCount > 0 ? '#38bdf8' : 'var(--text-muted)'} />
        {unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '2px',
              right: '2px',
              background: 'linear-gradient(135deg, #ef4444, #f43f5e)',
              color: '#ffffff',
              fontSize: '0.65rem',
              fontWeight: 800,
              minWidth: '17px',
              height: '17px',
              borderRadius: '999px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 3px',
              boxShadow: '0 0 10px rgba(239, 68, 68, 0.6)',
              animation: 'pulse 2s infinite',
            }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          className="glass-panel"
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: '360px',
            maxHeight: '440px',
            zIndex: 1000,
            borderRadius: '14px',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            boxShadow: '0 20px 40px rgba(0, 0, 0, 0.45)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '14px 18px',
              borderBottom: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'rgba(255, 255, 255, 0.02)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Bell size={16} color="var(--primary)" />
              <span style={{ fontWeight: 700, fontSize: '0.92rem', color: 'var(--text-main)' }}>
                Notifications
              </span>
              <span
                className="badge badge-primary"
                style={{ fontSize: '0.65rem', padding: '1px 6px' }}
              >
                SSE Live
              </span>
            </div>

            {notifications.length > 0 && (
              <button
                onClick={markAllAsRead}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-dim)',
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                }}
                className="hover-bright"
              >
                <CheckCheck size={13} />
                Clear
              </button>
            )}
          </div>

          {/* Notifications List */}
          <div
            style={{
              overflowY: 'auto',
              flex: 1,
              maxHeight: '360px',
              padding: '8px',
            }}
          >
            {notifications.length === 0 ? (
              <div
                style={{
                  padding: '40px 20px',
                  textAlign: 'center',
                  color: 'var(--text-dim)',
                }}
              >
                <Sparkles size={28} style={{ margin: '0 auto 10px', opacity: 0.5 }} />
                <p style={{ margin: 0, fontSize: '0.88rem' }}>No new notifications</p>
                <p style={{ margin: '4px 0 0', fontSize: '0.74rem' }}>
                  Live status updates & applications will appear here
                </p>
              </div>
            ) : (
              notifications.map((notif, index) => (
                <div
                  key={notif.id || index}
                  style={{
                    padding: '12px',
                    borderRadius: '10px',
                    marginBottom: '6px',
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid rgba(255, 255, 255, 0.05)',
                    display: 'flex',
                    gap: '12px',
                    transition: 'background 0.2s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.06)')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)')}
                >
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      background: 'rgba(255, 255, 255, 0.05)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    {getIconForType(notif.type)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '3px',
                      }}
                    >
                      <span
                        style={{
                          fontWeight: 600,
                          fontSize: '0.82rem',
                          color: 'var(--text-main)',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        {notif.title}
                      </span>
                      <span style={{ fontSize: '0.68rem', color: 'var(--text-dim)', flexShrink: 0 }}>
                        {formatRelativeTime(notif.timestamp)}
                      </span>
                    </div>
                    <p
                      style={{
                        margin: 0,
                        fontSize: '0.76rem',
                        color: 'var(--text-muted)',
                        lineHeight: '1.35',
                        wordBreak: 'break-word',
                      }}
                    >
                      {notif.message}
                    </p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
