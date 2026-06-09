import request from './request'

export const getRecipeList = (params) => {
  return request.get('/v1/recipes', { params })
}

export const getRecipeDetail = (id) => {
  return request.get(`/v1/recipes/${id}`)
}

export const searchRecipe = (params) => {
  return request.get('/v1/recipes/search', { params })
}

export const publishRecipe = (id) => {
  return request.post(`/v1/recipes/${id}/publish`)
}
