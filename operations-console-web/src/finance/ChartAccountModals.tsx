import { useMemo } from 'react';
import { Drawer, Empty, Form, Input, Radio, Select, Table, Tag, TreeSelect } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  accountClasses, buildAccountTree, chartAccountBody, chartAccountNoError, chartAccountUpdateBody, chartChangeFields,
  decodeChartAccountChange, decodeCreated, decodeMaintainedChartAccount, defaultNormalSide, financeErrorTarget, financePaths, localTime, pageOf,
} from './finance';
import type { AccountClass, ChartAccount, ChartAccountChange, ChartAccountFormValues, Side, TreeNode } from './finance';
import { CommandModal } from './FinanceModals';
import { FinanceNote, LoadFailure } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }
const formFields = ['parentAccountNo', 'accountNo', 'accountCode', 'accountName', 'accountClass', 'normalSide', 'postingAllowed'] as const;
const required = (message: string) => ({ required: true, whitespace: true, message });

/** 科目自身及其全部下级的主键：编辑时不能选为上级（避免循环）。 */
function subtreeIds(accounts: readonly ChartAccount[], rootId: string): Set<string> {
  const ids = new Set([rootId]);
  for (let grew = true; grew;) {
    grew = false;
    for (const item of accounts) if (item.parentChartAccountId && ids.has(item.parentChartAccountId) && !ids.has(item.chartAccountId)) { ids.add(item.chartAccountId); grew = true; }
  }
  return ids;
}

interface ParentOption { value: string; title: string; children?: ParentOption[] }

/** 上级候选：启用的汇总科目，按树展示「编号 名称」；排除的主键（编辑时自己与下级）不出现。 */
function parentTree(accounts: readonly ChartAccount[], excluded: ReadonlySet<string>): ParentOption[] {
  const candidates = accounts.filter(item => !item.postingAllowed && item.accountStatus === 'ACTIVE' && !excluded.has(item.chartAccountId) && item.level < 4);
  const toNode = (node: TreeNode<ChartAccount>): ParentOption => ({
    value: node.accountNo, title: `${node.accountNo} ${node.accountName}`, ...(node.children ? { children: node.children.map(toNode) } : {}),
  });
  return buildAccountTree(candidates).map(toNode);
}

/**
 * 新增 / 编辑科目弹窗（technique 2.5）
 * <li>新增：上级只列启用的汇总科目，选上级后编号带出上级前缀、类别锁定为上级类别；无上级即一级科目（4 位）。</li>
 * <li>编辑：代码与科目类型只读；科目表已被采用或科目有下级时编号与上级置灰并说明原因；名称任何时候可改。</li>
 */
