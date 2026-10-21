import { App, Button, Form, Input, Popconfirm, Select, Table } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { usePaging } from '../hooks/usePaging';
import { decodeWithId, directionLabels, hasReconAction, pageOf, platformTxnStatusLabels, reconActions, reconPaths } from '../reconciliation/reconModel';
import type { PlatformTxn, ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { useClearingAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<PlatformTxn>('platformTxnId'));
const statusOptions = Object.entries(platformTxnStatusLabels).map(([value, label]) => ({ value, label }));

/**
 * 平台流水（UC-R05、R17）：由 billing PAYMENT / REFUND 事实生成的待清算应收 / 应付；查询与人工开异常（D-02）
 * <li>人工开异常只给来源对象，异常类型（PLATFORM_ITEM_UNSETTLED）与差异金额由对账服务按未完成金额算出；开立后去「对账异常」处置。</li>
 */
export function ReconPlatformTxns({ resources, onAuthorizationChanged }: Props) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = usePaging<PlatformTxn>(reconPaths.platformTxns, decodePage);
  const command = useReconCommand(onAuthorizationChanged);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  async function openException(row: PlatformTxn) {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'PLATFORM_TXN', sourceId: row.platformTxnId },
      decodeWithId<ReconException>('exceptionId'), '开立异常失败');
    if (!result) return;
    void message.success(`已开立异常，差异 ${result.differenceAmount} ${result.currency}`);
    page.refresh();
    navigate(`/finance/reconciliation/exceptions?exception=${result.exceptionId}`);
  }
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>平台流水</h1></div>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); page.refresh(); }} />
    <Form form={form} name="recon-platform-filters" layout="vertical" onFinish={values => page.applyFilters(Object.fromEntries(
      Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) as Record<string, string>)}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={statusOptions} /></Form.Item>
        <Form.Item name="direction" label="方向"><Select allowClear placeholder="全部" options={[{ value: 'RECEIVABLE', label: '应收' }, { value: 'PAYABLE', label: '应付' }]} /></Form.Item>
        <Form.Item name="externalTransactionId" label="外部交易号"><Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="businessDateFrom" label="业务日起" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="businessDateTo" label="业务日止" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<PlatformTxn> rowKey="platformTxnId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1360 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <EmptyHint text="没有平台流水。billing 记录支付或退款事实后，对应清算账户下会出现待清算的应收或应付。" /> }}
        columns={[
          { title: '外部交易号', width: 200, render: (_, row) => <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> },
          { title: '清算账户', width: 150, render: (_, row) => clearing.byId.get(row.clearingAccountId)?.clearingAccountCode ?? row.clearingAccountId },
          { title: '方向', width: 80, render: (_, row) => directionLabels[row.direction] ?? row.direction },
          { title: '来源', width: 90, dataIndex: 'txnSource' },
          { title: '应清算', width: 120, align: 'right', render: (_, row) => <><Money value={row.expectedAmount} /> {row.currency}</> },
          { title: '已匹配', width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
          { title: '预估 / 实际费用', width: 140, align: 'right', render: (_, row) => <><Money value={row.expectedFee} /> / <Money value={row.actualFee} /></> },
          { title: '已结算', width: 110, align: 'right', render: (_, row) => <Money value={row.settledAmount} /> },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={platformTxnStatusLabels} /> },
          { title: '业务时间', width: 150, render: (_, row) => localTime(row.businessTime) },
          ...(canOperate ? [{ title: '', width: 110, fixed: 'right' as const, render: (_: unknown, row: PlatformTxn) => row.status === 'SETTLED' ? null
            : <Popconfirm title="为这笔平台流水开一个不阻塞的「平台流水未结算」异常，差异金额由系统算出。确认？" okText="开立" cancelText="取消"
              onConfirm={() => void openException(row)}><Button type="link" disabled={command.saving}>开异常</Button></Popconfirm> }] : []),
        ]} />
    </section>}
    <FinanceNote>平台流水只由 billing 事实生成，页面不能新增或修改；长期未结算的流水可开异常，经处置「接受时间差」或「归类 RECON_DIFFERENCE」结案。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
  </div>;
}
