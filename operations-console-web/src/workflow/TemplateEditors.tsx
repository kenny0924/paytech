import { useEffect, useMemo, useState } from 'react';
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
import { actionTypeLabel, operatorLabel } from './workflowLabels';
import { tOr } from '../i18n/index';
import { useT } from '../i18n/react';
import type {
  ActionConfig, ActionTemplate, ActionType, DataQueryConfig, FileUploadConfig, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferenceItem,
  TemplateReferences,
} from './workflowTypes';
import { Field, LoadFailed, workflowRoutes } from './WorkflowParts';
import { uploadRequired } from './fileModel';

export const actionTypes: ActionType[] = ['FORM', 'FILE_UPLOAD', 'PAGE_TASK', 'MANUAL_CONFIRM', 'DATA_QUERY'];

/** 类型标签：图标 + 名称，按类型着色。 */
export function ActionTypeTag({ type }: { type: ActionType }) {
  return <span className={`wf-type-tag is-${type.toLowerCase()}`}><span aria-hidden>{actionTypeIcons[type]}</span>{actionTypeLabel(type)}</span>;
}

/** 按类型的配置编辑：表单为字段设计，其他类型为说明（原型 08，需求 E1）。 */
export function ConfigEditor({ type, config, onChange, readOnly, templateName, description, savedKeys }: {
  type: ActionType; config: ActionConfig; onChange: (config: ActionConfig) => void; readOnly?: boolean;
  /** 表单画布标题与说明（执行人在待办里看到的动作名称与描述）。 */
  templateName?: string; description?: string;
  /** 已保存配置的字段键，键不再随显示名称自动生成。 */
  savedKeys?: ReadonlySet<string>;
}) {
  const t = useT('workflow');
  switch (type) {
    case 'FORM': return <FieldDesigner form={config as FormDefinition} readOnly={readOnly} variablePrefix={`${t('config.actionKey')}.`} savedKeys={savedKeys}
      previewTitle={templateName || t('config.unnamedAction')} previewDescription={description} onChange={onChange} />;
    case 'FILE_UPLOAD': return <FileUploadEditor config={config as FileUploadConfig} onChange={onChange} readOnly={readOnly} />;
    case 'PAGE_TASK': return <PageTaskEditor config={config as PageTaskConfig} onChange={onChange} readOnly={readOnly} />;
    case 'MANUAL_CONFIRM': return <ManualConfirmEditor config={config as ManualConfirmConfig} onChange={onChange} readOnly={readOnly} />;
    case 'DATA_QUERY': return <DataQueryEditor config={config as DataQueryConfig} onChange={onChange} readOnly={readOnly} />;
  }
}

/** 上传文件：只有执行说明与「至少上传一个」（需求 E1）；旧配置 minFiles ≥ 1 显示为已开启，不再写数量与类型。 */
function FileUploadEditor({ config, onChange, readOnly }: { config: FileUploadConfig; onChange: (config: FileUploadConfig) => void; readOnly?: boolean }) {
  const t = useT('workflow');
  const set = (patch: Partial<FileUploadConfig>) => onChange({ required: uploadRequired(config), instruction: config.instruction ?? '', ...patch });
  return <section className="wf-card">
    <Field label={t('config.instruction')} hint={t('config.fileInstructionHint', { count: (config.instruction ?? '').length, max: instructionLimit })}>
      <Input.TextArea rows={6} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    <label className="wf-inline"><Switch size="small" checked={uploadRequired(config)} disabled={readOnly} onChange={required => set({ required })} /> {t('config.atLeastOne')}</label>
    <div className="wf-subtle"><div>{t('config.output')}<code>{`${t('config.actionKey')}.files`}</code></div>
      <div className="muted">{t('config.fileOutputHint')}</div></div>
  </section>;
}

