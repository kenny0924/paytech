import { useEffect, useMemo } from 'react';
import { Alert, Collapse, Table } from 'antd';
import { Link } from 'react-router-dom';
import { Loading, LoadFailure, useDetail } from '../externalApiHooks';
import { integrationRoute } from '../externalApiModel';
import { Field, Mono, PageHeader, Panel, formatTime } from '../ui';
import { decodeRun, runStatusLabels, stepStatusLabels, verifyPath, verifyRoute } from './verifyModel';
import type { VerifyAssertion, VerifyRunDetail as RunDetail, VerifyRunStep } from './verifyModel';
import { JsonBlock, VerifyTag } from './VerifyParts';

/** 轮询间隔：运行在后台执行，详情页在 RUNNING 时定期刷新。 */
const POLL_MILLIS = 2000;

/**
 * 验证运行详情与证据（沙箱验证技术方案第 6 节）：结论、运行原因、出站地址（含沙箱标记）、配置版本，
 * 以及每一步的实际输入、结果、期望核对与节点轨迹（脱敏后的渠道请求与响应原文）。
 */
export function VerifyRunDetail({ integrationCode, runCode }: { integrationCode: string; runCode: string }) {
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
  if (!detail.data) return <LoadFailure title="运行记录加载失败" failure={detail.failure} retry={detail.refresh} />;
  const run = detail.data; const base = verifyRoute(integrationCode);
  const reason = typeof run.caseSnapshot.reason === 'string' ? run.caseSnapshot.reason : '—';
  return <>
    <PageHeader crumbs={[{ label: '外部 API' }, { label: '接入方', to: integrationRoute }, { label: integrationCode, to: `${integrationRoute}/${integrationCode}` },
      { label: '验证', to: `${integrationRoute}/${integrationCode}?tab=verify` }, { label: `运行 ${run.runCode}` }]}
      title={`运行 ${run.runCode}`} code={run.caseCode} />
    {running && <Alert type="info" showIcon title="运行在后台执行中，本页每 2 秒刷新。" />}
    {run.stale && !running && <Alert type="warning" showIcon title="这次运行之后，用例、验证账号或引用的配置已修改；本记录只代表运行当时的结果。" />}
    <div className="xapi-facts">
      <Field label="结论"><VerifyTag {...runStatusLabels[run.runStatus]} /></Field>
      <Field label="验证用例"><Link to={`${base}/verify-cases/${run.caseCode}`}><Mono>{run.caseCode}</Mono></Link> · {run.caseName}</Field>
      <Field label="运行原因">{reason}</Field>
      <Field label="发起">{formatTime(run.startTime)} · 账号 {run.startedBy}{run.finishTime && <> · 结束 {formatTime(run.finishTime)}</>}</Field>
    </div>
    {run.failureSummary && <Alert type="error" showIcon title={run.failureSummary} />}
    <Panel title="出站地址"><div className="xapi-table-wrap"><Table rowKey="endpointCode" size="small" pagination={false} dataSource={run.endpoints}
      locale={{ emptyText: '未加载配置（运行在执行前结束）' }} columns={[
        { title: '地址代码', width: 220, render: (_, row) => <Mono>{row.endpointCode}</Mono> },
        { title: '基础地址', ellipsis: true, render: (_, row) => <Mono>{row.baseUrl}</Mono> },
        { title: '沙箱', width: 90, render: (_, row) => row.sandbox ? <VerifyTag label="沙箱" tone="success" /> : <VerifyTag label="非沙箱" tone="error" /> },
      ]} /></div></Panel>
    <Collapse className="xapi-gap-top" items={[{ key: 'versions', label: '运行时的配置版本（判断是否需要重新验证）',
      children: <JsonBlock value={run.configVersions} empty="未加载配置" /> }]} />
    {run.steps.map(step => <StepEvidence key={step.stepNo} step={step} />)}
  </>;
}

