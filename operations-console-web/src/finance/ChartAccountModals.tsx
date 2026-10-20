import { useMemo } from 'react';
import { Drawer, Empty, Form, Input, Radio, Select, Table, Tag, TreeSelect } from 'antd';
import { usePaging } from '../hooks/usePaging';
import {
  accountClasses, accountClassLabels, buildAccountTree, changeTypeLabels, chartAccountBody, chartAccountNoError, chartAccountUpdateBody, chartChangeFields,
  decodeChartAccountChange, decodeCreated, decodeMaintainedChartAccount, defaultNormalSide, financeErrorTarget, financePaths, localTime, pageOf, sideLabels,
} from './finance';
import type { AccountClass, ChartAccount, ChartAccountChange, ChartAccountFormValues, Side, TreeNode } from './finance';
import { CommandModal } from './FinanceModals';
import { FinanceNote, LoadFailure } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }
const formFields = ['parentAccountNo', 'accountNo', 'accountCode', 'accountName', 'accountClass', 'normalSide', 'postingAllowed'] as const;
const required = (message: string) => ({ required: true, whitespace: true, message });
const classOptions = accountClasses.map(value => ({ value, label: `${accountClassLabels[value]} ${value}` }));
const sideOptions = (['DEBIT', 'CREDIT'] as const).map(value => ({ value, label: `${sideLabels[value]} ${value}` }));

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
  const [form] = Form.useForm<ChartAccountFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields);
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
      ? await command.run(path, chartAccountUpdateBody(values), decodeMaintainedChartAccount(editing.chartAccountId), '修改科目失败')
      : await command.run(path, chartAccountBody(values), decodeCreated('chartAccountId'), '新增科目失败');
    if (result) onDone();
  }
  const lockReason = adopted ? '科目表已被账簿采用，编号与上级不可修改' : hasChildren ? '该科目有下级，需先移动下级后才能修改编号或上级' : undefined;
  return <CommandModal title={editing ? `编辑科目 · ${editing.accountNo} ${editing.accountName}` : parent ? `新增下级科目 · ${parent.accountNo} ${parent.accountName}` : '新增科目'}
    command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone} width={620}>
    <Form form={form} name="finance-chart-account" layout="vertical" disabled={command.saving || command.conflict} initialValues={initial}>
      <Form.Item name="parentAccountNo" label="上级科目" extra={lockReason ?? '只列启用的汇总科目；不选表示一级科目'}>
        <TreeSelect allowClear showSearch treeDefaultExpandAll disabled={structureLocked} placeholder="一级科目（无上级）" treeData={treeData}
          treeNodeFilterProp="title" onChange={value => changeParent(value as string | undefined)} />
      </Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="accountNo" label="科目编号" dependencies={['parentAccountNo', 'accountClass']}
          rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
            const message = chartAccountNoError(value, getFieldValue('accountClass'), getFieldValue('parentAccountNo'));
            if (message) throw new Error(message);
          } })]}>
          <Input maxLength={10} disabled={structureLocked} placeholder={chosenParent ? `${chosenParent.accountNo}01` : '如 2201'} className="mono" /></Form.Item>
        <Form.Item name="accountCode" label="科目代码" normalize={(value: string) => value?.toUpperCase()}
          extra={editing ? '代码创建后不可修改' : '大写字母开头，大写字母、数字与下划线'}
          rules={[required('请填写科目代码'), { pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写字母开头，只含大写字母、数字与下划线，最长 64 位' }]}>
          <Input maxLength={64} disabled={!!editing} className="mono" /></Form.Item>
      </div>
      <Form.Item name="accountName" label="科目名称" rules={[required('请填写科目名称')]}><Input maxLength={255} /></Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="accountClass" label="类别" rules={[{ required: true, message: '请选择类别' }]}
          extra={chosenParent ? '与上级科目类别相同' : undefined}>
          <Select options={classOptions} disabled={!!editing || !!chosenParent}
            onChange={(value: AccountClass) => form.setFieldValue('normalSide', defaultNormalSide(value) as Side)} /></Form.Item>
        <Form.Item name="normalSide" label="余额方向" rules={[{ required: true, message: '请选择余额方向' }]}>
          <Select options={sideOptions} disabled={!!editing} /></Form.Item>
      </div>
      <Form.Item name="postingAllowed" label="科目类型" extra={editing ? '科目类型创建后不可修改' : '末级科目可开户记账；汇总科目只汇总下级，可再建下级'}>
        <Radio.Group disabled={!!editing} options={[{ value: false, label: '汇总' }, { value: true, label: '末级' }]} /></Form.Item>
    </Form>
    <FinanceNote>编号 4-2-2-2：一级 4 位、每下一级加 2 位，最多 4 级；一级首位 1 资产、2 负债、3 权益、4 收入、5 费用。科目不审批、保存即生效，并记入变更记录。</FinanceNote>
  </CommandModal>;
}

/** 停用 / 启用确认：停用说明影响范围；被引用或上级已停用时由 ledger 返回原因并显示在弹窗顶部。 */
export function ToggleChartAccountModal({ chartCode, account, onClose, onDone, onAuthorizationChanged }: CommandProps & { chartCode: string; account: ChartAccount }) {
  const command = useFinanceCommand(onAuthorizationChanged);
  const disabling = account.accountStatus === 'ACTIVE';
  async function submit() {
    const result = await command.run(financePaths.chartAccount(chartCode, account.chartAccountId, disabling ? 'disable' : 'enable'), undefined,
      decodeMaintainedChartAccount(account.chartAccountId), disabling ? '停用科目失败' : '启用科目失败');
    if (result) onDone();
  }
  return <CommandModal title={`${disabling ? '停用' : '启用'}科目 ${account.accountNo} ${account.accountName}？`} command={command}
    okText={disabling ? '确认停用' : '确认启用'} onOk={() => void submit()} onClose={onClose} onDone={onDone} width={520}>
    <p>{disabling ? '停用后不能新建账户、不能被新规则引用，已有账户不受影响。仍被规则、结转规则、账户类型目录或启用中的下级引用的科目不能停用。'
      : '启用后可以新建账户并被新规则引用；上级科目已停用时不能启用。'}</p>
    <dl className="finance-detail-box">
      <dt>科目</dt><dd><span className="mono">{account.accountNo}</span> · {account.accountCode}</dd>
      <dt>类型</dt><dd>{account.postingAllowed ? '末级' : '汇总'} · {accountClassLabels[account.accountClass]}</dd>
    </dl>
  </CommandModal>;
}

/** 变更记录抽屉：按时间倒序，只列有变化的字段（变更前 → 变更后）。 */
export function ChartAccountChangesDrawer({ chartCode, account, onClose }: { chartCode: string; account: ChartAccount; onClose: () => void }) {
  const decode = useMemo(() => pageOf(decodeChartAccountChange(account.chartAccountId)), [account.chartAccountId]);
  const page = usePaging<ChartAccountChange>(financePaths.chartChanges(chartCode), decode, { chartAccountId: account.chartAccountId });
  const hint = financeErrorTarget(page.failure)?.message;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden title={`变更记录 · ${account.accountNo} ${account.accountName}`}>
    {page.error ? <LoadFailure message={hint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChartAccountChange> rowKey="changeId" size="small" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 580 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有变更记录" /> }} columns={[
          { title: '时间', width: 140, render: (_, row) => localTime(row.changeTime) },
          { title: '操作人', width: 130, ellipsis: true, render: (_, row) => <span className="mono" title={row.operatorId}>{row.operatorId}</span> },
          { title: '类型', width: 70, render: (_, row) => <Tag>{changeTypeLabels[row.changeType]}</Tag> },
          { title: '变更前 → 变更后', render: (_, row) => chartChangeFields(row).map(field => <div key={field.label}>
            <span className="cell-meta">{field.label}</span> {field.before} → {field.after}</div>) },
        ]} />
    </section>}
  </Drawer>;
}
