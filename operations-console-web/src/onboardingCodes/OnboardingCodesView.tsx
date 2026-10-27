import { useMemo, useState } from 'react';
import { Alert, Button, DatePicker, Empty, Form, Input, InputNumber, Modal, Segmented, Space, Table, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { CommandFailure, StatusDot, has, tone } from '../clientReview/reviewParts';
import { useReviewCommand } from '../clientReview/useReviewCommand';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { codeStatuses, codesApi, codesButton, decodeCode, decodeCodes, decodeIssuedCode, effectiveStatus, decodePartnerOptions, issueIssues, issuePayload, maxUsesLimit, noteLimit, usageText } from './onboardingCodes';
import type { IssuedCode, OnboardingCode, PartnerOption } from './onboardingCodes';
import '../clientReview/clientReview.css';
import '../onboardingConfig/onboardingConfig.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 入驻码：左侧 PayFac（OE_PROVIDER），右侧该 PayFac 的入驻码；签发后明文只展示一次，作废需原因。 */
export function OnboardingCodesView({ resources, onAuthorizationChanged }: Props) {
  const t = useT('client-review'), tNav = useT('nav'), tCommon = useT('common');
  const partners = useDetail(`${codesApi}/local-partners`, decodePartnerOptions);
  const [selected, setSelected] = useState<string>();
  const current = partners.data?.find(p => p.localPartnerId === selected) ?? partners.data?.[0];
  return <div className="obw-page">
    <div className="obw-head"><div className="obw-grow"><h1>{tNav('resource.OPERATIONS_ONBOARDING_CODES')}</h1><div className="obw-meta">{t('codes.intro')}</div></div>
      <Button onClick={partners.refresh} loading={partners.loading}>{tCommon('action.refresh')}</Button></div>
    {partners.loading && !partners.data ? <Loading /> : partners.failure || !partners.data ? <QueryFailure failure={partners.failure} retry={partners.refresh} />
      : partners.data.length === 0 ? <div className="obw-card"><Empty description={t('codes.noPartners')} /></div>
      : <div className="obc-split">
        <div className="obc-list">{partners.data.map(p => <button type="button" key={p.localPartnerId}
          className={`obc-item${p.localPartnerId === current?.localPartnerId ? ' is-selected' : ''}`} onClick={() => setSelected(p.localPartnerId)}>
          <span className="obw-grow"><span className="obc-name">{p.legalName ?? p.partnerNo}</span><span className="cell-meta">{p.partnerNo}</span></span>
          <StatusDot tone={tone(p.status)}>{enumLabel(t, 'codes.enum.partnerStatus', p.status)}</StatusDot></button>)}</div>
        {current && <PartnerCodes key={current.localPartnerId} partner={current} canManage={has(resources, codesButton)} onAuthorizationChanged={onAuthorizationChanged} />}
      </div>}
  </div>;
}

function PartnerCodes({ partner, canManage, onAuthorizationChanged }: { partner: PartnerOption; canManage: boolean; onAuthorizationChanged: () => void }) {
  const t = useT('client-review');
  const decode = useMemo(() => (value: unknown) => decodeCodes(value, partner.localPartnerId), [partner.localPartnerId]);
  const codes = useDetail(`${codesApi}/local-partners/${partner.localPartnerId}/onboarding-codes`, decode);
  const [status, setStatus] = useState<string>('ALL');
  const [issuing, setIssuing] = useState(false);
  const [issued, setIssued] = useState<IssuedCode | null>(null);
  const [revoking, setRevoking] = useState<OnboardingCode | null>(null);
  const now = Date.now();
  const rows = (codes.data ?? []).filter(c => status === 'ALL' || effectiveStatus(c, now) === status);
  const counts = Object.fromEntries(codeStatuses.map(s => [s, (codes.data ?? []).filter(c => effectiveStatus(c, now) === s).length]));
  return <div className="obw-main"><div className="obw-card">
    <div className="obw-card-title">{partner.legalName ?? partner.partnerNo}<span className="muted">{partner.partnerNo}</span><span className="obw-grow" />
      {canManage && partner.status === 'ACTIVE' && <Button type="primary" onClick={() => setIssuing(true)}>{t('codes.issue')}</Button>}</div>
    {partner.status !== 'ACTIVE' && <div className="obw-notice" style={{ marginBottom: 12 }}>{t('codes.partnerInactive')}</div>}
    <Segmented value={status} onChange={value => setStatus(String(value))} style={{ marginBottom: 12 }}
      options={[{ value: 'ALL', label: t('codes.all', { count: codes.data?.length ?? 0 }) }, ...codeStatuses.map(s => ({ value: s,
        label: `${enumLabel(t, 'codes.enum.status', s)} ${counts[s] ?? 0}` }))]} />
    {codes.failure ? <QueryFailure failure={codes.failure} retry={codes.refresh} /> :
      <Table<OnboardingCode> size="small" rowKey="codeId" loading={codes.loading} pagination={false} dataSource={rows} scroll={{ x: 900 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('codes.empty')} /> }} columns={[
          { title: t('codes.column.code'), width: 140, render: (_, c) => <span className="qualification-mono">{c.codeMasked}</span> },
          { title: t('codes.column.status'), width: 110, render: (_, c) => { const shown = effectiveStatus(c, now);
            return <StatusDot tone={tone(shown)}>{enumLabel(t, 'codes.enum.status', shown)}</StatusDot>; } },
          { title: t('codes.column.usage'), width: 130, render: (_, c) => usageText(c) },
          { title: t('codes.column.validity'), width: 260, render: (_, c) => t('codes.validity', { from: date(c.effectiveFrom), to: c.effectiveTo ? date(c.effectiveTo) : t('codes.noExpiry') }) },
          { title: t('codes.column.issued'), width: 220, render: (_, c) => <div>{date(c.issuedTime)}
            {c.issuedBy && <div className="cell-meta">{t('codes.issuedByAccount', { accountId: c.issuedBy })}</div>}</div> },
          { title: t('codes.column.note'), ellipsis: true, render: (_, c) => c.revokedTime ? t('codes.revokedAt', { time: date(c.revokedTime) }) : c.note ?? '—' },
          { title: '', width: 90, render: (_, c) => canManage && effectiveStatus(c, now) === 'ACTIVE' ? <Button size="small" danger onClick={() => setRevoking(c)}>{t('codes.revoke')}</Button> : null },
        ]} />}
  </div>
    {issuing && <IssueModal partner={partner} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setIssuing(false)}
      onIssued={code => { setIssuing(false); setIssued(code); codes.refresh(); }} />}
    {issued && <IssuedModal code={issued} partner={partner} onClose={() => setIssued(null)} />}
    {revoking && <RevokeModal code={revoking} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setRevoking(null)}
      onDone={() => { setRevoking(null); codes.refresh(); }} />}
  </div>;
}

