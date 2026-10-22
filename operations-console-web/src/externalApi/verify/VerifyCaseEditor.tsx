import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Empty, Form, Input, Radio, Select, Table } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../../api/session';
import type { ConsoleResource } from '../../api/types';
import { usePaging } from '../../hooks/usePaging';
import { useErrorNotification } from '../../feedback/useErrorNotification';
import { ReasonDialog, ValidationPanel, useReasonFlow } from '../EditorParts';
import { decodeValidation } from '../editorModel';
import type { ValidationIssue } from '../editorModel';
import { ConflictBanner, Loading, LoadFailure, hasButton, useCopyDraft, useDetail, useEditorSave } from '../externalApiHooks';
import {
  codePattern, decodeImplementationList, decodeMutation, decodePage, listSizes, manageProtocol, replaceQuery,
} from '../externalApiModel';
import type { ImplementationSummary } from '../externalApiModel';
import { Field, Mono, Note, PageHeader, Panel, ReadOnlyValue, formatTime } from '../ui';
import type { Crumb } from '../ui';
import {
  caseStatusLabels, decodeAccounts, decodeCase, decodeRunSummary, emptyStep, executeStatuses, hasTemplateVariable, issueStep, matchLabels, matches,
  runStatusLabels, templateVariables, toStepDrafts, toSteps, verifyPath,
} from './verifyModel';
import { workspaceCrumbs, workspaceRoutes } from '../../channel/channelWorkspace';
import type { Match, OutputRow, StepDraft, VerifyAccountSummary, VerifyCaseDetail, VerifyRunSummary } from './verifyModel';
import { JsonBlock, StartRunDialog, VerifyTag } from './VerifyParts';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channelId: string; integrationCode: string; caseCode: string | null }
interface Meta { caseCode: string; caseName: string; description?: string; accountCode: string }

const crumbsFor = (channelId: string, code: string): Crumb[] => workspaceCrumbs(channelId, code, 'verify');
const runPage = (value: unknown) => decodePage(value, decodeRunSummary, item => item.runCode);

/** 验证用例：详情（/:caseCode）、编辑（?mode=edit）与新建（/new）。步骤按顺序执行，后一步可引用前一步的输入与输出。 */
export function VerifyCaseEditor({ resources, onAuthorizationChanged, channelId, integrationCode, caseCode }: Props) {
  const location = useLocation();
  const canManage = hasButton(resources, manageProtocol);
  const editing = caseCode === null || canManage && new URLSearchParams(location.search).get('mode') === 'edit';
  const decode = useMemo(() => (value: unknown) => decodeCase(value, caseCode ?? ''), [caseCode]);
  const detail = useDetail<VerifyCaseDetail>(caseCode ? `${verifyPath(integrationCode)}/verify-cases/${caseCode}` : null, decode);
  if (caseCode === null && !canManage) return <Alert type="warning" showIcon title="当前没有沙箱验证维护权限" />;
  if (caseCode && detail.loading && !detail.data) return <Loading />;
  if (caseCode && (detail.failure && !detail.data || !detail.data)) {
    return <LoadFailure title="验证用例加载失败" failure={detail.failure} retry={detail.refresh} />;
  }
  if (editing) {
    return <CaseForm key={detail.data?.version ?? 'new'} channelId={channelId} integrationCode={integrationCode} detail={detail.data}
      onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh} />;
  }
  return <CaseView channelId={channelId} integrationCode={integrationCode} verifyCase={detail.data!} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />;
}

