const API_BASE = '/api';

/**
 * Helper to get the saved JWT token
 */
export function getToken() {
  return localStorage.getItem('hirehub_token');
}

export function setToken(token) {
  if (token) {
    localStorage.setItem('hirehub_token', token);
  } else {
    localStorage.removeItem('hirehub_token');
  }
}

/**
 * Universal request wrapper handling JWT injection and standard error envelopes
 */
async function request(endpoint, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  // 204 No Content
  if (response.status === 204) {
    return null;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const errorMsg = data?.message || data?.error || `Request failed with status ${response.status}`;
    const err = new Error(errorMsg);
    err.status = response.status;
    err.data = data;
    err.fieldErrors = data?.fieldErrors;
    throw err;
  }

  // Many endpoints return { success: true, data: { ... } } or raw objects/arrays
  return data?.data !== undefined ? data.data : data;
}

export const api = {
  // Auth
  auth: {
    login: (credentials) => request('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    }),
    register: (payload) => request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    getMe: () => request('/auth/me'),
  },

  // Health
  health: () => request('/health'),

  // Jobs
  jobs: {
    list: (params = {}) => {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, val]) => {
        if (val !== undefined && val !== null && val !== '') {
          searchParams.append(key, val);
        }
      });
      const query = searchParams.toString();
      return request(`/jobs${query ? `?${query}` : ''}`);
    },
    getById: (id) => request(`/jobs/${id}`),
    create: (jobData) => request('/jobs', {
      method: 'POST',
      body: JSON.stringify(jobData),
    }),
    update: (id, jobData) => request(`/jobs/${id}`, {
      method: 'PUT',
      body: JSON.stringify(jobData),
    }),
    publish: (id) => request(`/jobs/${id}/publish`, {
      method: 'PATCH',
    }),
    close: (id) => request(`/jobs/${id}/close`, {
      method: 'PATCH',
    }),
    delete: (id) => request(`/jobs/${id}`, {
      method: 'DELETE',
    }),
    getMyCreatedJobs: () => request('/recruiters/me/jobs'),
  },

  // Candidate
  candidate: {
    getProfile: () => request('/candidates/me'),
    updateProfile: (profileData) => request('/candidates/me', {
      method: 'PUT',
      body: JSON.stringify(profileData),
    }),
    getSkills: () => request('/candidates/me/skills'),
    addSkill: (skillData) => request('/candidates/me/skills', {
      method: 'POST',
      body: JSON.stringify(skillData),
    }),
    deleteSkill: (skillId) => request(`/candidates/me/skills/${skillId}`, {
      method: 'DELETE',
    }),
    getApplications: (status) => {
      const query = status ? `?status=${status}` : '';
      return request(`/candidates/me/applications${query}`);
    },
    applyToJob: (jobId, payload) => request(`/jobs/${jobId}/applications`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    withdrawApplication: (appId) => request(`/applications/${appId}/withdraw`, {
      method: 'PATCH',
    }),
    getApplicationHistory: (appId) => request(`/applications/${appId}/history`),
    uploadResume: async (file) => {
      const formData = new FormData();
      formData.append('file', file);
      const token = getToken();
      const res = await fetch('/api/candidates/me/resume', {
        method: 'POST',
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: formData,
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || data?.error || 'Failed to upload resume');
      }
      return data?.data !== undefined ? data.data : data;
    },
    deleteResume: () => request('/candidates/me/resume', {
      method: 'DELETE',
    }),
  },

  // Resumes
  resumes: {
    getResumeBlobUrl: async (resumeUrl) => {
      const token = getToken();
      const url = resumeUrl.startsWith('/') ? resumeUrl : `/api/resumes/${resumeUrl}`;
      const res = await fetch(url, {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      if (!res.ok) {
        throw new Error('Failed to load resume document');
      }
      const blob = await res.blob();
      return URL.createObjectURL(blob);
    },
  },

  // Recruiter & Companies
  recruiter: {
    getMe: () => request('/recruiters/me'),
    updateMe: (data) => request('/recruiters/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
    createCompany: (companyData) => request('/companies', {
      method: 'POST',
      body: JSON.stringify(companyData),
    }),
    getCompany: (id) => request(`/companies/${id}`),
    getJobApplicants: (jobId, status) => {
      const query = status ? `?status=${status}` : '';
      return request(`/jobs/${jobId}/applications${query}`);
    },
    updateApplicantStatus: (appId, { status, note }) => request(`/applications/${appId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, note }),
    }),
  },

  // Admin
  admin: {
    getPendingCompanies: () => request('/admin/companies?status=PENDING'),
    decideCompany: (companyId, decision) => request(`/admin/companies/${companyId}/approval`, {
      method: 'PATCH',
      body: JSON.stringify({ decision }),
    }),
  },

  // Notifications (Real-Time SSE & History)
  notifications: {
    getRecent: () => request('/notifications/recent'),
    getStreamUrl: () => {
      const token = getToken();
      return `${API_BASE}/notifications/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`;
    },
  },
};
