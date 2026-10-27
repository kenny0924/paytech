import {useState} from 'react';
import type {InputHTMLAttributes, ReactNode, Ref} from 'react';
import {useT} from './i18n/react.tsx';

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'children' | 'onChange'> & {
  id: string;
  label: string;
  inputRef?: Ref<HTMLInputElement>;
  onValueChange: (value: string) => void;
  error?: string;
  message?: string;
  messageTone?: 'error' | 'success';
  hint?: string;
  invalid?: boolean;
  describedBy?: string;
  /** 标签行右侧的次要操作（如"忘记密码？"）。 */
  labelAction?: ReactNode;
};

export function focusField(input: HTMLInputElement | null | undefined) {
  if (!input) return;
  input.focus({preventScroll:true});
  const bounds=input.getBoundingClientRect();
  if (bounds.top<0 || bounds.bottom>window.innerHeight) input.scrollIntoView({block:'nearest'});
}

/** 所有文本输入共用标签、提示预留区域与错误状态；业务校验留在调用页面。 */
export function TextField({id, label, inputRef, onValueChange, error, message, messageTone='error',
  hint, invalid, describedBy, labelAction, type='text', ...input}: TextFieldProps) {
  const [visible, setVisible]=useState(false), t=useT('common');
  const password=type==='password', feedbackId=id+'-feedback';
  const content=error || message || hint;
  const tone=error ? 'error' : message ? messageTone : 'hint';
  return <div className="field text-field">
    {labelAction ? <div className="label-row"><label htmlFor={id}>{label}</label>{labelAction}</div> : <label htmlFor={id}>{label}</label>}
    <div className={password ? 'password' : undefined}>
      <input {...input} ref={inputRef} id={id} name={input.name ?? id}
        type={password && visible ? 'text' : type}
        onChange={event=>onValueChange(event.target.value)}
        aria-invalid={!!error || !!invalid} aria-describedby={[feedbackId,describedBy].filter(Boolean).join(' ')}/>
      {password && <button className="reveal" type="button" disabled={input.disabled}
        onClick={()=>setVisible(!visible)} aria-label={t(visible ? 'password.hide_label' : 'password.show_label', {label})}
        aria-pressed={visible}>{t(visible ? 'password.hide' : 'password.show')}</button>}
    </div>
    <p className={'field-message '+tone} id={feedbackId} aria-live="polite" aria-atomic="true">{content}</p>
  </div>;
}
