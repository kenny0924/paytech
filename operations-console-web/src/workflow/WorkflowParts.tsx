import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, ConfigProvider, Form, Input, InputNumber, Popover, Radio, Select, Spin, Switch, Tag, Typography } from 'antd';
import { ApiError, session } from '../api/session';
import { DatePicker } from '../components/DatePicker';
import { displayValue } from './formModel';
import { decodeCandidates, workflowPaths } from './workflowApi';
import type { AssigneeCandidate, FormDefinition, FormField, WorkflowFile } from './workflowTypes';
import { FileUploadField } from './WorkflowFiles';
import './workflow.css';

/** 读取一次详情；path 为 null 时不请求。失败由页面就地展示。 */
export function useLoad<T>(path: string | null, decode: (value: unknown) => T) {
  const [data, setData] = useState<T>(); const [loading, setLoading] = useState(path !== null);
  const [failure, setFailure] = useState<unknown>(); const [reload, setReload] = useState(0);
  const decoder = useRef(decode); decoder.current = decode;
  useEffect(() => {
    if (path === null) { setLoading(false); return; }
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, value => decoder.current(value))
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, reload]);
  return { data, loading, failure, refresh: () => setReload(value => value + 1), setData };
}

/** 页面内的加载失败卡片：请求编号可复制，带重试。 */
export function LoadFailed({ failure, retry, title = '加载失败' }: { failure: unknown; retry?: () => void; title?: string }) {
  const notFound = failure instanceof ApiError && failure.status === 404;
  const forbidden = failure instanceof ApiError && failure.status === 403;
  const requestId = failure instanceof ApiError ? failure.requestId : '';
  return <Alert type="warning" showIcon className="wf-load-failed"
    title={notFound ? '记录不存在，可能已被删除' : forbidden ? '没有查看权限' : title}
    description={failure instanceof ApiError && failure.detail ? failure.detail : requestId ? <>请求编号 <Typography.Text copyable>{requestId}</Typography.Text></> : '服务暂时不可用，请稍后重试。'}
    action={retry && !notFound ? <Button size="small" onClick={retry}>重试</Button> : undefined} />;
}

export const Spinner = () => <div className="content-loading"><Spin /></div>;

/** 本地时间 MM-DD HH:mm（同年）或 YYYY-MM-DD HH:mm。 */
export function shortTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const pad = (number: number) => String(number).padStart(2, '0');
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  return date.getFullYear() === new Date().getFullYear() ? day : `${date.getFullYear()}-${day}`;
}

/** 人员显示名：先查实例 people，再查本页搜索缓存，最后显示 ID。 */
export function personName(id: string | null | undefined, people: Record<string, string> = {}): string {
  if (!id) return '—';
  return people[id] ?? candidateNames.get(id) ?? `#${id}`;
}
const candidateNames = new Map<string, string>();

export const isMe = (id: string | null | undefined, actorId: string) => !!id && id === actorId;

