import { useRef, useState } from 'react';
import { AutoComplete, Form, Input, Modal, Select, Space } from 'antd';
import type { RegisteredFunction, ValidationIssue } from './editorModel';
import type { Binding, BindingKind } from './integrationApiModel';

const kindLabels: Record<BindingKind, string> = { PATH: '路径', EXPRESSION: '表达式', FUNCTION: '函数' };

/** 新建某种取值时的空白值。 */
export function emptyBinding(kind: BindingKind): Binding {
  if (kind === 'PATH') return { kind, path: '' };
  if (kind === 'EXPRESSION') return { kind, expression: '' };
  return { kind, function: '', args: {}, result: 'result' };
}

/**
 * 统一的取值编辑器：路径（可从变量提示中选择）、表达式（单行 Aviator）、函数（注册纯函数 + 参数取值 + 结果表达式）。
 * 函数参数只允许路径或表达式，不嵌套函数。
 */
export function BindingInput({ value, onChange, kinds, paths = [], functions = [], disabled, placeholder }: {
  value?: Binding; onChange: (next: Binding) => void; kinds: BindingKind[]; paths?: string[];
  functions?: RegisteredFunction[]; disabled?: boolean; placeholder?: string;
}) {
  const current = value ?? emptyBinding(kinds[0]);
  const pure = functions.filter(item => item.kind === 'PURE');
  const declared = current.kind === 'FUNCTION' ? pure.find(item => item.functionCode === current.function) : undefined;
  return <div className="external-api-binding">
    <Space.Compact block>
      {kinds.length > 1 && <Select style={{ width: 96 }} value={current.kind} disabled={disabled}
        options={kinds.map(kind => ({ value: kind, label: kindLabels[kind] }))}
        onChange={(kind: BindingKind) => onChange(emptyBinding(kind))} />}
      {current.kind === 'PATH' && <AutoComplete style={{ flex: 1 }} value={current.path} disabled={disabled}
        placeholder={placeholder ?? '选择或输入路径'}
        options={paths.filter(path => path.includes(current.path)).slice(0, 50).map(path => ({ value: path }))}
        onChange={path => onChange({ kind: 'PATH', path })} />}
      {current.kind === 'EXPRESSION' && <Input style={{ flex: 1 }} value={current.expression} disabled={disabled} maxLength={4096}
        placeholder={placeholder ?? "表达式，常量写成 'text' 或 100"} className="external-api-code-input"
        onChange={event => onChange({ kind: 'EXPRESSION', expression: event.target.value })} />}
      {current.kind === 'FUNCTION' && <Select style={{ flex: 1 }} value={current.function || undefined} disabled={disabled}
        placeholder="选择注册函数" options={pure.map(item => ({ value: item.functionCode, label: item.functionCode }))}
        onChange={(code: string) => onChange({ kind: 'FUNCTION', function: code, args: {}, result: 'result' })} />}
    </Space.Compact>
    {current.kind === 'FUNCTION' && declared && <div className="external-api-binding-args">
      {declared.params.map(param => <div key={param.name} className="external-api-binding-arg">
        <span className="muted">{param.name}{param.required ? ' *' : ''}</span>
        <BindingInput value={current.args[param.name]} kinds={['PATH', 'EXPRESSION']} paths={paths} disabled={disabled}
          onChange={next => onChange({ ...current, args: { ...current.args, [param.name]: next } })} />
      </div>)}
      <div className="external-api-binding-arg"><span className="muted">结果</span>
        <Input value={current.result} disabled={disabled} className="external-api-code-input" placeholder="result"
          onChange={event => onChange({ ...current, result: event.target.value })} /></div>
    </div>}
  </div>;
}