export function ChartAccountFormModal({ chartCode, accounts, editing, parent, adopted, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  chartCode: string; accounts: readonly ChartAccount[]; editing?: ChartAccount; parent?: ChartAccount; adopted: boolean;
}) {
  const t = useT('finance');
  const [form] = Form.useForm<ChartAccountFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields);
  const classOptions = useMemo(() => accountClasses.map(value => ({ value, label: `${enumLabel(t, 'enum.accountClass', value)} ${value}` })), [t]);
  const sideOptions = useMemo(() => (['DEBIT', 'CREDIT'] as const).map(value => ({ value, label: `${enumLabel(t, 'enum.side', value)} ${value}` })), [t]);
  const byNo = useMemo(() => new Map(accounts.map(item => [item.accountNo, item])), [accounts]);
  const parentOf = (account: ChartAccount) => accounts.find(item => item.chartAccountId === account.parentChartAccountId);
  const excluded = useMemo(() => editing ? subtreeIds(accounts, editing.chartAccountId) : new Set<string>(), [accounts, editing]);
  const treeData = useMemo(() => parentTree(accounts, excluded), [accounts, excluded]);
  const hasChildren = !!editing && accounts.some(item => item.parentChartAccountId === editing.chartAccountId);
  const structureLocked = !!editing && (adopted || hasChildren);
  const parentNo = Form.useWatch('parentAccountNo', form) as string | undefined;
  const chosenParent = parentNo ? byNo.get(parentNo) : undefined;
  const initial: Partial<ChartAccountFormValues> = editing
    ? { ...editing, parentAccountNo: editing.parentChartAccountId ? parentOf(editing)?.accountNo : undefined }
    : parent ? { parentAccountNo: parent.accountNo, accountNo: parent.accountNo, accountClass: parent.accountClass, normalSide: parent.normalSide, postingAllowed: true }
      : { postingAllowed: true };

  /** 新增时换上级：编号改为新上级前缀、类别与方向取上级。 */
  function changeParent(next: string | undefined) {
    if (editing) return;
    const chosen = next ? byNo.get(next) : undefined;
    form.setFieldsValue({ accountNo: chosen?.accountNo ?? '', ...(chosen ? { accountClass: chosen.accountClass, normalSide: chosen.normalSide } : {}) });
  }
  async function submit() {
    const values = await form.validateFields();
    const path = editing ? financePaths.chartAccount(chartCode, editing.chartAccountId, 'update') : financePaths.chartAccounts(chartCode);
    const result = editing
      ? await command.run(path, chartAccountUpdateBody(values), decodeMaintainedChartAccount(editing.chartAccountId), t('chartAccount.updateFailed'))
      : await command.run(path, chartAccountBody(values), decodeCreated('chartAccountId'), t('chartAccount.createFailed'));
    if (result) onDone();
  }
  const lockReason = adopted ? t('chartAccount.lockAdopted') : hasChildren ? t('chartAccount.lockChildren') : undefined;
  return <CommandModal title={editing ? t('chartAccount.editTitle', { account: `${editing.accountNo} ${editing.accountName}` })
    : parent ? t('chartAccount.createChildTitle', { account: `${parent.accountNo} ${parent.accountName}` }) : t('charts.createAccount')}
    command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone} width={620}>
    <Form form={form} name="finance-chart-account" layout="vertical" disabled={command.saving || command.conflict} initialValues={initial}>
      <Form.Item name="parentAccountNo" label={t('chartAccount.parent')} extra={lockReason ?? t('chartAccount.parentHint')}>
        <TreeSelect allowClear showSearch treeDefaultExpandAll disabled={structureLocked} placeholder={t('chartAccount.parentPlaceholder')} treeData={treeData}
          treeNodeFilterProp="title" onChange={value => changeParent(value as string | undefined)} />
      </Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="accountNo" label={t('chartAccount.accountNo')} dependencies={['parentAccountNo', 'accountClass']}
          rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
            const message = chartAccountNoError(value, getFieldValue('accountClass'), getFieldValue('parentAccountNo'));
            if (message) throw new Error(message);
          } })]}>
          <Input maxLength={10} disabled={structureLocked} placeholder={chosenParent ? `${chosenParent.accountNo}01` : t('chartAccount.accountNoPlaceholder')} className="mono" /></Form.Item>
        <Form.Item name="accountCode" label={t('chartAccount.accountCode')} normalize={(value: string) => value?.toUpperCase()}
          extra={editing ? t('chartAccount.codeLocked') : t('chartAccount.codeHint')}
          rules={[required(t('chartAccount.codeRequired')), { pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('chartAccount.codePattern') }]}>
          <Input maxLength={64} disabled={!!editing} className="mono" /></Form.Item>
      </div>
      <Form.Item name="accountName" label={t('chartAccount.accountName')} rules={[required(t('chartAccount.nameRequired'))]}><Input maxLength={255} /></Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="accountClass" label={t('charts.column.class')} rules={[{ required: true, message: t('chartAccount.classRequired') }]}
          extra={chosenParent ? t('chartAccount.classFromParent') : undefined}>
          <Select options={classOptions} disabled={!!editing || !!chosenParent}
            onChange={(value: AccountClass) => form.setFieldValue('normalSide', defaultNormalSide(value) as Side)} /></Form.Item>
        <Form.Item name="normalSide" label={t('chartAccount.normalSide')} rules={[{ required: true, message: t('chartAccount.normalSideRequired') }]}>
          <Select options={sideOptions} disabled={!!editing} /></Form.Item>
      </div>
      <Form.Item name="postingAllowed" label={t('chartAccount.accountType')} extra={editing ? t('chartAccount.typeLocked') : t('chartAccount.typeHint')}>
        <Radio.Group disabled={!!editing} options={[{ value: false, label: t('charts.summary') }, { value: true, label: t('charts.leaf') }]} /></Form.Item>
    </Form>
    <FinanceNote>{t('chartAccount.note')}</FinanceNote>
  </CommandModal>;
}

