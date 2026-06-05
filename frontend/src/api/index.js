import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 300000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const documentApi = {
  getDocuments: (params) => api.get('/documents', { params }),
  getDocument: (id) => api.get(`/documents/${id}`),
  getDocumentResult: (id) => api.get(`/documents/${id}/result`),
  createDocument: (data) => api.post('/documents', data),
  processDocument: (id) => api.post(`/documents/${id}/process`),
  infer: (text) => api.post('/documents/infer', { text }),
  updateEntity: (docId, entityId, data) => 
    api.put(`/documents/${docId}/entities/${entityId}`, data),
  addEntity: (docId, data) => api.post(`/documents/${docId}/entities`, data),
  deleteEntity: (docId, entityId) => 
    api.delete(`/documents/${docId}/entities/${entityId}`),
  updateRelation: (docId, relationId, data) => 
    api.put(`/documents/${docId}/relations/${relationId}`, data),
  addRelation: (docId, data) => api.post(`/documents/${docId}/relations`, data),
  deleteRelation: (docId, relationId) => 
    api.delete(`/documents/${docId}/relations/${relationId}`),
  saveAnnotation: (docId) => api.post(`/documents/${docId}/annotate`),
  getStatusCounts: () => api.get('/documents/status-counts'),
  batchUpdateStatus: (documentIds, status) => 
    api.post('/documents/batch-status', { documentIds, status }),
  checkConsistency: () => api.get('/documents/consistency-check'),
  resolveConsistency: (entityText, targetType) => 
    api.post('/documents/resolve-consistency', { entityText, targetType }),
  exportAnnotations: (documentIds) => 
    api.post('/documents/export', { documentIds }, { responseType: 'json' })
}

export const batchTaskApi = {
  getTasks: (params) => api.get('/batch-tasks', { params }),
  getTask: (id) => api.get(`/batch-tasks/${id}`),
  uploadAndProcess: (formData) => 
    api.post('/batch-tasks/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  processJson: (data) => api.post('/batch-tasks/json', data)
}

export const modelApi = {
  getModels: () => api.get('/models'),
  getModel: (id) => api.get(`/models/${id}`),
  getActiveModel: () => api.get('/models/active'),
  createModel: (data) => api.post('/models', data),
  activateModel: (id) => api.post(`/models/${id}/activate`),
  updateModel: (id, data) => api.put(`/models/${id}`, data),
  deleteModel: (id) => api.delete(`/models/${id}`),
  uploadModel: (formData) => 
    api.post('/models/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  evaluateModel: (id, formData) => 
    api.post(`/models/${id}/evaluate`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
}

export default api
