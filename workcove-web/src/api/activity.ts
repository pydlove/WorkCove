import { get } from '@/api/http'
import type { SogouInputStats } from '@/types/activity'

export const getSogouInputStats = () =>
  get<SogouInputStats>('/development/activity/sogou-input')
