import { SOURCE_DOCS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put, del, uploadFile, downloadFile } from './client.js'

export const sourceDocs = {
  list:               (params)             => isDemo.value ? Promise.resolve(SOURCE_DOCS)                                   : get(`/api/v1/source-documents?${new URLSearchParams(params)}`),
  get:                (id)                 => isDemo.value ? Promise.resolve(SOURCE_DOCS.find(d => d.id===id))             : get(`/api/v1/source-documents/${id}`),
  create:             (body)               => isDemo.value ? Promise.resolve({ ...body, id: String(Date.now()) })           : post('/api/v1/source-documents', body),
  update:             (id, body)           => isDemo.value ? Promise.resolve({ ...body, id })                               : put(`/api/v1/source-documents/${id}`, body),
  delete:             (id)                 => isDemo.value ? Promise.resolve()                                              : del(`/api/v1/source-documents/${id}`),
  submit:             (id)                 => isDemo.value ? Promise.resolve({ id, status: 'SUBMITTED' })                   : post(`/api/v1/source-documents/${id}/submit`),
  review:             (id)                 => isDemo.value ? Promise.resolve({ id, status: 'REVIEWED' })                    : post(`/api/v1/source-documents/${id}/review`),
  approve:            (id)                 => isDemo.value ? Promise.resolve({ id, status: 'APPROVED' })                    : post(`/api/v1/source-documents/${id}/approve`),
  void:               (id)                 => isDemo.value ? Promise.resolve({ id, status: 'VOID' })                        : post(`/api/v1/source-documents/${id}/void`),
  archive:            (id)                 => isDemo.value ? Promise.resolve({ id, status: 'ARCHIVED' })                    : post(`/api/v1/source-documents/${id}/archive`),
  restore:            (id)                 => isDemo.value ? Promise.resolve({ id, status: 'DRAFT' })                       : post(`/api/v1/source-documents/${id}/restore`),
  listAttachments:    (id)                 => isDemo.value ? Promise.resolve([])                                            : get(`/api/v1/source-documents/${id}/attachments`),
  uploadAttachment:   (id, formData)       => isDemo.value ? Promise.resolve({ id: String(Date.now()), fileName: 'demo.pdf', fileSize: 0 }) : uploadFile(`/api/v1/source-documents/${id}/attachments`, formData),
  removeAttachment:   (docId, attId)       => isDemo.value ? Promise.resolve()                                              : del(`/api/v1/source-documents/${docId}/attachments/${attId}`),
  downloadAttachment: (docId, attId, name) => isDemo.value ? Promise.resolve()                                              : downloadFile(`/api/v1/source-documents/${docId}/attachments/${attId}/download`, name),
}
