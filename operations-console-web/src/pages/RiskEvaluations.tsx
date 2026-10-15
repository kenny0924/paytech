import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Empty, Form, Input, Select, Skeleton, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { KNOWN_POINTS, pointName } from '../risk/riskPoints';
import { PAYMENT_ATTEMPT_POINT, riskEvaluationApi } from '../risk/riskEvaluationApi';
import {
  authenticationLabel, changedLabel, conclusionTitle, conditionLines, CURRENT_VALUE_NOTE, evaluationQuerySearch, exitEdgeLabel, failureLabel, formatUtc, groupRules,
  ID_FORMAT_MESSAGE, inputRows, isCurrentValue, isShadow, modeLabel, nodeTypeLabel, normalizeId, NOT_PROVIDED, parseEvaluationQuery, PATH_UNAVAILABLE, predicateMark,
  queryTypeLabels, resultColor, resultLabel, ruleOutcome, stepName, stepSummary, variableBasisText, variableFailed, variableNameLookup, variableSourceLabel, variableTypeLabel, variableValueText,
} from '../risk/riskEvaluation';
import type { Evaluation, EvaluationExplanation, EvaluationQuery, EvaluationQueryType, RuleItem, ShadowItem, VariableItem } from '../risk/riskEvaluation';
import '../risk/riskEvaluation.css';

function errorText(e: unknown): string {
  if (e instanceof ApiError) return `${e.message}${e.requestId ? ` · Request ID: ${e.requestId}` : ''}`;
  return e instanceof Error ? e.message : '查询失败，请重试';
}
const notFound = (e: unknown) => e instanceof ApiError && e.status === 404;

type Outcome = { state: 'idle' | 'invalid' | 'loading' | 'notFound' } | { state: 'error'; message: string } | { state: 'ready'; evaluation: Evaluation; explanation: EvaluationExplanation | null; explanationError: string };

