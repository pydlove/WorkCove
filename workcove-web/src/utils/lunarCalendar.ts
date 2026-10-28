const lunarDateFormatter = new Intl.DateTimeFormat(
  'zh-CN-u-ca-chinese',
  {
    month: 'long',
    day: 'numeric',
  },
)

const lunarDigits = [
  '',
  '一',
  '二',
  '三',
  '四',
  '五',
  '六',
  '七',
  '八',
  '九',
]

function formatLunarDay(day: number) {
  if (!Number.isFinite(day)) return ''
  if (day === 10) return '初十'
  if (day < 10) return `初${lunarDigits[day]}`
  if (day < 20) return `十${lunarDigits[day - 10]}`
  if (day === 20) return '二十'
  if (day < 30) return `廿${lunarDigits[day - 20]}`
  return '三十'
}

export function lunarDayName(date: Date) {
  const day = Number(
    lunarDateFormatter
      .formatToParts(date)
      .find((part) => part.type === 'day')?.value,
  )
  return formatLunarDay(day)
}

export function lunarMonthName(date: Date) {
  return lunarDateFormatter
    .formatToParts(date)
    .find((part) => part.type === 'month')?.value ?? ''
}

export function lunarDateName(date: Date) {
  return `${lunarMonthName(date)}${lunarDayName(date)}`
}
