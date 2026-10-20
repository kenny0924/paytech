import { useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Radio, Select, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { consoleRoutes } from '../commercial/commercialConsole';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, decodeProfile, decodeProfileList, hasButton, kindDimensions, profileBody, profileKindLabels, profileKinds,
  profileStatusLabels, profileStatuses,
} from '../commercial/commercial';
import type { PricingProfile, ProfileFormValues, ProfileKind } from '../commercial/commercial';
import { CommandModal, CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { useCommercialCommand } from '../commercial/useCommercialCommand';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: 'create' } | { kind: 'edit' | 'activate' | 'retire'; profile: PricingProfile };
const options = (labels: Readonly<Record<string, string>>, values: readonly string[]) => values.map(value => ({ value, label: `${labels[value]} ${value}` }));

/** 定价画像：按种类与状态筛选；草稿可编辑与启用，启用后冻结，只能退役。 */
export function CommercialPricingProfiles({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<{ kind?: string; status?: string }>({});
  const query = new URLSearchParams(Object.entries(filters).filter(([, value]) => value) as [string, string][]).toString();
  const list = useDetail(`${commercialPaths.profiles}${query ? `?${query}` : ''}`, decodeProfileList);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const canManage = hasButton(resources, commercialButtons.profileManage);
  const close = () => setDialog(null);
  const done = () => { setDialog(null); list.refresh(); };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>定价画像</h1>{canManage && <Button type="primary" onClick={() => setDialog({ kind: 'create' })}>新建画像</Button>}</div>
    <Form name="commercial-profile-filters" layout="vertical" initialValues={{ kind: '', status: '' }}
      onFinish={(values: { kind: string; status: string }) => setFilters({ kind: values.kind || undefined, status: values.status || undefined })}>
      <div className="commercial-open">
        <Form.Item name="kind" label="种类"><Select style={{ width: 200 }} options={[{ value: '', label: '全部' }, ...options(profileKindLabels, profileKinds)]} /></Form.Item>
        <Form.Item name="status" label="状态"><Select style={{ width: 160 }} options={[{ value: '', label: '全部' }, ...options(profileStatusLabels, profileStatuses)]} /></Form.Item>
        <Button htmlType="submit">查询</Button>
      </div>
    </Form>
    {list.loading && !list.data ? <Loading /> : list.failure || !list.data ? <QueryFailure failure={list.failure} retry={list.refresh} />
      : <section className="table-panel"><Table<PricingProfile> rowKey="pricingProfileId" tableLayout="fixed" dataSource={list.data} loading={list.loading}
        pagination={{ pageSize: 20, showTotal: total => `共 ${total} 条` }} scroll={{ x: 980 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有画像" /> }} columns={[
          { title: '代码 / 名称', render: (_, row) => <div className="commercial-cell"><span className="mono">{row.pricingProfileCode}</span>
            <span className="commercial-cell-meta">{row.profileName}</span></div> },
          { title: '种类', width: 130, render: (_, row) => profileKindLabels[row.profileKind] },
          { title: '支付方式', width: 120, render: (_, row) => row.paymentMethodCode ?? '—' },
          { title: '条件', render: (_, row) => row.conditions.length ? <div className="commercial-limits">{row.conditions.map(c =>
            <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)}</div> : <span className="commercial-cell-meta">无条件</span> },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={profileStatusLabels} /> },
          { title: '操作', width: 230, render: (_, row) => <div className="commercial-actions">
            {canManage && row.status === 'DRAFT' && <Button size="small" onClick={() => setDialog({ kind: 'edit', profile: row })}>编辑</Button>}
            {canManage && row.status === 'DRAFT' && <Button size="small" type="primary" onClick={() => setDialog({ kind: 'activate', profile: row })}>启用</Button>}
            {canManage && row.status === 'ACTIVE' && <Button size="small" danger onClick={() => setDialog({ kind: 'retire', profile: row })}>退役</Button>}
            <Button size="small" onClick={() => void navigate(consoleRoutes.history('PAYMENT_METHOD_PROFILE', row.pricingProfileId))}>历史</Button>
          </div> },
        ]} /></section>}
    <CommercialNote>画像全局复用：启用后种类、支付方式与条件永久冻结；退役只阻止新价格引用，已引用的价格不受影响。同维度多值为 OR，异维度为 AND。</CommercialNote>
    {(dialog?.kind === 'create' || dialog?.kind === 'edit') && <ProfileModal profile={dialog.kind === 'edit' ? dialog.profile : undefined} onDone={done}
      onClose={close} onAuthorizationChanged={onAuthorizationChanged} />}
    {(dialog?.kind === 'activate' || dialog?.kind === 'retire') && <ProfileTransitionModal profile={dialog.profile} action={dialog.kind} onDone={done}
      onClose={close} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function ProfileModal({ profile, onDone, onClose, onAuthorizationChanged }: { profile?: PricingProfile; onDone: () => void; onClose: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<ProfileFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'profile', form);
  const kind = (Form.useWatch('profileKind', form) ?? profile?.profileKind ?? 'PAYMENT_METHOD') as ProfileKind;
  const dimensions = useMemo(() => kindDimensions[kind].map(value => ({ value, label: value })), [kind]);
  const submit = async () => {
    const values = await form.validateFields();
    const result = profile
      ? await command.run(commercialPaths.profile(profile.pricingProfileId), 'PUT', profileBody({ ...values, profileKind: profile.profileKind }, profile.changeVersion), decodeProfile, '画像保存失败')
      : await command.run(commercialPaths.profiles, 'POST', profileBody(values), decodeProfile, '画像创建失败');
    if (result) onDone();
  };
  return <CommandModal title={profile ? `编辑画像 · ${profile.pricingProfileCode}` : '新建画像'} command={command} onOk={() => void submit()} onClose={onClose} onReload={onDone} width={680}>
    <Form form={form} name="commercial-profile" layout="vertical" initialValues={profile
      ? { profileKind: profile.profileKind, profileName: profile.profileName, paymentMethodCode: profile.paymentMethodCode ?? undefined, conditions: profile.conditions }
      : { profileKind: 'PAYMENT_METHOD', paymentMethodCode: 'CARD', conditions: [] }}>
      <div className="commercial-form-grid">
        <Form.Item name="pricingProfileCode" label="画像代码" extra="全局唯一，创建后不可修改" hidden={!!profile} normalize={(v: string) => v?.toUpperCase()}
          rules={profile ? [] : [{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写稳定代码，如 CARD_VISA_CREDIT' }]}><Input maxLength={64} /></Form.Item>
        <Form.Item name="profileName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="profileKind" label="种类"><Radio.Group disabled={!!profile} options={options(profileKindLabels, profileKinds)} /></Form.Item>
        {kind === 'PAYMENT_METHOD' && <Form.Item name="paymentMethodCode" label="支付方式代码" normalize={(v: string) => v?.toUpperCase()}
          rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写代码，如 CARD' }]}><Input maxLength={64} /></Form.Item>}
      </div>
      {kind !== 'PERIODIC' && <Form.List name="conditions">{(fields, { add, remove }) => <div className="commercial-row-list">
        {fields.map(field => <div className="commercial-row" key={field.key}>
          <Form.Item name={[field.name, 'dimensionCode']} rules={[{ required: true, message: '维度' }]}><Select options={dimensions} /></Form.Item>
          <Form.Item name={[field.name, 'conditionValue']} normalize={(v: string) => v?.toUpperCase()}
            rules={[{ required: true, pattern: /^[A-Z0-9][A-Z0-9_./-]{0,127}$/, message: '大写规范化值' }]}><Input placeholder="VISA / CREDIT / AE" /></Form.Item>
          <div />
          <Button onClick={() => remove(field.name)}>移除</Button>
        </div>)}
        <Button onClick={() => add({ dimensionCode: dimensions[0]?.value })} disabled={dimensions.length === 0}>添加条件</Button>
      </div>}</Form.List>}
    </Form>
  </CommandModal>;
}

function ProfileTransitionModal({ profile, action, onDone, onClose, onAuthorizationChanged }: {
  profile: PricingProfile; action: 'activate' | 'retire'; onDone: () => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const command = useCommercialCommand(onAuthorizationChanged, 'profile');
  const submit = async () => {
    const result = await command.run(commercialPaths.profileAction(profile.pricingProfileId, action), 'POST',
      { expectedChangeVersion: profile.changeVersion }, decodeProfile, action === 'activate' ? '启用失败' : '退役失败');
    if (result) onDone();
  };
  return <CommandModal title={`${action === 'activate' ? '启用' : '退役'}画像 · ${profile.pricingProfileCode}`} command={command}
    okText={action === 'activate' ? '启用' : '退役'} danger={action === 'retire'} onOk={() => void submit()} onClose={onClose} onReload={onDone} width={480}>
    <p>{action === 'activate' ? '启用后种类、支付方式与条件永久冻结，价格才能在排期时引用该画像。' : '退役后新价格不能再引用该画像；已有价格不受影响。'}</p>
  </CommandModal>;
}
