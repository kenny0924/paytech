import { useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  commercialPaths, componentSummary, currencyPattern, idPattern, joinList, localTime, utcInstant, versionBasisLabel,
} from '../commercial/commercial';
import { capabilityLabel, cutoffTime, decodeRateCard, rateCardPath } from '../commercial/commercialPreview';
import type { AgreementRateCard, CurrencyPrices, ProfilePrice, RateCard, RateCardCapability, RateCardQuery, TermVersionRateCard } from '../commercial/commercialPreview';
import { conditionTags, require3dsTag, reserveText } from '../commercial/capabilityConditions';
import { ClientIdInput } from '../commercial/ClientIdInput';
import { browserStorage, rememberClient } from '../commercial/commercialConsole';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface QueryForm { clientId: string; asOf?: string; currency?: string }

/** 费率预览：按 Client 读取 commercial 费率卡（与 Client 门户同源），按合同 → 版本 → 开通项 → 币种分块展示。 */
export function CommercialRateCards(_props: Props) {
  const t = useT('commercial'), tNav = useT('nav');
  const [query, setQuery] = useState<RateCardQuery | null>(null);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_RATE_CARDS')}</h1></div>
    <Form name="commercial-rate-card-query" layout="vertical" onFinish={(values: QueryForm) => {
      rememberClient(browserStorage(), values.clientId);
      setQuery({ clientId: values.clientId.trim(), asOf: values.asOf ? utcInstant(values.asOf) : null, currency: values.currency?.trim() || null });
    }}>
      <div className="commercial-open">
        <Form.Item name="clientId" label="Client ID" rules={[{ required: true, whitespace: true, message: t('rateCards.clientIdRequired') },
          { pattern: idPattern, message: t('list.clientIdPattern') }]}><ClientIdInput placeholder={t('rateCards.clientIdPlaceholder')} /></Form.Item>
        <Form.Item name="asOf" label={t('rateCards.asOf')}><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item name="currency" label={t('rateCards.currency')} normalize={(v: string) => v?.toUpperCase()}
          rules={[{ pattern: currencyPattern, message: t('simulator.field.currencyPattern') }]}><Input allowClear maxLength={3} placeholder="USD" /></Form.Item>
        <Button type="primary" htmlType="submit">{t('rateCards.preview')}</Button>
      </div>
    </Form>
    {query ? <RateCardResult key={rateCardPath(commercialPaths.rateCards, query)} query={query} />
      : <CommercialNote>{t('rateCards.hint')}</CommercialNote>}
  </div>;
}

function RateCardResult({ query }: { query: RateCardQuery }) {
  const decode = useMemo(() => decodeRateCard(query.clientId), [query.clientId]);
  const card = useDetail(rateCardPath(commercialPaths.rateCards, query), decode);
  if (card.loading && !card.data) return <Loading />;
  if (card.failure || !card.data) return <QueryFailure failure={card.failure} retry={card.refresh} />;
  return <RateCardView card={card.data} />;
}

function RateCardView({ card }: { card: RateCard }) {
  const t = useT('commercial');
  return <>
    <dl className="commercial-summary">
      <div><dt>Client</dt><dd className="mono">{card.clientId}</dd></div>
      <div><dt>{t('rateCards.field.asOf')}</dt><dd>{localTime(card.asOf)}</dd></div>
      <div><dt>{t('matrix.column.currency')}</dt><dd>{card.currency ?? t('catalog.filter.all')}</dd></div>
      <div><dt>{t('enum.dashboardMetric.ACTIVE_AGREEMENTS')}</dt><dd>{card.agreements.length}</dd></div>
    </dl>
    {card.agreements.length === 0
      ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('rateCards.empty')} />
      : card.agreements.map(agreement => <AgreementCard key={agreement.agreementId} agreement={agreement} />)}
    <CommercialNote>{t('rateCards.note')}</CommercialNote>
  </>;
}

