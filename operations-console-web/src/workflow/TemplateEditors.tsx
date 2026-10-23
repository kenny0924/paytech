import { useEffect, useState } from 'react';
import { Alert, AutoComplete, Button, Empty, Input, Modal, Select, Spin, Switch, Table, Tag } from 'antd';
import { actionTypeIcons } from './workflowIcons';
import { CheckOutlined, CloseOutlined, PlusOutlined, StopOutlined, UndoOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { session } from '../api/session';
import { pageCapabilities } from '../navigation/pageCapabilities';
import { creatableTypes } from './catalogModel';
import { FieldDesigner, FormPreview } from './FieldDesigner';
import { operatorsByType } from './graphModel';
import { configDiff, defaultConfig, frozenConfig, instructionLimit, referenceSummary } from './templateModel';
import { decodeReferences, decodeVersion, workflowPaths } from './workflowApi';
import { actionTypeLabels, operatorLabels } from './workflowLabels';
import type {
  ActionConfig, ActionTemplate, ActionType, DataQueryConfig, FileUploadConfig, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferenceItem,
  TemplateReferences,
} from './workflowTypes';
import { Field, LoadFailed, workflowRoutes } from './WorkflowParts';
import { uploadRequired } from './fileModel';

export const actionTypes: ActionType[] = ['FORM', 'FILE_UPLOAD', 'PAGE_TASK', 'MANUAL_CONFIRM', 'DATA_QUERY'];
/** 类型说明（列表类型标签、新建选类型卡片，原型 06、07）；图标见 workflowIcons。 */
export const actionTypeSummaries: Record<ActionType, string> = {
  FORM: '填写结构化信息', FILE_UPLOAD: '上传一个或多个文件', PAGE_TASK: '到指定页面完成操作', MANUAL_CONFIRM: '确认一件线下事项', DATA_QUERY: '按条件查询并带出数据',
};

/** 类型标签：图标 + 名称，按类型着色。 */
export function ActionTypeTag({ type }: { type: ActionType }) {
  return <span className={`wf-type-tag is-${type.toLowerCase()}`}><span aria-hidden>{actionTypeIcons[type]}</span>{actionTypeLabels[type]}</span>;
}

const routeOptions = pageCapabilities.map(page => ({ value: page.frontendRoute as string, label: `${page.displayName}（${page.frontendRoute}）` }));
/** 按类型的配置编辑：表单为字段设计，其他类型为说明（原型 08，需求 E1）。 */
export function ConfigEditor({ type, config, onChange, readOnly, templateName, description, savedKeys }: {
  type: ActionType; config: ActionConfig; onChange: (config: ActionConfig) => void; readOnly?: boolean;
  /** 表单画布标题与说明（执行人在待办里看到的动作名称与描述）。 */
  templateName?: string; description?: string;
  /** 已保存配置的字段键，键不再随显示名称自动生成。 */
  savedKeys?: ReadonlySet<string>;
}) {
  switch (type) {
    case 'FORM': return <FieldDesigner form={config as FormDefinition} readOnly={readOnly} variablePrefix="<动作键>." savedKeys={savedKeys}
      previewTitle={templateName || '（未命名动作）'} previewDescription={description} onChange={onChange} />;
    case 'FILE_UPLOAD': return <FileUploadEditor config={config as FileUploadConfig} onChange={onChange} readOnly={readOnly} />;
    case 'PAGE_TASK': return <PageTaskEditor config={config as PageTaskConfig} onChange={onChange} readOnly={readOnly} />;
    case 'MANUAL_CONFIRM': return <ManualConfirmEditor config={config as ManualConfirmConfig} onChange={onChange} readOnly={readOnly} />;
    case 'DATA_QUERY': return <DataQueryEditor config={config as DataQueryConfig} onChange={onChange} readOnly={readOnly} />;
  }
}

/** 上传文件：只有执行说明与「至少上传一个」（需求 E1）；旧配置 minFiles ≥ 1 显示为已开启，不再写数量与类型。 */
function FileUploadEditor({ config, onChange, readOnly }: { config: FileUploadConfig; onChange: (config: FileUploadConfig) => void; readOnly?: boolean }) {
  const set = (patch: Partial<FileUploadConfig>) => onChange({ required: uploadRequired(config), instruction: config.instruction ?? '', ...patch });
  return <section className="wf-card">
    <Field label="执行说明" hint={`显示在执行人待办里；写清楚要传什么、从哪里拿（${(config.instruction ?? '').length} / ${instructionLimit}）`}>
      <Input.TextArea rows={6} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    <label className="wf-inline"><Switch size="small" checked={uploadRequired(config)} disabled={readOnly} onChange={required => set({ required })} /> 至少上传一个</label>
    <div className="wf-subtle"><div>输出：<code>&lt;动作键&gt;.files</code></div>
      <div className="muted">不限数量与类型；单个文件大小由文件服务统一限制。安全检查在后台进行，不影响完成，未通过的文件不能预览和下载。</div></div>
  </section>;
}

function PageTaskEditor({ config, onChange, readOnly }: { config: PageTaskConfig; onChange: (config: PageTaskConfig) => void; readOnly?: boolean }) {
  const set = (patch: Partial<PageTaskConfig>) => onChange({ ...config, ...patch });
  const parameters = config.parameters ?? [];
  return <section className="wf-card">
    <Field label="目标页面 *" hint="只允许以 / 开头的控制台内部路由；不接受外部地址"><AutoComplete value={config.pageRoute} disabled={readOnly} style={{ width: '100%' }}
      options={routeOptions} filterOption={(input, option) => String(option?.label ?? '').includes(input)} onChange={pageRoute => set({ pageRoute })} placeholder="/commercial/agreements" /></Field>
    <Field label="跳转参数（在流程节点上映射到实例变量）">
      {parameters.map((parameter, index) => <div key={index} className="wf-option-row">
        <Input value={parameter.key} disabled={readOnly} placeholder="参数名，如 channelCode"
          onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, key: event.target.value.trim() } : item) })} />
        <Input value={parameter.label} disabled={readOnly} placeholder="说明，如 渠道编码"
          onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, label: event.target.value } : item) })} />
        {!readOnly && <Button type="text" danger aria-label="删除参数" icon={<CloseOutlined />} onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })} />}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', label: '' }] })} icon={<PlusOutlined />}>添加参数</Button>}</Field>
    <Field label="执行说明"><Input.TextArea rows={4} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    <div className="wf-subtle"><div>输出：<code>&lt;动作键&gt;.note</code>（说明，选填）</div><div className="muted">执行人打开页面完成操作后点「完成」</div></div>
  </section>;
}