function PageTaskEditor({ config, onChange, readOnly }: { config: PageTaskConfig; onChange: (config: PageTaskConfig) => void; readOnly?: boolean }) {
  const t = useT('workflow'), tNav = useT('nav');
  const set = (patch: Partial<PageTaskConfig>) => onChange({ ...config, ...patch });
  const parameters = config.parameters ?? [];
  const routeOptions = useMemo(() => pageCapabilities.map(page => ({ value: page.frontendRoute as string,
    label: t('config.routeOption', { name: tOr(tNav, 'resource.' + page.componentKey, page.displayName), route: page.frontendRoute }) })), [t, tNav]);
  return <section className="wf-card">
    <Field label={t('config.pageRoute')} hint={t('config.pageRouteHint')}><AutoComplete value={config.pageRoute} disabled={readOnly} style={{ width: '100%' }}
      options={routeOptions} filterOption={(input, option) => String(option?.label ?? '').includes(input)} onChange={pageRoute => set({ pageRoute })} placeholder="/commercial/agreements" /></Field>
    <Field label={t('config.parameters')}>
      {parameters.map((parameter, index) => <div key={index} className="wf-option-row">
        <Input value={parameter.key} disabled={readOnly} placeholder={t('config.parameterKeyPlaceholder')}
          onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, key: event.target.value.trim() } : item) })} />
        <Input value={parameter.label} disabled={readOnly} placeholder={t('config.parameterLabelPlaceholder')}
          onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, label: event.target.value } : item) })} />
        {!readOnly && <Button type="text" danger aria-label={t('config.deleteParameter')} icon={<CloseOutlined />} onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })} />}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', label: '' }] })} icon={<PlusOutlined />}>{t('config.addParameter')}</Button>}</Field>
    <Field label={t('config.instruction')}><Input.TextArea rows={4} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => set({ instruction: event.target.value })} /></Field>
    <div className="wf-subtle"><div>{t('config.output')}<code>{`${t('config.actionKey')}.note`}</code>{t('config.noteOptional')}</div><div className="muted">{t('config.pageTaskHint')}</div></div>
  </section>;
}

function ManualConfirmEditor({ config, onChange, readOnly }: { config: ManualConfirmConfig; onChange: (config: ManualConfirmConfig) => void; readOnly?: boolean }) {
  const t = useT('workflow');
  return <section className="wf-card">
    <Field label={t('config.instructionRequiredLabel')}><Input.TextArea rows={5} maxLength={instructionLimit} value={config.instruction} disabled={readOnly} onChange={event => onChange({ instruction: event.target.value })} /></Field>
    <div className="wf-subtle"><div>{t('config.output')}<code>{`${t('config.actionKey')}.note`}</code>{t('config.noteOptional')}</div><div className="muted">{t('config.manualHint')}</div></div>
  </section>;
}

