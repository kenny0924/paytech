import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Empty, Form, Input, Modal, Spin, Steps, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { blockingFiles, fileAddressesOf, scanFailedAddresses } from './fileModel';
import { outputOf, valueProblems } from './formModel';
import { previewPath } from './runtimeModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { decodeDefinitionSummary, decodeLaunch, decodeMutation, decodePage, workflowPaths } from './workflowApi';
import type { DefinitionSummary, LaunchDefinition, StartInstanceRequest } from './workflowTypes';
import { DynamicFields, Field, LoadFailed, PersonSelect, personName, useLoad, workflowRoutes } from './WorkflowParts';
import { useT } from '../i18n/react';

const decodeDefinitions = decodePage(decodeDefinitionSummary);

/** 发起流程三步弹窗：选择流程 → 填写信息（右侧随填写变化的流程预览）→ 确认人员（原型 04）。 */
export function LaunchModal({ initialDefinitionId, onClose, onAuthorizationChanged, onStarted }: {
  initialDefinitionId?: string | null; onClose: () => void; onAuthorizationChanged: () => void;
  /** 发起成功后的去向；缺省打开流程实例详情（「我发起的」页传入，留在本页并选中新实例）。 */
  onStarted?: (instanceId: string) => void;
}) {
  const t = useT('workflow');
  const navigate = useNavigate(); const { message } = App.useApp();
  const [step, setStep] = useState(initialDefinitionId ? 1 : 0);
  const [definitionId, setDefinitionId] = useState<string | null>(initialDefinitionId ?? null);
  const [definitions, setDefinitions] = useState<DefinitionSummary[] | null>(null); const [listFailure, setListFailure] = useState<unknown>();
  const [keyword, setKeyword] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(workflowPaths.launchable, controller.signal, undefined, {}, decodeDefinitions)
      .then(items => { if (!controller.signal.aborted) setDefinitions(items); })
      .catch(error => { if (!controller.signal.aborted) setListFailure(error); });
    return () => controller.abort();
  }, []);
  const launch = useLoad<LaunchDefinition>(definitionId ? workflowPaths.launchableOne(definitionId) : null, decodeLaunch);
  const [form] = Form.useForm(); const [basic] = Form.useForm<{ title: string; businessKey?: string }>();
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [approvers, setApprovers] = useState<Record<string, string[]>>({});
  const [executors, setExecutors] = useState<Record<string, string>>({});
  const [basicValues, setBasicValues] = useState<{ title?: string; businessKey?: string }>({});
  const command = useWorkflowCommand(onAuthorizationChanged);
  const data = launch.data;
  /** 服务端 422 报告未通过安全检查的文件地址（startValues.{key}[i]）；删除前不能发起。 */
  const [reportedFailed, setReportedFailed] = useState<ReadonlySet<string>>(new Set());
  const submittedValues = useRef<Record<string, unknown>>({});
  useEffect(() => {
    const failed = scanFailedAddresses(command.fieldErrors, 'startValues', submittedValues.current);
    setReportedFailed(new Set(failed));
    // 文件在「填写信息」一步，回到该步删除后重新上传。
    if (failed.length) setStep(1);
  }, [command.fieldErrors]);
  useEffect(() => {
    if (!data) return;
    setApprovers(Object.fromEntries(data.graph.nodes.filter(node => node.type === 'TASK').map(node => [node.key, node.approval?.approverIds ?? []])));
    setExecutors(Object.fromEntries(data.graph.nodes.flatMap(node => node.actions ?? []).map(action => [action.key, action.executorId ?? ''])));
    setValues({});
  }, [data]);
  const problems = data ? valueProblems(data.startForm, values) : {};
  const preview = useMemo(() => data ? previewPath(data.graph, values) : [], [data, values]);
  const tasks = data?.graph.nodes.filter(node => node.type === 'TASK') ?? [];
  const chosenFiles = data ? data.startForm.fields.filter(field => field.type === 'FILE').flatMap(field => fileAddressesOf(values[field.key])) : [];
  const blocking = blockingFiles(chosenFiles, undefined, reportedFailed);
  const missingPeople = tasks.flatMap(node => (node.actions ?? []).filter(action => !executors[action.key]).map(action => t('launch.missingExecutor', { node: node.name, action: action.key })));
  async function submit() {
    if (!data || !definitionId) return;
    const original = new Map(tasks.map(node => [node.key, node]));
    const body: StartInstanceRequest = { definitionId, versionNo: data.versionNo, title: basicValues.title!.trim(),
      ...(basicValues.businessKey?.trim() ? { businessKey: basicValues.businessKey.trim() } : {}), startValues: outputOf(data.startForm, values),
      approvers: Object.entries(approvers).filter(([key, ids]) => JSON.stringify(ids) !== JSON.stringify(original.get(key)?.approval?.approverIds ?? []))
        .map(([nodeKey, approverIds]) => ({ nodeKey, approverIds })),
      executors: tasks.flatMap(node => node.actions ?? []).filter(action => executors[action.key] && executors[action.key] !== action.executorId)
        .map(action => ({ actionKey: action.key, executorId: executors[action.key] })) };
    submittedValues.current = body.startValues;
    const result = await command.run(workflowPaths.instances, 'POST', body, decodeMutation, t('launch.failed'));
    if (!result) return;
    void message.success(t('launch.started')); onClose();
    if (onStarted) onStarted(result.objectId); else void navigate(`${workflowRoutes.instances}/${result.objectId}`);
  }
  const visible = (definitions ?? []).filter(item => !keyword.trim() || `${item.definitionCode} ${item.definitionName}`.toLowerCase().includes(keyword.trim().toLowerCase()));
  const canNext = step === 0 ? !!definitionId : step === 1 ? !!data && !!basicValues.title?.trim() && !Object.keys(problems).length && !blocking.length
    : !missingPeople.length && !blocking.length;
  return <Modal open width={1180} title={<span>{t('launch.title')}{data ? ` · ${data.definition.definitionName}` : ''}</span>} onCancel={onClose}
    footer={<div className="wf-modal-foot">
      <span className="wf-warn-text">{blocking.length ? t('launch.blockingFiles', { count: blocking.length }) : step === 1 && Object.keys(problems).length ? t('launch.fieldProblems', { count: Object.keys(problems).length })
        : step === 2 && missingPeople.length ? t('launch.missingPeople', { list: missingPeople.join(t('list.comma')) }) : command.formError ?? ''}</span>
      <span>{step > 0 && <Button onClick={() => setStep(step - 1)}>{t('launch.previous')}</Button>}
        {step < 2 ? <Button type="primary" disabled={!canNext} onClick={() => setStep(step + 1)}>{step === 0 ? t('launch.nextFill') : t('launch.nextPeople')}</Button>
          : <Button type="primary" loading={command.saving} disabled={!canNext} onClick={() => void submit()}>{t('launch.submit')}</Button>}</span></div>}>
    <Steps size="small" current={step} items={[{ title: t('launch.step.choose') }, { title: t('launch.step.fill') }, { title: t('launch.step.people') }]} className="wf-steps" />
    {command.conflict && <Alert type="warning" showIcon title={t('launch.conflict')} />}
    {step === 0 && <div>
      <Input.Search allowClear placeholder={t('launch.search')} value={keyword} onChange={event => setKeyword(event.target.value)} style={{ maxWidth: 360, marginBottom: 12 }} />
      {listFailure ? <LoadFailed failure={listFailure} title={t('launch.listFailed')} /> : !definitions ? <Spin /> : !visible.length ? <Empty description={t('launch.empty')} />
        : <div className="wf-pick-grid">{visible.map(item => <button type="button" key={item.definitionId}
          className={`wf-pick${definitionId === item.definitionId ? ' is-selected' : ''}`} onClick={() => setDefinitionId(item.definitionId)}>
          <strong>{item.definitionName}</strong> <Tag color="green">v{item.currentVersionNo}</Tag>
          <div className="muted">{item.definitionCode}{item.description ? ` · ${item.description}` : ''}</div></button>)}</div>}
    </div>}
    {step > 0 && launch.loading && <Spin />}
    {step > 0 && launch.failure ? <LoadFailed failure={launch.failure} retry={launch.refresh} title={t('launch.loadFailed')} /> : null}
    {step === 1 && data && <div className="wf-launch">
      <div className="wf-launch-form">
        <h3>{t('templateEditor.step.basic')}</h3>
        <Form form={basic} layout="vertical" initialValues={basicValues} onValuesChange={(_, all) => setBasicValues(all)} className="wf-two-columns">
          <Form.Item name="title" label={t('launch.instanceTitle')} required rules={[{ required: true, whitespace: true, message: t('launch.instanceTitleRequired') }]}><Input maxLength={128} /></Form.Item>
          <Form.Item name="businessKey" label={t('launch.businessKey')} extra={t('launch.businessKeyHint')}><Input maxLength={128} /></Form.Item>
        </Form>
        <h3>{t('graph.startForm')}</h3>
        {data.startForm.fields.length ? <Form form={form} layout="vertical" initialValues={values} onValuesChange={(_, all) => setValues(all)}>
          <DynamicFields form={data.startForm} failedFiles={reportedFailed} problems={Object.fromEntries(Object.entries(problems).filter(([key]) => values[key] !== undefined || problems[key] !== t('form.value.required')))} />
        </Form> : <p className="muted">{t('launch.noStartForm')}</p>}
        {data.startForm.fields.some(field => field.type === 'FILE') && <p className="muted">{t('launch.fileScanHint')}</p>}
      </div>
      <aside className="wf-launch-preview"><h3>{t('launch.preview.title')}</h3><p className="muted">{t('launch.preview.hint')}</p>
        {preview.map((item, index) => item.kind === 'task'
          ? <div key={index} className="wf-preview-step"><strong>{item.node.name}</strong>
            <div className="muted">{item.reason ?? (item.node.approval?.approverIds.length ? t('launch.preview.approval', { mode: item.node.approval.mode }) : t('launch.preview.actions', { count: item.node.actions?.length ?? 0 }))}</div></div>
          : item.kind === 'parallel'
            ? <div key={index} className="wf-preview-step is-parallel"><strong>{t('launch.preview.parallel', { count: item.branches.length })}</strong>
              {item.branches.map((branch, branchIndex) => <div key={branchIndex}>· {branch.map(node => node.name).join(' → ') || t('launch.preview.emptyBranch')}</div>)}</div>
            : <div key={index} className="wf-preview-step is-undecided"><strong>{item.node.name}</strong><div className="muted">{t('launch.preview.undecided')}</div></div>)}
      </aside>
    </div>}
    {step === 2 && data && <div className="wf-people">
      <p className="muted">{t('launch.peopleHint')}</p>
      {tasks.map(node => <div key={node.key} className="wf-people-row"><strong>{node.name}</strong>
        <Field label={node.approval?.mode === 'ALL' ? t('launch.approversAll') : t('launch.approversAny')}>
          <PersonSelect multiple value={approvers[node.key] ?? []} people={data.people} placeholder={t('launch.noApprovers')}
            onChange={value => setApprovers(current => ({ ...current, [node.key]: value as string[] }))} /></Field>
        {(node.actions ?? []).map(action => <Field key={action.key} label={action.required ? t('launch.executorRequired', { action: action.key }) : t('launch.executorOptional', { action: action.key })}>
          <PersonSelect value={executors[action.key] || undefined} people={data.people}
            onChange={value => setExecutors(current => ({ ...current, [action.key]: (value as string | undefined) ?? '' }))} />
          {action.executorId && executors[action.key] !== action.executorId && <span className="muted">{t('launch.defaultPerson', { name: personName(action.executorId, data.people) })}</span>}
        </Field>)}
      </div>)}
    </div>}
  </Modal>;
}
