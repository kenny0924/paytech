import { useEffect, useState } from 'react';
import { Alert, AutoComplete, Button, Empty, Form, Input, InputNumber, Modal, Select, Spin, Steps, Switch, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { session } from '../api/session';
import { pageCapabilities } from '../navigation/pageCapabilities';
import { FieldDesigner, FormPreview } from './FieldDesigner';
import { operatorsByType } from './graphModel';
import { configDiff, configProblems, defaultConfig, fileCountCeiling, frozenConfig, instructionLimit, normalizeConfig, referenceSummary, templateCodePattern } from './templateModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { decodeMutation, decodeReferences, decodeVersion, workflowPaths } from './workflowApi';
import { actionTypeGuides, actionTypeLabels, operatorLabels } from './workflowLabels';
import type {
  ActionConfig, ActionTemplate, ActionType, DataQueryConfig, FileUploadConfig, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferenceItem,
  TemplateReferences,
} from './workflowTypes';
import { Field, LoadFailed, workflowRoutes } from './WorkflowParts';
import { rulesHint, uploadRules } from './fileModel';

export const actionTypes: ActionType[] = ['FORM', 'FILE_UPLOAD', 'PAGE_TASK', 'MANUAL_CONFIRM', 'DATA_QUERY'];
const routeOptions = pageCapabilities.map(page => ({ value: page.frontendRoute as string, label: `${page.displayName}（${page.frontendRoute}）` }));
const mimeOptions = [
  { value: 'application/pdf', label: 'PDF' }, { value: 'application/x-pem-file', label: 'PEM 证书' }, { value: 'application/x-pkcs12', label: 'PKCS#12' },
  { value: 'application/zip', label: 'ZIP' }, { value: 'image/png', label: 'PNG' }, { value: 'image/jpeg', label: 'JPEG' },
  { value: 'text/csv', label: 'CSV' }, { value: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', label: 'Excel' },
];

/** 按类型的配置编辑（原型 12、14—16）。 */
export function ConfigEditor({ type, config, onChange, readOnly }: {
  type: ActionType; config: ActionConfig; onChange: (config: ActionConfig) => void; readOnly?: boolean;
}) {
  switch (type) {
    case 'FORM': return <FieldDesigner form={config as FormDefinition} readOnly={readOnly} variablePrefix="<动作键>." previewTitle="字段" onChange={onChange} />;
    case 'FILE_UPLOAD': return <FileUploadEditor config={config as FileUploadConfig} onChange={onChange} readOnly={readOnly} />;
    case 'PAGE_TASK': return <PageTaskEditor config={config as PageTaskConfig} onChange={onChange} readOnly={readOnly} />;
    case 'MANUAL_CONFIRM': return <ManualConfirmEditor config={config as ManualConfirmConfig} onChange={onChange} readOnly={readOnly} />;
    case 'DATA_QUERY': return <DataQueryEditor config={config as DataQueryConfig} onChange={onChange} readOnly={readOnly} />;
  }
}

function FileUploadEditor({ config, onChange, readOnly }: { config: FileUploadConfig; onChange: (config: FileUploadConfig) => void; readOnly?: boolean }) {
  const set = (patch: Partial<FileUploadConfig>) => onChange({ ...config, ...patch });
  return <div className="wf-template-grid">
    <section className="wf-card"><div className="wf-card-title">上传规则</div>
      <Field label="文件数量" hint={`执行人至少上传多少、最多上传多少；上限不超过 ${fileCountCeiling}`}><div className="wf-inline">
        至少 <InputNumber min={0} max={fileCountCeiling} value={config.minFiles} disabled={readOnly} onChange={value => set({ minFiles: value ?? 0 })} />
        最多 <InputNumber min={1} max={fileCountCeiling} value={config.maxFiles} disabled={readOnly} onChange={value => set({ maxFiles: value ?? 1 })} /></div></Field>
      <Field label="允许的文件类型" hint="按 MIME 类型校验，不只看扩展名"><Select mode="tags" value={config.mimeTypes ?? []} disabled={readOnly} style={{ width: '100%' }}
        options={mimeOptions.map(item => ({ value: item.value, label: `${item.label} ${item.value}` }))} onChange={mimeTypes => set({ mimeTypes })} /></Field>
      <Field label="单个文件大小"><span className="muted">由文件服务统一限制，动作里不单独配置</span></Field>
      <Field label="执行说明 *" hint={`显示在执行人待办里；写清楚要传什么、从哪里拿（${(config.instruction ?? '').length} / ${instructionLimit}）`}>
        <Input.TextArea rows={6} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    </section>
    <section className="wf-card"><div className="wf-card-title">验证规则 <Tag color="green">自动</Tag></div>
      <p className="muted">执行人提交时系统逐条检查；全部通过动作才变为“已验证”。</p>
      {[['文件属于本实例', '通过本动作上传，挂在当前实例下；不能引用其他实例或别处的文件'], ['扫描已通过', '每个文件的状态为“可用”（AVAILABLE）'],
        ['数量符合', `${config.minFiles ?? 0} ≤ 文件数 ≤ ${config.maxFiles ?? '?'}`], ['类型符合', 'MIME 类型在允许列表内']].map(([title, text]) =>
        <div key={title} className="wf-check"><span className="wf-ok-text">✓</span><div><strong>{title}</strong><div className="muted">{text}</div></div></div>)}
      <Alert type="info" showIcon title="提交时文件还在扫描" description="动作记为“未验证”，原因写明哪些文件未就绪；扫描完成后执行人点“重新验证”，不用重新上传；每次验证都留记录。" />
    </section>
  </div>;
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
        {!readOnly && <Button type="text" danger onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })}>✕</Button>}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', label: '' }] })}>＋ 添加参数</Button>}</Field>
    <Field label="执行说明"><Input.TextArea rows={4} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    <div className="wf-subtle"><div>输出：<code>&lt;动作键&gt;.note</code>（确认说明，选填）</div><div className="muted">验证：执行人确认完成；以后可改为数据查询验证</div></div>
  </section>;
}

