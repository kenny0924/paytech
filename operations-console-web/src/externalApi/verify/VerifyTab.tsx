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
  caseStatusTag, decodeAccount, decodeAccounts, decodeCaseSummary, decodeRunSummary, runStatusTag, verifyPath,
} from './verifyModel';
import { workspaceRoutes } from '../../channel/channelWorkspace';
import type { VerifyAccountSummary, VerifyCaseStatus, VerifyCaseSummary, VerifyRunSummary } from './verifyModel';
import { StartRunDialog, VerifyTag } from './VerifyParts';
import { translator } from '../../i18n/index';
import { useT } from '../../i18n/react';
import { richText } from '../../i18n/richText';

const tVerify = translator('channel');

/** code 为渠道编码（管理接口路径），channelId 用于页面路由。 */
interface Props { channelId: string; code: string; canManage: boolean; onAuthorizationChanged: () => void }

const casePage = (value: unknown) => decodePage(value, decodeCaseSummary, item => item.caseCode);
const runPage = (value: unknown) => decodePage(value, decodeRunSummary, item => item.runCode);
const statusOrder: VerifyCaseStatus[] = ['PASSED', 'STALE', 'FAILED', 'RUNNING', 'NOT_RUN'];

/** 验证账号被哪些用例引用；有引用时不能删除。 */
function accountReferences(channelId: string, code: string, accountCode: string) {
  return () => session.request(`${verifyPath(code)}/verify-accounts/${accountCode}`, {}, value => decodeAccount(value, accountCode))
    .then(detail => detail.referencedCaseCodes.map<Reference>(caseCode => ({
      label: <>{richText(tVerify, 'verify.reference.case', { code: <Mono>{caseCode}</Mono> })}</>, to: workspaceRoutes(channelId).verifyCase(caseCode) })));
}

/**
 * 渠道详情「沙箱验证」页签（沙箱验证技术方案第 8 节）：按标准 API 汇总的验证状态、验证账号、验证用例与最近运行。
 * 运行真实调用渠道标记为沙箱的出站地址，结果与脱敏报文作为证据保存。
 */
