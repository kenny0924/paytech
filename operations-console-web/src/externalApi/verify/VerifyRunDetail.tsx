import { useEffect, useMemo } from 'react';
import { Alert, Collapse, Table } from 'antd';
import { Link } from 'react-router-dom';
import { Loading, LoadFailure, useDetail } from '../externalApiHooks';
import { Field, Mono, PageHeader, Panel, formatTime } from '../ui';
import { decodeRun, runStatusTag, stepStatusTag, verifyPath } from './verifyModel';
import { workspaceCrumbs, workspaceRoutes } from '../../channel/channelWorkspace';
import type { VerifyAssertion, VerifyRunDetail as RunDetail, VerifyRunStep } from './verifyModel';
import { JsonBlock, VerifyTag } from './VerifyParts';
import { useT } from '../../i18n/react';
import { richText } from '../../i18n/richText';

/** 轮询间隔：运行在后台执行，详情页在 RUNNING 时定期刷新。 */
const POLL_MILLIS = 2000;

/**
 * 验证运行详情与证据（沙箱验证技术方案第 6 节）：结论、运行原因、出站地址（含沙箱标记）、配置版本，
 * 以及每一步的实际输入、结果、期望核对与节点轨迹（脱敏后的渠道请求与响应原文）。
 */
export function VerifyRunDetail({ channelId, integrationCode, runCode }: { channelId: string; integrationCode: string; runCode: string }) {
  const t = useT('channel');
  const decode = useMemo(() => (value: unknown) => decodeRun(value, runCode), [runCode]);
  const detail = useDetail<RunDetail>(`${verifyPath(integrationCode)}/verify-runs/${runCode}`, decode);
  const running = detail.data?.runStatus === 'RUNNING';
  const { refresh } = detail;
  useEffect(() => {
    if (!running) return undefined;
    const timer = window.setInterval(refresh, POLL_MILLIS);
    return () => window.clearInterval(timer);
  }, [running, refresh]);
  if (detail.loading && !detail.data) return <Loading />;
  if (!detail.data) return <LoadFailure title={t('verify.runsLoadFailed')} failure={detail.failure} retry={detail.refresh} />;
  const run = detail.data;
  const reason = typeof run.caseSnapshot.reason === 'string' ? run.caseSnapshot.reason : '—';
  return <>
    <PageHeader crumbs={[...workspaceCrumbs(channelId, integrationCode, 'verify'), { label: t('verify.runDetail.title', { code: run.runCode }) }]}
      title={t('verify.runDetail.title', { code: run.runCode })} code={run.caseCode} />
    {running && <Alert type="info" showIcon title={t('verify.runDetail.running')} />}
    {run.stale && !running && <Alert type="warning" showIcon title={t('verify.runDetail.stale')} />}
    <div className="xapi-facts">
      <Field label={t('verify.result')}><VerifyTag {...runStatusTag(run.runStatus)} /></Field>
      <Field label={t('verify.testCase')}><Link to={workspaceRoutes(channelId).verifyCase(run.caseCode)}><Mono>{run.caseCode}</Mono></Link> · {run.caseName}</Field>
      <Field label={t('verify.run.reason')}>{reason}</Field>
      <Field label={t('verify.runDetail.started')}>{t('verify.runDetail.startedBy', { time: formatTime(run.startTime), account: run.startedBy })}{run.finishTime
        && t('verify.runDetail.finished', { time: formatTime(run.finishTime) })}</Field>
    </div>
    {run.failureSummary && <Alert type="error" showIcon title={run.failureSummary} />}
    <Panel title={t('overview.endpoints')}><div className="xapi-table-wrap"><Table rowKey="endpointCode" size="small" pagination={false} dataSource={run.endpoints}
      locale={{ emptyText: t('verify.runDetail.noConfig') }} columns={[
        { title: t('api.endpointCode'), width: 220, render: (_, row) => <Mono>{row.endpointCode}</Mono> },
        { title: t('verify.runDetail.baseUrl'), ellipsis: true, render: (_, row) => <Mono>{row.baseUrl}</Mono> },
        { title: t('overview.sandbox'), width: 90, render: (_, row) => row.sandbox ? <VerifyTag label={t('overview.sandbox')} tone="success" /> : <VerifyTag label={t('verify.runDetail.notSandbox')} tone="error" /> },
      ]} /></div></Panel>
    <Collapse className="xapi-gap-top" items={[{ key: 'versions', label: t('verify.runDetail.configVersions'),
      children: <JsonBlock value={run.configVersions} empty={t('verify.runDetail.configNotLoaded')} /> }]} />
    {run.steps.map(step => <StepEvidence key={step.stepNo} step={step} />)}
  </>;
}

