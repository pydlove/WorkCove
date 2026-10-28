import type {
  WellnessAction,
  WellnessActionType,
  WellnessData,
  WellnessDaySummary,
  WellnessRestSession,
} from '@/types/wellness'

export interface WorkTimeInterval {
  startAt: number
  endAt: number
}

export interface WorkTimeCurvePoint {
  at: number
  workMs: number
}

export interface WorkTimeCurve {
  startAt: number
  endAt: number
  workMs: number
  restMs: number
  workIntervals: WorkTimeInterval[]
  restIntervals: WorkTimeInterval[]
  points: WorkTimeCurvePoint[]
  clockInAt: string | null
  clockOutAt: string | null
  active: boolean
}

export function localDateKey(value: Date | string) {
  const date = value instanceof Date ? value : new Date(value)
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

export function formatWellnessDuration(value: number) {
  const minutes = Math.max(0, Math.round(value / 60_000))
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest > 0 ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}

export function wellnessDaySummary(
  data: WellnessData,
  date: string,
  now: Date = new Date(),
): WellnessDaySummary {
  const actions = data.actions
    .filter((action) => localDateKey(action.occurredAt) === date)
    .sort(
      (left, right) =>
        new Date(left.occurredAt).getTime() -
        new Date(right.occurredAt).getTime(),
    )
  const rests = data.restSessions
    .filter((rest) => localDateKey(rest.startedAt) === date)
    .sort(
      (left, right) =>
        new Date(left.startedAt).getTime() -
        new Date(right.startedAt).getTime(),
    )
  const workSessions = data.workSessions
    .filter((work) => localDateKey(work.startedAt) === date)
    .sort(
      (left, right) =>
        new Date(left.startedAt).getTime() -
        new Date(right.startedAt).getTime(),
    )
  const activeWorkStartedAt =
    data.activeWork && localDateKey(data.activeWork.startedAt) === date
      ? data.activeWork.startedAt
      : null
  const curve = workTimeCurve(data, date, now)
  const allWorkSessions = [
    ...workSessions.map((work) => work.startedAt),
    ...(activeWorkStartedAt ? [activeWorkStartedAt] : []),
  ].sort()
  const allWorkEnds = workSessions
    .map((work) => work.endedAt)
    .sort()

  return {
    date,
    water: actions.filter((action) => action.type === 'water').length,
    stand: actions.filter((action) => action.type === 'stand').length,
    restroom: actions.filter((action) => action.type === 'restroom').length,
    movements: actions.filter((action) => action.type === 'stand').length,
    restMs: curve.restMs,
    focusMs: curve.workMs,
    actions,
    rests,
    workSessions,
    clockInAt: allWorkSessions[0] ?? null,
    clockOutAt: activeWorkStartedAt
      ? null
      : (allWorkEnds.at(-1) ?? null),
    activeWorkStartedAt,
  }
}

export function workTimeCurve(
  data: WellnessData,
  date: string,
  now: Date = new Date(),
): WorkTimeCurve {
  const dayStart = new Date(`${date}T00:00:00`).getTime()
  const nextDay = new Date(dayStart)
  nextDay.setDate(nextDay.getDate() + 1)
  const dayEnd = nextDay.getTime()
  const nowMs = Math.min(dayEnd, Math.max(dayStart, now.getTime()))

  const workIntervals = mergeIntervals(
    [
      ...data.workSessions.map((work) =>
        clipInterval(
          new Date(work.startedAt).getTime(),
          new Date(work.endedAt).getTime(),
          dayStart,
          dayEnd,
        ),
      ),
      data.activeWork
        ? clipInterval(
            new Date(data.activeWork.startedAt).getTime(),
            nowMs,
            dayStart,
            dayEnd,
          )
        : null,
    ].filter((interval): interval is WorkTimeInterval => Boolean(interval)),
  )
  const restIntervals = mergeIntervals(
    [
      ...data.restSessions.map((rest) =>
        clipInterval(
          new Date(rest.startedAt).getTime(),
          new Date(rest.endedAt).getTime(),
          dayStart,
          dayEnd,
        ),
      ),
      data.activeRest
        ? clipInterval(
            new Date(data.activeRest.startedAt).getTime(),
            nowMs,
            dayStart,
            dayEnd,
          )
        : null,
    ].filter((interval): interval is WorkTimeInterval => Boolean(interval)),
  )
  const wallWorkMs = totalIntervalDuration(workIntervals)
  const restDuringWorkMs = workIntervals.reduce(
    (total, work) =>
      total +
      restIntervals.reduce(
        (restTotal, rest) =>
          restTotal + overlapDuration(work, rest),
        0,
      ),
    0,
  )
  const firstWork = workIntervals[0]
  const lastWork = workIntervals.at(-1)
  const active =
    Boolean(data.activeWork) &&
    localDateKey(data.activeWork!.startedAt) === date
  const clockInAt =
    data.workSessions
      .map((work) => work.startedAt)
      .concat(data.activeWork?.startedAt ?? [])
      .filter((value) => localDateKey(value) === date)
      .sort()[0] ?? null
  const clockOutAt = active
    ? null
    : data.workSessions
        .map((work) => work.endedAt)
        .filter((value) => localDateKey(value) === date)
        .sort()
        .at(-1) ?? null

  const curveStart = firstWork?.startAt ?? 0
  const curveEnd = Math.max(
    lastWork?.endAt ?? 0,
    curveStart + 1000,
  )
  const points: WorkTimeCurvePoint[] = []
  if (firstWork) {
    const step = 5 * 60_000
    for (let at = curveStart; at <= curveEnd; at += step) {
      points.push({
        at,
        workMs: workedDurationAt(
          workIntervals,
          restIntervals,
          at,
        ),
      })
    }
    if (points.at(-1)?.at !== curveEnd) {
      points.push({
        at: curveEnd,
        workMs: workedDurationAt(
          workIntervals,
          restIntervals,
          curveEnd,
        ),
      })
    }
  }

  return {
    startAt: curveStart,
    endAt: curveEnd,
    workMs: Math.max(0, wallWorkMs - restDuringWorkMs),
    restMs: totalIntervalDuration(restIntervals),
    workIntervals,
    restIntervals,
    points,
    clockInAt,
    clockOutAt,
    active,
  }
}

export function lastWellnessAction(
  data: WellnessData,
  types: WellnessActionType[],
  date: string,
) {
  const allowed = new Set(types)
  return data.actions
    .filter(
      (action) =>
        allowed.has(action.type) &&
        localDateKey(action.occurredAt) === date,
    )
    .sort(
      (left, right) =>
        new Date(right.occurredAt).getTime() -
        new Date(left.occurredAt).getTime(),
    )[0] ?? null
}

export type WellnessTimelineEntry =
  | {
      kind: 'action'
      at: string
      action: WellnessAction
    }
  | {
      kind: 'rest'
      at: string
      rest: WellnessRestSession
    }
  | {
      kind: 'clockIn'
      at: string
      workId: string
    }
  | {
      kind: 'clockOut'
      at: string
      workId: string
    }

export function wellnessTimeline(
  summary: WellnessDaySummary,
): WellnessTimelineEntry[] {
  return [
    ...summary.actions.map<WellnessTimelineEntry>((action) => ({
      kind: 'action',
      at: action.occurredAt,
      action,
    })),
    ...summary.rests.map<WellnessTimelineEntry>((rest) => ({
      kind: 'rest',
      at: rest.startedAt,
      rest,
    })),
    ...summary.workSessions.flatMap<WellnessTimelineEntry>((work) => [
      {
        kind: 'clockIn',
        at: work.startedAt,
        workId: work.id,
      },
      {
        kind: 'clockOut',
        at: work.endedAt,
        workId: `${work.id}-end`,
      },
    ]),
    ...(summary.activeWorkStartedAt
      ? [
          {
            kind: 'clockIn' as const,
            at: summary.activeWorkStartedAt,
            workId: 'active-work',
          },
        ]
      : []),
  ].sort(
    (left, right) =>
      new Date(left.at).getTime() - new Date(right.at).getTime(),
  )
}

export function wellnessHistoryDates(
  data: WellnessData,
  limit = 30,
) {
  const today = localDateKey(new Date())
  return [
    ...new Set([
      ...Object.keys(data.startedAtByDate),
      ...data.actions.map((action) => localDateKey(action.occurredAt)),
      ...data.restSessions.map((rest) => localDateKey(rest.startedAt)),
      ...data.workSessions.map((work) => localDateKey(work.startedAt)),
      ...(data.activeWork
        ? [localDateKey(data.activeWork.startedAt)]
        : []),
    ]),
  ]
    .filter((date) => date !== today)
    .sort((left, right) => right.localeCompare(left))
    .slice(0, limit)
}

function clipInterval(
  startAt: number,
  endAt: number,
  lower: number,
  upper: number,
): WorkTimeInterval | null {
  const start = Math.max(startAt, lower)
  const end = Math.min(endAt, upper)
  return end > start ? { startAt: start, endAt: end } : null
}

function mergeIntervals(intervals: WorkTimeInterval[]) {
  const sorted = intervals
    .filter((interval) => interval.endAt > interval.startAt)
    .sort((left, right) => left.startAt - right.startAt)
  const merged: WorkTimeInterval[] = []
  for (const interval of sorted) {
    const previous = merged.at(-1)
    if (previous && interval.startAt <= previous.endAt) {
      previous.endAt = Math.max(previous.endAt, interval.endAt)
    } else {
      merged.push({ ...interval })
    }
  }
  return merged
}

function totalIntervalDuration(intervals: WorkTimeInterval[]) {
  return intervals.reduce(
    (total, interval) => total + interval.endAt - interval.startAt,
    0,
  )
}

function overlapDuration(
  left: WorkTimeInterval,
  right: WorkTimeInterval,
) {
  return Math.max(
    0,
    Math.min(left.endAt, right.endAt) -
      Math.max(left.startAt, right.startAt),
  )
}

function workedDurationAt(
  workIntervals: WorkTimeInterval[],
  restIntervals: WorkTimeInterval[],
  at: number,
) {
  return workIntervals.reduce((workTotal, work) => {
    const end = Math.min(work.endAt, at)
    if (end <= work.startAt) return workTotal
    const activeWork = { startAt: work.startAt, endAt: end }
    const rest = restIntervals.reduce(
      (restTotal, interval) =>
        restTotal + overlapDuration(activeWork, interval),
      0,
    )
    return workTotal + end - work.startAt - rest
  }, 0)
}