/** 选人：按登录名或邮箱搜索 ACTIVE Operations 账号（接口 25，最多 20 条）。 */
export function PersonSelect({ value, onChange, multiple, people, disabled, placeholder, disabledIds = {}, style }: {
  value?: string | string[]; onChange?: (value: never) => void; multiple?: boolean; people?: Record<string, string>; disabled?: boolean;
  placeholder?: string; disabledIds?: Record<string, string>; style?: React.CSSProperties;
}) {
  const [options, setOptions] = useState<AssigneeCandidate[]>([]); const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined); const controller = useRef<AbortController>(undefined);
  useEffect(() => () => { clearTimeout(timer.current); controller.current?.abort(); }, []);
  function search(keyword: string) {
    clearTimeout(timer.current); controller.current?.abort();
    if (!keyword.trim()) { setOptions([]); return; }
    timer.current = setTimeout(() => {
      const abort = new AbortController(); controller.current = abort; setLoading(true);
      session.request(`${workflowPaths.candidates}?keyword=${encodeURIComponent(keyword.trim())}`, { signal: abort.signal }, decodeCandidates)
        .then(items => { if (!abort.signal.aborted) { items.forEach(item => candidateNames.set(item.accountId, item.displayName)); setOptions(items); } })
        .catch(() => { if (!abort.signal.aborted) setOptions([]); })
        .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    }, 250);
  }
  const selected = (Array.isArray(value) ? value : value ? [value] : []);
  const merged = useMemo(() => {
    const map = new Map<string, string>(selected.map(id => [id, personName(id, people)]));
    for (const item of options) map.set(item.accountId, item.emailMasked && item.emailMasked !== item.displayName ? `${item.displayName} · ${item.emailMasked}` : item.displayName);
    return [...map].map(([id, name]) => ({ value: id, label: disabledIds[id] ? `${name}（${disabledIds[id]}）` : name, disabled: !!disabledIds[id] }));
  }, [options, selected.join(','), people, disabledIds]);
  return <Select mode={multiple ? 'multiple' : undefined} showSearch={{ filterOption: false, onSearch: search }} value={value as never}
    onChange={onChange as never} options={merged} disabled={disabled} placeholder={placeholder ?? '搜索登录名或邮箱'} allowClear={!multiple}
    notFoundContent={loading ? <Spin size="small" /> : '输入关键字搜索'} style={{ width: '100%', ...style }} />;
}

/** 转交浮层：搜索选人，不能转给自己或同节点其他审批人，可写转交说明；未提交的填写不随转交带走。 */
export function TransferPopover({ title, open, onOpenChange, actorId, people, blocked = {}, saving, error, onConfirm, children }: {
  title: string; open: boolean; onOpenChange: (open: boolean) => void; actorId: string; people: Record<string, string>;
  blocked?: Record<string, string>; saving: boolean; error?: string | null; onConfirm: (toUserId: string, reason: string) => void; children: ReactNode;
}) {
  const [target, setTarget] = useState<string>(); const [reason, setReason] = useState('');
  useEffect(() => { if (!open) { setTarget(undefined); setReason(''); } }, [open]);
  const disabledIds = { ...blocked, [actorId]: '不能转交给自己' };
  const content = <div className="wf-transfer">
    <p className="muted">转交后由对方执行，你不再看到这条待办；已填写但未提交的内容不会带过去。</p>
    <PersonSelect value={target} onChange={value => setTarget(value)} people={people} disabledIds={disabledIds} placeholder="按登录名或邮箱搜索" />
    <div className="wf-field-label">转交说明（选填，对方可见）</div>
    <Input.TextArea rows={3} maxLength={512} value={reason} onChange={event => setReason(event.target.value)} />
    {error && <Alert type="error" showIcon title={error} />}
    <div className="wf-transfer-foot"><span className="muted">对方会收到邮件和待办提醒</span>
      <span><Button onClick={() => onOpenChange(false)}>取消</Button>
        <Button type="primary" disabled={!target || !!disabledIds[target]} loading={saving}
          onClick={() => target && onConfirm(target, reason.trim())}>转交{target ? `给 ${personName(target, people)}` : ''}</Button></span></div>
  </div>;
  return <Popover open={open} onOpenChange={onOpenChange} trigger="click" placement="bottomRight" title={title} content={content} destroyOnHidden>{children}</Popover>;
}

/** 按字段定义渲染 antd Form.Item（启动表单与 FORM 动作共用，3.2）。changed 标出与上一轮不同的字段。 */
export function DynamicFields({ form, disabled, changed, previous, problems = {}, files }: {
  form: FormDefinition; disabled?: boolean; changed?: Set<string>; previous?: Record<string, unknown> | null; problems?: Record<string, string>;
  /** 已有文件的元数据（显示名称与扫描状态）。 */
  files?: Map<string, WorkflowFile>;
}) {
  return <>{form.fields.map(field => <Form.Item key={field.key} name={field.key} label={<span>{field.label || field.key}
    {changed?.has(field.key) && <Tag color="gold" className="wf-changed-tag">已修改</Tag>}</span>}
    required={field.required} validateStatus={problems[field.key] ? 'error' : undefined} help={problems[field.key]}
    valuePropName={field.type === 'BOOLEAN' ? 'checked' : 'value'}
    extra={previous && changed?.has(field.key) ? `上一轮：${displayValue(previous[field.key], field)}` : undefined}>
    {fieldInput(field, disabled, files)}
  </Form.Item>)}</>;
}

