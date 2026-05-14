import request from '../utils/request'

export function runNaturalQuery(data: { question: string; page?: number; size?: number }) {
  return request.post('/natural-query', data) as unknown as Promise<any>
}
