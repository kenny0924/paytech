import { useState } from 'react';
import { Alert, AutoComplete, Button, Form, Input, List, Modal, Select, Space, Tag } from 'antd';
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

/** 校验结果列表：错误与警告分级显示，点击“定位”交给调用方切换页签或高亮。 */
export function ValidationPanel({ issues, onLocate, stale }: {
  issues: ValidationIssue[] | null; onLocate: (issue: ValidationIssue) => void; stale?: boolean;
}) {
  if (issues === null) return null;
  return <section className="configuration-editor external-api-validation">
    <h2>校验结果{stale && <Tag className="external-api-tag">内容已修改，结果可能过期</Tag>}</h2>
    {issues.length === 0 ? <Alert type="success" showIcon title="校验通过" /> : <List size="small" dataSource={issues} renderItem={issue =>
      <List.Item actions={[<Button key="locate" type="link" size="small" onClick={() => onLocate(issue)}>定位</Button>]}>
        <Space align="start"><Tag color={issue.severity === 'ERROR' ? 'red' : 'orange'}>{issue.severity === 'ERROR' ? '错误' : '提示'}</Tag>
          <div><div>{issue.message}</div><div className="cell-meta"><code>{issue.location}</code> · {issue.code}</div></div></Space>
      </List.Item>} />}
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