function ManualConfirmEditor({ config, onChange, readOnly }: { config: ManualConfirmConfig; onChange: (config: ManualConfirmConfig) => void; readOnly?: boolean }) {
  return <section className="wf-card">
    <Field label="执行说明 *"><Input.TextArea rows={5} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => onChange({ instruction: event.target.value })} /></Field>
    <div className="wf-subtle"><div>输出：<code>&lt;动作键&gt;.note</code>（说明，选填）</div><div className="muted">执行人点「完成」时记录完成人与时间</div></div>
  </section>;
}

function DataQueryEditor({ config, onChange, readOnly }: { config: DataQueryConfig; onChange: (config: DataQueryConfig) => void; readOnly?: boolean }) {
  const set = (patch: Partial<DataQueryConfig>) => onChange({ ...config, ...patch });
  const parameters = config.parameters ?? [];
  const rule = config.passRule ?? { column: '', operator: 'GTE' as const, value: '' };
  return <>
    <Alert type="warning" showIcon title="预留类型：可以先配置，流程发布时不能引用"
      description="等 data-service 提供查询目录后启用；SQL 由 data-service 维护，查询有误时在 data-service 修正，再让执行人重新验证，不需要改流程。本页的内部结构为前端暂定，查询编码需手填。" />
    <section className="wf-card">
      <Field label="查询编码 *" hint="data-service 的查询目录未接入，这里不能选择，也看不到 SQL"><Input value={config.queryCode} disabled={readOnly} maxLength={64}
        onChange={event => set({ queryCode: event.target.value.toUpperCase() })} placeholder="CHANNEL_MID_COUNT" /></Field>
      <Field label="参数（来源：启动表单 / 前面节点的动作输出 / 固定值）">
        {parameters.map((parameter, index) => <div key={index} className="wf-option-row">
          <Input value={parameter.key} disabled={readOnly} placeholder="查询参数" onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, key: event.target.value } : item) })} />
          <Select value={parameter.source} disabled={readOnly} style={{ width: 110 }} options={[{ value: 'VARIABLE', label: '实例变量' }, { value: 'CONSTANT', label: '固定值' }]}
            onChange={source => set({ parameters: parameters.map((item, current) => current === index ? { ...item, source } : item) })} />
          <Input value={parameter.value} disabled={readOnly} placeholder={parameter.source === 'VARIABLE' ? 'start.channelCode' : 'ACTIVE'}
            onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, value: event.target.value } : item) })} />
          {!readOnly && <Button type="text" danger aria-label="删除参数" icon={<CloseOutlined />} onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })} />}</div>)}
        {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', source: 'VARIABLE', value: '' }] })} icon={<PlusOutlined />}>添加参数</Button>}</Field>
      <Field label="通过规则 *" hint="查询结果第一行满足条件才算验证通过"><div className="wf-inline">第一行的
        <Input value={rule.column} disabled={readOnly} style={{ width: 180 }} placeholder="列名" onChange={event => set({ passRule: { ...rule, column: event.target.value } })} />
        <Select value={rule.operator} disabled={readOnly} style={{ width: 110 }} options={operatorsByType.NUMBER.map(value => ({ value, label: operatorLabels[value] }))}
          onChange={operator => set({ passRule: { ...rule, operator } })} />
        <Input value={rule.value} disabled={readOnly} style={{ width: 120 }} onChange={event => set({ passRule: { ...rule, value: event.target.value } })} /></div></Field>
      <Field label="证据列" hint="验证记录里保存哪些列，供审批人查看"><Select mode="tags" value={config.evidenceColumns ?? []} disabled={readOnly} style={{ width: '100%' }}
        onChange={evidenceColumns => set({ evidenceColumns })} /></Field>
    </section>
  </>;
}

