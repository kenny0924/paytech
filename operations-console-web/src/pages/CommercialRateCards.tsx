import { useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  capabilityStatusLabels, commercialPaths, componentSummary, currencyPattern, idPattern, localTime, termTypeLabels, utcInstant, versionStatusLabels,
} from '../commercial/commercial';
import type { CapabilityStatus, VersionStatus } from '../commercial/commercial';
import { capabilityLabel, cutoffTime, decodeRateCard, rateCardPath } from '../commercial/commercialPreview';
import type { AgreementRateCard, CurrencyPrices, ProfilePrice, RateCard, RateCardCapability, RateCardQuery, TermVersionRateCard } from '../commercial/commercialPreview';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface QueryForm { clientId: string; asOf?: string; currency?: string }

/** 费率预览：按 Client 读取 commercial 费率卡（与 Client 门户同源），按合同 → 版本 → 开通项 → 币种分块展示。 */
export function CommercialRateCards(_props: Props) {
  const [query, setQuery] = useState<RateCardQuery | null>(null);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>费率预览</h1></div>
    <Form name="commercial-rate-card-query" layout="vertical" onFinish={(values: QueryForm) => setQuery({ clientId: values.clientId.trim(),
      asOf: values.asOf ? utcInstant(values.asOf) : null, currency: values.currency?.trim() || null })}>
      <div className="commercial-open">
        <Form.Item name="clientId" label="Client ID" rules={[{ required: true, whitespace: true, message: '请输入 Client ID' },
          { pattern: idPattern, message: 'Client ID 是正十进制整数' }]}><Input allowClear maxLength={19} placeholder="例如 7300000000000000011" /></Form.Item>
        <Form.Item name="asOf" label="时点（可选，默认当前）"><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item name="currency" label="币种（可选）" normalize={(v: string) => v?.toUpperCase()}
          rules={[{ pattern: currencyPattern, message: '三位大写币种' }]}><Input allowClear maxLength={3} placeholder="USD" /></Form.Item>
        <Button type="primary" htmlType="submit">预览</Button>
      </div>
    </Form>
    {query ? <RateCardResult key={rateCardPath(commercialPaths.rateCards, query)} query={query} />
      : <CommercialNote>输入 Client ID 预览其 ACTIVE 合同在时点的费率卡：订阅产品、开通项（约束与保证金）、当前与已排期版本的价格。Client 选择器将在接入客户主档查询后提供。</CommercialNote>}
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
  return <>
    <dl className="commercial-summary">
      <div><dt>Client</dt><dd className="mono">{card.clientId}</dd></div>
      <div><dt>时点</dt><dd>{localTime(card.asOf)}</dd></div>
      <div><dt>币种</dt><dd>{card.currency ?? '全部'}</dd></div>
      <div><dt>生效合同</dt><dd>{card.agreements.length}</dd></div>
    </dl>
    {card.agreements.length === 0
      ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该 Client 在时点没有 ACTIVE 的 CLIENT 合同" />
      : card.agreements.map(agreement => <AgreementCard key={agreement.agreementId} agreement={agreement} />)}
    <CommercialNote>价格块内画像按具体度（条件维度数）降序、优先级升序排列，与 commercial 命中顺序一致；不含分润与平台服务费。</CommercialNote>
  </>;
}