/** 停用 / 启用确认：停用说明影响范围；被引用或上级已停用时由 ledger 返回原因并显示在弹窗顶部。 */
export function ToggleChartAccountModal({ chartCode, account, onClose, onDone, onAuthorizationChanged }: CommandProps & { chartCode: string; account: ChartAccount }) {
  const t = useT('finance');
  const command = useFinanceCommand(onAuthorizationChanged);
  const disabling = account.accountStatus === 'ACTIVE';
  async function submit() {
    const result = await command.run(financePaths.chartAccount(chartCode, account.chartAccountId, disabling ? 'disable' : 'enable'), undefined,
      decodeMaintainedChartAccount(account.chartAccountId), disabling ? t('chartAccount.disableFailed') : t('chartAccount.enableFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t(disabling ? 'chartAccount.disableTitle' : 'chartAccount.enableTitle', { account: `${account.accountNo} ${account.accountName}` })} command={command}
    okText={disabling ? t('chartAccount.confirmDisable') : t('chartAccount.confirmEnable')} onOk={() => void submit()} onClose={onClose} onDone={onDone} width={520}>
    <p>{disabling ? t('chartAccount.disableNotice') : t('chartAccount.enableNotice')}</p>
    <dl className="finance-detail-box">
      <dt>{t('field.accountCode')}</dt><dd><span className="mono">{account.accountNo}</span> · {account.accountCode}</dd>
      <dt>{t('ledger.lines.column.type')}</dt><dd>{account.postingAllowed ? t('charts.leaf') : t('charts.summary')} · {enumLabel(t, 'enum.accountClass', account.accountClass)}</dd>
    </dl>
  </CommandModal>;
}

/** 变更记录抽屉：按时间倒序，只列有变化的字段（变更前 → 变更后）。 */
export function ChartAccountChangesDrawer({ chartCode, account, onClose }: { chartCode: string; account: ChartAccount; onClose: () => void }) {
  const t = useT('finance');
  const decode = useMemo(() => pageOf(decodeChartAccountChange(account.chartAccountId)), [account.chartAccountId]);
  const page = usePaging<ChartAccountChange>(financePaths.chartChanges(chartCode), decode, { chartAccountId: account.chartAccountId });
  const hint = financeErrorTarget(page.failure)?.message;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden title={t('chartAccount.changesTitle', { account: `${account.accountNo} ${account.accountName}` })}>
    {page.error ? <LoadFailure message={hint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChartAccountChange> rowKey="changeId" size="small" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 580 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('chartAccount.changesEmpty')} /> }} columns={[
          { title: t('workflow.approvals.time'), width: 140, render: (_, row) => localTime(row.changeTime) },
          { title: t('workflow.approvals.actor'), width: 130, ellipsis: true, render: (_, row) => <span className="mono" title={row.operatorId}>{row.operatorId}</span> },
          { title: t('ledger.lines.column.type'), width: 70, render: (_, row) => <Tag>{enumLabel(t, 'enum.chartChangeType', row.changeType)}</Tag> },
          { title: t('chartAccount.beforeAfter'), render: (_, row) => chartChangeFields(row).map(field => <div key={field.label}>
            <span className="cell-meta">{field.label}</span> {field.before} → {field.after}</div>) },
        ]} />
    </section>}
  </Drawer>;
}