/** 执行人看到的样子（各类型，只读）：与我的待办展开的动作行同一套组件与样式，按钮不可用。 */
export function ExecutorPreview({ template }: { template: Pick<ActionTemplate, 'templateName' | 'actionType' | 'config'> }) {
  const config = template.config ?? defaultConfig(template.actionType);
  const instruction = (config as { instruction?: string }).instruction;
  const page = config as PageTaskConfig;
  return <div className="wf-action-row is-open wf-preview-card">
    <div className="wf-action-row-head"><span className="wf-action-icon">{actionTypeIcons[template.actionType]}</span>
      <div className="wf-grow"><strong>{template.templateName || '（未命名动作）'}</strong><div className="muted">{actionTypeLabels[template.actionType]}</div></div>
      <Tag>待处理</Tag></div>
    <div className="wf-action-row-body">
      {instruction && <p className="wf-instruction">{instruction}</p>}
      {template.actionType === 'FORM' && ((config as FormDefinition).fields?.length
        ? <FormPreview form={config as FormDefinition} readOnly /> : <span className="muted">还没有字段</span>)}
      {template.actionType === 'FILE_UPLOAD' && <div className="wf-field"><div className="wf-field-label">上传文件{uploadRequired(config as FileUploadConfig) ? ' · 至少一个' : '（选填）'}</div>
        <div className="wf-upload-zone is-preview">拖入文件或点击上传</div></div>}
      {template.actionType === 'PAGE_TASK' && <div className="wf-page-link"><div><strong>{page.pageRoute || '（未选择页面）'}</strong>
        <div className="muted">{page.pageRoute}{(page.parameters ?? []).length ? `?${(page.parameters ?? []).map(item => `${item.key}=…`).join('&')}` : ''}（参数来自实例变量）</div></div>
        <Button disabled>打开页面</Button></div>}
      {(template.actionType === 'PAGE_TASK' || template.actionType === 'MANUAL_CONFIRM') && <div className="wf-field"><div className="wf-field-label">说明（选填）</div>
        <Input.TextArea rows={2} disabled /></div>}
      {template.actionType === 'DATA_QUERY' && <Alert type="info" showIcon title="数据查询即将支持：本期不能被流程引用" />}
      {template.actionType !== 'DATA_QUERY' && <div className="wf-action-foot"><Button type="primary" disabled>完成</Button><span className="muted">预览 · 不可提交</span></div>}
    </div>
  </div>;
}

/** 接口 6：引用该模板的已发布流程版本（冻结的模板版本、是否等于当前、运行中实例数）。 */
export function useTemplateReferences(template: ActionTemplate | undefined, enabled: boolean) {
  const [state, setState] = useState<{ loading: boolean; data: TemplateReferences | null; failure: unknown }>({ loading: false, data: null, failure: undefined });
  useEffect(() => {
    if (!enabled || !template) return;
    const controller = new AbortController();
    setState(current => ({ ...current, loading: true, failure: undefined }));
    session.request(workflowPaths.templateReferences(template.templateId), { signal: controller.signal }, decodeReferences)
      .then(data => { if (!controller.signal.aborted) setState({ loading: false, data, failure: undefined }); })
      .catch(failure => { if (!controller.signal.aborted) setState({ loading: false, data: null, failure }); });
    return () => controller.abort();
  }, [template, enabled]);
  return state;
}