function StepEvidence({ step }: { step: VerifyRunStep }) {
  const nodes = Array.isArray(step.trace?.nodes) ? step.trace.nodes as Record<string, unknown>[] : [];
  const initializers = Array.isArray(step.trace?.initializers) ? step.trace.initializers as Record<string, unknown>[] : [];
  return <Panel title={`步骤 ${step.stepNo} · ${step.stepKey}`} extra={<span className="xapi-ops">
    <Mono>{step.standardApiCode}</Mono><VerifyTag {...stepStatusLabels[step.stepStatus]} />
    {step.elapsedMs !== null && <span className="xapi-desc">{step.elapsedMs} ms</span>}</span>}>
    {step.stepStatus === 'SKIPPED' ? <p className="xapi-desc">前序步骤未通过，本步骤未执行。</p> : <>
      <div className="xapi-verify-step-view">
        <Field label="实际输入"><JsonBlock value={step.resolvedInput} /></Field>
        <Field label="实际结果"><JsonBlock value={step.result} /></Field>
      </div>
      <Field label="期望核对" className="xapi-gap-top"><div className="xapi-table-wrap">
        <Table<VerifyAssertion> rowKey={(_, at) => String(at)} size="small" pagination={false} dataSource={step.assertions} columns={[
          { title: '', width: 40, render: (_, row) => row.passed ? <span className="xapi-verify-ok">✓</span> : <span className="xapi-verify-bad">✗</span> },
          { title: '核对对象', width: 240, render: (_, row) => <Mono>{row.target}</Mono> },
          { title: '方式', width: 90, render: (_, row) => row.match },
          { title: '期望', render: (_, row) => row.expected === null || row.expected === undefined ? '—' : <Mono>{JSON.stringify(row.expected)}</Mono> },
          { title: '实际', render: (_, row) => row.actual === null || row.actual === undefined ? <span className="xapi-desc">不存在</span> : <Mono>{JSON.stringify(row.actual)}</Mono> },
        ]} /></div></Field>
      {(initializers.length > 0 || nodes.length > 0) && <Collapse className="xapi-gap-top" items={[
        ...initializers.map((item, at) => ({ key: `init-${at}`, label: <span>初始化 <Mono>{String(item.id)}</Mono> · {item.succeeded ? '成功' : `失败：${String(item.reason ?? '')}`}</span>,
          children: <JsonBlock value={item} /> })),
        ...nodes.map((node, at) => ({ key: `node-${at}`, label: <span>节点 <Mono>{String(node.id)}</Mono> · <Mono>{String(node.integrationApi ?? '')}</Mono> · {String(node.executeStatus ?? '')}
          {node.reason ? ` · ${String(node.reason)}` : ''}</span>, children: <Exchange node={node} /> })),
      ]} />}
      {typeof step.trace?.returnId === 'string' && <p className="xapi-desc xapi-gap-top">流程出口：<Mono>{step.trace.returnId}</Mono></p>}
    </>}
  </Panel>;
}

function Exchange({ node }: { node: Record<string, unknown> }) {
  const exchange = node.exchange as Record<string, Record<string, unknown>> | undefined;
  if (!exchange) return <p className="xapi-desc">节点在发出渠道请求之前结束。</p>;
  const request = exchange.request ?? {}; const response = exchange.response;
  return <div className="xapi-verify-exchange">
    <Field label={`请求 · ${String(request.method)} ${String(request.url)}`}>
      <JsonBlock value={request.headers} /><BodyBlock body={request.body} /></Field>
    <Field label={response ? `响应 · ${String(response.status)}` : `未收到响应 · ${String(exchange.error ?? '')}`}>
      {response && <><JsonBlock value={response.headers} /><BodyBlock body={response.body} /></>}</Field>
    <p className="xapi-desc">认证类 Header 与值来自凭据的 Header 已记为 [REDACTED]；签名 Header 保留。耗时 {String(exchange.elapsedMs)} ms。</p>
  </div>;
}

function BodyBlock({ body }: { body: unknown }) {
  const value = body as { text?: string; base64?: string; length?: number; truncated?: boolean } | undefined;
  if (!value) return null;
  if (value.base64 !== undefined) return <p className="xapi-desc">非文本内容 {value.length} 字节，前 4 KiB（Base64）：<Mono>{value.base64}</Mono></p>;
  let text = value.text ?? '';
  try { text = JSON.stringify(JSON.parse(text), null, 2); } catch { /* 原文展示 */ }
  return <>{text ? <pre className="xapi-json-block">{text}</pre> : <p className="xapi-desc">（空）</p>}
    {value.truncated && <p className="xapi-desc">报文超过 64 KiB，已截断（原长 {value.length} 字节）。</p>}</>;
}
