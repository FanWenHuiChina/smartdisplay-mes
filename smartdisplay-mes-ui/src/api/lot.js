import request from './request'

export const getLotList = (params) => {
  return request.get('/lots', { params })
}

export const trackIn = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/track-in`, data)
}

export const trackOut = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/track-out`, data)
}

export const holdLot = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/hold`, data)
}

export const releaseLot = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/release`, data)
}