function fieldInput(field: FormField, disabled?: boolean, files?: Map<string, WorkflowFile>): ReactNode {
  const rules = field.rules ?? {};
  switch (field.type) {
    case 'TEXT': return <Input disabled={disabled} maxLength={rules.maxLength ?? 255} />;
    case 'LONG_TEXT': return <Input.TextArea disabled={disabled} rows={3} maxLength={rules.maxLength ?? 4000} showCount />;
    case 'NUMBER': return <InputNumber disabled={disabled} stringMode style={{ width: '100%' }}
      min={rules.min as never} max={rules.max as never} precision={rules.scale} />;
    case 'BOOLEAN': return <Switch disabled={disabled} />;
    case 'ENUM': return (rules.options?.length ?? 0) <= 4
      ? <Radio.Group disabled={disabled} optionType="button" options={(rules.options ?? []).map(option => ({ value: option.value, label: option.label || option.value }))} />
      : <Select disabled={disabled} allowClear options={(rules.options ?? []).map(option => ({ value: option.value, label: `${option.value} · ${option.label}` }))} />;
    case 'DATE': return <DatePicker disabled={disabled} />;
    case 'FILE': return <FileUploadField disabled={disabled} rules={rules} known={files} />;
  }
}

/** 只读展示一组输出（按表单定义取标签）。 */
export function OutputView({ output, form, highlight }: { output: Record<string, unknown> | null | undefined; form?: FormDefinition; highlight?: Set<string> }) {
  const entries = form ? form.fields.map(field => [field.key, field] as const) : Object.keys(output ?? {}).map(key => [key, undefined] as const);
  if (!output || !Object.keys(output).length) return <span className="muted">尚未提交</span>;
  return <dl className="wf-output">{entries.map(([key, field]) => <div key={key} className={highlight?.has(key) ? 'is-changed' : ''}>
    <dt>{field?.label ?? (key === 'note' ? '确认说明' : key === 'files' ? '文件' : key)}</dt><dd>{displayValue(output[key], field)}</dd></div>)}</dl>;
}

/** 标签 + 内容。 */
export function Field({ label, children, hint }: { label: ReactNode; children: ReactNode; hint?: ReactNode }) {
  return <div className="wf-field"><div className="wf-field-label">{label}</div>{children}{hint && <div className="wf-field-hint">{hint}</div>}</div>;
}

/** 本人持有的按钮资源。 */
export const hasButton = (resources: { resourceType: string; resourceCode: string }[], code: string) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

/** 按钮资源码（pageCapabilities 中登记，绑定 technique 第 7 节的权限）。 */
export const workflowButtons = {
  definitionManage: 'OPERATIONS_WORKFLOW_DEFINITION_MANAGE',
  templateManage: 'OPERATIONS_WORKFLOW_ACTION_TEMPLATE_MANAGE',
  instanceStart: 'OPERATIONS_WORKFLOW_INSTANCE_START',
  instanceReadAll: 'OPERATIONS_WORKFLOW_INSTANCE_READ',
  instanceManage: 'OPERATIONS_WORKFLOW_INSTANCE_MANAGE',
} as const;

/** 前端路由（与 pageCapabilities、menu.pageRoutePaths 一致）。 */
export const workflowRoutes = {
  myTasks: '/workflow/my-tasks', instances: '/workflow/instances', definitions: '/workflow/definitions', templates: '/workflow/action-templates',
} as const;

/** 工作流页面外框：按钮文字不自动插空格（与外部 API 页面一致）。 */
export function WorkflowFrame({ children }: { children: ReactNode }) {
  return <ConfigProvider button={{ autoInsertSpace: false }}>{children}</ConfigProvider>;
}
