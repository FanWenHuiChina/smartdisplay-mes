import request from './request'

export const getLotList = (params) => {
  return request.get('/v1/lots', { params })
}

export const trackIn = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/track-in`, data)
}

export const trackOut = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/track-out`, data)
}

export const holdLot = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/hold`, data)
}

export const releaseLot = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/release`, data)
}

export const reworkLot = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/rework`, data)
}

export const scrapLot = (lotNo, data) => {
  return request.post(`/v1/lots/${lotNo}/scrap`, data)
}
