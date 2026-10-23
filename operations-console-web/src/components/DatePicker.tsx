import { DatePicker as AntDatePicker } from 'antd';
import type { DatePickerProps as AntDatePickerProps } from 'antd';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import { useState } from 'react';
import { useT } from '../i18n/react';
import './DatePicker.css';

dayjs.extend(customParseFormat);

export type DatePickerProps = Pick<AntDatePickerProps,
  'id' | 'ref' | 'disabled' | 'placeholder' | 'status' | 'size' | 'className' | 'style' | 'onBlur' | 'onFocus'> & {
  /** 表单值为本地日期；含时间时使用 YYYY-MM-DDTHH:mm:ss，不在组件内转换时区。 */
  value?: string | null;
  onChange?: (value: string | undefined) => void;
  showTime?: boolean;
};

/** 统一日历（语言随 ConfigProvider 与 dayjs 全局语言）、24 小时时间与字符串表单值；清空后返回 undefined。 */
export function DatePicker({ value, onChange, showTime = false, placeholder, className, ...props }: DatePickerProps) {
  const t = useT('common');
  const [open, setOpen] = useState(false);
  const valueFormat = showTime ? 'YYYY-MM-DD[T]HH:mm:ss' : 'YYYY-MM-DD';
  const displayFormat = showTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
  const selected = value ? dayjs(value, valueFormat, true) : null;
  return <AntDatePicker {...props} value={selected?.isValid() ? selected : null}
    onChange={date => onChange?.(date?.format(valueFormat))}
    onKeyDown={event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      if (!open) { setOpen(true); return; }
      if (!showTime) return;
      if (!(event.target instanceof HTMLInputElement)) return;
      const typed = dayjs(event.target.value, displayFormat, true);
      if (!typed.isValid()) return;
      onChange?.(typed.format(valueFormat));
      setOpen(false);
    }}
    open={open} onOpenChange={setOpen}
    format={displayFormat}
    showTime={showTime ? { format: 'HH:mm:ss', use12Hours: false, defaultOpenValue: dayjs().startOf('day') } : false}
    needConfirm={showTime} showNow={false} allowClear
    placeholder={placeholder ?? t(showTime ? 'datePicker.selectDateTime' : 'datePicker.selectDate')}
    className={['shared-date-picker', className].filter(Boolean).join(' ')}
    classNames={{ popup: { root: 'shared-date-picker-popup' } }} />;
}