/** 校验结果栏：错误为浅红横幅“校验结果 · N 个错误”，逐条显示说明与位置，“定位”交给调用方切换页签或选中对象。 */
export function ValidationPanel({ issues, onLocate, stale }: {
  issues: ValidationIssue[] | null; onLocate: (issue: ValidationIssue) => void; stale?: boolean;
}) {
  if (issues === null || issues.length === 0) return null;
  const errors = issues.filter(issue => issue.severity === 'ERROR').length;
  const warnings = issues.length - errors;
  const title = errors ? `校验结果 · ${errors} 个错误${warnings ? `，${warnings} 个提示` : ''}`
    : warnings ? `校验通过 · ${warnings} 个提示` : '校验通过';
  return <section className={`xapi-validation${errors ? '' : ' ok'}`} aria-live="polite">
    <div className="xapi-validation-title">{title}{stale && <span className="xapi-field-hint">（内容已修改，结果可能过期）</span>}</div>
    {issues.map((issue, index) => <div key={`${issue.location}:${index}`} className="xapi-validation-item">
      <span>{issue.severity === 'ERROR' ? '⊗' : 'ⓘ'}</span>
      <span>{issue.message}<small>{issue.location} · {issue.code}</small></span>
      <button type="button" className="xapi-link" onClick={() => onLocate(issue)}>定位</button></div>)}
  </section>;
}

/** 保存前填写变更原因。 */
export function ReasonDialog({ open, saving, onCancel, onConfirm }: {
  open: boolean; saving: boolean; onCancel: () => void; onConfirm: (reason: string) => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const [submitted, setSubmitted] = useState(false);
  return <Modal open={open} title="保存配置" okText="保存" okButtonProps={{ loading: saving }} onCancel={onCancel}
    onOk={() => form.submit()} destroyOnHidden afterOpenChange={visible => { if (visible) setSubmitted(false); }}>
    <p className="muted">保存后对新调用立即生效。</p>
    <Form form={form} layout="vertical" requiredMark={false} onFinish={values => { if (!submitted) { setSubmitted(true); onConfirm(values.reason.trim()); } }}>
      <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount autoFocus /></Form.Item>
    </Form>
  </Modal>;
}

/**
 * 保存流程：点击保存（或离开时“保存并离开”）→ 先做本地检查 → 填写变更原因 → 提交；返回是否保存成功。
 * 原因弹窗取消或本地检查失败都返回 false。
 */
export function useReasonFlow(perform: (reason: string) => Promise<boolean>, check: () => Promise<boolean> | boolean = () => true) {
  const [open, setOpen] = useState(false);
  const waiter = useRef<((ok: boolean) => void) | null>(null);
  const finish = (ok: boolean) => { waiter.current?.(ok); waiter.current = null; };
  async function start(): Promise<boolean> {
    if (!(await check())) return false;
    setOpen(true);
    return new Promise<boolean>(resolve => { waiter.current = resolve; });
  }
  return {
    open, start,
    cancel: () => { setOpen(false); finish(false); },
    confirm: async (reason: string) => { const ok = await perform(reason); setOpen(false); finish(ok); },
  };
}

/** 取值的文字形式：路径原样，表达式原文，函数写成 FN(参数…) → result。 */
export function bindingText(binding: Binding | undefined): string {
  if (!binding) return '';
  if (binding.kind === 'PATH') return binding.path;
  if (binding.kind === 'EXPRESSION') return binding.expression;
  const args = Object.entries(binding.args).map(([name, arg]) => `${name}: ${bindingText(arg)}`).join(', ');
  return `${binding.function}(${args})${binding.result && binding.result !== 'result' ? ` → ${binding.result}` : ''}`;
}

/** 取值的只读展示：灰底小标签“路径 / 表达式 / 函数” + 蓝色等宽文字；没有取值时显示占位文字。 */
export function BindingView({ binding, empty = '未赋值' }: { binding?: Binding; empty?: string }) {
  if (!binding) return <span className="xapi-field-hint">{empty}</span>;
  return <span><span className="xapi-kind">{kindLabels[binding.kind]}</span>
    <code className="xapi-mono xapi-mono-ref">{breakAtDots(bindingText(binding))}</code></span>;
}

/** 长路径只在“.”之后允许换行，避免在单词中间断开。 */
export function breakAtDots(text: string) {
  return text.split(/(?<=\.)/).map((part, index) => <span key={index}>{part}<wbr /></span>);
}