function ManualConfirmEditor({ config, onChange, readOnly }: { config: ManualConfirmConfig; onChange: (config: ManualConfirmConfig) => void; readOnly?: boolean }) {
  return <section className="wf-card">
    <Field label="执行说明 *"><Input.TextArea rows={5} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => onChange({ ...config, instruction: event.target.value })} /></Field>
    <label className="wf-inline"><Switch size="small" checked={!!config.noteRequired} disabled={readOnly} onChange={noteRequired => onChange({ ...config, noteRequired })} /> 确认说明必填</label>
    <div className="wf-subtle"><div>输出：<code>&lt;动作键&gt;.note</code></div><div className="muted">验证：记录确认人与时间</div></div>
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
          {!readOnly && <Button type="text" danger onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })}>✕</Button>}</div>)}
        {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', source: 'VARIABLE', value: '' }] })}>＋ 添加参数</Button>}</Field>
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

/** 执行人看到的样子（各类型）。 */
export function ExecutorPreview({ template }: { template: Pick<ActionTemplate, 'templateName' | 'actionType' | 'config'> }) {
  const config = template.config ?? defaultConfig(template.actionType);
  const instruction = (config as { instruction?: string }).instruction;
  return <div className="wf-action is-expanded wf-preview-card">
    <div className="wf-action-head"><strong>{template.templateName || '（未命名动作）'}</strong><Tag color="blue">{actionTypeLabels[template.actionType]}</Tag><Tag color="red">必选</Tag></div>
    {instruction && <p className="wf-instruction">{instruction}</p>}
    {template.actionType === 'FORM' && <FormPreview form={config as FormDefinition} />}
    {template.actionType === 'FILE_UPLOAD' && <div className="wf-upload-zone is-preview">拖入文件或点击上传（{rulesHint(uploadRules(config as FileUploadConfig))}）</div>}
    {template.actionType === 'PAGE_TASK' && <><div className="wf-page-link"><div><strong>{(config as PageTaskConfig).pageRoute || '（未选择页面）'}</strong>
      <div className="muted">{(config as PageTaskConfig).pageRoute}?{((config as PageTaskConfig).parameters ?? []).map(item => `${item.key}=…`).join('&')}</div></div>
      <Button type="primary" disabled>打开页面</Button></div><Input.TextArea rows={2} placeholder="确认说明（选填）" disabled /></>}
    {template.actionType === 'MANUAL_CONFIRM' && <Input.TextArea rows={2} placeholder={`确认说明${(config as ManualConfirmConfig).noteRequired ? ' *' : '（选填）'}`} disabled />}
    {template.actionType === 'DATA_QUERY' && <Alert type="info" showIcon title="启用后执行人看到每次验证的结论与证据列；查询出错时动作保持未验证，可重新验证" />}
    {template.actionType !== 'FORM' && template.actionType !== 'DATA_QUERY' && <div className="wf-right"><Button type="primary" disabled>{template.actionType === 'FILE_UPLOAD' ? '提交' : '确认完成'}</Button></div>}
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

/** 停用确认（原型 18）：说明影响与仍在运行的实例；引用它的草稿会在发布时被校验拒绝。 */
export function DeactivateModal({ template, references, saving, error, onCancel, onConfirm }: {
  template: ActionTemplate; references: ReturnType<typeof useTemplateReferences>; saving: boolean; error: string | null; onCancel: () => void; onConfirm: () => void;
}) {
  const summary = references.data ? referenceSummary(references.data) : null;
  return <Modal open title={`停用“${template.templateName}”？`} okText="确认停用" okButtonProps={{ danger: true, loading: saving }} onCancel={onCancel} onOk={onConfirm}>
    <div className="muted">{template.templateCode} · {actionTypeLabels[template.actionType]} · 当前版本 {template.version}</div>
    <div className="wf-check"><span className="wf-danger-text">⊘</span><div><strong>新发布不能再引用</strong><div className="muted">流程编辑器的动作列表里不再可选；已引用它的草稿在发布时会校验失败，需要替换</div></div></div>
    <div className="wf-check"><span className="wf-ok-text">✓</span><div><strong>已发布版本照常运行</strong><div className="muted">
      {references.loading ? '正在读取引用…' : summary ? `发布时已冻结配置：${summary.versions} 个已发布版本、${summary.running} 个运行中实例按原样执行，执行人不受影响` : '发布时已冻结配置，按原样执行'}</div></div></div>
    <div className="wf-check"><span className="wf-info-text">↺</span><div><strong>可以随时重新启用</strong><div className="muted">停用也会增加版本号，防止并发覆盖</div></div></div>
    <p className="muted">停用原因：接口不记录原因（technique 7 · 接口 5 只有状态与版本），审计按操作记录。</p>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

/** 新建动作：选择类型与基本信息 → 配置规则 → 预览并保存（原型 13）。 */
export function CreateTemplateModal({ onClose, onCreated, onAuthorizationChanged, existingCodes }: {
  onClose: () => void; onCreated: (templateId: string) => void; onAuthorizationChanged: () => void; existingCodes: Set<string>;
}) {
  const [step, setStep] = useState(0);
  const [type, setType] = useState<ActionType>('FORM');
  const [config, setConfig] = useState<ActionConfig>(defaultConfig('FORM'));
  const [form] = Form.useForm<{ templateCode: string; templateName: string; description?: string }>();
  const [basic, setBasic] = useState<{ templateCode?: string; templateName?: string; description?: string }>({});
  const command = useWorkflowCommand(onAuthorizationChanged);
  const codeTaken = !!basic.templateCode && existingCodes.has(basic.templateCode);
  const codeValid = !!basic.templateCode && templateCodePattern.test(basic.templateCode) && !codeTaken;
  const problems = configProblems(type, config);
  const guide = actionTypeGuides[type];
  async function save() {
    const result = await command.run(workflowPaths.templates, 'POST', { templateCode: basic.templateCode, templateName: basic.templateName?.trim(), actionType: type,
      ...(basic.description?.trim() ? { description: basic.description.trim() } : {}), config: normalizeConfig(type, config) }, decodeMutation, '新建失败');
    if (result) onCreated(result.objectId);
  }
  return <Modal open width={1100} title="新建动作" onCancel={onClose} footer={<div className="wf-modal-foot">
    <span className={codeTaken ? 'wf-danger-text' : 'wf-ok-text'}>{step === 0 && basic.templateCode ? (codeTaken ? `编码 ${basic.templateCode} 已被使用` : codeValid ? `编码 ${basic.templateCode} 可用` : '') : step === 1 && problems.length ? <span className="wf-danger-text">{problems[0]}</span> : command.formError}</span>
    <span>{step > 0 ? <Button onClick={() => setStep(step - 1)}>上一步</Button> : <Button onClick={onClose}>取消</Button>}
      {step < 2 ? <Button type="primary" disabled={step === 0 ? !codeValid || !basic.templateName?.trim() : problems.length > 0} onClick={() => setStep(step + 1)}>
        下一步：{step === 0 ? '配置规则' : '预览并保存'}</Button>
        : <Button type="primary" loading={command.saving} onClick={() => void save()}>保存</Button>}</span></div>}>
    <Steps size="small" current={step} items={[{ title: '类型与基本信息' }, { title: '配置规则' }, { title: '预览并保存' }]} className="wf-steps" />
    {step === 0 && <div className="wf-create-template">
      <div>
        <h3>选择动作类型</h3><p className="muted">类型决定执行人看到什么、提交什么、系统怎样判断“完成”。类型在创建后不能修改。</p>
        <div className="wf-type-grid">{actionTypes.map(value => <button type="button" key={value} className={`wf-type${type === value ? ' is-selected' : ''}${value === 'DATA_QUERY' ? ' is-reserved' : ''}`}
          onClick={() => { setType(value); setConfig(defaultConfig(value)); }}>
          <strong>{actionTypeLabels[value]}</strong> <code>{value}</code>{value === 'DATA_QUERY' && <Tag color="orange">预留</Tag>}
          <div className="muted">{actionTypeGuides[value].summary}</div><div className="wf-info-text">输出：{actionTypeGuides[value].output}</div></button>)}</div>
        <h3>基本信息</h3>
        <Form form={form} layout="vertical" initialValues={basic} onValuesChange={(_, all) => setBasic(all)} className="wf-two-columns">
          <Form.Item name="templateCode" label="动作编码 *" normalize={(value?: string) => value?.toUpperCase()} extra="大写字母、数字、下划线；全局唯一，保存后不能修改"
            rules={[{ pattern: templateCodePattern, message: '格式不正确' }]}><Input maxLength={64} /></Form.Item>
          <Form.Item name="templateName" label="动作名称 *" extra="执行人在待办里看到的名称"><Input maxLength={128} /></Form.Item>
          <Form.Item name="description" label="描述" className="wf-span-two"><Input.TextArea rows={3} maxLength={512} /></Form.Item>
        </Form>
      </div>
      <aside className="wf-subtle wf-type-guide"><h3>选择“{actionTypeLabels[type]}”后</h3>
        <Field label="执行人看到">{guide.sees}</Field><Field label="提交内容">{guide.submits}</Field>
        <Field label="自动验证">{guide.verifies}</Field><Field label="可被引用为">{guide.output}</Field>
        <Alert type="info" showIcon title="同一个动作可被多个流程复用" description="流程发布时会冻结动作当时的配置；之后在这里修改，只影响以后新发布的版本。" />
      </aside>
    </div>}
    {step === 1 && <ConfigEditor type={type} config={config} onChange={setConfig} />}
    {step === 2 && <div className="wf-template-grid"><section className="wf-card"><div className="wf-card-title">{basic.templateName} <code>{basic.templateCode}</code></div>
      <p className="muted">{basic.description}</p>{type === 'DATA_QUERY' && <Alert type="warning" showIcon title="数据查询为预留类型：保存后不能被发布的流程引用" />}</section>
      <section><div className="wf-card-title">执行人看到的样子</div><ExecutorPreview template={{ templateName: basic.templateName ?? '', actionType: type, config }} /></section></div>}
  </Modal>;
}