function CaseView({ channelId, integrationCode, verifyCase, canManage, onAuthorizationChanged }: {
  channelId: string; integrationCode: string; verifyCase: VerifyCaseDetail; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const navigate = useNavigate(); const [running, setRunning] = useState(false);
  const runs = usePaging<VerifyRunSummary>(`${verifyPath(integrationCode)}/verify-runs`, runPage, { caseCode: verifyCase.caseCode },
    { initialPaging: { page: 1, size: 10 }, notifyOnError: false });
  const steps = toStepDrafts(verifyCase.steps);
  return <>
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: verifyCase.caseCode }]} title={verifyCase.caseName} code={verifyCase.caseCode}
      actions={canManage && <>
        <Button onClick={() => void navigate(replaceQuery('', { mode: 'edit' }))}>编辑</Button>
        <Button type="primary" disabled={verifyCase.verifyStatus === 'RUNNING'} onClick={() => setRunning(true)}>运行</Button></>} />
    <div className="xapi-facts">
      <Field label="验证状态"><VerifyTag {...caseStatusLabels[verifyCase.verifyStatus]} /></Field>
      <Field label="验证账号">{verifyCase.accountCode
        ? <Link to={workspaceRoutes(channelId).verifyAccount(verifyCase.accountCode)}><Mono>{verifyCase.accountCode}</Mono></Link> : '已删除'}</Field>
      <Field label="说明">{verifyCase.description || '—'}</Field>
      <Field label="最近修改">{formatTime(verifyCase.updateTime)} · 账号 {verifyCase.updatedBy} · 版本 {verifyCase.version}</Field>
    </div>
    {verifyCase.verifyStatus === 'STALE' && <Alert type="warning" showIcon
      title="最近一次通过之后，用例、验证账号或引用的标准 API / 原子 API / 脚本 / 流程已修改，需要重新运行验证。" />}
    {steps.map((step, index) => <Panel key={index} title={`步骤 ${index + 1} · ${step.id}`}
      extra={<Mono>{step.standardApiCode}</Mono>}>
      <div className="xapi-verify-step-view">
        <Field label="输入模板"><JsonBlock value={step.input} /></Field>
        <Field label="期望">{step.expect === 'PROBLEM'
          ? <span>执行前返回问题 <Mono>{step.problemStatus}{step.problemCode ? ` ${step.problemCode}` : ''}</Mono></span>
          : <div>
            <div>执行状态 <Mono>{step.executeStatus}</Mono>{step.returnId && <> · 出口 <Mono>{step.returnId}</Mono></>}</div>
            {step.outputs.length > 0 && <ul className="xapi-verify-expect-list">{step.outputs.map((row, outputIndex) =>
              <li key={outputIndex}><Mono>outputs.{row.path}</Mono> {matchLabels[row.match]} {row.value && <Mono>{row.value}</Mono>}</li>)}</ul>}
          </div>}</Field>
      </div>
    </Panel>)}
    <div className="xapi-toolbar"><h2 className="xapi-section-title">运行记录</h2>
      <button type="button" className="xapi-link" onClick={runs.refresh}>刷新</button></div>
    {runs.error ? <LoadFailure title="运行记录加载失败" failure={runs.failure} retry={runs.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyRunSummary> rowKey="runCode" tableLayout="fixed" dataSource={runs.data.items} loading={runs.loading}
        pagination={{ ...runs.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未运行" /> }} columns={[
          { title: '运行编号', width: 190, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyRun(row.runCode)}><Mono>{row.runCode}</Mono></Link> },
          { title: '结论', width: 100, render: (_, row) => <VerifyTag {...runStatusLabels[row.runStatus]} /> },
          { title: '失败摘要', ellipsis: true, render: (_, row) => row.failureSummary ?? '—' },
          { title: '开始时间', width: 150, render: (_, row) => formatTime(row.startTime) },
          { title: '发起账号', width: 110, dataIndex: 'startedBy' },
        ]} /></div>}
    {running && <StartRunDialog channelId={channelId} integrationCode={integrationCode} caseCode={verifyCase.caseCode} onClose={() => setRunning(false)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function CaseForm({ channelId, integrationCode, detail, onAuthorizationChanged, onReload }: {
  channelId: string; integrationCode: string; detail?: VerifyCaseDetail; onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const [form] = Form.useForm<Meta>(); const navigate = useNavigate(); const notify = useErrorNotification();
  const editor = useEditorSave(onAuthorizationChanged); const copy = useCopyDraft();
  const creating = !detail; const leaving = useRef(false);
  const api = verifyPath(integrationCode);
  const accounts = useDetail<VerifyAccountSummary[]>(`${api}/verify-accounts`, decodeAccounts);
  const implementations = useDetail<ImplementationSummary[]>(`${api}/implementations`, decodeImplementationList);
  const [steps, setSteps] = useState<StepDraft[]>(() => detail ? toStepDrafts(detail.steps) : [emptyStep(0)]);
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [checked, setChecked] = useState<string | null>(null);
  const [focus, setFocus] = useState<number | null>(null);
  const update = (index: number, change: Partial<StepDraft>) => {
    setSteps(current => current.map((step, at) => at === index ? { ...step, ...change } : step));
    editor.setDirty(true);
  };
  const draft = toSteps(steps);
  const stale = checked !== null && checked !== JSON.stringify(draft.steps);
  async function validate() {
    if (draft.problems.length) { setFocus(draft.problems[0].step); return; }
    try {
      const result = await session.request(`${api}/verify-cases/validate`, { method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accountCode: form.getFieldValue('accountCode'), steps: draft.steps }) }, decodeValidation);
      setIssues(result.issues); setChecked(JSON.stringify(draft.steps));
    } catch (error) { notify(error, '校验未完成'); }
  }
  const reasonFlow = useReasonFlow(async reason => {
    const values = form.getFieldsValue(true) as Meta;
    const code = creating ? values.caseCode : detail.caseCode;
    const body = { caseName: values.caseName.trim(), description: values.description?.trim() || null,
      accountCode: values.accountCode, steps: draft.steps, reason };
    const result = await editor.save(creating ? `${api}/verify-cases` : `${api}/verify-cases/${code}`, creating ? 'POST' : 'PUT',
      creating ? { caseCode: code, ...body } : { ...body, expectedVersion: detail.version },
      value => decodeMutation(value, 'INTEGRATION_VERIFY_CASE', `${integrationCode}/${code}`));
    if (!result) { if (editor.fieldErrors.length) void validate(); return false; }
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(workspaceRoutes(channelId).verifyCase(code), { replace: true });
    return true;
  }, async () => {
    const valid = await form.validateFields().then(() => true, () => false);
    if (draft.problems.length) { setFocus(draft.problems[0].step); return false; }
    return valid;
  });
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(() => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${api}/verify-cases/${detail?.caseCode}`, {}, value => decodeCase(value, detail!.caseCode)),
    [api, detail]);
  const stepIssues = (index: number) => (issues ?? []).filter(issue => issueStep(issue.location) === index);
  const move = (index: number, offset: number) => {
    setSteps(current => { const next = [...current]; const [item] = next.splice(index, 1); next.splice(index + offset, 0, item); return next; });
    editor.setDirty(true);
  };
  return <>
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: creating ? '新建验证用例' : `编辑 ${detail.caseCode}` }]}
      title={creating ? '新建验证用例' : `编辑验证用例 · ${detail.caseCode}`}
      actions={<><Button onClick={() => void validate()}>校验</Button>
        <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button></>} />
    {editor.conflict && detail && <ConflictBanner code={detail.caseCode} baseVersion={detail.version} latest={latest}
      onReload={onReload} onCopy={() => copy({ ...form.getFieldsValue(true), steps: draft.steps })} />}
    <ValidationPanel issues={issues} stale={stale} onLocate={issue => setFocus(issueStep(issue.location))} />
    <Form form={form} layout="vertical" requiredMark={false} disabled={editor.saving} onValuesChange={() => editor.setDirty(true)}
      initialValues={detail ? { caseCode: detail.caseCode, caseName: detail.caseName, description: detail.description ?? '',
        accountCode: detail.accountCode ?? undefined } : { accountCode: accounts.data?.[0]?.accountCode }}>
      <Panel title="基本信息"><div className="xapi-grid xapi-grid-3">
        <Form.Item name="caseCode" label="编码（创建后只读）" rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线' }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder="例如 PAY_THEN_QUERY" /> : <ReadOnlyValue mono value={detail.caseCode} />}</Form.Item>
        <Form.Item name="caseName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="accountCode" label="验证账号" rules={[{ required: true, message: '请选择验证账号' }]}>
          <Select loading={accounts.loading} options={(accounts.data ?? []).map(item => ({ value: item.accountCode,
            label: `${item.accountCode} · ${item.accountName}` }))} /></Form.Item>
      </div>
      <Form.Item name="description" label="说明" style={{ marginBottom: 0 }}><Input maxLength={512} /></Form.Item></Panel>
    </Form>
    {steps.map((step, index) => <StepPanel key={index} index={index} step={step} total={steps.length} focused={focus === index}
      problems={draft.problems.filter(item => item.step === index).map(item => item.message)} issues={stepIssues(index)}
      standardApis={implementations.data ?? []} onChange={change => update(index, change)} onMove={offset => move(index, offset)}
      onRemove={() => { setSteps(current => current.filter((_, at) => at !== index)); editor.setDirty(true); }} />)}
    <div><button type="button" className="xapi-link xapi-link-add" disabled={steps.length >= 10}
      onClick={() => { setSteps(current => [...current, emptyStep(current.length)]); editor.setDirty(true); }}>添加步骤</button>
      <span className="xapi-desc"> 最多 10 步，按顺序执行；某步未通过时后续步骤跳过。</span></div>
    <Panel title="输入模板变量">
      <ul className="xapi-verify-variables">{templateVariables.map(item => <li key={item.name}><Mono>{item.name}</Mono> {item.description}</li>)}</ul>
      <Note items={['变量只能写在字符串值中；整串只有一个变量时保留原类型（如对象、数字），嵌在文字中时按字符串拼接。',
        '模板变量只在输入中替换；期望值按字面比较，跨步骤一致性用“存在”或在后续步骤输入中引用。',
        '期望“执行前返回问题”（如 422）的负向用例本就有意提交不合法输入，保存时不校验输入结构。']} />
    </Panel>
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

function StepPanel({ index, step, total, focused, problems, issues, standardApis, onChange, onMove, onRemove }: {
  index: number; step: StepDraft; total: number; focused: boolean; problems: string[]; issues: ValidationIssue[];
  standardApis: ImplementationSummary[]; onChange: (change: Partial<StepDraft>) => void; onMove: (offset: number) => void; onRemove: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (focused) ref.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, [focused]);
  const setOutput = (at: number, change: Partial<OutputRow>) =>
    onChange({ outputs: step.outputs.map((row, position) => position === at ? { ...row, ...change } : row) });
  return <div ref={ref} className={`xapi-verify-step${focused ? ' xapi-verify-step-focused' : ''}${problems.length || issues.some(issue => issue.severity === 'ERROR') ? ' xapi-row-error' : ''}`}>
    <Panel title={`步骤 ${index + 1}`} extra={<span className="xapi-ops">
      <button type="button" className="xapi-link" disabled={index === 0} onClick={() => onMove(-1)}>上移</button>
      <button type="button" className="xapi-link" disabled={index === total - 1} onClick={() => onMove(1)}>下移</button>
      <button type="button" className="xapi-link" disabled={total === 1} onClick={onRemove}>删除</button></span>}>
      <div className="xapi-grid xapi-grid-3">
        <Field label="步骤 ID" hint="小写字母开头；后续步骤以 ${steps.<ID>...} 引用">
          <Input value={step.id} maxLength={64} className="xapi-mono-input" onChange={event => onChange({ id: event.target.value })} /></Field>
        <Field label="标准 API" hint="本渠道已实现的标准 API">
          <Select value={step.standardApiCode || undefined} placeholder="选择标准 API" onChange={value => onChange({ standardApiCode: value })}
            options={standardApis.map(item => ({ value: item.standardApiCode, label: `${item.standardApiCode} · ${item.standardApiName}` }))} /></Field>
        <Field label="期望类型">
          <Radio.Group value={step.expect} onChange={event => onChange({ expect: event.target.value })} options={[
            { value: 'EXECUTE', label: '执行结果' }, { value: 'PROBLEM', label: '执行前返回问题' }]} /></Field>
      </div>
      <Field label="输入（JSON，可用模板变量）" className="xapi-gap-top">
        <Input.TextArea value={step.input} autoSize={{ minRows: 4, maxRows: 18 }} className="xapi-mono-input" spellCheck={false}
          onChange={event => onChange({ input: event.target.value })} /></Field>
      {step.expect === 'PROBLEM' ? <div className="xapi-grid xapi-grid-3 xapi-gap-top">
        <Field label="HTTP 状态"><Input value={step.problemStatus} maxLength={3} className="xapi-mono-input"
          onChange={event => onChange({ problemStatus: event.target.value })} /></Field>
        <Field label="问题代码（可选）"><Input value={step.problemCode} maxLength={64} className="xapi-mono-input" placeholder="例如 VALIDATION_ERROR"
          onChange={event => onChange({ problemCode: event.target.value })} /></Field>
      </div> : <>
        <div className="xapi-grid xapi-grid-3 xapi-gap-top">
          <Field label="执行状态"><Select value={step.executeStatus} onChange={value => onChange({ executeStatus: value })}
            options={executeStatuses.map(item => ({ value: item, label: item }))} /></Field>
          <Field label="出口 ID（可选）" hint="期望流程从哪个出口返回"><Input value={step.returnId} maxLength={64} className="xapi-mono-input"
            onChange={event => onChange({ returnId: event.target.value })} /></Field>
        </div>
        <Field label="输出期望" className="xapi-gap-top">
          <div className="xapi-table-wrap"><Table<OutputRow> rowKey={(_, at) => String(at)} size="small" pagination={false} dataSource={step.outputs}
            locale={{ emptyText: '未设置输出期望' }} columns={[
              { title: '输出字段路径', render: (_, row, at) => <Input value={row.path} className="xapi-mono-input" placeholder="例如 paymentStatus"
                onChange={event => setOutput(at, { path: event.target.value })} /> },
              { title: '匹配方式', width: 150, render: (_, row, at) => <Select value={row.match} style={{ width: '100%' }}
                onChange={(value: Match) => setOutput(at, { match: value })} options={matches.map(item => ({ value: item, label: matchLabels[item] }))} /> },
              { title: '期望值', render: (_, row, at) => row.match === 'EXISTS' || row.match === 'ABSENT' ? <span className="xapi-desc">—</span>
                : <><Input value={row.value} className="xapi-mono-input" placeholder={row.match === 'ONE_OF' ? 'A, B 或 JSON 数组' : row.match === 'REGEX' ? '^https://' : '"PENDING" 或 PENDING'}
                  status={hasTemplateVariable(row.value) ? 'warning' : undefined}
                  onChange={event => setOutput(at, { value: event.target.value })} />
                  {hasTemplateVariable(row.value) && <p className="xapi-field-hint warn">期望值按字面比较，模板变量不会替换；
                    要核对前序步骤的值，请改用“存在”，或在后续步骤的输入中引用它。</p>}</> },
              { title: '', width: 60, render: (_, __, at) => <button type="button" className="xapi-link"
                onClick={() => onChange({ outputs: step.outputs.filter((_, position) => position !== at) })}>删除</button> },
            ]} /></div>
          <button type="button" className="xapi-link xapi-link-add" disabled={step.outputs.length >= 32}
            onClick={() => onChange({ outputs: [...step.outputs, { path: '', match: 'EQUALS', value: '' }] })}>添加输出期望</button>
        </Field>
      </>}
      {problems.map(message => <Alert key={message} className="xapi-gap-top" type="error" showIcon title={message} />)}
      {issues.map((issue, at) => <Alert key={at} className="xapi-gap-top" type={issue.severity === 'ERROR' ? 'error' : 'warning'} showIcon
        title={issue.message} description={<Mono>{issue.location}</Mono>} />)}
    </Panel>
  </div>;
}