function StepEvidence({ step }: { step: VerifyRunStep }) {
  const nodes = Array.isArray(step.trace?.nodes) ? step.trace.nodes as Record<string, unknown>[] : [];
  const t = useT('channel');
  const initializers = Array.isArray(step.trace?.initializers) ? step.trace.initializers as Record<string, unknown>[] : [];
  return <Panel title={t('verify.runDetail.step', { no: step.stepNo, key: step.stepKey })} extra={<span className="xapi-ops">
    <Mono>{step.standardApiCode}</Mono><VerifyTag {...stepStatusTag(step.stepStatus)} />
    {step.elapsedMs !== null && <span className="xapi-desc">{step.elapsedMs} ms</span>}</span>}>
    {step.stepStatus === 'SKIPPED' ? <p className="xapi-desc">{t('verify.runDetail.skipped')}</p> : <>
      <div className="xapi-verify-step-view">
        <Field label={t('verify.runDetail.input')}><JsonBlock value={step.resolvedInput} /></Field>
        <Field label={t('verify.runDetail.output')}><JsonBlock value={step.result} /></Field>
      </div>
      <Field label={t('verify.runDetail.assertions')} className="xapi-gap-top"><div className="xapi-table-wrap">
        <Table<VerifyAssertion> rowKey={(_, at) => String(at)} size="small" pagination={false} dataSource={step.assertions} columns={[
          { title: '', width: 40, render: (_, row) => row.passed ? <span className="xapi-verify-ok">✓</span> : <span className="xapi-verify-bad">✗</span> },
          { title: t('verify.runDetail.target'), width: 240, render: (_, row) => <Mono>{row.target}</Mono> },
          { title: t('verify.runDetail.match'), width: 90, render: (_, row) => row.match },
          { title: t('verify.runDetail.expected'), render: (_, row) => row.expected === null || row.expected === undefined ? '—' : <Mono>{JSON.stringify(row.expected)}</Mono> },
          { title: t('verify.runDetail.actual'), render: (_, row) => row.actual === null || row.actual === undefined ? <span className="xapi-desc">{t('enum.match.ABSENT')}</span> : <Mono>{JSON.stringify(row.actual)}</Mono> },
        ]} /></div></Field>
      {(initializers.length > 0 || nodes.length > 0) && <Collapse className="xapi-gap-top" items={[
        ...initializers.map((item, at) => ({ key: `init-${at}`, label: <span>{richText(t, 'verify.trace.initializer', { id: <Mono>{String(item.id)}</Mono> },
          { result: item.succeeded ? t('verify.trace.succeeded') : t('verify.trace.failed', { reason: String(item.reason ?? '') }) })}</span>,
          children: <JsonBlock value={item} /> })),
        ...nodes.map((node, at) => ({ key: `node-${at}`, label: <span>{richText(t, 'flow.node.title', { id: <Mono>{String(node.id)}</Mono> })} · <Mono>{String(node.integrationApi ?? '')}</Mono> · {String(node.executeStatus ?? '')}
          {node.reason ? ` · ${String(node.reason)}` : ''}</span>, children: <Exchange node={node} /> })),
      ]} />}
      {typeof step.trace?.returnId === 'string' && <p className="xapi-desc xapi-gap-top">{richText(t, 'verify.trace.returnId', { id: <Mono>{step.trace.returnId}</Mono> })}</p>}
    </>}
  </Panel>;
}

function Exchange({ node }: { node: Record<string, unknown> }) {
  const t = useT('channel');
  const exchange = node.exchange as Record<string, Record<string, unknown>> | undefined;
  if (!exchange) return <p className="xapi-desc">{t('verify.trace.endedBeforeRequest')}</p>;
  const request = exchange.request ?? {}; const response = exchange.response;
  return <div className="xapi-verify-exchange">
    <Field label={t('verify.trace.request', { method: String(request.method), url: String(request.url) })}>
      <JsonBlock value={request.headers} /><BodyBlock body={request.body} /></Field>
    <Field label={response ? t('verify.trace.response', { status: String(response.status) }) : t('verify.trace.noResponse', { error: String(exchange.error ?? '') })}>
      {response && <><JsonBlock value={response.headers} /><BodyBlock body={response.body} /></>}</Field>
    <p className="xapi-desc">{t('verify.trace.redacted', { elapsed: String(exchange.elapsedMs) })}</p>
  </div>;
}

function BodyBlock({ body }: { body: unknown }) {
  const t = useT('channel');
  const value = body as { text?: string; base64?: string; length?: number; truncated?: boolean } | undefined;
  if (!value) return null;
  if (value.base64 !== undefined) return <p className="xapi-desc">{richText(t, 'verify.trace.binary', { data: <Mono>{value.base64}</Mono> }, { length: String(value.length) })}</p>;
  let text = value.text ?? '';
  try { text = JSON.stringify(JSON.parse(text), null, 2); } catch { /* 原文展示 */ }
  return <>{text ? <pre className="xapi-json-block">{text}</pre> : <p className="xapi-desc">{t('verify.trace.empty')}</p>}
    {value.truncated && <p className="xapi-desc">{t('verify.trace.truncated', { length: String(value.length) })}</p>}</>;
}
