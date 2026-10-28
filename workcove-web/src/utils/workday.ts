import chineseDays from 'chinese-days'

export interface WorkdayInfo {
  workday: boolean
  label: '班' | '休'
  festival: string
}

export interface FestivalCountdown {
  name: string
  days: number
  date: string
}

const LUNAR_FESTIVALS = new Set([
  '春节',
  '元宵节',
  '端午节',
  '中秋节',
  '重阳节',
  '腊八节',
  '除夕',
])

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function localDateKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function festivalName(value: string) {
  return value
    .split(',')
    .map((part) => part.trim())
    .find((part) => /[\u3400-\u9fff]/.test(part)) ?? ''
}

function startOfDay(date: Date) {
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    12,
    0,
    0,
    0,
  )
}

function dayDistance(from: Date, dateKey: string) {
  const [year, month, day] = dateKey.split('-').map(Number)
  const target = new Date(year, month - 1, day, 12)
  return Math.max(
    0,
    Math.round(
      (target.getTime() - startOfDay(from).getTime()) / 86_400_000,
    ),
  )
}

export function workdayInfo(date: Date): WorkdayInfo {
  try {
    const detail = chineseDays.getDayDetail(localDateKey(date))
    return {
      workday: detail.work,
      label: detail.work ? '班' : '休',
      festival: festivalName(detail.name),
    }
  } catch {
    const day = date.getDay()
    const workday = day !== 0 && day !== 6
    return {
      workday,
      label: workday ? '班' : '休',
      festival: '',
    }
  }
}

export function nextFestivalInfo(
  from: Date = new Date(),
): FestivalCountdown | null {
  const today = startOfDay(from)
  const end = new Date(today)
  end.setDate(end.getDate() + 400)
  const candidates = new Map<string, string>()

  try {
    for (const date of chineseDays.getHolidaysInRange(
      today,
      end,
      false,
    )) {
      const detail = chineseDays.getDayDetail(date)
      const name = festivalName(detail.name)
      if (name) candidates.set(date, name)
    }
  } catch {
    // Keep the built-in major festivals when holiday data is unavailable.
  }

  try {
    for (const festival of chineseDays.getLunarFestivals(today, end)) {
      const name = festival.name.find((item) =>
        LUNAR_FESTIVALS.has(item),
      )
      if (name && !candidates.has(festival.date)) {
        candidates.set(festival.date, name)
      }
    }
  } catch {
    // Lunar festival data is optional for the countdown.
  }

  try {
    for (const term of chineseDays.getSolarTermsInRange(today, end)) {
      if (
        term.term === 'the_winter_solstice' &&
        !candidates.has(term.date)
      ) {
        candidates.set(term.date, '冬至节')
      }
      if (
        term.term === 'pure_brightness' &&
        !candidates.has(term.date)
      ) {
        candidates.set(term.date, '清明节')
      }
    }
  } catch {
    // The fixed-date festivals below remain available as a fallback.
  }

  for (const year of [today.getFullYear(), today.getFullYear() + 1]) {
    addFixedCandidate(candidates, today, year, 1, 1, '元旦')
    addFixedCandidate(candidates, today, year, 5, 1, '劳动节')
    addFixedCandidate(candidates, today, year, 10, 1, '国庆节')
  }

  const next = [...candidates.entries()]
    .map(([date, name]) => ({
      date,
      name,
      days: dayDistance(today, date),
    }))
    .filter((festival) => festival.days > 0)
    .sort((left, right) => left.days - right.days)[0]
  return next ?? null
}

function addFixedCandidate(
  candidates: Map<string, string>,
  today: Date,
  year: number,
  month: number,
  day: number,
  name: string,
) {
  const date = new Date(year, month - 1, day, 12)
  if (date.getTime() <= startOfDay(today).getTime()) return
  candidates.set(localDateKey(date), name)
}
