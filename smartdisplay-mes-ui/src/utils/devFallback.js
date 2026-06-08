export const isDevFallbackEnabled = () => {
  return __DEV_MOCK_FALLBACK__
}

export const devFallback = (fallbackValue, productionValue) => {
  return __DEV_MOCK_FALLBACK__ ? fallbackValue : productionValue
}

export const warnDevFallback = (message, error) => {
  if (isDevFallbackEnabled()) {
    console.warn(`${message}，使用开发 fallback 数据`, error)
    return
  }
  console.error(`${message}，生产环境不使用 fallback 数据`, error)
}