function DataQueryEditor({ config, onChange, readOnly }: { config: DataQueryConfig; onChange: (config: DataQueryConfig) => void; readOnly?: boolean }) {
  const t = useT('workflow');
  const set = (patch: Partial<DataQueryConfig>) => onChange({ ...config, ...patch });
  const parameters = config.parameters ?? [];
  const rule = config.passRule ?? { column: '', operator: 'GTE' as const, value: '' };
  return <>
    <Alert type="warning" showIcon title={t('dataQuery.reserved')}
      description={t('dataQuery.reservedHint')} />
    <section className="wf-card">
      <Field label={t('dataQuery.queryCode')} hint={t('dataQuery.queryCodeHint')}><Input value={config.queryCode} disabled={readOnly} maxLength={64}
        onChange={event => set({ queryCode: event.target.value.toUpperCase() })} placeholder="CHANNEL_MID_COUNT" /></Field>
      <Field label={t('dataQuery.parameters')}>
        {parameters.map((parameter, index) => <div key={index} className="wf-option-row">
          <Input value={parameter.key} disabled={readOnly} placeholder={t('dataQuery.parameter')} onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, key: event.target.value } : item) })} />
          <Select value={parameter.source} disabled={readOnly} style={{ width: 110 }} options={[{ value: 'VARIABLE', label: t('dataQuery.source.VARIABLE') }, { value: 'CONSTANT', label: t('dataQuery.source.CONSTANT') }]}
            onChange={source => set({ parameters: parameters.map((item, current) => current === index ? { ...item, source } : item) })} />
          <Input value={parameter.value} disabled={readOnly} placeholder={parameter.source === 'VARIABLE' ? 'start.channelCode' : 'ACTIVE'}
            onChange={event => set({ parameters: parameters.map((item, current) => current === index ? { ...item, value: event.target.value } : item) })} />
          {!readOnly && <Button type="text" danger aria-label={t('config.deleteParameter')} icon={<CloseOutlined />} onClick={() => set({ parameters: parameters.filter((_, current) => current !== index) })} />}</div>)}
        {!readOnly && <Button type="link" onClick={() => set({ parameters: [...parameters, { key: '', source: 'VARIABLE', value: '' }] })} icon={<PlusOutlined />}>{t('config.addParameter')}</Button>}</Field>
      <Field label={t('dataQuery.passRule')} hint={t('dataQuery.passRuleHint')}><div className="wf-inline">{t('dataQuery.firstRow')}
        <Input value={rule.column} disabled={readOnly} style={{ width: 180 }} placeholder={t('dataQuery.column')} onChange={event => set({ passRule: { ...rule, column: event.target.value } })} />
        <Select value={rule.operator} disabled={readOnly} style={{ width: 110 }} options={operatorsByType.NUMBER.map(value => ({ value, label: operatorLabel(value) }))}
          onChange={operator => set({ passRule: { ...rule, operator } })} />
        <Input value={rule.value} disabled={readOnly} style={{ width: 120 }} onChange={event => set({ passRule: { ...rule, value: event.target.value } })} /></div></Field>
      <Field label={t('dataQuery.evidence')} hint={t('dataQuery.evidenceHint')}><Select mode="tags" value={config.evidenceColumns ?? []} disabled={readOnly} style={{ width: '100%' }}
        onChange={evidenceColumns => set({ evidenceColumns })} /></Field>
    </section>
  </>;
}