function AgreementCard({ agreement }: { agreement: AgreementRateCard }) {
  const t = useT('commercial');
  const capabilities = new Map(agreement.capabilities.map(capability => [capability.agreementCapabilityId, capability]));
  return <section className="commercial-block">
    <div className="commercial-block-head"><h3>{agreement.agreementNo}</h3><Tag>{agreement.entityNo}</Tag>
      {agreement.businessMode && <Tag color="blue">{agreement.businessMode}</Tag>}
      <span className="commercial-cell-meta">{t('rateCards.effectiveRange', { from: localTime(agreement.effectiveFrom),
        to: agreement.effectiveTo ? localTime(agreement.effectiveTo) : t('rateCards.indefinite') })}</span>
      <span className="commercial-cell-meta">{t('rateCards.subscribed', { products: joinList(agreement.products) || '—' })}</span>
    </div>
    <h4 className="commercial-subtitle">{t('rateCards.capabilities')}</h4>
    <Table<RateCardCapability> size="small" rowKey="agreementCapabilityId" pagination={false} dataSource={agreement.capabilities} scroll={{ x: 820 }}
      locale={{ emptyText: t('rateCards.noCapabilities') }} columns={[
        { title: t('rateCards.capability'), render: (_, row) => <span className="mono">{capabilityLabel(row)}</span> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: t('shared.column.status'), width: 90, render: (_, row) => <StatusTag status={row.status} kind="AgreementCapabilityStatus" /> },
        { title: t('productSections.column.conditions'), render: (_, row) => <div className="commercial-limits">{row.conditions.length || row.require3ds
          ? conditionTags(row).map(tag => <Tag key={tag} color={tag === require3dsTag() ? 'blue' : undefined}>{tag}</Tag>)
          : row.limits.map(limit => <Tag key={`${limit.limitCode}-${limit.limitValue}`}>{limit.limitCode}={limit.limitValue}</Tag>)}</div> },
        { title: t('rateCards.reserve'), width: 140, render: (_, row) => reserveText(row) },
      ]} />
    {agreement.termVersions.length === 0
      ? <CommercialNote tone="warning">{t('rateCards.noVersions')}</CommercialNote>
      : agreement.termVersions.map(version => <VersionBlock key={version.versionId} version={version} capabilities={capabilities} />)}
  </section>;
}

function VersionBlock({ version, capabilities }: { version: TermVersionRateCard; capabilities: Map<string, RateCardCapability> }) {
  const t = useT('commercial');
  return <div>
    <div className="commercial-section-head">
      <h2>{enumLabel(t, 'enum.termType', version.termType)} · v{version.versionNo}</h2>
      <Tag color={version.timing === 'CURRENT' ? 'green' : 'blue'}>{version.timing === 'CURRENT' ? t('agreement.detail.current') : enumLabel(t, 'enum.versionStatus', 'SCHEDULED')}</Tag>
      <StatusTag status={version.status} kind="versionStatus" />
      <span className="commercial-cell-meta">{version.effectiveTo
        ? t('rateCards.effectiveRange', { from: localTime(version.effectiveFrom), to: localTime(version.effectiveTo) })
        : t('rateCards.effectiveFrom', { from: localTime(version.effectiveFrom) })}</span>
      <span className="commercial-cell-meta">{versionBasisLabel(version.pricingModel, version.feeTaxTreatment)}</span>
    </div>
    {version.capabilities.map(capability => <div key={capability.agreementCapabilityId}>
      <h4 className="commercial-subtitle">{capabilities.get(capability.agreementCapabilityId)
        ? capabilityLabel(capabilities.get(capability.agreementCapabilityId)!) : capability.capabilityCode}</h4>
      {capability.currencies.length === 0 ? <CommercialNote tone="warning">{t('rateCards.noPrices')}</CommercialNote>
        : capability.currencies.map(block => <CurrencyBlock key={block.currency} block={block} />)}
    </div>)}
  </div>;
}

function CurrencyBlock({ block }: { block: CurrencyPrices }) {
  const t = useT('commercial');
  return <div className="commercial-currency">
    <strong>{block.currency}</strong>
    <Table<ProfilePrice> size="small" rowKey="priceId" pagination={false} dataSource={block.profiles} scroll={{ x: 900 }} columns={[
      { title: t('matrix.column.profile'), render: (_, row) => <div className="commercial-cell"><span className="mono">{row.pricingProfileCode}</span>
        <span className="commercial-cell-meta">{t('rateCards.profileMeta', { name: row.profileName ?? '', specificity: row.specificity, priority: row.priorityNo })}</span></div> },
      { title: t('profiles.column.conditions'), render: (_, row) => row.conditions.length ? <div className="commercial-limits">{row.conditions.map(c =>
        <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)}</div> : <span className="commercial-cell-meta">{t('rateCards.noConditions')}</span> },
      { title: t('rateCards.feeEvents'), render: (_, row) => <div className="commercial-cell">{row.feeEvents.map(event =>
        <span key={event.feeEventCode}><span className="mono">{event.feeEventCode}</span>{t('compare.fieldSeparator')}{event.components.map(componentSummary).join(' + ')}</span>)}</div> },
      { title: t('matrix.column.settlement'), width: 200, render: (_, row) => row.settlement ? <div className="commercial-cell">
        <span>T+{row.settlement.settlementBusinessDays} · {row.settlement.businessCalendarCode}</span>
        <span className="commercial-cell-meta">{t('rateCards.cutoff', { timezone: row.settlement.settlementTimezone, cutoff: cutoffTime(row.settlement.cutoffSecondOfDay) })}</span></div> : '—' },
    ]} />
  </div>;
}