function AgreementCard({ agreement }: { agreement: AgreementRateCard }) {
  const capabilities = new Map(agreement.capabilities.map(capability => [capability.agreementCapabilityId, capability]));
  return <section className="commercial-block">
    <div className="commercial-block-head"><h3>{agreement.agreementNo}</h3><Tag>{agreement.entityNo}</Tag>
      {agreement.businessMode && <Tag color="blue">{agreement.businessMode}</Tag>}
      <span className="commercial-cell-meta">生效 {localTime(agreement.effectiveFrom)} – {agreement.effectiveTo ? localTime(agreement.effectiveTo) : '长期'}</span>
      <span className="commercial-cell-meta">订阅 {agreement.products.join('、') || '—'}</span>
    </div>
    <h4 className="commercial-subtitle">开通项</h4>
    <Table<RateCardCapability> size="small" rowKey="agreementCapabilityId" pagination={false} dataSource={agreement.capabilities} scroll={{ x: 820 }}
      locale={{ emptyText: '没有开通项' }} columns={[
        { title: '开通项', render: (_, row) => <span className="mono">{capabilityLabel(row)}</span> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: '状态', width: 90, render: (_, row) => <StatusTag status={row.status} labels={capabilityStatusLabels as Record<CapabilityStatus, string>} /> },
        { title: '约束', render: (_, row) => <div className="commercial-limits">{row.limits.map(limit =>
          <Tag key={`${limit.limitCode}-${limit.limitValue}`}>{limit.limitCode}={limit.limitValue}</Tag>)}</div> },
        { title: '保证金', width: 140, render: (_, row) => row.rollingReserveRate ? `${row.rollingReserveRate} / ${row.rollingReserveDays} 天` : '—' },
      ]} />
    {agreement.termVersions.length === 0
      ? <CommercialNote tone="warning">该合同在时点没有适用或已排期的条款版本。</CommercialNote>
      : agreement.termVersions.map(version => <VersionBlock key={version.versionId} version={version} capabilities={capabilities} />)}
  </section>;
}

function VersionBlock({ version, capabilities }: { version: TermVersionRateCard; capabilities: Map<string, RateCardCapability> }) {
  return <div>
    <div className="commercial-section-head">
      <h2>{termTypeLabels[version.termType] ?? version.termType} · v{version.versionNo}</h2>
      <Tag color={version.timing === 'CURRENT' ? 'green' : 'blue'}>{version.timing === 'CURRENT' ? '当前' : '已排期'}</Tag>
      <StatusTag status={version.status} labels={versionStatusLabels as Record<VersionStatus, string>} />
      <span className="commercial-cell-meta">生效 {localTime(version.effectiveFrom)}{version.effectiveTo ? ` – ${localTime(version.effectiveTo)}` : ''}</span>
      <span className="commercial-cell-meta">{version.pricingModel} · {version.feeTaxTreatment}</span>
    </div>
    {version.capabilities.map(capability => <div key={capability.agreementCapabilityId}>
      <h4 className="commercial-subtitle">{capabilities.get(capability.agreementCapabilityId)
        ? capabilityLabel(capabilities.get(capability.agreementCapabilityId)!) : capability.capabilityCode}</h4>
      {capability.currencies.length === 0 ? <CommercialNote tone="warning">该开通项没有价格。</CommercialNote>
        : capability.currencies.map(block => <CurrencyBlock key={block.currency} block={block} />)}
    </div>)}
  </div>;
}

function CurrencyBlock({ block }: { block: CurrencyPrices }) {
  return <div className="commercial-currency">
    <strong>{block.currency}</strong>
    <Table<ProfilePrice> size="small" rowKey="priceId" pagination={false} dataSource={block.profiles} scroll={{ x: 900 }} columns={[
      { title: '画像', render: (_, row) => <div className="commercial-cell"><span className="mono">{row.pricingProfileCode}</span>
        <span className="commercial-cell-meta">{row.profileName ?? ''} · 具体度 {row.specificity} · 优先级 {row.priorityNo}</span></div> },
      { title: '条件', render: (_, row) => row.conditions.length ? <div className="commercial-limits">{row.conditions.map(c =>
        <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)}</div> : <span className="commercial-cell-meta">无条件（兜底）</span> },
      { title: '计费事件与组件', render: (_, row) => <div className="commercial-cell">{row.feeEvents.map(event =>
        <span key={event.feeEventCode}><span className="mono">{event.feeEventCode}</span>：{event.components.map(componentSummary).join(' + ')}</span>)}</div> },
      { title: '结算', width: 200, render: (_, row) => row.settlement ? <div className="commercial-cell">
        <span>T+{row.settlement.settlementBusinessDays} · {row.settlement.businessCalendarCode}</span>
        <span className="commercial-cell-meta">{row.settlement.settlementTimezone} 截止 {cutoffTime(row.settlement.cutoffSecondOfDay)}</span></div> : '—' },
    ]} />
  </div>;
}