/** 执行人看到的样子（各类型，只读）：与我的待办展开的动作行同一套组件与样式，按钮不可用。 */
export function ExecutorPreview({ template }: { template: Pick<ActionTemplate, 'templateName' | 'actionType' | 'config'> }) {
  const t = useT('workflow');
  const config = template.config ?? defaultConfig(template.actionType);
  const instruction = (config as { instruction?: string }).instruction;
  const page = config as PageTaskConfig;
  return <div className="wf-action-row is-open wf-preview-card">
    <div className="wf-action-row-head"><span className="wf-action-icon">{actionTypeIcons[template.actionType]}</span>
      <div className="wf-grow"><strong>{template.templateName || t('config.unnamedAction')}</strong><div className="muted">{actionTypeLabel(template.actionType)}</div></div>
      <Tag>{t('enum.actionRunStatus.PENDING')}</Tag></div>
    <div className="wf-action-row-body">
      {instruction && <p className="wf-instruction">{instruction}</p>}
      {template.actionType === 'FORM' && ((config as FormDefinition).fields?.length
        ? <FormPreview form={config as FormDefinition} readOnly /> : <span className="muted">{t('designer.noFields')}</span>)}
      {template.actionType === 'FILE_UPLOAD' && <div className="wf-field"><div className="wf-field-label">{uploadRequired(config as FileUploadConfig) ? t('preview.uploadRequired') : t('preview.uploadOptional')}</div>
        <div className="wf-upload-zone is-preview">{t('preview.dropHint')}</div></div>}
      {template.actionType === 'PAGE_TASK' && <div className="wf-page-link"><div><strong>{page.pageRoute || t('preview.noPage')}</strong>
        <div className="muted">{page.pageRoute}{(page.parameters ?? []).length ? `?${(page.parameters ?? []).map(item => `${item.key}=…`).join('&')}` : ''}{t('preview.parametersFromVariables')}</div></div>
        <Button disabled>{t('preview.openPage')}</Button></div>}
      {(template.actionType === 'PAGE_TASK' || template.actionType === 'MANUAL_CONFIRM') && <div className="wf-field"><div className="wf-field-label">{t('preview.noteOptional')}</div>
        <Input.TextArea rows={2} disabled /></div>}
      {template.actionType === 'DATA_QUERY' && <Alert type="info" showIcon title={t('preview.dataQuerySoon')} />}
      {template.actionType !== 'DATA_QUERY' && <div className="wf-action-foot"><Button type="primary" disabled>{t('preview.complete')}</Button><span className="muted">{t('preview.notSubmittable')}</span></div>}
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
  const t = useT('workflow');
  const [selected, setSelected] = useState<string>();
  const items = state.data?.items ?? [];
  const current = items.find(item => `${item.definitionId}:${item.versionNo}` === selected) ?? items.find(item => !item.sameAsCurrent);
  const frozen = useFrozenConfig(template.templateId, current && !current.sameAsCurrent ? current : undefined);
  if (state.loading && !state.data) return <Spin />;
  if (state.failure) return <LoadFailed failure={state.failure} title={t('references.loadFailed')} />;
  if (!state.data) return null;
  const summary = referenceSummary(state.data);
  const diff = current && !current.sameAsCurrent && frozen ? configDiff(template.actionType, frozen, template.config) : [];
  return <div className="wf-template-grid">
    <section className="wf-card"><div className="wf-card-title">{t('references.title')}<span className="muted">{t('references.currentVersion', { version: state.data.currentTemplateVersion })}</span></div>
      <div className="wf-counters">
        <div><strong>{summary.definitions}</strong>{t('references.counter.definitions')}</div>
        <div><strong>{summary.versions}</strong>{t('references.counter.versions')}</div>
        <div><strong className="wf-info-text">{summary.running}</strong>{t('references.counter.running')}</div>
        <div><strong className="wf-warn-text">{summary.outdated}</strong>{t('references.counter.outdated')}</div>
      </div>
      {!items.length ? <Empty description={t('references.empty')} /> : <Table<TemplateReferenceItem> size="small" rowKey={row => `${row.definitionId}:${row.versionNo}`} pagination={false}
        dataSource={items} rowClassName={row => current && row.definitionId === current.definitionId && row.versionNo === current.versionNo ? 'wf-row-selected' : ''}
        onRow={row => ({ onClick: () => setSelected(`${row.definitionId}:${row.versionNo}`) })} columns={[
          { title: t('references.column.definition'), render: (_, row) => <span><strong>{row.definitionName}</strong> v{row.versionNo}<div className="muted">{row.definitionCode}</div></span> },
          { title: t('references.column.versionStatus'), render: (_, row) => <Tag color={row.currentVersion ? 'green' : 'default'}>{row.currentVersion ? t('references.current') : t('references.historical')}</Tag> },
          { title: t('references.column.frozen'), render: (_, row) => t('references.version', { version: row.frozenTemplateVersion }) },
          { title: t('references.column.compare'), render: (_, row) => row.sameAsCurrent ? <Tag color="green">{t('references.same')}</Tag> : <Tag color="orange">{t('references.outdated')}</Tag> },
          { title: t('references.counter.running'), align: 'right', render: (_, row) => row.runningInstanceCount },
          { title: '', render: (_, row) => <Link to={`${workflowRoutes.definitions}/${row.definitionId}/versions/${row.versionNo}`}>{t('references.viewProcess')}</Link> },
        ]} />}
      <p className="muted">{t('references.note')}</p>
    </section>
    <section className="wf-card"><div className="wf-card-title">{t('references.compare')}{diff.length > 0 && <Tag color="orange">{t('references.differences', { count: diff.length })}</Tag>}</div>
      {!current || current.sameAsCurrent ? <Empty description={t('references.allSame')} /> : frozen === undefined ? <Spin /> : <>
        <div className="muted">{t('references.compareLine', { name: current.definitionName, version: current.versionNo, frozen: current.frozenTemplateVersion, current: state.data.currentTemplateVersion })}</div>
        {!diff.length && <p className="muted">{t('references.frozenUnavailable')}</p>}
        {diff.map((item, index) => <div key={index} className={`wf-diff is-${item.kind}`}><strong>{t(item.kind === 'added' ? 'references.diff.added' : item.kind === 'removed' ? 'references.diff.removed' : 'references.diff.changed')} · {item.label}</strong>
          <div className="wf-compare"><div className="wf-compare-before">{item.before}</div><div className="wf-compare-after">{item.after}</div></div></div>)}
        <ul className="wf-plain-list muted"><li>{t('references.runningKeep', { version: current.versionNo, count: current.runningInstanceCount })}</li>
          <li>{t('references.publishToUpdate')}</li></ul>
      </>}
    </section>
  </div>;
}

