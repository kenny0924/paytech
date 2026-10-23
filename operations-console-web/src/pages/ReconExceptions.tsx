import { useState } from 'react';
import { Alert, Button, Form, Select, Table, Tag } from 'antd';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { ExceptionDetail } from '../reconciliation/detail/ExceptionDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  decodeWithId, exceptionStatusEnum, exceptionTypeEnum, hasReconAction, pageOf, reconActions, reconLabel, reconLayerEnum, reconOptions, reconPaths, sourceTypeEnum,
} from '../reconciliation/reconModel';
import type { ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, reconRoutes } from '../reconciliation/reconRoutes';
import { useCashAccounts, useClearingAccounts } from '../reconciliation/useReconData';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<ReconException>('exceptionId'));
const filterKeys = ['status', 'exceptionType', 'reconLayer', 'clearingAccountId', 'cashAccountId', 'statementId'] as const;

/**
 * 对账异常（UC-R15、R16、AC-V4）：按账龄排序的列表（默认只看未结），按类型、状态、清算账户、银行账户、账单筛选；点行进入详情页（/exceptions/:exceptionId）
 * <li>筛选与页码存于 URL 查询串（statementId 只能经链接带入）；旧链接 ?exception= 转到详情页。</li>
 */
export function ReconExceptions(props: Props) {
  const { exceptionId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('exception');
  if (exceptionId !== undefined) return <ExceptionDetail key={exceptionId} exceptionId={exceptionId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.exception(legacy!)} />;
  return <ExceptionList {...props} />;
}

function ExceptionList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('recon'), tNav = useT('nav');
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const cash = useCashAccounts(onAuthorizationChanged);
  const page = useReconListQuery<ReconException>(reconPaths.exceptions, decodePage, filterKeys);
  const [statementId, setStatementId] = useState<string | undefined>(page.filters.statementId);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const search = (values: Record<string, string | undefined>) => page.applyFilters(compactFilters({ ...values, statementId }));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_EXCEPTIONS')}</h1></div>
    {statementId && <Alert type="info" showIcon className="finance-banner" title={<>{t('exceptions.statementFilterBefore')}<Link className="mono" to={reconRoutes.statement(statementId)}>{statementId}</Link>{t('exceptions.statementFilterAfter')}</>}
      action={<Button size="small" onClick={() => { setStatementId(undefined); page.applyFilters(compactFilters({ ...form.getFieldsValue(), statementId: undefined })); }}>{t('exceptions.showAll')}</Button>} />}
    <Form form={form} name="recon-exception-filters" layout="vertical" initialValues={page.filters} onFinish={search}>
      <div className="finance-search">
        <Form.Item name="status" label={t('field.status')} extra={t('exceptions.statusHint')}><Select allowClear placeholder={t('exceptions.unresolved')} options={reconOptions(t, exceptionStatusEnum)} /></Form.Item>
        <Form.Item name="exceptionType" label={t('field.type')}><Select allowClear placeholder={t('placeholder.all')} showSearch={{ optionFilterProp: 'label' }} options={reconOptions(t, exceptionTypeEnum)} /></Form.Item>
        <Form.Item name="reconLayer" label={t('field.reconLayer')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, reconLayerEnum)} /></Form.Item>
        <Form.Item name="clearingAccountId" label={t('field.clearingAccount')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="cashAccountId" label={t('field.cashAccount')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={cash.loading}
          options={cash.items.map(item => ({ value: item.cashAccountId, label: t('cash.nameWithLast4', { name: item.accountName, last4: item.accountLast4 }) }))} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); search({}); }}>{t('common.reset')}</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ReconException> rowKey="exceptionId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1290 }}
        pagination={{ ...page.pagination, showTotal: total => t('exceptions.total', { total }) }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.exception(row.exceptionId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={t('exceptions.empty')} /> }} columns={[
          { title: t('field.age'), width: 80, align: 'right', render: (_, row) => t('common.days', { days: row.ageDays }) },
          { title: t('field.type'), width: 160, render: (_, row) => <>{reconLabel(t, exceptionTypeEnum, row.exceptionType)}{row.blocking && <Tag color="red" className="finance-self-tag">{t('summary.blocking')}</Tag>}</> },
          { title: t('field.reconLayer'), width: 130, render: (_, row) => reconLabel(t, reconLayerEnum, row.reconLayer) },
          { title: t('exception.source'), width: 200, render: (_, row) => <span>{reconLabel(t, sourceTypeEnum, row.sourceType)}<div className="cell-meta mono">{row.sourceId}</div></span> },
          { title: t('field.account'), width: 170, render: (_, row) => row.clearingAccountId ? clearing.byId.get(row.clearingAccountId)?.clearingAccountCode ?? row.clearingAccountId
            : row.cashAccountId ? (cash.byId.get(row.cashAccountId)?.accountName ?? row.cashAccountId) : '—' },
          { title: t('field.difference'), width: 130, align: 'right', render: (_, row) => <><Money value={row.differenceAmount} /> {row.currency}</> },
          { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.status} kind={exceptionStatusEnum} /> },
          { title: t('exceptions.openedAt'), width: 150, render: (_, row) => localTime(row.openedTime) },
        ]} />
    </section>}
    <FinanceNote>{t('exceptions.note')}{!canOperate && ' ' + t('permission.noOperate')}{!canApprove && ' ' + t('permission.noApprove')}</FinanceNote>
  </div>;
}
