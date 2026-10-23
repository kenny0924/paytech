import { useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Radio, Select, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { consoleRoutes } from '../commercial/commercialConsole';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialButtons, commercialPaths, decodeProfile, decodeProfileList, hasButton, kindDimensions, profileBody, profileKinds, profileStatuses,
} from '../commercial/commercial';
import type { PricingProfile, ProfileFormValues, ProfileKind } from '../commercial/commercial';
import { CommandModal, CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { useCommercialCommand } from '../commercial/useCommercialCommand';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: 'create' } | { kind: 'edit' | 'activate' | 'retire'; profile: PricingProfile };
const options = (t: Translate, kind: string, values: readonly string[]) => values.map(value => ({ value, label: `${enumLabel(t, 'enum.' + kind, value)} ${value}` }));

/** 定价画像：按种类与状态筛选；草稿可编辑与启用，启用后冻结，只能退役。 */
export function CommercialPricingProfiles({ resources, onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tNav = useT('nav');
  const navigate = useNavigate();
  const [filters, setFilters] = useState<{ kind?: string; status?: string }>({});
  const query = new URLSearchParams(Object.entries(filters).filter(([, value]) => value) as [string, string][]).toString();
  const list = useDetail(`${commercialPaths.profiles}${query ? `?${query}` : ''}`, decodeProfileList);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const canManage = hasButton(resources, commercialButtons.profileManage);
  const close = () => setDialog(null);
  const done = () => { setDialog(null); list.refresh(); };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_PRICING_PROFILES')}</h1></div>
    <Form name="commercial-profile-filters" layout="vertical" initialValues={{ kind: '', status: '' }}
      onFinish={(values: { kind: string; status: string }) => setFilters({ kind: values.kind || undefined, status: values.status || undefined })}>
      <div className="commercial-open">
        <Form.Item name="kind" label={t('catalogDrawer.kind')}><Select style={{ width: 200 }} options={[{ value: '', label: t('catalog.filter.all') }, ...options(t, 'profileKind', profileKinds)]} /></Form.Item>
        <Form.Item name="status" label={t('shared.column.status')}><Select style={{ width: 160 }} options={[{ value: '', label: t('catalog.filter.all') }, ...options(t, 'profileStatus', profileStatuses)]} /></Form.Item>
        <Button htmlType="submit">{t('list.search')}</Button>
        {canManage && <Button className="commercial-open-extra" type="primary" onClick={() => setDialog({ kind: 'create' })}>{t('profiles.create')}</Button>}
      </div>
    </Form>
    {list.loading && !list.data ? <Loading /> : list.failure || !list.data ? <QueryFailure failure={list.failure} retry={list.refresh} />
      : <section className="table-panel"><Table<PricingProfile> rowKey="pricingProfileId" tableLayout="fixed" dataSource={list.data} loading={list.loading}
        pagination={{ pageSize: 20, showTotal: total => t('list.total', { count: total }) }} scroll={{ x: 980 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('profiles.empty')} /> }} columns={[
          { title: t('profiles.column.codeName'), render: (_, row) => <div className="commercial-cell"><span className="mono">{row.pricingProfileCode}</span>
            <span className="commercial-cell-meta">{row.profileName}</span></div> },
          { title: t('catalogDrawer.kind'), width: 130, render: (_, row) => enumLabel(t, 'enum.profileKind', row.profileKind) },
          { title: t('catalog.column.paymentMethod'), width: 120, render: (_, row) => row.paymentMethodCode ?? '—' },
          { title: t('profiles.column.conditions'), render: (_, row) => row.conditions.length ? <div className="commercial-limits">{row.conditions.map(c =>
            <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)}</div> : <span className="commercial-cell-meta">{t('profiles.noConditions')}</span> },
          { title: t('shared.column.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="profileStatus" /> },
          { title: t('shared.column.actions'), width: 230, render: (_, row) => <div className="commercial-actions">
            {canManage && row.status === 'DRAFT' && <Button size="small" onClick={() => setDialog({ kind: 'edit', profile: row })}>{t('shared.edit')}</Button>}
            {canManage && row.status === 'DRAFT' && <Button size="small" type="primary" onClick={() => setDialog({ kind: 'activate', profile: row })}>{t('catalogStatus.activate')}</Button>}
            {canManage && row.status === 'ACTIVE' && <Button size="small" danger onClick={() => setDialog({ kind: 'retire', profile: row })}>{t('profiles.retire')}</Button>}
            <Button size="small" onClick={() => void navigate(consoleRoutes.history('PAYMENT_METHOD_PROFILE', row.pricingProfileId))}>{t('profiles.history')}</Button>
          </div> },
        ]} /></section>}
    <CommercialNote>{t('profiles.note')}</CommercialNote>
    {(dialog?.kind === 'create' || dialog?.kind === 'edit') && <ProfileModal profile={dialog.kind === 'edit' ? dialog.profile : undefined} onDone={done}
      onClose={close} onAuthorizationChanged={onAuthorizationChanged} />}
    {(dialog?.kind === 'activate' || dialog?.kind === 'retire') && <ProfileTransitionModal profile={dialog.profile} action={dialog.kind} onDone={done}
      onClose={close} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function ProfileModal({ profile, onDone, onClose, onAuthorizationChanged }: { profile?: PricingProfile; onDone: () => void; onClose: () => void; onAuthorizationChanged: () => void }) {
  const t = useT('commercial');
  const [form] = Form.useForm<ProfileFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'profile', form);
  const kind = (Form.useWatch('profileKind', form) ?? profile?.profileKind ?? 'PAYMENT_METHOD') as ProfileKind;
  const dimensions = useMemo(() => kindDimensions[kind].map(value => ({ value, label: value })), [kind]);
  const submit = async () => {
    const values = await form.validateFields();
    const result = profile
      ? await command.run(commercialPaths.profile(profile.pricingProfileId), 'PUT', profileBody({ ...values, profileKind: profile.profileKind }, profile.changeVersion), decodeProfile, t('profiles.saveFailed'))
      : await command.run(commercialPaths.profiles, 'POST', profileBody(values), decodeProfile, t('profiles.createFailed'));
    if (result) onDone();
  };
  return <CommandModal title={profile ? t('profiles.editTitle', { code: profile.pricingProfileCode }) : t('profiles.create')} command={command} onOk={() => void submit()} onClose={onClose} onReload={onDone} width={680}>
    <Form form={form} name="commercial-profile" layout="vertical" initialValues={profile
      ? { profileKind: profile.profileKind, profileName: profile.profileName, paymentMethodCode: profile.paymentMethodCode ?? undefined, conditions: profile.conditions }
      : { profileKind: 'PAYMENT_METHOD', paymentMethodCode: 'CARD', conditions: [] }}>
      <Form.Item name="profileKind" label={t('catalogDrawer.kind')} extra={profile ? t('profiles.kindFixed') : undefined}>
        <Radio.Group disabled={!!profile} optionType="button" options={profileKinds.map(value => ({ value, label: enumLabel(t, 'enum.profileKind', value), title: value }))} /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="pricingProfileCode" label={t('profiles.code')} extra={t('profiles.codeHint')} hidden={!!profile} normalize={(v: string) => v?.toUpperCase()}
          rules={profile ? [] : [{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('profiles.codePattern') }]}><Input maxLength={64} /></Form.Item>
        <Form.Item name="profileName" label={t('catalogDrawer.name')} rules={[{ required: true, whitespace: true, message: t('catalogDrawer.nameRequired') }]}><Input maxLength={128} /></Form.Item>
        {kind === 'PAYMENT_METHOD' && <Form.Item name="paymentMethodCode" label={t('catalogDrawer.paymentMethodCode')} normalize={(v: string) => v?.toUpperCase()}
          rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('simulator.field.paymentMethodPattern') }]}><Input maxLength={64} /></Form.Item>}
      </div>
      {kind !== 'PERIODIC' && <Form.List name="conditions">{(fields, { add, remove }) => <div className="commercial-row-list">
        {fields.map(field => <div className="commercial-row" key={field.key}>
          <Form.Item name={[field.name, 'dimensionCode']} rules={[{ required: true, message: t('simulator.dimension') }]}><Select options={dimensions} /></Form.Item>
          <Form.Item name={[field.name, 'conditionValue']} normalize={(v: string) => v?.toUpperCase()}
            rules={[{ required: true, pattern: /^[A-Z0-9][A-Z0-9_./-]{0,127}$/, message: t('profiles.valuePattern') }]}><Input placeholder="VISA / CREDIT / AE" /></Form.Item>
          <div />
          <Button onClick={() => remove(field.name)}>{t('componentTable.band.remove')}</Button>
        </div>)}
        <Button onClick={() => add({ dimensionCode: dimensions[0]?.value })} disabled={dimensions.length === 0}>{t('profiles.addCondition')}</Button>
      </div>}</Form.List>}
    </Form>
  </CommandModal>;
}

function ProfileTransitionModal({ profile, action, onDone, onClose, onAuthorizationChanged }: {
  profile: PricingProfile; action: 'activate' | 'retire'; onDone: () => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('commercial');
  const command = useCommercialCommand(onAuthorizationChanged, 'profile');
  const activate = action === 'activate';
  const submit = async () => {
    const result = await command.run(commercialPaths.profileAction(profile.pricingProfileId, action), 'POST',
      { expectedChangeVersion: profile.changeVersion }, decodeProfile, activate ? t('profiles.activateFailed') : t('profiles.retireFailed'));
    if (result) onDone();
  };
  return <CommandModal title={activate ? t('profiles.activateTitle', { code: profile.pricingProfileCode }) : t('profiles.retireTitle', { code: profile.pricingProfileCode })}
    command={command} okText={activate ? t('catalogStatus.activate') : t('profiles.retire')} danger={!activate} onOk={() => void submit()} onClose={onClose} onReload={onDone} width={480}>
    <p>{activate ? t('profiles.activateBody') : t('profiles.retireBody')}</p>
  </CommandModal>;
}