/** 停用确认：显示被几个流程引用、已发布版本与运行中实例不受影响；引用它的草稿在发布时被校验拒绝。 */
export function DeactivateModal({ template, references, saving, error, onCancel, onConfirm }: {
  template: Pick<ActionTemplate, 'templateName' | 'actionType' | 'referencedDefinitionCount'>; references: ReturnType<typeof useTemplateReferences>;
  saving: boolean; error: string | null; onCancel: () => void; onConfirm: () => void;
}) {
  const t = useT('workflow');
  const summary = references.data ? referenceSummary(references.data) : null;
  const definitions = references.data ? new Set(references.data.items.filter(item => item.currentVersion).map(item => item.definitionId)).size
    : template.referencedDefinitionCount ?? null;
  return <Modal open title={t('deactivate.title', { name: template.templateName })} okText={t('deactivate.confirm')} cancelText={t('action.cancel')} okButtonProps={{ danger: true, loading: saving }} onCancel={onCancel} onOk={onConfirm}>
    <p>{references.loading && definitions === null ? t('deactivate.loading') : definitions ? t('deactivate.referenced', { count: definitions }) : t('deactivate.notReferenced')}</p>
    <div className="wf-check"><span className="wf-danger-text"><StopOutlined /></span><div><strong>{t('deactivate.noNewUse')}</strong><div className="muted">{t('deactivate.noNewUseHint')}</div></div></div>
    <div className="wf-check"><span className="wf-ok-text"><CheckOutlined /></span><div><strong>{t('deactivate.publishedKeep')}</strong><div className="muted">
      {summary ? t('deactivate.publishedKeepCount', { versions: summary.versions, running: summary.running }) : t('deactivate.publishedKeepHint')}</div></div></div>
    <div className="wf-check"><span className="wf-info-text"><UndoOutlined /></span><div><strong>{t('deactivate.reenable')}</strong></div></div>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

/** 新建动作 · 选择类型（原型 07）：四类可选，数据查询标「即将支持」不可选；「下一步」把类型交给调用方（进入编辑页或抽屉）。 */
export function ActionTypePicker({ onCancel, onNext }: { onCancel: () => void; onNext: (type: ActionType) => void }) {
  const t = useT('workflow');
  const [type, setType] = useState<ActionType>('FORM');
  return <Modal open width={760} title={t('picker.title')} onCancel={onCancel} footer={<div className="wf-modal-foot">
    <span className="muted">{t('picker.selected', { type: actionTypeLabel(type) })}</span>
    <span><Button onClick={onCancel}>{t('action.cancel')}</Button> <Button type="primary" onClick={() => onNext(type)}>{t('picker.next')}</Button></span></div>}>
    <p className="muted">{t('picker.hint')}</p>
    <div className="wf-type-cards" role="radiogroup">{actionTypes.map(value => {
      const disabled = !creatableTypes.includes(value);
      return <button type="button" role="radio" aria-checked={type === value} key={value} disabled={disabled}
        className={`wf-type-card is-${value.toLowerCase()}${type === value ? ' is-selected' : ''}`} onClick={() => setType(value)}
        onDoubleClick={() => !disabled && onNext(value)}>
        <span className="wf-type-card-icon" aria-hidden>{actionTypeIcons[value]}</span>
        {disabled && <span className="wf-type-card-soon">{t('picker.soon')}</span>}
        {type === value && <span className="wf-type-card-check" aria-hidden><CheckOutlined /></span>}
        <strong>{actionTypeLabel(value)}</strong><span className="muted">{t(`picker.summary.${value}`)}</span></button>;
    })}</div>
  </Modal>;
}
