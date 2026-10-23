import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Empty, Form, Input, Select, Skeleton, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { hasAuthenticationOutput, knownPoints, pointName } from '../risk/riskPoints';
import { QUERY_POINTS, riskEvaluationApi } from '../risk/riskEvaluationApi';
import {
  authenticationLabel, businessKeyLabel, changedLabel, conclusionTitle, conditionLines, currentValueNote, evaluationQuerySearch, exitEdgeLabel, failureLabel, formatUtc, groupRules,
  idFormatMessage, inputRows, isCurrentValue, isShadow, modeLabel, nodeTypeLabel, normalizeId, notProvidedText, parseEvaluationQuery, pathUnavailableText, predicateMark,
  queryTypeLabel, queryTypes, resultColor, resultLabel, ruleOutcome, stepName, stepSummary, variableBasisText, variableFailed, variableNameLookup, variableSourceLabel, variableTypeLabel, variableValueText,
} from '../risk/riskEvaluation';
import type { Evaluation, EvaluationExplanation, EvaluationQuery, EvaluationQueryType, RuleItem, ShadowItem, VariableItem } from '../risk/riskEvaluation';
import '../risk/riskEvaluation.css';
import { translator } from '../i18n/index';
import { useT } from '../i18n/react';

function errorText(e: unknown): string {
  if (e instanceof ApiError) return `${e.message}${e.requestId ? ` · Request ID: ${e.requestId}` : ''}`;
  return e instanceof Error ? e.message : translator('risk')('evaluationPage.queryFailed');
}
const notFound = (e: unknown) => e instanceof ApiError && e.status === 404;

type Outcome = { state: 'idle' | 'invalid' | 'loading' | 'notFound' } | { state: 'error'; message: string } | { state: 'ready'; evaluation: Evaluation; explanation: EvaluationExplanation | null; explanationError: string };

