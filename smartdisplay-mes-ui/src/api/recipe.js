import request from './request'

export const getRecipeList = (params) => {
  return request.get('/recipes', { params })
}

export const getRecipeDetail = (id) => {
  return request.get(`/recipes/${id}`)
}

export const searchRecipe = (params) => {
  return request.get('/recipes/search', { params })
}