/** 选中一行“用旧配置”的版本时读取该版本的冻结配置，与当前配置逐项对比。 */
function useFrozenConfig(templateId: string, item: TemplateReferenceItem | undefined) {
  const [config, setConfig] = useState<{ key: string; value: ActionConfig | null } | null>(null);
  const key = item ? `${item.definitionId}:${item.versionNo}` : '';
  useEffect(() => {
    if (!item || item.sameAsCurrent) return;
    const controller = new AbortController();
    session.request(workflowPaths.version(item.definitionId, item.versionNo), { signal: controller.signal }, decodeVersion)
      .then(version => { if (!controller.signal.aborted) setConfig({ key, value: frozenConfig(version, templateId) }); })
      .catch(() => { if (!controller.signal.aborted) setConfig({ key, value: null }); });
    return () => controller.abort();
  }, [item, key, templateId]);
  return config?.key === key ? config.value : undefined;
}

/** 引用与影响（原型 17）：数据来自接口 6；草稿在发布时才冻结配置，不在列表中。 */
export function ReferencesView({ template, state }: { template: ActionTemplate; state: ReturnType<typeof useTemplateReferences> }) {
  const [selected, setSelected] = useState<string>();
  const items = state.data?.items ?? [];
  const current = items.find(item => `${item.definitionId}:${item.versionNo}` === selected) ?? items.find(item => !item.sameAsCurrent);
  const frozen = useFrozenConfig(template.templateId, current && !current.sameAsCurrent ? current : undefined);
  if (state.loading && !state.data) return <Spin />;
  if (state.failure) return <LoadFailed failure={state.failure} title="引用查询失败" />;
  if (!state.data) return null;
  const summary = referenceSummary(state.data);
  const diff = current && !current.sameAsCurrent && frozen ? configDiff(template.actionType, frozen, template.config) : [];
  return <div className="wf-template-grid">
    <section className="wf-card"><div className="wf-card-title">哪些流程在用<span className="muted">当前配置：版本 {state.data.currentTemplateVersion}</span></div>
      <div className="wf-counters">
        <div><strong>{summary.definitions}</strong>流程定义</div>
        <div><strong>{summary.versions}</strong>已发布版本</div>
        <div><strong className="wf-info-text">{summary.running}</strong>运行中实例</div>
        <div><strong className="wf-warn-text">{summary.outdated}</strong>用旧配置的版本</div>
      </div>
      {!items.length ? <Empty description="还没有已发布的流程引用这个动作" /> : <Table<TemplateReferenceItem> size="small" rowKey={row => `${row.definitionId}:${row.versionNo}`} pagination={false}
        dataSource={items} rowClassName={row => current && row.definitionId === current.definitionId && row.versionNo === current.versionNo ? 'wf-row-selected' : ''}
        onRow={row => ({ onClick: () => setSelected(`${row.definitionId}:${row.versionNo}`) })} columns={[
          { title: '流程定义 / 版本', render: (_, row) => <span><strong>{row.definitionName}</strong> v{row.versionNo}<div className="muted">{row.definitionCode}</div></span> },
          { title: '版本状态', render: (_, row) => <Tag color={row.currentVersion ? 'green' : 'default'}>{row.currentVersion ? '当前发布' : '历史版本'}</Tag> },
          { title: '冻结的动作配置', render: (_, row) => `版本 ${row.frozenTemplateVersion}` },
          { title: '与当前配置', render: (_, row) => row.sameAsCurrent ? <Tag color="green">一致</Tag> : <Tag color="orange">旧配置</Tag> },
          { title: '运行中实例', align: 'right', render: (_, row) => row.runningInstanceCount },
          { title: '', render: (_, row) => <Link to={`${workflowRoutes.definitions}/${row.definitionId}/versions/${row.versionNo}`}>查看流程</Link> },
        ]} />}
      <p className="muted">流程发布时把动作当时的配置复制进版本；之后修改动作，已发布版本和它们的运行中实例都不受影响。草稿在下次发布时才冻结新配置，不在此列表中。</p>
    </section>
    <section className="wf-card"><div className="wf-card-title">配置对比{diff.length > 0 && <Tag color="orange">{diff.length} 处不同</Tag>}</div>
      {!current || current.sameAsCurrent ? <Empty description="已发布版本都与当前配置一致" /> : frozen === undefined ? <Spin /> : <>
        <div className="muted">{current.definitionName} v{current.versionNo} 冻结（版本 {current.frozenTemplateVersion}）→ 当前配置（版本 {state.data.currentTemplateVersion}）</div>
        {!diff.length && <p className="muted">冻结配置读取不到或内容相同（仅版本号不同）。</p>}
        {diff.map((item, index) => <div key={index} className={`wf-diff is-${item.kind}`}><strong>{{ added: '新增', removed: '删除', changed: '变化' }[item.kind]} · {item.label}</strong>
          <div className="wf-compare"><div className="wf-compare-before">{item.before}</div><div className="wf-compare-after">{item.after}</div></div></div>)}
        <ul className="wf-plain-list muted"><li>v{current.versionNo} 的 {current.runningInstanceCount} 个运行中实例继续按冻结配置执行</li>
          <li>想让该流程也用新配置，只能发布新版本；已发起的实例不会切换</li></ul>
      </>}
    </section>
  </div>;
}