function IssueModal({ partner, onClose, onIssued, onAuthorizationChanged }: { partner: PartnerOption; onClose: () => void; onIssued: (code: IssuedCode) => void; onAuthorizationChanged: () => void }) {
  const t = useT('client-review'), tCommon = useT('common');
  const [form] = Form.useForm<{ effectiveTo?: Dayjs | null; maxUses?: number | null; note?: string }>();
  const command = useReviewCommand(onAuthorizationChanged);
  async function submit(values: { effectiveTo?: Dayjs | null; maxUses?: number | null; note?: string }) {
    const raw = { effectiveTo: values.effectiveTo ? values.effectiveTo.toISOString() : null, maxUses: values.maxUses ?? null, note: values.note };
    if (issueIssues(raw).length) return;
    const result = await command.run(`${codesApi}/local-partners/${partner.localPartnerId}/onboarding-codes`, 'POST', issuePayload(raw),
      value => decodeIssuedCode(value, partner.localPartnerId), t('codes.issued'));
    if (result) onIssued(result);
  }
  return <Modal open title={t('codes.issueTitle', { partner: partner.legalName ?? partner.partnerNo })} footer={null} closable={!command.saving} maskClosable={false} onCancel={() => { if (!command.saving) onClose(); }}>
    <p className="cell-meta">{t('codes.issueIntro')}</p>
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="effectiveTo" label={t('codes.field.effectiveTo')} extra={t('codes.field.effectiveToHint')}
        rules={[{ validator: (_, value?: Dayjs | null) => !value || value.valueOf() > Date.now() ? Promise.resolve() : Promise.reject(new Error(t('codes.issue.pastExpiry'))) }]}>
        <DatePicker showTime style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="maxUses" label={t('codes.field.maxUses')} extra={t('codes.field.maxUsesHint')}><InputNumber min={1} max={maxUsesLimit} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="note" label={t('field.noteInternal')}><Input.TextArea rows={2} maxLength={noteLimit} showCount /></Form.Item>
      <CommandFailure failure={command.failure} />
      <div className="obw-modal-foot"><Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" htmlType="submit" loading={command.saving}>{t('codes.issue')}</Button></Space></div>
    </Form>
  </Modal>;
}

/** 明文码只在此展示一次；关闭后无法再次查看。 */
function IssuedModal({ code, partner, onClose }: { code: IssuedCode; partner: PartnerOption; onClose: () => void }) {
  const t = useT('client-review');
  return <Modal open title={t('codes.issuedTitle')} footer={<Button type="primary" onClick={onClose}>{t('codes.issuedDone')}</Button>} onCancel={onClose} maskClosable={false}>
    <p>{t('codes.issuedFor', { partner: partner.legalName ?? partner.partnerNo })}</p>
    <div className="obk-code"><Typography.Text copyable={{ text: code.code }} className="obk-code-text">{code.code}</Typography.Text></div>
    <Alert showIcon type="info" title={t('codes.onceOnly')} description={[usageText(code), code.effectiveTo ? t('codes.until', { time: date(code.effectiveTo) }) : t('codes.noExpiry')].join(' · ')} />
  </Modal>;
}

/** 作废确认：client-master 合同不收原因，只确认后作废。 */
function RevokeModal({ code, onClose, onDone, onAuthorizationChanged }: { code: OnboardingCode; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }) {
  const t = useT('client-review'), tCommon = useT('common');
  const command = useReviewCommand(onAuthorizationChanged);
  async function submit() {
    const result = await command.run(`${codesApi}/local-partners/${code.localPartnerId}/onboarding-codes/${code.codeId}/revocation`, 'POST', undefined,
      value => decodeCode(value, code.localPartnerId), t('codes.revoked'));
    if (result) onDone();
  }
  return <Modal open title={t('codes.revokeTitle', { code: code.codeMasked })} footer={null} closable={!command.saving} maskClosable={!command.saving} onCancel={() => { if (!command.saving) onClose(); }}>
    <p>{t('codes.revokeIntro', { used: code.useCount })}</p>
    <CommandFailure failure={command.failure} />
    <div className="obw-modal-foot"><Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button danger type="primary" loading={command.saving} onClick={() => void submit()}>{t('codes.revoke')}</Button></Space></div>
  </Modal>;
}
