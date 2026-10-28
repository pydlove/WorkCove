export interface DailyActivity {
  date: string
  typedCharacters: number
  mouseClicks: number
  updatedAt: string
}

export interface SogouDailyInput {
  date: string
  count: number
}

export interface SogouInputStats {
  installed: boolean
  serviceAvailable: boolean
  appPath: string | null
  totalInput: number
  currentSpeed: number
  historySpeed: number
  historySpeedAt: number | null
  days: SogouDailyInput[]
  message: string
  checkedAt: string
}