/** 停用确认：显示被几个流程引用、已发布版本与运行中实例不受影响；引用它的草稿在发布时被校验拒绝。 */
export function DeactivateModal({ template, references, saving, error, onCancel, onConfirm }: {
  template: Pick<ActionTemplate, 'templateName' | 'actionType' | 'referencedDefinitionCount'>; references: ReturnType<typeof useTemplateReferences>;
  saving: boolean; error: string | null; onCancel: () => void; onConfirm: () => void;
}) {
  const summary = references.data ? referenceSummary(references.data) : null;
  const definitions = references.data ? new Set(references.data.items.filter(item => item.currentVersion).map(item => item.definitionId)).size
    : template.referencedDefinitionCount ?? null;
  return <Modal open title={`停用「${template.templateName}」？`} okText="确认停用" okButtonProps={{ danger: true, loading: saving }} onCancel={onCancel} onOk={onConfirm}>
    <p>{references.loading && definitions === null ? '正在读取引用…' : definitions ? <>当前被 <strong>{definitions} 个流程</strong>引用。</> : '当前没有流程引用这个动作。'}</p>
    <div className="wf-check"><span className="wf-danger-text"><StopOutlined /></span><div><strong>新发布不能再引用</strong><div className="muted">流程编辑器里不再可选；已引用它的草稿在发布时会提示替换</div></div></div>
    <div className="wf-check"><span className="wf-ok-text"><CheckOutlined /></span><div><strong>已发布版本照常运行</strong><div className="muted">
      {summary ? `${summary.versions} 个已发布版本、${summary.running} 个运行中实例按发布时的配置执行` : '发布时已冻结配置，按原样执行'}</div></div></div>
    <div className="wf-check"><span className="wf-info-text"><UndoOutlined /></span><div><strong>可以随时重新启用</strong></div></div>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

/** 新建动作 · 选择类型（原型 07）：四类可选，数据查询标「即将支持」不可选；「下一步」把类型交给调用方（进入编辑页或抽屉）。 */
export function ActionTypePicker({ onCancel, onNext }: { onCancel: () => void; onNext: (type: ActionType) => void }) {
  const [type, setType] = useState<ActionType>('FORM');
  return <Modal open width={760} title="新建动作 · 选择类型" onCancel={onCancel} footer={<div className="wf-modal-foot">
    <span className="muted">已选：{actionTypeLabels[type]}</span>
    <span><Button onClick={onCancel}>取消</Button> <Button type="primary" onClick={() => onNext(type)}>下一步</Button></span></div>}>
    <p className="muted">不同类型的配置方式不同，选好后进入对应的编辑页</p>
    <div className="wf-type-cards" role="radiogroup">{actionTypes.map(value => {
      const disabled = !creatableTypes.includes(value);
      return <button type="button" role="radio" aria-checked={type === value} key={value} disabled={disabled}
        className={`wf-type-card is-${value.toLowerCase()}${type === value ? ' is-selected' : ''}`} onClick={() => setType(value)}
        onDoubleClick={() => !disabled && onNext(value)}>
        <span className="wf-type-card-icon" aria-hidden>{actionTypeIcons[value]}</span>
        {disabled && <span className="wf-type-card-soon">即将支持</span>}
        {type === value && <span className="wf-type-card-check" aria-hidden><CheckOutlined /></span>}
        <strong>{actionTypeLabels[value]}</strong><span className="muted">{actionTypeSummaries[value]}</span></button>;
    })}</div>
  </Modal>;
}
