import { useEffect, useMemo, useState } from 'react';
import { Alert, AutoComplete, Button, Empty, Input, Modal, Select, Spin, Switch, Table, Tag } from 'antd';
import { actionTypeIcons } from './workflowIcons';
import {
  CheckOutlined, CloseOutlined, FieldTimeOutlined, PlusOutlined, StopOutlined, SyncOutlined, ThunderboltOutlined, UndoOutlined, UserOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { session } from '../api/session';
import { pageCapabilities } from '../navigation/pageCapabilities';
import { creatableTypes } from './catalogModel';
import { FieldDesigner, FormPreview } from './FieldDesigner';
import {
  configDiff, dataQueryLimits, dataQueryOutputs, dataQueryOutputTypes, dataQueryParameters, defaultConfig, effectiveTimeout, frozenConfig, groupDataQueryProblems,
  instructionLimit, parametersOutdated, referenceSummary, selectQueryTemplate,
} from './templateModel';
import { decodeDataQueryTemplate, decodePage, decodeReferences, decodeVersion, workflowPaths } from './workflowApi';
import { actionTypeLabel } from './workflowLabels';
import { enumLabel, tOr, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import type {
  ActionConfig, ActionTemplate, ActionType, DataQueryConfig, DataQueryOutput, DataQueryTemplateOption, FileUploadConfig, Problem, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferenceItem,
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
export function ConfigEditor({ type, config, onChange, readOnly, templateName, description, savedKeys, validationProblems }: {
  type: ActionType; config: ActionConfig; onChange: (config: ActionConfig) => void; readOnly?: boolean;
  /** 表单画布标题与说明（执行人在待办里看到的动作名称与描述）。 */
  templateName?: string; description?: string;
  /** 已保存配置的字段键，键不再随显示名称自动生成。 */
  savedKeys?: ReadonlySet<string>;
  /** 数据查询保存前校验返回的问题（按 itemKey 标到参数行、输出行或模板选择处）。 */
  validationProblems?: readonly Problem[];
}) {
  const t = useT('workflow');
  switch (type) {
    case 'FORM': return <FieldDesigner form={config as FormDefinition} readOnly={readOnly} variablePrefix={`${t('config.actionKey')}.`} savedKeys={savedKeys}
      previewTitle={templateName || t('config.unnamedAction')} previewDescription={description} onChange={onChange} />;
    case 'FILE_UPLOAD': return <FileUploadEditor config={config as FileUploadConfig} onChange={onChange} readOnly={readOnly} />;
    case 'PAGE_TASK': return <PageTaskEditor config={config as PageTaskConfig} onChange={onChange} readOnly={readOnly} />;
    case 'MANUAL_CONFIRM': return <ManualConfirmEditor config={config as ManualConfirmConfig} onChange={onChange} readOnly={readOnly} />;
    case 'DATA_QUERY': return <DataQueryEditor config={config as DataQueryConfig} onChange={onChange} readOnly={readOnly} problems={validationProblems} />;
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

/** 按关键字读取启用中的 data-query 模板（接口 8.5，第一页 50 条）；关键字为空时列出全部。loaded 为已读完的关键字。 */
export function useDataQueryTemplates(keyword: string, enabled = true) {
  const [state, setState] = useState<{ loading: boolean; items: DataQueryTemplateOption[]; failure: unknown; loaded: string | null }>(
    { loading: false, items: [], failure: undefined, loaded: null });
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    const query = new URLSearchParams({ page: '1', size: '50', ...(keyword.trim() ? { keyword: keyword.trim() } : {}) });
    setState(current => ({ ...current, loading: true, failure: undefined }));
    const timer = window.setTimeout(() => {
      session.request(`${workflowPaths.dataQueryTemplates}?${query}`, { signal: controller.signal }, decodeQueryTemplates)
        .then(page => { if (!controller.signal.aborted) setState({ loading: false, items: page.items, failure: undefined, loaded: keyword }); })
        .catch(failure => { if (!controller.signal.aborted) setState({ loading: false, items: [], failure, loaded: keyword }); });
    }, keyword ? 300 : 0);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [keyword, enabled]);
  return state;
}
const decodeQueryTemplates = decodePage(decodeDataQueryTemplate);

/** 已选模板的声明：按编码查询后取编码完全相同的一项；不存在或已停用为 null，读取中或读取失败为 undefined。 */
function useSelectedQueryTemplate(code: string): DataQueryTemplateOption | null | undefined {
  const search = useDataQueryTemplates(code, !!code);
  if (!code) return null;
  const match = search.items.find(item => item.templateCode === code);
  if (match) return match;
  return search.loaded === code && !search.loading && !search.failure ? null : undefined;
}

/** 校验问题的一行文字：列未声明 / 已屏蔽用本地文案，其余用服务端说明。 */
function problemLine(problem: Problem): string {
  const t = translator('workflow');
  if (problem.code === 'DATA_QUERY_OUTPUT_UNDECLARED') return t('dataQuery.outputUndeclared');
  if (problem.code === 'DATA_QUERY_OUTPUT_MASKED') return t('dataQuery.outputMasked');
  return problem.message || problem.code;
}

/**
 * 数据查询（原型 17）：选择启用中的 data-query 模板，参数只读取自模板声明（取值在流程节点映射）；
 * 输出声明 out_<名称> 列写入变量 <动作键>.<名称>；完成标准文案键与说明显示在任务卡「系统检查」块。
 */
function DataQueryEditor({ config, onChange, readOnly, problems = [] }: {
  config: DataQueryConfig; onChange: (config: DataQueryConfig) => void; readOnly?: boolean; problems?: readonly Problem[];
}) {
  const t = useT('workflow');
  const [keyword, setKeyword] = useState('');
  const search = useDataQueryTemplates(keyword, !readOnly);
  const selected = useSelectedQueryTemplate(config.queryCode ?? '');
  const outputs = dataQueryOutputs(config);
  const parameters = dataQueryParameters(config);
  const groups = useMemo(() => groupDataQueryProblems(problems, config), [problems, config]);
  const timeout = selected ? effectiveTimeout(selected.timeoutSeconds) : null;
  const set = (patch: Partial<DataQueryConfig>) => onChange({ ...config, ...patch });
  const setOutput = (index: number, patch: Partial<DataQueryOutput>) => set({ outputs: outputs.map((item, current) => current === index ? { ...item, ...patch } : item) });
  const options = [...search.items, ...(selected && !search.items.some(item => item.templateCode === selected.templateCode) ? [selected] : [])]
    .map(item => ({ value: item.templateCode, label: <span className="wf-option-line"><span><code>{item.templateCode}</code> {item.templateName}</span>
      <span className="wf-inline">{item.versionNo !== null && <Tag color="green" className="wf-dq-version">{t('dataQuery.activeVersion', { version: item.versionNo })}</Tag>}
        <span className="muted">{item.dataSourceName ?? ''}</span></span></span> }));
  const declared = selected?.parameters ?? parameters.map(name => ({ name, type: '', required: null as boolean | null }));
  const parameterRows = new Set(declared.map(item => item.name));
  const descriptionKey = config.descriptionKey?.trim() ?? '';
  const keyText = descriptionKey.startsWith('workflow.') && t.has(descriptionKey.slice('workflow.'.length)) ? t(descriptionKey.slice('workflow.'.length)) : null;
  return <div className="wf-dq-editor">
    {groups.general.length > 0 && <Alert type="error" showIcon title={t('dataQuery.validationFailed', { count: problems.length })}
      description={<ul className="wf-plain-list">{groups.general.map((problem, index) => <li key={index}>{problemLine(problem)}</li>)}</ul>} />}
    <section className="wf-card">
      <div className="wf-card-title">{t('dataQuery.template')}</div>
      <div className="wf-field-hint">{t('dataQuery.templateHint')}</div>
      <Select showSearch={{ filterOption: false, onSearch: setKeyword }} value={config.queryCode || undefined} disabled={readOnly} style={{ width: '100%' }}
        status={groups.template.length ? 'error' : undefined}
        placeholder={t('dataQuery.templatePlaceholder')} loading={search.loading} options={options}
        notFoundContent={search.loading ? <Spin size="small" /> : <span className="muted">{t('dataQuery.templateEmpty')}</span>}
        onChange={(code: string) => { const template = [...search.items, ...(selected ? [selected] : [])].find(item => item.templateCode === code);
          if (template) onChange(selectQueryTemplate(config, template)); }} />
      {selected && <div className="wf-dq-meta muted">{t('dataQuery.dataSource', { name: selected.dataSourceName ?? '—' })}
        {timeout && <span>{timeout.capped ? t('dataQuery.timeoutCapped', { seconds: timeout.seconds, template: selected.timeoutSeconds ?? 0 })
          : t('dataQuery.timeout', { seconds: timeout.seconds })}</span>}
        {selected.description ? <span>{selected.description}</span> : null}</div>}
      {groups.template.map((problem, index) => <div key={index} className="wf-danger-text">{problem.message}</div>)}
      {config.queryCode && selected === null && <div className="wf-danger-text">{t('dataQuery.templateUnavailable', { code: config.queryCode })}</div>}
    </section>
    <section className="wf-card">
      <div className="wf-card-title">{t('dataQuery.parameters')}</div>
      <div className="wf-field-hint">{t('dataQuery.parametersHint')}</div>
      {!config.queryCode ? <div className="muted">{t('dataQuery.pickTemplateFirst')}</div> : !declared.length ? <div className="muted">{t('dataQuery.noParameters')}</div>
        : <div className="wf-dq-scroll"><table className="wf-table wf-dq-table"><thead><tr><th>{t('dataQuery.parameter')}</th><th>{t('dataQuery.parameterRequired')}</th><th>{t('dataQuery.parameterValue')}</th></tr></thead>
          <tbody>{declared.map(item => { const rowProblems = groups.parameters.get(item.name) ?? [];
            return <tr key={item.name} className={rowProblems.length ? 'is-error' : undefined}><td><strong>{item.name}</strong>{item.type && <span className="muted"> · {item.type}</span>}</td>
              <td>{item.required === null ? '—' : item.required ? <Tag color="red">{t('dataQuery.required')}</Tag> : <span className="muted">{t('dataQuery.optional')}</span>}</td>
              <td className={rowProblems.length ? 'wf-danger-text' : 'muted'}>{rowProblems.length ? rowProblems.map(problemLine).join(t('list.semicolon')) : t('dataQuery.mappedOnNode')}</td></tr>; })}</tbody></table></div>}
      {[...groups.parameters].filter(([key]) => !parameterRows.has(key)).flatMap(([, list]) => list)
        .map((problem, index) => <div key={index} className="wf-danger-text">{problemLine(problem)}</div>)}
      {selected && parametersOutdated(config, selected) && <Alert type="warning" showIcon title={t('dataQuery.parametersOutdated')}
        action={!readOnly && <Button size="small" onClick={() => onChange(selectQueryTemplate(config, selected))}>{t('dataQuery.syncParameters')}</Button>} />}
    </section>
    <section className="wf-card">
      <div className="wf-card-title">{t('dataQuery.outputs')}</div>
      <div className="wf-field-hint">{t('dataQuery.outputsHint', { name: t('dataQuery.outputNameArg'), actionKey: t('config.actionKey') })}</div>
      {outputs.length > 0 && <div className="wf-dq-scroll"><table className="wf-table wf-dq-table wf-dq-outputs-table"><colgroup><col className="is-name" /><col className="is-column" /><col className="is-type" />
        <col /><col className="is-variable" /><col className="is-remove" /></colgroup><thead><tr><th>{t('dataQuery.outputName')}</th><th>{t('dataQuery.outputColumn')}</th>
        <th>{t('dataQuery.outputType')}</th><th>{t('dataQuery.outputEnumValues')}</th><th>{t('dataQuery.outputVariable')}</th><th /></tr></thead>
        <tbody>{outputs.map((output, index) => {
          const rowProblems = groups.outputs.get(output.name) ?? [];
          const problem = rowProblems.length > 0;
          return <tr key={index} className={problem ? 'is-error' : undefined}>
            <td><Input size="small" value={output.name} disabled={readOnly} maxLength={64} status={problem ? 'error' : undefined}
              onChange={event => setOutput(index, { name: event.target.value.trim() })} /></td>
            <td><code className={problem ? 'wf-danger-text' : 'muted'}>out_{output.name || '…'}</code>
              {rowProblems.map((item, current) => <div key={current} className="wf-danger-text wf-small">{problemLine(item)}</div>)}</td>
            <td><Select size="small" value={output.type} disabled={readOnly} style={{ width: 110 }}
              options={dataQueryOutputTypes.map(value => ({ value, label: enumLabel(t, 'dataQuery.outputTypeName', value) }))}
              onChange={type => setOutput(index, type === 'ENUM' ? { type } : { type, enumValues: undefined })} /></td>
            <td>{output.type === 'ENUM' ? <Select size="small" mode="tags" value={output.enumValues ?? []} disabled={readOnly} style={{ width: '100%', minWidth: 160 }}
              placeholder={t('dataQuery.enumValuesPlaceholder')} tokenSeparators={[',', ' ']} open={false}
              onChange={(values: string[]) => setOutput(index, { enumValues: values.map(value => value.trim()).filter(Boolean) })} />
              : <span className="muted">—</span>}</td>
            <td><code className="wf-info-text">{`${t('config.actionKey')}.${output.name || '…'}`}</code></td>
            <td className="wf-right">{!readOnly && <Button type="text" size="small" aria-label={t('dataQuery.removeOutput')} icon={<CloseOutlined />}
              onClick={() => set({ outputs: outputs.filter((_, current) => current !== index) })} />}</td>
          </tr>;
        })}</tbody></table></div>}
      {outputs.some(output => output.type === 'TEXT') && <div className="wf-field-hint">{t('dataQuery.textContainsHint')}</div>}
      {!readOnly && outputs.length < dataQueryLimits.outputs && <Button type="link" icon={<PlusOutlined />}
        onClick={() => set({ outputs: [...outputs, { name: '', type: 'TEXT' }] })}>{t('dataQuery.addOutput')}</Button>}
    </section>
    <section className="wf-card">
      <div className="wf-card-title">{t('dataQuery.criteria')}</div>
      <div className="wf-field-hint">{t('dataQuery.criteriaHint')}</div>
      <div className="wf-three-columns">
        <Field label={t('dataQuery.descriptionKey')}><Input value={config.descriptionKey ?? ''} disabled={readOnly} maxLength={128}
          placeholder={config.queryCode ? `workflow.dataQuery.criteria.${config.queryCode}` : 'workflow.dataQuery.criteria.…'}
          onChange={event => set({ descriptionKey: event.target.value.trim() })} /></Field>
        <Field label={t('dataQuery.currentLanguage')}><Input value={keyText ?? (descriptionKey ? t('dataQuery.keyUntranslated') : '')} disabled /></Field>
        <Field label={t('dataQuery.instruction')}><Input value={config.instruction ?? ''} disabled={readOnly} maxLength={instructionLimit}
          onChange={event => set({ instruction: event.target.value })} /></Field>
      </div>
    </section>
  </div>;
}

/** 数据查询编辑页右栏（原型 17）：查询返回约定与执行方式。 */
export function DataQueryContract() {
  const t = useT('workflow');
  return <aside className="wf-dq-contract">
    <div className="wf-card-title">{t('dataQuery.contract.title')}</div>
    <p className="muted">{t('dataQuery.contract.firstRow')}</p>
    <dl className="wf-dq-columns">
      <dt><code>satisfied</code></dt><dd>{t('dataQuery.contract.satisfied')}</dd>
      <dt><code>{`out_${t('dataQuery.outputNameArg')}`}</code></dt><dd>{t('dataQuery.contract.out')}</dd>
      <dt><code>missing</code></dt><dd>{t('dataQuery.contract.missing')}</dd>
    </dl>
    <div className="wf-card-title">{t('dataQuery.contract.execution')}</div>
    <ul className="wf-dq-steps">
      <li><ThunderboltOutlined /> {t('dataQuery.contract.onActivate')}</li>
      <li><FieldTimeOutlined /> {t('dataQuery.contract.recheck')}</li>
      <li><SyncOutlined /> {t('dataQuery.contract.checkNow')}</li>
      <li><UserOutlined /> {t('dataQuery.contract.follower')}</li>
    </ul>
    <div className="wf-hint-box">{t('dataQuery.contract.oneWay')}</div>
  </aside>;
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
      {template.actionType === 'DATA_QUERY' && <p className="muted">{t('dataQuery.check.autoComplete')}</p>}
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

/** 新建动作 · 选择类型（原型 07）：五类可选（不在 creatableTypes 中的类型标「即将支持」不可选）；「下一步」把类型交给调用方（进入编辑页或抽屉）。 */
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