/** 风控评估查询与解释：按支付尝试 ID、订单 ID 或评估 ID 查看结论、路径、规则条件、变量、输入与影子评估；查询条件只保存在地址栏。 */
export function RiskEvaluations({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const t = useT('risk'), tNav = useT('nav');
  const location = useLocation(), navigate = useNavigate();
  const query = parseEvaluationQuery(location.search);
  const validId = normalizeId(query.id);
  const [type, setType] = useState<EvaluationQueryType>(query.type), [draft, setDraft] = useState(query.id), [draftError, setDraftError] = useState('');
  const [outcome, setOutcome] = useState<Outcome>({ state: 'idle' }), [revision, setRevision] = useState(0);
  useEffect(() => { setType(query.type); setDraft(query.id); setDraftError(query.id && !validId ? idFormatMessage() : ''); }, [query.type, query.id]);
  useEffect(() => {
    if (!query.id) { setOutcome({ state: 'idle' }); return; }
    if (!validId) { setOutcome({ state: 'invalid' }); return; }
    const abort = new AbortController(); setOutcome({ state: 'loading' });
    void load(query.type, validId, abort.signal).then(next => { if (!abort.signal.aborted) setOutcome(next); });
    return () => abort.abort();
  }, [query.type, validId, revision]);
  async function load(kind: EvaluationQueryType, id: string, signal: AbortSignal): Promise<Outcome> {
    let evaluation: Evaluation;
    try { evaluation = kind === 'evaluation' ? await riskEvaluationApi.get(id, signal) : await riskEvaluationApi.find(QUERY_POINTS[kind], id, signal); }
    catch (e) { return failed(e); }
    try { return { state: 'ready', evaluation, explanation: await riskEvaluationApi.explanation(evaluation.evaluationId, signal), explanationError: '' }; }
    catch (e) { if (e instanceof ApiError && e.status === 403) onAuthorizationChanged(); return { state: 'ready', evaluation, explanation: null, explanationError: errorText(e) }; }
  }
  function failed(e: unknown): Outcome {
    if (notFound(e)) return { state: 'notFound' };
    if (e instanceof ApiError && e.status === 403) onAuthorizationChanged();
    return { state: 'error', message: errorText(e) };
  }
  const open = (next: EvaluationQuery) => { if (next.type === query.type && next.id === query.id) setRevision(r => r + 1); else void navigate({ search: evaluationQuerySearch(next) }); };
  function submit() {
    const id = normalizeId(draft);
    if (!id) { setDraftError(idFormatMessage()); return; }
    setDraftError(''); open({ type, id });
  }
  const loading = outcome.state === 'loading';
  return <div className="variable-page evaluation-page">
    <div className="page-heading"><div><h1>{tNav('resource.OPERATIONS_RISK_EVALUATIONS')}</h1><p className="cell-meta">{t('evaluationPage.subtitle')}</p></div></div>
    <Form layout="inline" className="evaluation-query" onFinish={submit}>
      <Form.Item><Select aria-label={t('evaluationPage.queryType')} style={{ width: 140 }} value={type} options={queryTypes.map(value => ({ value, label: queryTypeLabel(value) }))} onChange={setType} /></Form.Item>
      <Form.Item validateStatus={draftError ? 'error' : undefined} help={draftError || undefined}>
        <Input aria-label={queryTypeLabel(type)} style={{ width: 320 }} value={draft} maxLength={19} allowClear placeholder={t('evaluationPage.placeholder', { type: queryTypeLabel(type) })} onChange={e => { setDraft(e.target.value); setDraftError(''); }} /></Form.Item>
      <Form.Item><Button type="primary" htmlType="submit" disabled={loading}>{t('candidateList.search')}</Button></Form.Item>
    </Form>
    {outcome.state === 'idle' && <p className="cell-meta evaluation-hint">{t('evaluationPage.hint')}</p>}
    {outcome.state === 'loading' && <section className="evaluation-card"><Skeleton active paragraph={{ rows: 6 }} /></section>}
    {outcome.state === 'notFound' && <section className="evaluation-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('evaluationPage.notFound')} /></section>}
    {outcome.state === 'error' && <Alert type="error" showIcon title={outcome.message} action={<Button onClick={() => setRevision(r => r + 1)}>{t('flowPage.retry')}</Button>} />}
    {outcome.state === 'ready' && <EvaluationResultView evaluation={outcome.evaluation} explanation={outcome.explanation} explanationError={outcome.explanationError}
      onOpen={id => open({ type: 'evaluation', id })} onRetry={() => setRevision(r => r + 1)} />}
  </div>;
}

function EvaluationResultView({ evaluation, explanation, explanationError, onOpen, onRetry }: { evaluation: Evaluation; explanation: EvaluationExplanation | null; explanationError: string; onOpen: (id: string) => void; onRetry: () => void }) {
  const t = useT('risk');
  const shadow = isShadow(evaluation);
  return <>
    <ConclusionCard evaluation={evaluation} onOpen={onOpen} />
    {explanationError && <Alert type="error" showIcon title={t('evaluationPage.explanationFailed', { error: explanationError })} action={<Button onClick={onRetry}>{t('flowPage.retry')}</Button>} />}
    {explanation && <Tabs className="evaluation-tabs" items={[
      { key: 'path', label: t('evaluationPage.tab.path'), children: <PathPanel explanation={explanation} /> },
      { key: 'rules', label: t('evaluationPage.tab.rules'), children: <RulesPanel explanation={explanation} /> },
      { key: 'variables', label: t('evaluationPage.tab.variables'), children: <VariablesPanel variables={explanation.variables} /> },
      { key: 'inputs', label: t('evaluationPage.tab.inputs'), children: <InputsPanel inputs={explanation.inputs} pointCode={evaluation.pointCode} /> },
      ...(shadow ? [] : [{ key: 'shadows', label: t('evaluationPage.tab.shadows', { count: explanation.shadows.length }), children: <ShadowsPanel shadows={explanation.shadows} authentication={hasAuthenticationOutput(evaluation.pointCode)} onOpen={onOpen} /> }]),
    ]} />}
  </>;
}

function ConclusionCard({ evaluation, onOpen }: { evaluation: Evaluation; onOpen: (id: string) => void }) {
  const t = useT('risk');
  const authentication = authenticationLabel(evaluation.outputs);
  const reason = evaluation.reasonCode ? `${evaluation.reasonCode}${evaluation.reasonMessage ? ` · ${evaluation.reasonMessage}` : ''}` : '—';
  return <section className={`evaluation-card evaluation-conclusion is-${resultColor(evaluation.result)}`}>
    <div className="evaluation-card-title">
      <Space><span className="evaluation-dot" /><strong className="evaluation-result">{conclusionTitle(evaluation)}</strong><Tag color={resultColor(evaluation.result)}>{evaluation.result}</Tag></Space>
      <Space wrap><Tag color={isShadow(evaluation) ? 'purple' : 'blue'}>{modeLabel(evaluation.mode)}</Tag><span className="cell-meta">{t('evaluationPage.evaluationId', { id: evaluation.evaluationId })}</span>
        {evaluation.baselineEvaluationId && <Button type="link" size="small" onClick={() => onOpen(evaluation.baselineEvaluationId!)}>{t('evaluationPage.viewBaseline', { id: evaluation.baselineEvaluationId })}</Button>}</Space>
    </div>
    <Descriptions size="small" column={{ xs: 1, md: 2, xl: 3 }} items={[
      { key: 'point', label: t('ruleSetList.point'), children: t('evaluationPage.pointValue', { name: pointName(knownPoints(), evaluation.pointCode), code: evaluation.pointCode }) },
      { key: 'business', label: businessKeyLabel(evaluation.pointCode), children: evaluation.businessKey },
      ...(hasAuthenticationOutput(evaluation.pointCode) ? [{ key: 'authentication', label: t('flow.output.authentication'), children: authentication ?? '—' }] : []),
      { key: 'release', label: t('ruleSetExplorer.column.release'), children: `${evaluation.releaseId} · ${evaluation.environment}` },
      { key: 'flow', label: t('evaluationPage.field.flow'), children: `${evaluation.flowCode} v${evaluation.flowVersionNo}` },
      { key: 'time', label: t('evaluationPage.field.time'), children: formatUtc(evaluation.evaluationTime) },
      { key: 'elapsed', label: t('evaluationPage.field.elapsed'), children: `${evaluation.elapsedMillis} ms` },
      { key: 'reason', label: t('flowPanel.rejectReason'), children: reason },
      { key: 'failure', label: t('evaluationPage.field.failure'), children: failureLabel(evaluation.failureCode) },
      ...(evaluation.shadowRunId ? [{ key: 'shadowRun', label: t('evaluationPage.field.shadowRun'), children: evaluation.shadowRunId }] : []),
    ]} />
  </section>;
}

function PathPanel({ explanation }: { explanation: EvaluationExplanation }) {
  const t = useT('risk');
  if (!explanation.pathAvailable) return <Alert type="info" showIcon title={pathUnavailableText()} />;
  if (!explanation.path.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('evaluationPage.noPath')} />;
  return <ol className="evaluation-path">{explanation.path.map(step => {
    const edge = exitEdgeLabel(step), summary = stepSummary(step);
    return <li key={step.stepNo} className={step.nodeType === 'RULE_SET' && step.matched ? 'is-matched' : undefined}>
      <span className="evaluation-step-no">{step.stepNo}</span>
      <div className="evaluation-step-body"><strong>{stepName(step)}</strong><Tag>{nodeTypeLabel(step.nodeType)}</Tag>
        {summary && <span className="evaluation-step-summary">{summary}</span>}
        {step.reused && <Tag color="gold">{t('evaluationPage.reused')}</Tag>}</div>
      {edge && <span className="evaluation-step-edge">→ {edge}</span>}
    </li>;
  })}</ol>;
}

function RulesPanel({ explanation }: { explanation: EvaluationExplanation }) {
  const t = useT('risk');
  const groups = groupRules(explanation.rules, explanation.path), nameOf = variableNameLookup(explanation.variables);
  if (!groups.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('evaluationPage.noRules')} />;
  return <div className="evaluation-rule-groups">{groups.map(group => <section key={group.ruleSetVersionId} className="evaluation-rule-group">
    <h4>{group.title}</h4>
    {group.rules.map(rule => <RuleRow key={`${rule.ruleSetVersionId}:${rule.ruleId}`} rule={rule} nameOf={nameOf} />)}
  </section>)}</div>;
}

function RuleRow({ rule, nameOf }: { rule: RuleItem; nameOf: (code: string) => string | null }) {
  const t = useT('risk');
  return <div className={`evaluation-rule${rule.matched ? ' is-matched' : ''}`}>
    <div className="evaluation-rule-head"><span className="evaluation-rule-mark">{rule.matched ? '●' : '○'}</span><strong>{rule.ruleCode}</strong><span>{rule.ruleName}</span>
      <span className="cell-meta">{t('evaluationPage.priority', { priority: rule.priorityNo })}</span><Tag color={rule.matched ? 'red' : 'default'}>{ruleOutcome(rule)}</Tag></div>
    {rule.conditionAvailable && rule.condition
      ? <ul className="evaluation-condition">{conditionLines(rule.condition, nameOf).map(line => {
        const mark = predicateMark(line.result);
        return <li key={line.key} className={`is-${line.kind.toLowerCase()} is-${mark.tone}`} style={{ paddingLeft: line.depth * 20 }}>
          <span className="evaluation-condition-text">{line.text}</span><Tooltip title={mark.label}><span className="evaluation-condition-result" aria-label={mark.label}>{mark.mark} {mark.label}</span></Tooltip></li>;
      })}</ul>
      : <Alert className="evaluation-condition-unavailable" type="info" showIcon title={t('evaluationPage.conditionUnavailable')} />}
  </div>;
}

function VariablesPanel({ variables }: { variables: VariableItem[] }) {
  const t = useT('risk');
  const hasCurrent = variables.some(isCurrentValue);
  return <section className="table-panel">
    <Table<VariableItem> rowKey="variableCode" size="small" pagination={false} dataSource={variables} locale={{ emptyText: t('evaluationPage.noVariables') }}
      rowClassName={v => variableFailed(v) ? 'evaluation-variable-failed' : ''}
      columns={[
        { title: t('readWorkspace.code'), dataIndex: 'variableCode' },
        { title: t('ruleSetPicker.column.name'), render: (_, v) => v.variableName ?? '—' },
        { title: t('dictionary.column.source'), render: (_, v) => variableSourceLabel(v.sourceType) },
        { title: t('evaluationPage.column.type'), render: (_, v) => variableTypeLabel(v.valueType) },
        { title: t('evaluationPage.column.value'), render: (_, v) => <span className="evaluation-value">{variableValueText(v)}</span> },
        { title: t('evaluationPage.column.basis'), render: (_, v) => variableFailed(v) ? <Tag color="red">{variableBasisText(v)}</Tag>
          : isCurrentValue(v) ? <Tooltip title={currentValueNote()}><Tag color="gold">{t('evaluationPage.currentValueTag')}</Tag></Tooltip> : <span className="cell-meta">{variableBasisText(v)}</span> },
      ]} />
    {hasCurrent && <Alert className="evaluation-note" type="info" showIcon title={currentValueNote()} />}
  </section>;
}

function InputsPanel({ inputs, pointCode }: { inputs: Record<string, string | null>; pointCode: string }) {
  const t = useT('risk');
  return <section className="evaluation-card"><h3>{t('evaluationPage.inputsTitle')}</h3>
    <Descriptions size="small" bordered column={{ xs: 1, md: 2, xl: 3 }} items={inputRows(inputs, pointCode).map(row => ({
      key: row.key, label: row.label, children: row.value === null ? <span className="evaluation-missing">{notProvidedText()}</span> : <span className="evaluation-value">{row.value}</span>,
    }))} /></section>;
}

function ShadowsPanel({ shadows, authentication, onOpen }: { shadows: ShadowItem[]; authentication: boolean; onOpen: (id: string) => void }) {
  const t = useT('risk');
  return <section className="table-panel"><Table<ShadowItem> rowKey="evaluationId" size="small" pagination={false} dataSource={shadows} locale={{ emptyText: t('evaluationPage.noShadows') }}
    columns={[
      { title: t('evaluation.queryType.evaluation'), dataIndex: 'evaluationId' },
      { title: t('evaluationPage.column.batch'), dataIndex: 'shadowRunId' },
      { title: t('evaluationPage.column.result'), render: (_, s) => <Tag color={resultColor(s.result)}>{resultLabel(s.result)}</Tag> },
      { title: t('evaluationPage.column.resultChanged'), render: (_, s) => <span className={s.decisionChanged ? 'evaluation-changed' : undefined}>{changedLabel(s.decisionChanged)}</span> },
      ...(authentication ? [{ title: t('evaluationPage.column.authenticationChanged'), render: (_: unknown, s: ShadowItem) => <span className={s.outputChanged ? 'evaluation-changed' : undefined}>{changedLabel(s.outputChanged)}</span> }] : []),
      { title: t('evaluationPage.column.severity'), render: (_, s) => s.severity ?? '—' },
      { title: t('candidateList.column.actions'), render: (_, s) => <Button type="link" onClick={() => onOpen(s.evaluationId)}>{t('ruleSetExplorer.view')}</Button> },
    ]} /></section>;
}
