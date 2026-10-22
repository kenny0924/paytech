import { useMemo, useState } from 'react';
import { Button, Dropdown, Empty, Table } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../../api/session';
import { usePaging } from '../../hooks/usePaging';
import { DeleteDialog, LoadFailure, useDetail } from '../externalApiHooks';
import type { DeleteTarget, Reference } from '../externalApiHooks';
import { decodePage, listSizes, replaceQuery } from '../externalApiModel';
import { Mono, Note, Panel, formatTime } from '../ui';
import {
  caseStatusLabels, decodeAccount, decodeAccounts, decodeCaseSummary, decodeRunSummary, runStatusLabels, verifyPath,
} from './verifyModel';
import { workspaceRoutes } from '../../channel/channelWorkspace';
import type { VerifyAccountSummary, VerifyCaseStatus, VerifyCaseSummary, VerifyRunSummary } from './verifyModel';
import { StartRunDialog, VerifyTag } from './VerifyParts';

/** code 为渠道编码（管理接口路径），channelId 用于页面路由。 */
interface Props { channelId: string; code: string; canManage: boolean; onAuthorizationChanged: () => void }

const casePage = (value: unknown) => decodePage(value, decodeCaseSummary, item => item.caseCode);
const runPage = (value: unknown) => decodePage(value, decodeRunSummary, item => item.runCode);
const statusOrder: VerifyCaseStatus[] = ['PASSED', 'STALE', 'FAILED', 'RUNNING', 'NOT_RUN'];

/** 验证账号被哪些用例引用；有引用时不能删除。 */
function accountReferences(channelId: string, code: string, accountCode: string) {
  return () => session.request(`${verifyPath(code)}/verify-accounts/${accountCode}`, {}, value => decodeAccount(value, accountCode))
    .then(detail => detail.referencedCaseCodes.map<Reference>(caseCode => ({
      label: <>验证用例 · <Mono>{caseCode}</Mono></>, to: workspaceRoutes(channelId).verifyCase(caseCode) })));
}

/**
 * 渠道详情「沙箱验证」页签（沙箱验证技术方案第 8 节）：按标准 API 汇总的验证状态、验证账号、验证用例与最近运行。
 * 运行真实调用渠道标记为沙箱的出站地址，结果与脱敏报文作为证据保存。
 */