export function VerifyTab({ channelId, code, canManage, onAuthorizationChanged }: Props) {
  const t = useT('channel'), tCommon = useT('common');
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
    <Panel title={t('verify.overview')} extra={<span className="xapi-desc">{t('verify.overviewHint')}</span>}>
      {overview.length === 0 ? <p className="xapi-desc">{t('verify.overviewEmpty')}</p>
        : <div className="xapi-verify-overview">{overview.map(item => <div key={item.standardApiCode} className="xapi-verify-overview-item">
          <Mono>{item.standardApiCode}</Mono>
          <span className="xapi-verify-counts">{statusOrder.filter(status => item.counts[status]).map(status =>
            <VerifyTag key={status} tone={caseStatusTag(status).tone} label={`${caseStatusTag(status).label} ${item.counts[status]}`} />)}</span>
        </div>)}</div>}
    </Panel>

    <div className="xapi-toolbar"><h2 className="xapi-section-title">{t('verify.accounts')}</h2>
      {canManage && <Button onClick={() => void navigate(workspaceRoutes(channelId).verifyAccount('new'))}>{t('verify.newAccount')}</Button>}</div>
    {accounts.failure ? <LoadFailure title={t('verify.accountsLoadFailed')} failure={accounts.failure} retry={accounts.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyAccountSummary> rowKey="accountCode" tableLayout="fixed" pagination={false} loading={accounts.loading}
        dataSource={accounts.data ?? []} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('verify.accountsEmpty')} /> }}
        columns={[
          { title: t('protocol.column.code'), width: 180, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyAccount(row.accountCode)}><Mono>{row.accountCode}</Mono></Link> },
          { title: t('form.name'), dataIndex: 'accountName', width: 160, ellipsis: true },
          { title: t('verify.channelAccountEntity'), ellipsis: true, render: (_, row) => <Mono>{row.channelCode} / {row.channelAccountId} / {row.entityNo}</Mono> },
          { title: t('channels.updatedAt'), width: 150, render: (_, row) => formatTime(row.updateTime) },
          { title: t('grants.actions'), width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={workspaceRoutes(channelId).verifyAccount(row.accountCode, 'edit')}>{t('protocol.edit')}</Link>}
            {canManage && <button type="button" className="xapi-link" onClick={() => setDeleting({ title: t('verify.subject.account'),
              objectType: 'INTEGRATION_VERIFY_ACCOUNT', objectCode: `${code}/${row.accountCode}`, version: row.version,
              path: `${api}/verify-accounts/${row.accountCode}`, references: accountReferences(channelId, code, row.accountCode) })}>{t('endpointEditor.delete')}</button>}</span> },
        ]} /></div>}

    <div className="xapi-toolbar"><h2 className="xapi-section-title">{t('verify.cases')}</h2>
      {canManage && <Button type="primary" disabled={!accounts.data?.length} title={accounts.data?.length ? undefined : t('verify.accountFirst')}
        onClick={() => void navigate(workspaceRoutes(channelId).verifyCase('new'))}>{t('verify.newCase')}</Button>}</div>
    {cases.error ? <LoadFailure title={t('verify.casesLoadFailed')} failure={cases.failure} retry={cases.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyCaseSummary> rowKey="caseCode" tableLayout="fixed" dataSource={cases.data.items} loading={cases.loading}
        pagination={{ ...cases.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('verify.casesEmpty')} /> }} columns={[
          { title: t('protocol.column.code'), width: 180, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyCase(row.caseCode)}><Mono>{row.caseCode}</Mono></Link> },
          { title: t('form.name'), dataIndex: 'caseName', width: 160, ellipsis: true },
          { title: t('verify.steps'), ellipsis: true, render: (_, row) => <span>{t('verify.case.steps', { count: row.stepCount })} · <Mono>{row.standardApiCodes.join(' → ')}</Mono></span> },
          { title: t('verify.status'), width: 120, render: (_, row) => <VerifyTag {...caseStatusTag(row.verifyStatus)} /> },
          { title: t('verify.latestRun'), width: 170, render: (_, row) => row.latestRun
            ? <Link to={workspaceRoutes(channelId).verifyRun(row.latestRun.runCode)}>{formatTime(row.latestRun.startTime)}</Link> : '—' },
          { title: t('grants.actions'), width: 130, render: (_, row) => <span className="xapi-ops">
            {canManage && <button type="button" className="xapi-link" disabled={row.verifyStatus === 'RUNNING'}
              onClick={() => setRunning(row.caseCode)}>{t('verify.run.ok')}</button>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: t('protocol.details'), onClick: () => void navigate(workspaceRoutes(channelId).verifyCase(row.caseCode)) },
              ...(canManage ? [
                { key: 'edit', label: t('protocol.edit'), onClick: () => void navigate(workspaceRoutes(channelId).verifyCase(row.caseCode, 'edit')) },
                { key: 'delete', label: t('endpointEditor.delete'), danger: true, onClick: () => setDeleting({ title: t('verify.subject.case'),
                  objectType: 'INTEGRATION_VERIFY_CASE', objectCode: `${code}/${row.caseCode}`, version: row.version,
                  path: `${api}/verify-cases/${row.caseCode}`, references: noReferences }) },
              ] : []),
            ] }}><button type="button" className="xapi-more" aria-label={t('protocol.more')}>···</button></Dropdown></span> },
        ]} /></div>}

    <div className="xapi-toolbar"><h2 className="xapi-section-title">{t('verify.latestRun')}</h2>
      <button type="button" className="xapi-link" onClick={() => { runs.refresh(); cases.refresh(); }}>{tCommon('action.refresh')}</button></div>
    {runs.error ? <LoadFailure title={t('verify.runsLoadFailed')} failure={runs.failure} retry={runs.refresh} /> : <div className="xapi-table-wrap">
      <Table<VerifyRunSummary> rowKey="runCode" tableLayout="fixed" dataSource={runs.data.items} loading={runs.loading}
        pagination={{ ...runs.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('verify.runsEmpty')} /> }} columns={[
          { title: t('verify.runCode'), width: 190, render: (_, row) => <Link className="xapi-link-code" to={workspaceRoutes(channelId).verifyRun(row.runCode)}><Mono>{row.runCode}</Mono></Link> },
          { title: t('verify.case'), width: 170, ellipsis: true, render: (_, row) => <Mono>{row.caseCode}</Mono> },
          { title: t('verify.result'), width: 100, render: (_, row) => <VerifyTag {...runStatusTag(row.runStatus)} /> },
          { title: t('verify.failureSummary'), ellipsis: true, render: (_, row) => row.failureSummary ?? '—' },
          { title: t('verify.startTime'), width: 150, render: (_, row) => formatTime(row.startTime) },
          { title: t('verify.startedBy'), width: 110, dataIndex: 'startedBy' },
        ]} /></div>}
    <Note items={[t('verify.note.sandbox'), t('verify.note.plaintext'), t('verify.note.stale')]} />
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); accounts.refresh(); cases.refresh(); }} />}
    {running && <StartRunDialog channelId={channelId} integrationCode={code} caseCode={running} onClose={() => setRunning(null)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
