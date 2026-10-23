import { DatePicker as AntDatePicker } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import { useId, useRef, useState } from 'react';
import { useT } from '../i18n/react';
import { resolveTimeRange } from './timeRange';
import type { TimeRangePreset, TimeRangeValue } from './timeRange';
import './TimeRangePicker.css';

dayjs.extend(customParseFormat);

const { RangePicker } = AntDatePicker;
const VALUE_FORMAT = 'YYYY-MM-DD[T]HH:mm:ss';
const DISPLAY_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** Shortcut order; labels are `common.timeRange.preset.<value>`. */
const shortcuts: TimeRangePreset[] = ['TODAY', 'LAST_3_DAYS', 'LAST_7_DAYS', 'CUSTOM'];

export interface TimeRangePickerProps {
  id?: string;
  value?: TimeRangeValue;
  onChange?: (value?: TimeRangeValue) => void;
  disabled?: boolean;
  className?: string;
}

function pickerDate(value?: string): Dayjs | null {
  if (!value) return null;
  const parsed = dayjs(value, VALUE_FORMAT, true);
  return parsed.isValid() ? parsed : null;
}

function pickerRange(value?: TimeRangeValue): [Dayjs | null, Dayjs | null] | null {
  if (!value) return null;
  const range = resolveTimeRange(value);
  return [pickerDate(range.start), pickerDate(range.end)];
}

function localTimeZone(): string | undefined {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
}

/** 页面查询通用的本地日期时间范围选择器。 */
export function TimeRangePicker({ id, value, onChange, disabled, className }: TimeRangePickerProps) {
  const t = useT('common');
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
      aria-label={t('timeRange.label')}
      value={pickerRange(value)}
      onChange={commitRange}
      onCalendarChange={() => setDraftPreset('CUSTOM')}
      onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }}
      open={open}
      onOpenChange={changeOpen}
      disabled={disabled}
      format={DISPLAY_FORMAT}
      showTime={{ format: 'HH:mm:ss', use12Hours: false,
        defaultOpenValue: [dayjs().startOf('day'), dayjs().startOf('day')] }}
      needConfirm
      showNow={false}
      allowClear
      separator={<span aria-hidden="true" className="time-range-separator">→</span>}
      placeholder={[t('timeRange.start'), t('timeRange.end')]}
      placement="bottomLeft"
      classNames={{ popup: { root: 'time-range-picker-popup' } }}
      panelRender={panel => <div className="time-range-dropdown">
        <aside className="time-range-shortcuts" aria-label={t('timeRange.shortcuts')}>
          <div className="time-range-shortcuts-title">{t('timeRange.shortcutsTitle')}</div>
          {shortcuts.map(shortcut => <button key={shortcut} type="button"
            className={activePreset === shortcut ? 'is-active' : undefined}
            aria-pressed={activePreset === shortcut}
            onClick={() => selectShortcut(shortcut)}>{t('timeRange.preset.' + shortcut)}</button>)}
        </aside>
        <div className="time-range-calendar">{panel}</div>
      </div>}
      renderExtraFooter={() => <div className="time-range-footer">
        <span>{t('timeRange.localTime', { timeZone: localTimeZone() ?? t('timeRange.localTimeZone') })}</span>
        <button type="button" onMouseDown={event => event.preventDefault()} onClick={() => changeOpen(false)}>{t('action.cancel')}</button>
      </div>} />
  </div>;
}
