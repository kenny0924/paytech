import { DatePicker as AntDatePicker } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import { useId, useRef, useState } from 'react';
import zhCN from 'antd/es/date-picker/locale/zh_CN';
import 'dayjs/locale/zh-cn';
import { resolveTimeRange } from './timeRange';
import type { TimeRangePreset, TimeRangeValue } from './timeRange';
import './TimeRangePicker.css';

dayjs.extend(customParseFormat);

const { RangePicker } = AntDatePicker;
const VALUE_FORMAT = 'YYYY-MM-DD[T]HH:mm:ss';
const DISPLAY_FORMAT = 'YYYY-MM-DD HH:mm:ss';

const shortcuts: Array<{ label: string; value: TimeRangePreset }> = [
  { label: '今天', value: 'TODAY' },
  { label: '最近 3 天', value: 'LAST_3_DAYS' },
  { label: '最近 7 天', value: 'LAST_7_DAYS' },
  { label: '自定义', value: 'CUSTOM' },
];

export interface TimeRangePickerProps {
  id?: string;
  value?: TimeRangeValue;
  onChange?: (value?: TimeRangeValue) => void;
  disabled?: boolean;
  className?: string;
}

function pickerDate(value?: string): Dayjs | null {
  if (!value) return null;
  const parsed = dayjs(value, VALUE_FORMAT, true).locale('zh-cn');
  return parsed.isValid() ? parsed : null;
}

function pickerRange(value?: TimeRangeValue): [Dayjs | null, Dayjs | null] | null {
  if (!value) return null;
  const range = resolveTimeRange(value);
  return [pickerDate(range.start), pickerDate(range.end)];
}

function localTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || '本地时区';
}

/** 页面查询通用的本地日期时间范围选择器。 */
export function TimeRangePicker({ id, value, onChange, disabled, className }: TimeRangePickerProps) {
  const generatedId = useId().replaceAll(':', '');
  const controlId = id || `time-range-${generatedId}`;
  const [open, setOpen] = useState(false);
  const [draftPreset, setDraftPreset] = useState<TimeRangePreset>();
  const discardClosingDraft = useRef(false);
  const activePreset = open ? draftPreset : value?.preset;

  function changeOpen(nextOpen: boolean) {
    discardClosingDraft.current = !nextOpen;
    setOpen(nextOpen);
    setDraftPreset(nextOpen ? value?.preset : undefined);
  }

  function selectShortcut(preset: TimeRangePreset) {
    if (preset === 'CUSTOM') {
      setDraftPreset('CUSTOM');
      return;
    }
    discardClosingDraft.current = true;
    onChange?.({ preset });
    setOpen(false);
    setDraftPreset(undefined);
  }

  function commitRange(dates: [Dayjs | null, Dayjs | null] | null) {
    if (!dates?.[0] || !dates[1]) {
      discardClosingDraft.current = false;
      onChange?.(undefined);
      return;
    }
    // rc-picker may replay an unfinished range while its popup closes.
    if (discardClosingDraft.current) return;
    onChange?.({ preset: 'CUSTOM', start: dates[0].format(VALUE_FORMAT), end: dates[1].format(VALUE_FORMAT) });
  }

  return <div className={['time-range-picker', className].filter(Boolean).join(' ')}>
    <RangePicker id={{ start: `${controlId}-start`, end: `${controlId}-end` }}
      aria-label="时间范围"
      value={pickerRange(value)}
      onChange={commitRange}
      onCalendarChange={() => setDraftPreset('CUSTOM')}
      onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }}
      open={open}
      onOpenChange={changeOpen}
      disabled={disabled}
      locale={zhCN}
      format={DISPLAY_FORMAT}
      showTime={{ format: 'HH:mm:ss', use12Hours: false,
        defaultOpenValue: [dayjs().startOf('day'), dayjs().startOf('day')] }}
      needConfirm
      showNow={false}
      allowClear
      separator={<span aria-hidden="true" className="time-range-separator">→</span>}
      placeholder={['开始时间', '结束时间']}
      placement="bottomLeft"
      classNames={{ popup: { root: 'time-range-picker-popup' } }}
      panelRender={panel => <div className="time-range-dropdown">
        <aside className="time-range-shortcuts" aria-label="快捷时间范围">
          <div className="time-range-shortcuts-title">快捷选择</div>
          {shortcuts.map(shortcut => <button key={shortcut.value} type="button"
            className={activePreset === shortcut.value ? 'is-active' : undefined}
            aria-pressed={activePreset === shortcut.value}
            onClick={() => selectShortcut(shortcut.value)}>{shortcut.label}</button>)}
        </aside>
        <div className="time-range-calendar">{panel}</div>
      </div>}
      renderExtraFooter={() => <div className="time-range-footer">
        <span>本地时间 · {localTimeZone()}</span>
        <button type="button" onMouseDown={event => event.preventDefault()} onClick={() => changeOpen(false)}>取消</button>
      </div>} />
  </div>;
}