/** 风控评估查询与解释：按支付尝试 ID 或评估 ID 查看结论、路径、规则条件、变量、输入与影子评估；查询条件只保存在地址栏。 */
export function RiskEvaluations({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const location = useLocation(), navigate = useNavigate();
  const query = parseEvaluationQuery(location.search);
  const validId = normalizeId(query.id);
  const [type, setType] = useState<EvaluationQueryType>(query.type), [draft, setDraft] = useState(query.id), [draftError, setDraftError] = useState('');
  const [outcome, setOutcome] = useState<Outcome>({ state: 'idle' }), [revision, setRevision] = useState(0);
  useEffect(() => { setType(query.type); setDraft(query.id); setDraftError(query.id && !validId ? ID_FORMAT_MESSAGE : ''); }, [query.type, query.id]);
  useEffect(() => {
    if (!query.id) { setOutcome({ state: 'idle' }); return; }
    if (!validId) { setOutcome({ state: 'invalid' }); return; }
    const abort = new AbortController(); setOutcome({ state: 'loading' });
    void load(query.type, validId, abort.signal).then(next => { if (!abort.signal.aborted) setOutcome(next); });
    return () => abort.abort();
  }, [query.type, validId, revision]);
  async function load(kind: EvaluationQueryType, id: string, signal: AbortSignal): Promise<Outcome> {
    let evaluation: Evaluation;
    try { evaluation = kind === 'attempt' ? await riskEvaluationApi.find(PAYMENT_ATTEMPT_POINT, id, signal) : await riskEvaluationApi.get(id, signal); }
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
    if (!id) { setDraftError(ID_FORMAT_MESSAGE); return; }
    setDraftError(''); open({ type, id });
  }
  const loading = outcome.state === 'loading';
  return <div className="variable-page evaluation-page">
    <div className="page-heading"><div><h1>评估查询</h1><p className="cell-meta">输入支付尝试 ID 或评估 ID，查看风控结论及其解释。</p></div></div>
    <Form layout="inline" className="evaluation-query" onFinish={submit}>
      <Form.Item><Select aria-label="查询类型" style={{ width: 140 }} value={type} options={Object.entries(queryTypeLabels).map(([value, label]) => ({ value, label }))} onChange={setType} /></Form.Item>
      <Form.Item validateStatus={draftError ? 'error' : undefined} help={draftError || undefined}>
        <Input aria-label={queryTypeLabels[type]} style={{ width: 320 }} value={draft} maxLength={19} allowClear placeholder={`请输入${queryTypeLabels[type]}`} onChange={e => { setDraft(e.target.value); setDraftError(''); }} /></Form.Item>
      <Form.Item><Button type="primary" htmlType="submit" disabled={loading}>查询</Button></Form.Item>
    </Form>
    {outcome.state === 'idle' && <p className="cell-meta evaluation-hint">输入支付尝试 ID 或评估 ID</p>}
    {outcome.state === 'loading' && <section className="evaluation-card"><Skeleton active paragraph={{ rows: 6 }} /></section>}
    {outcome.state === 'notFound' && <section className="evaluation-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有找到该评估" /></section>}
    {outcome.state === 'error' && <Alert type="error" showIcon title={outcome.message} action={<Button onClick={() => setRevision(r => r + 1)}>重试</Button>} />}
    {outcome.state === 'ready' && <EvaluationResultView evaluation={outcome.evaluation} explanation={outcome.explanation} explanationError={outcome.explanationError}
      onOpen={id => open({ type: 'evaluation', id })} onRetry={() => setRevision(r => r + 1)} />}
  </div>;
}

function EvaluationResultView({ evaluation, explanation, explanationError, onOpen, onRetry }: { evaluation: Evaluation; explanation: EvaluationExplanation | null; explanationError: string; onOpen: (id: string) => void; onRetry: () => void }) {
  const shadow = isShadow(evaluation);
  return <>
    <ConclusionCard evaluation={evaluation} onOpen={onOpen} />
    {explanationError && <Alert type="error" showIcon title={`解释加载失败：${explanationError}`} action={<Button onClick={onRetry}>重试</Button>} />}
    {explanation && <Tabs className="evaluation-tabs" items={[
      { key: 'path', label: '路径', children: <PathPanel explanation={explanation} /> },
      { key: 'rules', label: '规则与条件', children: <RulesPanel explanation={explanation} /> },
      { key: 'variables', label: '变量', children: <VariablesPanel variables={explanation.variables} /> },
      { key: 'inputs', label: '输入', children: <InputsPanel inputs={explanation.inputs} /> },
      ...(shadow ? [] : [{ key: 'shadows', label: `影子评估 (${explanation.shadows.length})`, children: <ShadowsPanel shadows={explanation.shadows} onOpen={onOpen} /> }]),
    ]} />}
  </>;
}

function ConclusionCard({ evaluation, onOpen }: { evaluation: Evaluation; onOpen: (id: string) => void }) {
  const authentication = authenticationLabel(evaluation.outputs);
  const reason = evaluation.reasonCode ? `${evaluation.reasonCode}${evaluation.reasonMessage ? ` · ${evaluation.reasonMessage}` : ''}` : '—';
  return <section className={`evaluation-card evaluation-conclusion is-${resultColor(evaluation.result)}`}>
    <div className="evaluation-card-title">
      <Space><span className="evaluation-dot" /><strong className="evaluation-result">{conclusionTitle(evaluation)}</strong><Tag color={resultColor(evaluation.result)}>{evaluation.result}</Tag></Space>
      <Space wrap><Tag color={isShadow(evaluation) ? 'purple' : 'blue'}>{modeLabel(evaluation.mode)}</Tag><span className="cell-meta">评估 ID {evaluation.evaluationId}</span>
        {evaluation.baselineEvaluationId && <Button type="link" size="small" onClick={() => onOpen(evaluation.baselineEvaluationId!)}>查看基线评估 {evaluation.baselineEvaluationId}</Button>}</Space>
    </div>
    <Descriptions size="small" column={{ xs: 1, md: 2, xl: 3 }} items={[
      { key: 'point', label: '风控点', children: `${pointName(KNOWN_POINTS, evaluation.pointCode)}（${evaluation.pointCode}）` },
      { key: 'business', label: evaluation.pointCode === PAYMENT_ATTEMPT_POINT ? '支付尝试' : '业务身份', children: evaluation.businessKey },
      { key: 'authentication', label: '认证要求', children: authentication ?? '—' },
      { key: 'release', label: '发布', children: `${evaluation.releaseId} · ${evaluation.environment}` },
      { key: 'flow', label: '流程', children: `${evaluation.flowCode} v${evaluation.flowVersionNo}` },
      { key: 'time', label: '评估时间', children: formatUtc(evaluation.evaluationTime) },
      { key: 'elapsed', label: '耗时', children: `${evaluation.elapsedMillis} ms` },
      { key: 'reason', label: '拒绝原因', children: reason },
      { key: 'failure', label: '失败码', children: failureLabel(evaluation.failureCode) },
      ...(evaluation.shadowRunId ? [{ key: 'shadowRun', label: '影子批次', children: evaluation.shadowRunId }] : []),
    ]} />
  </section>;
}

function PathPanel({ explanation }: { explanation: EvaluationExplanation }) {
  if (!explanation.pathAvailable) return <Alert type="info" showIcon title={PATH_UNAVAILABLE} />;
  if (!explanation.path.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有路径记录" />;
  return <ol className="evaluation-path">{explanation.path.map(step => {
    const edge = exitEdgeLabel(step), summary = stepSummary(step);
    return <li key={step.stepNo} className={step.nodeType === 'RULE_SET' && step.matched ? 'is-matched' : undefined}>
      <span className="evaluation-step-no">{step.stepNo}</span>
      <div className="evaluation-step-body"><strong>{stepName(step)}</strong><Tag>{nodeTypeLabel(step.nodeType)}</Tag>
        {summary && <span className="evaluation-step-summary">{summary}</span>}
        {step.reused && <Tag color="gold">复用前次结果</Tag>}</div>
      {edge && <span className="evaluation-step-edge">→ {edge}</span>}
    </li>;
  })}</ol>;
}

function RulesPanel({ explanation }: { explanation: EvaluationExplanation }) {
  const groups = groupRules(explanation.rules, explanation.path), nameOf = variableNameLookup(explanation.variables);
  if (!groups.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次评估没有执行任何规则" />;
  return <div className="evaluation-rule-groups">{groups.map(group => <section key={group.ruleSetVersionId} className="evaluation-rule-group">
    <h4>{group.title}</h4>
    {group.rules.map(rule => <RuleRow key={`${rule.ruleSetVersionId}:${rule.ruleId}`} rule={rule} nameOf={nameOf} />)}
  </section>)}</div>;
}

function RuleRow({ rule, nameOf }: { rule: RuleItem; nameOf: (code: string) => string | null }) {
  return <div className={`evaluation-rule${rule.matched ? ' is-matched' : ''}`}>
    <div className="evaluation-rule-head"><span className="evaluation-rule-mark">{rule.matched ? '●' : '○'}</span><strong>{rule.ruleCode}</strong><span>{rule.ruleName}</span>
      <span className="cell-meta">优先级 {rule.priorityNo}</span><Tag color={rule.matched ? 'red' : 'default'}>{ruleOutcome(rule)}</Tag></div>
    {rule.conditionAvailable && rule.condition
      ? <ul className="evaluation-condition">{conditionLines(rule.condition, nameOf).map(line => {
        const mark = predicateMark(line.result);
        return <li key={line.key} className={`is-${line.kind.toLowerCase()} is-${mark.tone}`} style={{ paddingLeft: line.depth * 20 }}>
          <span className="evaluation-condition-text">{line.text}</span><Tooltip title={mark.label}><span className="evaluation-condition-result" aria-label={mark.label}>{mark.mark} {mark.label}</span></Tooltip></li>;
      })}</ul>
      : <Alert className="evaluation-condition-unavailable" type="info" showIcon title="条件结构暂不可用，仍显示命中结果" />}
  </div>;
}

function VariablesPanel({ variables }: { variables: VariableItem[] }) {
  const hasCurrent = variables.some(isCurrentValue);
  return <section className="table-panel">
    <Table<VariableItem> rowKey="variableCode" size="small" pagination={false} dataSource={variables} locale={{ emptyText: '本次发布没有引用变量' }}
      rowClassName={v => variableFailed(v) ? 'evaluation-variable-failed' : ''}
      columns={[
        { title: '编码', dataIndex: 'variableCode' },
        { title: '名称', render: (_, v) => v.variableName ?? '—' },
        { title: '来源', render: (_, v) => variableSourceLabel(v.sourceType) },
        { title: '类型', render: (_, v) => variableTypeLabel(v.valueType) },
        { title: '取值', render: (_, v) => <span className="evaluation-value">{variableValueText(v)}</span> },
        { title: '依据', render: (_, v) => variableFailed(v) ? <Tag color="red">{variableBasisText(v)}</Tag>
          : isCurrentValue(v) ? <Tooltip title={CURRENT_VALUE_NOTE}><Tag color="gold">当前值 ⓘ</Tag></Tooltip> : <span className="cell-meta">{variableBasisText(v)}</span> },
      ]} />
    {hasCurrent && <Alert className="evaluation-note" type="info" showIcon title={CURRENT_VALUE_NOTE} />}
  </section>;
}

function InputsPanel({ inputs }: { inputs: Record<string, string | null> }) {
  return <section className="evaluation-card"><h3>输入（评估时冻结）</h3>
    <Descriptions size="small" bordered column={{ xs: 1, md: 2, xl: 3 }} items={inputRows(inputs).map(row => ({
      key: row.key, label: row.label, children: row.value === null ? <span className="evaluation-missing">{NOT_PROVIDED}</span> : <span className="evaluation-value">{row.value}</span>,
    }))} /></section>;
}

function ShadowsPanel({ shadows, onOpen }: { shadows: ShadowItem[]; onOpen: (id: string) => void }) {
  return <section className="table-panel"><Table<ShadowItem> rowKey="evaluationId" size="small" pagination={false} dataSource={shadows} locale={{ emptyText: '没有以该评估为基线的影子评估' }}
    columns={[
      { title: '评估 ID', dataIndex: 'evaluationId' },
      { title: '批次', dataIndex: 'shadowRunId' },
      { title: '结论', render: (_, s) => <Tag color={resultColor(s.result)}>{resultLabel(s.result)}</Tag> },
      { title: '结论变化', render: (_, s) => <span className={s.decisionChanged ? 'evaluation-changed' : undefined}>{changedLabel(s.decisionChanged)}</span> },
      { title: '认证要求变化', render: (_, s) => <span className={s.outputChanged ? 'evaluation-changed' : undefined}>{changedLabel(s.outputChanged)}</span> },
      { title: '严重程度', render: (_, s) => s.severity ?? '—' },
      { title: '操作', render: (_, s) => <Button type="link" onClick={() => onOpen(s.evaluationId)}>查看</Button> },
    ]} /></section>;
}