export function VerifyTab({ channelId, code, canManage, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const api = verifyPath(code);
  const accounts = useDetail<VerifyAccountSummary[]>(`${api}/verify-accounts`, decodeAccounts);
  const cases = usePaging<VerifyCaseSummary>(`${api}/verify-cases`, casePage, {}, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery(location.search, { tab: 'verify', page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const runs = usePaging<VerifyRunSummary>(`${api}/verify-runs`, runPage, {}, { initialPaging: { page: 1, size: 10 }, notifyOnError: false });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  const [running, setRunning] = useState<string | null>(null);
  const overview = useMemo(() => {
    const byApi = new Map<string, Partial<Record<VerifyCaseStatus, number>>>();
    for (const item of cases.data.items) {
      for (const apiCode of item.standardApiCodes) {
        const counts = byApi.get(apiCode) ?? {};
        counts[item.verifyStatus] = (counts[item.verifyStatus] ?? 0) + 1;
        byApi.set(apiCode, counts);
      }
    }
    return [...byApi.entries()].map(([standardApiCode, counts]) => ({ standardApiCode, counts }));
  }, [cases.data.items]);
  const noReferences = () => Promise.resolve<Reference[]>([]);
  return <>
    <Panel title="验证概览" extra={<span className="xapi-desc">按标准 API 汇总当前页用例的验证状态</span>}>
      {overview.length === 0 ? <p className="xapi-desc">还没有验证用例。先维护验证账号，再按步骤编写用例。</p>
        : <div className="xapi-verify-overview">{overview.map(item => <div key={item.standardApiCode} className="xapi-verify-overview-item">
          <Mono>{item.standardApiCode}</Mono>
          <span className="xapi-verify-counts">{statusOrder.filter(status => item.counts[status]).map(status =>
            <VerifyTag key={status} tone={caseStatusLabels[status].tone} label={`${caseStatusLabels[status].label} ${item.counts[status]}`} />)}</span>
        </div>)}</div>}
    </Panel>

    <div className="xapi-toolbar"><h2 className="xapi-section-title">验证账号</h2>
      {canManage && <Button onClick={() => void navigate(workspaceRoutes(channelId).verifyAccount('new'))}>新建验证账号</Button>}</div>
    {accounts.failure ? <LoadFailure title="验证账号加载失败" failure={accounts.failure} retry={accounts.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyAccountSummary> rowKey="accountCode" tableLayout="fixed" pagination={false} loading={accounts.loading}
        dataSource={accounts.data ?? []} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无验证账号" /> }}
        columns={[
          { title: '编码', width: 180, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyAccount(row.accountCode)}><Mono>{row.accountCode}</Mono></Link> },
          { title: '名称', dataIndex: 'accountName', width: 160, ellipsis: true },
          { title: '渠道 / 账号 / 主体', ellipsis: true, render: (_, row) => <Mono>{row.channelCode} / {row.channelAccountId} / {row.entityNo}</Mono> },
          { title: '更新时间', width: 150, render: (_, row) => formatTime(row.updateTime) },
          { title: '操作', width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={workspaceRoutes(channelId).verifyAccount(row.accountCode, 'edit')}>编辑</Link>}
            {canManage && <button type="button" className="xapi-link" onClick={() => setDeleting({ title: '验证账号',
              objectType: 'INTEGRATION_VERIFY_ACCOUNT', objectCode: `${code}/${row.accountCode}`, version: row.version,
              path: `${api}/verify-accounts/${row.accountCode}`, references: accountReferences(channelId, code, row.accountCode) })}>删除</button>}</span> },
        ]} /></div>}

    <div className="xapi-toolbar"><h2 className="xapi-section-title">验证用例</h2>
      {canManage && <Button type="primary" disabled={!accounts.data?.length} title={accounts.data?.length ? undefined : '请先新建验证账号'}
        onClick={() => void navigate(workspaceRoutes(channelId).verifyCase('new'))}>新建验证用例</Button>}</div>
    {cases.error ? <LoadFailure title="验证用例加载失败" failure={cases.failure} retry={cases.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyCaseSummary> rowKey="caseCode" tableLayout="fixed" dataSource={cases.data.items} loading={cases.loading}
        pagination={{ ...cases.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无验证用例" /> }} columns={[
          { title: '编码', width: 180, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyCase(row.caseCode)}><Mono>{row.caseCode}</Mono></Link> },
          { title: '名称', dataIndex: 'caseName', width: 160, ellipsis: true },
          { title: '步骤', ellipsis: true, render: (_, row) => <span>{row.stepCount} 步 · <Mono>{row.standardApiCodes.join(' → ')}</Mono></span> },
          { title: '验证状态', width: 120, render: (_, row) => <VerifyTag {...caseStatusLabels[row.verifyStatus]} /> },
          { title: '最近运行', width: 170, render: (_, row) => row.latestRun
            ? <Link to={workspaceRoutes(channelId).verifyRun(row.latestRun.runCode)}>{formatTime(row.latestRun.startTime)}</Link> : '—' },
          { title: '操作', width: 130, render: (_, row) => <span className="xapi-ops">
            {canManage && <button type="button" className="xapi-link" disabled={row.verifyStatus === 'RUNNING'}
              onClick={() => setRunning(row.caseCode)}>运行</button>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: '详情', onClick: () => void navigate(workspaceRoutes(channelId).verifyCase(row.caseCode)) },
              ...(canManage ? [
                { key: 'edit', label: '编辑', onClick: () => void navigate(workspaceRoutes(channelId).verifyCase(row.caseCode, 'edit')) },
                { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '验证用例',
                  objectType: 'INTEGRATION_VERIFY_CASE', objectCode: `${code}/${row.caseCode}`, version: row.version,
                  path: `${api}/verify-cases/${row.caseCode}`, references: noReferences }) },
              ] : []),
            ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown></span> },
        ]} /></div>}

    <div className="xapi-toolbar"><h2 className="xapi-section-title">最近运行</h2>
      <button type="button" className="xapi-link" onClick={() => { runs.refresh(); cases.refresh(); }}>刷新</button></div>
    {runs.error ? <LoadFailure title="运行记录加载失败" failure={runs.failure} retry={runs.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyRunSummary> rowKey="runCode" tableLayout="fixed" dataSource={runs.data.items} loading={runs.loading}
        pagination={{ ...runs.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行记录" /> }} columns={[
          { title: '运行编号', width: 190, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyRun(row.runCode)}><Mono>{row.runCode}</Mono></Link> },
          { title: '用例', width: 170, ellipsis: true, render: (_, row) => <Mono>{row.caseCode}</Mono> },
          { title: '结论', width: 100, render: (_, row) => <VerifyTag {...runStatusLabels[row.runStatus]} /> },
          { title: '失败摘要', ellipsis: true, render: (_, row) => row.failureSummary ?? '—' },
          { title: '开始时间', width: 150, render: (_, row) => formatTime(row.startTime) },
          { title: '发起账号', width: 110, dataIndex: 'startedBy' },
        ]} /></div>}
    <Note items={['验证只会调用标记为“沙箱”的出站地址；用到的地址未标记沙箱时，运行在发出任何请求前失败。',
      '验证账号的密钥按用户决定以明文保存，只用于验证运行，请勿录入生产密钥。',
      '运行记录永久保存；用例、账号或引用的配置在通过后发生变化时，状态显示为“需重新验证”。']} />
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); accounts.refresh(); cases.refresh(); }} />}
    {running && <StartRunDialog channelId={channelId} integrationCode={code} caseCode={running} onClose={() => setRunning(null)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
