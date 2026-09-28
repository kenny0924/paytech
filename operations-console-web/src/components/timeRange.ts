export type TimeRangePreset = 'TODAY' | 'LAST_3_DAYS' | 'LAST_7_DAYS' | 'CUSTOM';

export interface TimeRangeValue {
  preset: TimeRangePreset;
  start?: string;
  end?: string;
}

export interface ResolvedTimeRange {
  start?: string;
  end?: string;
}

const twoDigits = (value: number) => String(value).padStart(2, '0');

function localDateTime(date: Date): string {
  return `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())}T${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`;
}

function calendarDays(days: number, now: Date): ResolvedTimeRange {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - days + 1);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  return { start: localDateTime(start), end: localDateTime(end) };
}

/** 快捷范围按浏览器本地自然日计算；结束值为下一日零点且不包含在范围内。 */
export function resolveTimeRange(value?: TimeRangeValue, now = new Date()): ResolvedTimeRange {
  if (!value) return {};
  if (value.preset === 'CUSTOM') return { start: value.start, end: value.end };
  if (value.start && value.end) return { start: value.start, end: value.end };
  if (value.preset === 'TODAY') return calendarDays(1, now);
  if (value.preset === 'LAST_3_DAYS') return calendarDays(3, now);
  return calendarDays(7, now);
}

/** 查询提交后冻结快捷范围，使控件显示与后续刷新复用的绝对条件保持一致。 */
export function freezeTimeRange(value?: TimeRangeValue, now = new Date()): TimeRangeValue | undefined {
  if (!value || value.preset === 'CUSTOM' || (value.start && value.end)) return value;
  return { ...value, ...resolveTimeRange(value, now) };
}
