import request from './request'

export const getSites = () => {
  return request.get('/master-data/sites')
}

export const getProductionLines = (params = {}) => {
  return request.get('/master-data/production-lines', { params })
}

export const getShifts = (params = {}) => {
  return request.get('/master-data/shifts', { params })
}

export const getProcessSteps = () => {
  return request.get('/master-data/process-steps')
}

export const getEquipments = () => {
  return request.get('/master-data/equipments')
}
