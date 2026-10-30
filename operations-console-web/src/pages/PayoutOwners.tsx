import { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Empty, Form, Input, Select, Space, Spin, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { enumLabel, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { actorText, AccountStatusBadge, BalanceCard, bankLine, CommandFailure, OwnerFilter, ReasonDialog } from '../payout/PayoutParts';
import {
  availableCommands, closeReasons, decodeAccount, decodeBalance, decodeOwnerAccounts, decodeRequirement, fieldIssues, hasAction, latestAction,
  payoutActions, payoutApi, payoutRoutes, railTypes, readOwnerQuery, submitFields, suspendReasons,
} from '../payout/payout';
import type { BeneficiaryAccount, OwnerAccounts, OwnerType, Requirement } from '../payout/payout';
import { usePayoutCommand } from '../payout/usePayoutCommand';
import '../payout/payout.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type Owner = { ownerType: OwnerType; ownerId: string };
type Command = 'suspend' | 'resume' | 'close';

/** Owner 账户与余额（O3）：按 Owner 查询余额解释与全部收款账户；停用 / 恢复 / 关闭（原因必选）与代提交。Owner 写入 URL。 */
export function PayoutOwners({ resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('payout'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate();
  const owner = readOwnerQuery(location.search);
  return <div className="configuration-page payout-page">
    <div className="page-heading payout-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_PAYOUT_OWNERS')}</h1>
      <OwnerFilter key={owner ? `${owner.ownerType}:${owner.ownerId}` : 'none'} value={owner} placeholder={t('owners.switchPlaceholder')}
        onApply={value => void navigate(value ? `?ownerType=${value.ownerType}&ownerId=${value.ownerId}` : '?')} /></div>
    {owner ? <OwnerView key={`${owner.ownerType}:${owner.ownerId}`} owner={owner} resources={resources} actorId={actorId}
      onAuthorizationChanged={onAuthorizationChanged} />
      : <Empty className="payout-owner-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('owners.searchHint')} />}
  </div>;
}

/** 一个 Owner 的标题、余额卡片与账户表。 */
function OwnerView({ owner, resources, actorId, onAuthorizationChanged }: { owner: Owner; resources: ConsoleResource[]; actorId: string;
  onAuthorizationChanged: () => void }) {
  const t = useT('payout');
  const query = `ownerType=${owner.ownerType}&ownerId=${owner.ownerId}`;
  const decodeAccounts = useMemo(() => decodeOwnerAccounts(owner.ownerType, owner.ownerId), [owner.ownerType, owner.ownerId]);
  const decodeBalances = useMemo(() => decodeBalance(owner.ownerType, owner.ownerId), [owner.ownerType, owner.ownerId]);
  const accounts = useDetail(`${payoutApi}/beneficiary-accounts?${query}`, decodeAccounts);
  const balances = useDetail(`${payoutApi}/balances?${query}`, decodeBalances);
  const [submitting, setSubmitting] = useState(false);
  const refresh = () => { accounts.refresh(); balances.refresh(); };
  const items = accounts.data?.items ?? [];
  return <>
    <OwnerTitle owner={owner} data={accounts.data} />
    <div className="payout-section-head"><h2>{t('balance.title')}</h2><span className="cell-meta">{t('balance.source')}</span></div>
    {balances.failure ? <QueryFailure failure={balances.failure} retry={balances.refresh} />
      : !balances.data ? <div className="content-loading"><Spin /></div>
        : balances.data.positions.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('balance.empty')} />
          : <div className="payout-balance-grid">{balances.data.positions.map(position =>
            <BalanceCard key={`${position.entityNo}:${position.currency}`} position={position} accounts={items} />)}</div>}
    <div className="payout-section-head"><h2>{t('owners.accountsTitle')}</h2>
      {hasAction(resources, payoutActions.submit) && <Button icon={<PlusOutlined />} onClick={() => setSubmitting(true)}>{t('owners.action.submit')}</Button>}</div>
    {accounts.failure ? <QueryFailure failure={accounts.failure} retry={accounts.refresh} />
      : <AccountTable data={accounts.data} loading={accounts.loading} canManage={hasAction(resources, payoutActions.manage)} actorId={actorId}
        onAuthorizationChanged={onAuthorizationChanged} onChanged={refresh} />}
    {submitting && <SubmitDrawer owner={owner} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setSubmitting(false)}
      onDone={() => { setSubmitting(false); refresh(); }} />}
  </>;
}

/** 标题：法定名 + Owner 类型与编号；副标题为主档编号与状态。主档不可用时只显示类型与编号。 */
function OwnerTitle({ owner, data }: { owner: Owner; data: OwnerAccounts | undefined }) {
  const t = useT('payout');
  const master = data?.owner;
  const typeId = t('owner.fallback', { type: enumLabel(t, 'enum.ownerType', owner.ownerType), id: owner.ownerId });
  return <div className="payout-owner-title">
    <div><h2>{master?.legalName ?? typeId}</h2>{master?.legalName && <Tag className="payout-owner-tag">{typeId}</Tag>}</div>
    <div className="cell-meta">{master ? t('owners.subtitle', { legalName: master.legalName ?? '—', ownerNo: master.ownerNo ?? '—',
      status: enumLabel(t, 'enum.ownerStatus', master.status) }) : data ? t('owners.masterUnavailable') : ''}</div>
  </div>;
}

/** 账户表：编号、银行与账号（默认标记）、币种、状态、最近动作与操作。 */
function AccountTable({ data, loading, canManage, actorId, onAuthorizationChanged, onChanged }: {
  data: OwnerAccounts | undefined; loading: boolean; canManage: boolean; actorId: string; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('payout'); const navigate = useNavigate();
  const [command, setCommand] = useState<{ account: BeneficiaryAccount; kind: Command } | null>(null);
  const labels = data?.operatorLabels ?? {};
  return <section className="table-panel payout-table">
    <Table<BeneficiaryAccount> rowKey="beneficiaryAccountId" size="middle" loading={loading} dataSource={data?.items ?? []} pagination={false}
      scroll={{ x: 960 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('owners.noAccounts')} /> }} columns={[
        { title: t('owners.column.code'), width: 110, dataIndex: 'accountCode' },
        { title: t('owners.column.bank'), width: 260, render: (_, row) => <span>{bankLine(t, row)}
          {row.defaultAccount && <Tag color="blue" className="payout-default-tag">{t('owners.default')}</Tag>}</span> },
        { title: t('owners.column.currency'), width: 90, dataIndex: 'currency' },
        { title: t('owners.column.status'), width: 220, render: (_, row) => <AccountStatusBadge account={row} /> },
        { title: t('owners.column.latest'), width: 260, render: (_, row) => latestText(t, row, labels, actorId) },
        { title: t('owners.column.actions'), width: 230, render: (_, row) => <Space size={4} wrap>
          {canManage && availableCommands(row).map(kind => <Button key={kind} size="small" type="link" danger={kind !== 'resume'}
            onClick={() => setCommand({ account: row, kind })}>{t(`owners.action.${kind}`)}</Button>)}
          <Button size="small" type="link" onClick={() => void navigate(`${payoutRoutes.reviews}?account=${row.beneficiaryAccountId}`)}>{t('owners.action.detail')}</Button>
        </Space> },
      ]} />
    {command && <CommandDialog account={command.account} kind={command.kind} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setCommand(null)} onDone={() => { setCommand(null); onChanged(); }} />}
  </section>;
}

/** 最近动作：时间、动作与操作人（运营账号显示名）。 */
function latestText(t: Translate, account: BeneficiaryAccount, labels: Record<string, string>, actorId: string) {
  const latest = latestAction(account);
  const actor = latest.actorId ? ` · ${actorText(t, labels, 'OPERATIONS', latest.actorId, actorId)}` : '';
  return `${formatDateTime(latest.time, { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })} ${enumLabel(t, 'enum.latestAction', latest.action)}${actor}`;
}

/** 停用 / 恢复 / 关闭：原因必选（恢复无原因），影响提示随命令变化。 */
function CommandDialog({ account, kind, onAuthorizationChanged, onClose, onDone }: {
  account: BeneficiaryAccount; kind: Command; onAuthorizationChanged: () => void; onClose: () => void; onDone: () => void;
}) {
  const t = useT('payout');
  const command = usePayoutCommand(onAuthorizationChanged);
  const reasons = kind === 'suspend' ? suspendReasons : kind === 'close' ? closeReasons : [];
  async function submit(reasonCode: string | null) {
    const result = await command.run(`${payoutApi}/beneficiary-accounts/${account.beneficiaryAccountId}/${kind}`,
      { ...(reasonCode ? { reasonCode } : {}), expectedVersion: account.version }, value => decodeAccount(value, account.beneficiaryAccountId),
      t(`commandDialog.${kind}.done`));
    if (result) onDone();
  }
  return <ReasonDialog title={t(`commandDialog.${kind}.title`)} reasons={reasons}
    reasonPrefix={kind === 'suspend' ? 'enum.suspendReason' : 'enum.closeReason'} danger={kind !== 'resume'} saving={command.saving}
    failure={command.failure} subject={t('commandDialog.subject', { code: account.accountCode, bank: bankLine(t, account), currency: account.currency })}
    notice={t(`commandDialog.${kind}.notice`)} okText={t(`commandDialog.${kind}.submit`)} onSubmit={reason => void submit(reason)} onClose={onClose} />;
}

/**
 * 代 Owner 提交：先选收款方式、开户国家与币种并读取字段要求，再按要求填写字段并就地校验
 * <li>原值只在表单内存中，提交后随抽屉关闭丢弃；payout 仍按字段要求重新校验。</li>
 */
function SubmitDrawer({ owner, onAuthorizationChanged, onClose, onDone }: { owner: Owner; onAuthorizationChanged: () => void;
  onClose: () => void; onDone: () => void }) {
  const t = useT('payout'), tCommon = useT('common');
  const [route, setRoute] = useState<{ railType: string; bankCountry: string; currency: string }>({ railType: 'LOCAL', bankCountry: '', currency: '' });
  const requirement = useRequirement(route);
  const [values, setValues] = useState<Record<string, string>>({});
  const [checked, setChecked] = useState(false);
  const command = usePayoutCommand(onAuthorizationChanged);
  const issues = requirement.data ? fieldIssues(requirement.data, values) : [];
  async function submit() {
    setChecked(true);
    if (!requirement.data || issues.length) return;
    const result = await command.run(`${payoutApi}/beneficiary-accounts`, { ownerType: owner.ownerType, ownerId: owner.ownerId, ...route,
      fields: submitFields(requirement.data, values) }, value => decodeAccount(value), t('submit.done'));
    if (result) onDone();
  }
  const upper = (value: string, length: number) => value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, length);
  return <Drawer open size="min(520px, 100vw)" title={t('submit.title')} onClose={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}
    footer={<div className="payout-dialog-foot"><Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={!requirement.data} onClick={() => void submit()}>{t('submit.submit')}</Button></Space></div>}>
    <p className="cell-meta">{t('submit.intro', { owner: t('owner.fallback', { type: enumLabel(t, 'enum.ownerType', owner.ownerType), id: owner.ownerId }) })}</p>
    <Form layout="vertical">
      <Form.Item label={t('submit.railType')}><Select value={route.railType} onChange={railType => { setRoute({ ...route, railType }); setValues({}); }}
        options={railTypes.map(code => ({ value: code, label: enumLabel(t, 'enum.railType', code) }))} /></Form.Item>
      <Space.Compact block>
        <Form.Item label={t('submit.bankCountry')} style={{ flex: 1 }}><Input value={route.bankCountry} maxLength={2} placeholder="AE"
          onChange={event => setRoute({ ...route, bankCountry: upper(event.target.value, 2) })} /></Form.Item>
        <Form.Item label={t('submit.currency')} style={{ flex: 1 }}><Input value={route.currency} maxLength={3} placeholder="USD"
          onChange={event => setRoute({ ...route, currency: upper(event.target.value, 3) })} /></Form.Item>
      </Space.Compact>
      {requirement.loading && <Spin />}
      {requirement.unsupported && <div className="payout-match is-mismatch">{t('submit.unsupported')}</div>}
      {requirement.data?.fields.map(field => {
        const issue = checked ? issues.find(item => item.code === field.code) : undefined;
        return <Form.Item key={field.code} required={field.required} label={enumLabel(t, 'enum.field', field.code)}
          validateStatus={issue ? 'error' : undefined} help={issue ? t(`submit.issue.${issue.issue}`) : field.oneOfGroup ? t('submit.oneOfHint') : undefined}>
          <Input value={values[field.code] ?? ''} maxLength={field.maxLength} autoComplete="off" spellCheck={false}
            onChange={event => setValues({ ...values, [field.code]: event.target.value })} /></Form.Item>;
      })}
    </Form>
    <div className="cell-meta">{t('submit.reviewNotice')}</div>
    <CommandFailure failure={command.failure} />
  </Drawer>;
}

/** 字段要求：三项都合法时读取；404 视为不支持的组合。 */
function useRequirement(route: { railType: string; bankCountry: string; currency: string }) {
  const [state, setState] = useState<{ data?: Requirement; loading: boolean; unsupported: boolean }>({ loading: false, unsupported: false });
  const ready = /^[A-Z]{2}$/.test(route.bankCountry) && /^[A-Z]{3}$/.test(route.currency);
  const path = `${payoutApi}/beneficiary-requirements?railType=${route.railType}&bankCountry=${route.bankCountry}&currency=${route.currency}`;
  useEffect(() => {
    if (!ready) { setState({ loading: false, unsupported: false }); return; }
    const controller = new AbortController(); setState({ loading: true, unsupported: false });
    session.request(path, { signal: controller.signal }, decodeRequirement)
      .then(data => { if (!controller.signal.aborted) setState({ data, loading: false, unsupported: false }); })
      .catch(failure => {
        if (!controller.signal.aborted) setState({ loading: false, unsupported: failure instanceof ApiError && failure.status === 404 });
      });
    return () => controller.abort();
  }, [path, ready]);
  return state;
}
