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
  caseStatusTag, decodeAccounts, decodeCase, decodeRunSummary, emptyStep, executeStatuses, hasTemplateVariable, issueStep, matchLabel, matches,
  runStatusTag, templateVariables, toStepDrafts, toSteps, verifyPath,
} from './verifyModel';
import { workspaceCrumbs, workspaceRoutes } from '../../channel/channelWorkspace';
import type { Match, OutputRow, StepDraft, VerifyAccountSummary, VerifyCaseDetail, VerifyRunSummary } from './verifyModel';
import { JsonBlock, StartRunDialog, VerifyTag } from './VerifyParts';
import { useT } from '../../i18n/react';
import { richText } from '../../i18n/richText';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channelId: string; integrationCode: string; caseCode: string | null }
interface Meta { caseCode: string; caseName: string; description?: string; accountCode: string }

const crumbsFor = (channelId: string, code: string): Crumb[] => workspaceCrumbs(channelId, code, 'verify');
const runPage = (value: unknown) => decodePage(value, decodeRunSummary, item => item.runCode);

/** 验证用例：详情（/:caseCode）、编辑（?mode=edit）与新建（/new）。步骤按顺序执行，后一步可引用前一步的输入与输出。 */
export function VerifyCaseEditor({ resources, onAuthorizationChanged, channelId, integrationCode, caseCode }: Props) {
  const t = useT('channel');
  const location = useLocation();
  const canManage = hasButton(resources, manageProtocol);
  const editing = caseCode === null || canManage && new URLSearchParams(location.search).get('mode') === 'edit';
  const decode = useMemo(() => (value: unknown) => decodeCase(value, caseCode ?? ''), [caseCode]);
  const detail = useDetail<VerifyCaseDetail>(caseCode ? `${verifyPath(integrationCode)}/verify-cases/${caseCode}` : null, decode);
  if (caseCode === null && !canManage) return <Alert type="warning" showIcon title={t('verify.noPermission')} />;
  if (caseCode && detail.loading && !detail.data) return <Loading />;
  if (caseCode && (detail.failure && !detail.data || !detail.data)) {
    return <LoadFailure title={t('verify.casesLoadFailed')} failure={detail.failure} retry={detail.refresh} />;
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
  const t = useT('channel'), tCommon = useT('common');
  const navigate = useNavigate(); const [running, setRunning] = useState(false);
  const runs = usePaging<VerifyRunSummary>(`${verifyPath(integrationCode)}/verify-runs`, runPage, { caseCode: verifyCase.caseCode },
    { initialPaging: { page: 1, size: 10 }, notifyOnError: false });
  const steps = toStepDrafts(verifyCase.steps);
  return <>
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: verifyCase.caseCode }]} title={verifyCase.caseName} code={verifyCase.caseCode}
      actions={canManage && <>
        <Button onClick={() => void navigate(replaceQuery('', { mode: 'edit' }))}>{t('protocol.edit')}</Button>
        <Button type="primary" disabled={verifyCase.verifyStatus === 'RUNNING'} onClick={() => setRunning(true)}>{t('verify.run.ok')}</Button></>} />
    <div className="xapi-facts">
      <Field label={t('verify.status')}><VerifyTag {...caseStatusTag(verifyCase.verifyStatus)} /></Field>
      <Field label={t('verify.testAccount')}>{verifyCase.accountCode
        ? <Link to={workspaceRoutes(channelId).verifyAccount(verifyCase.accountCode)}><Mono>{verifyCase.accountCode}</Mono></Link> : t('verify.case.accountDeleted')}</Field>
      <Field label={t('mids.note')}>{verifyCase.description || '—'}</Field>
      <Field label={t('verify.account.lastModified')}>{t('verify.account.modifiedBy', { time: formatTime(verifyCase.updateTime), account: verifyCase.updatedBy, version: verifyCase.version })}</Field>
    </div>
    {verifyCase.verifyStatus === 'STALE' && <Alert type="warning" showIcon
      title={t('verify.case.staleWarning')} />}
    {steps.map((step, index) => <Panel key={index} title={t('verify.runDetail.step', { no: index + 1, key: step.id })}
      extra={<Mono>{step.standardApiCode}</Mono>}>
      <div className="xapi-verify-step-view">
        <Field label={t('verify.case.inputTemplate')}><JsonBlock value={step.input} /></Field>
        <Field label={t('verify.runDetail.expected')}>{step.expect === 'PROBLEM'
          ? <span>{richText(t, 'verify.case.expectProblem', { problem: <Mono>{step.problemStatus}{step.problemCode ? ` ${step.problemCode}` : ''}</Mono> })}</span>
          : <div>
            <div>{richText(t, 'verify.case.expectStatus', { status: <Mono>{step.executeStatus}</Mono> })}{step.returnId
              && richText(t, 'verify.case.expectReturn', { id: <Mono>{step.returnId}</Mono> })}</div>
            {step.outputs.length > 0 && <ul className="xapi-verify-expect-list">{step.outputs.map((row, outputIndex) =>
              <li key={outputIndex}><Mono>outputs.{row.path}</Mono> {matchLabel(row.match)} {row.value && <Mono>{row.value}</Mono>}</li>)}</ul>}
          </div>}</Field>
      </div>
    </Panel>)}
    <div className="xapi-toolbar"><h2 className="xapi-section-title">{t('verify.runs')}</h2>
      <button type="button" className="xapi-link" onClick={runs.refresh}>{tCommon('action.refresh')}</button></div>
    {runs.error ? <LoadFailure title={t('verify.runsLoadFailed')} failure={runs.failure} retry={runs.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyRunSummary> rowKey="runCode" tableLayout="fixed" dataSource={runs.data.items} loading={runs.loading}
        pagination={{ ...runs.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('verify.case.notRun')} /> }} columns={[
          { title: t('verify.runCode'), width: 190, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyRun(row.runCode)}><Mono>{row.runCode}</Mono></Link> },
          { title: t('verify.result'), width: 100, render: (_, row) => <VerifyTag {...runStatusTag(row.runStatus)} /> },
          { title: t('verify.failureSummary'), ellipsis: true, render: (_, row) => row.failureSummary ?? '—' },
          { title: t('verify.startTime'), width: 150, render: (_, row) => formatTime(row.startTime) },
          { title: t('verify.startedBy'), width: 110, dataIndex: 'startedBy' },
        ]} /></div>}
    {running && <StartRunDialog channelId={channelId} integrationCode={integrationCode} caseCode={verifyCase.caseCode} onClose={() => setRunning(false)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function CaseForm({ channelId, integrationCode, detail, onAuthorizationChanged, onReload }: {
  channelId: string; integrationCode: string; detail?: VerifyCaseDetail; onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
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
    } catch (error) { notify(error, t('editor.validationFailed')); }
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
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: creating ? t('verify.newCase') : t('verify.account.editCrumb', { code: detail.caseCode }) }]}
      title={creating ? t('verify.newCase') : t('verify.case.editTitle', { code: detail.caseCode })}
      actions={<><Button onClick={() => void validate()}>{t('editor.validate')}</Button>
        <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>{tCommon('action.save')}</Button></>} />
    {editor.conflict && detail && <ConflictBanner code={detail.caseCode} baseVersion={detail.version} latest={latest}
      onReload={onReload} onCopy={() => copy({ ...form.getFieldsValue(true), steps: draft.steps })} />}
    <ValidationPanel issues={issues} stale={stale} onLocate={issue => setFocus(issueStep(issue.location))} />
    <Form form={form} layout="vertical" requiredMark={false} disabled={editor.saving} onValuesChange={() => editor.setDirty(true)}
      initialValues={detail ? { caseCode: detail.caseCode, caseName: detail.caseName, description: detail.description ?? '',
        accountCode: detail.accountCode ?? undefined } : { accountCode: accounts.data?.[0]?.accountCode }}>
      <Panel title={t('api.basicInfo')}><div className="xapi-grid xapi-grid-3">
        <Form.Item name="caseCode" label={t('editor.codeReadOnlyAfterCreate')} rules={[{ required: true, pattern: codePattern, message: t('api.codePattern') }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder={t('verify.case.codePlaceholder')} /> : <ReadOnlyValue mono value={detail.caseCode} />}</Form.Item>
        <Form.Item name="caseName" label={t('form.name')} rules={[{ required: true, whitespace: true, message: t('verify.account.nameRequired') }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="accountCode" label={t('verify.testAccount')} rules={[{ required: true, message: t('verify.case.accountRequired') }]}>
          <Select loading={accounts.loading} options={(accounts.data ?? []).map(item => ({ value: item.accountCode,
            label: `${item.accountCode} · ${item.accountName}` }))} /></Form.Item>
      </div>
      <Form.Item name="description" label={t('mids.note')} style={{ marginBottom: 0 }}><Input maxLength={512} /></Form.Item></Panel>
    </Form>
    {steps.map((step, index) => <StepPanel key={index} index={index} step={step} total={steps.length} focused={focus === index}
      problems={draft.problems.filter(item => item.step === index).map(item => item.message)} issues={stepIssues(index)}
      standardApis={implementations.data ?? []} onChange={change => update(index, change)} onMove={offset => move(index, offset)}
      onRemove={() => { setSteps(current => current.filter((_, at) => at !== index)); editor.setDirty(true); }} />)}
    <div><button type="button" className="xapi-link xapi-link-add" disabled={steps.length >= 10}
      onClick={() => { setSteps(current => [...current, emptyStep(current.length)]); editor.setDirty(true); }}>{t('verify.case.addStep')}</button>
      <span className="xapi-desc"> {t('verify.case.stepsHint')}</span></div>
    <Panel title={t('verify.case.templateVariables')}>
      <ul className="xapi-verify-variables">{templateVariables().map(item => <li key={item.name}><Mono>{item.name}</Mono> {item.description}</li>)}</ul>
      <Note items={[t('verify.case.note.stringOnly'), t('verify.case.note.literal'), t('verify.case.note.negative')]} />
    </Panel>
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

function StepPanel({ index, step, total, focused, problems, issues, standardApis, onChange, onMove, onRemove }: {
  index: number; step: StepDraft; total: number; focused: boolean; problems: string[]; issues: ValidationIssue[];
  standardApis: ImplementationSummary[]; onChange: (change: Partial<StepDraft>) => void; onMove: (offset: number) => void; onRemove: () => void;
}) {
  const t = useT('channel');
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (focused) ref.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, [focused]);
  const setOutput = (at: number, change: Partial<OutputRow>) =>
    onChange({ outputs: step.outputs.map((row, position) => position === at ? { ...row, ...change } : row) });
  return <div ref={ref} className={`xapi-verify-step${focused ? ' xapi-verify-step-focused' : ''}${problems.length || issues.some(issue => issue.severity === 'ERROR') ? ' xapi-row-error' : ''}`}>
    <Panel title={t('verify.case.stepTitle', { no: index + 1 })} extra={<span className="xapi-ops">
      <button type="button" className="xapi-link" disabled={index === 0} onClick={() => onMove(-1)}>{t('api.rules.moveUp')}</button>
      <button type="button" className="xapi-link" disabled={index === total - 1} onClick={() => onMove(1)}>{t('api.rules.moveDown')}</button>
      <button type="button" className="xapi-link" disabled={total === 1} onClick={onRemove}>{t('endpointEditor.delete')}</button></span>}>
      <div className="xapi-grid xapi-grid-3">
        <Field label={t('verify.case.stepId')} hint={t('verify.case.stepIdHint')}>
          <Input value={step.id} maxLength={64} className="xapi-mono-input" onChange={event => onChange({ id: event.target.value })} /></Field>
        <Field label={t('grants.standardApi')} hint={t('verify.case.standardApiHint')}>
          <Select value={step.standardApiCode || undefined} placeholder={t('verify.case.selectStandardApi')} onChange={value => onChange({ standardApiCode: value })}
            options={standardApis.map(item => ({ value: item.standardApiCode, label: `${item.standardApiCode} · ${item.standardApiName}` }))} /></Field>
        <Field label={t('verify.case.expectType')}>
          <Radio.Group value={step.expect} onChange={event => onChange({ expect: event.target.value })} options={[
            { value: 'EXECUTE', label: t('verify.case.expectExecute') }, { value: 'PROBLEM', label: t('verify.case.expectProblemOption') }]} /></Field>
      </div>
      <Field label={t('verify.case.input')} className="xapi-gap-top">
        <Input.TextArea value={step.input} autoSize={{ minRows: 4, maxRows: 18 }} className="xapi-mono-input" spellCheck={false}
          onChange={event => onChange({ input: event.target.value })} /></Field>
      {step.expect === 'PROBLEM' ? <div className="xapi-grid xapi-grid-3 xapi-gap-top">
        <Field label={t('verify.case.httpStatus')}><Input value={step.problemStatus} maxLength={3} className="xapi-mono-input"
          onChange={event => onChange({ problemStatus: event.target.value })} /></Field>
        <Field label={t('verify.case.problemCode')}><Input value={step.problemCode} maxLength={64} className="xapi-mono-input" placeholder={t('verify.case.problemCodePlaceholder')}
          onChange={event => onChange({ problemCode: event.target.value })} /></Field>
      </div> : <>
        <div className="xapi-grid xapi-grid-3 xapi-gap-top">
          <Field label={t('api.rules.executeStatus')}><Select value={step.executeStatus} onChange={value => onChange({ executeStatus: value })}
            options={executeStatuses.map(item => ({ value: item, label: item }))} /></Field>
          <Field label={t('verify.case.returnId')} hint={t('verify.case.returnIdHint')}><Input value={step.returnId} maxLength={64} className="xapi-mono-input"
            onChange={event => onChange({ returnId: event.target.value })} /></Field>
        </div>
        <Field label={t('verify.case.outputs')} className="xapi-gap-top">
          <div className="xapi-table-wrap"><Table<OutputRow> rowKey={(_, at) => String(at)} size="small" pagination={false} dataSource={step.outputs}
            locale={{ emptyText: t('verify.case.outputsEmpty') }} columns={[
              { title: t('verify.case.outputPath'), render: (_, row, at) => <Input value={row.path} className="xapi-mono-input" placeholder={t('verify.case.outputPathPlaceholder')}
                onChange={event => setOutput(at, { path: event.target.value })} /> },
              { title: t('verify.case.match'), width: 150, render: (_, row, at) => <Select value={row.match} style={{ width: '100%' }}
                onChange={(value: Match) => setOutput(at, { match: value })} options={matches.map(item => ({ value: item, label: matchLabel(item) }))} /> },
              { title: t('verify.case.expectedValue'), render: (_, row, at) => row.match === 'EXISTS' || row.match === 'ABSENT' ? <span className="xapi-desc">—</span>
                : <><Input value={row.value} className="xapi-mono-input" placeholder={row.match === 'ONE_OF' ? t('verify.case.oneOfPlaceholder') : row.match === 'REGEX' ? '^https://' : t('verify.case.equalsPlaceholder')}
                  status={hasTemplateVariable(row.value) ? 'warning' : undefined}
                  onChange={event => setOutput(at, { value: event.target.value })} />
                  {hasTemplateVariable(row.value) && <p className="xapi-field-hint warn">{t('verify.case.literalWarning')}</p>}</> },
              { title: '', width: 60, render: (_, __, at) => <button type="button" className="xapi-link"
                onClick={() => onChange({ outputs: step.outputs.filter((_, position) => position !== at) })}>{t('endpointEditor.delete')}</button> },
            ]} /></div>
          <button type="button" className="xapi-link xapi-link-add" disabled={step.outputs.length >= 32}
            onClick={() => onChange({ outputs: [...step.outputs, { path: '', match: 'EQUALS', value: '' }] })}>{t('verify.case.addOutput')}</button>
        </Field>
      </>}
      {problems.map(message => <Alert key={message} className="xapi-gap-top" type="error" showIcon title={message} />)}
      {issues.map((issue, at) => <Alert key={at} className="xapi-gap-top" type={issue.severity === 'ERROR' ? 'error' : 'warning'} showIcon
        title={issue.message} description={<Mono>{issue.location}</Mono>} />)}
    </Panel>
  </div>;
}
