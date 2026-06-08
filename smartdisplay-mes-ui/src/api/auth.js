import request from './request'

export const login = (data) => {
  return request.post('/v1/auth/login', data)
}
