export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

export async function api(path, options = {}) {
  const headers = { 'X-Requested-With': 'XMLHttpRequest', ...options.headers }
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    options = { ...options, body: JSON.stringify(options.body) }
  }
  let response
  try {
    response = await fetch(`/api${path}`, { ...options, credentials: 'same-origin', headers })
  } catch {
    throw new ApiError('连接失败，请检查网络或稍后重试', 0)
  }
  const result = await response.json().catch(() => null)
  if (!response.ok || !result || result.code !== 0) {
    throw new ApiError(result?.message || '服务暂不可用，请稍后重试', response.status)
  }
  return result.data
}

export function queryString(filters) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== '' && value !== null && value !== undefined) params.set(key, value)
  }
  return params.toString()
}
