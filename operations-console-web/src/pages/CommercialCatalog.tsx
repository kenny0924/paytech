import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Select, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import type { CatalogCapability } from '../commercial/commercialConsole';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { useCatalogCapabilities, useCatalogFeeEvents, useCatalogProducts } from '../commercial/useCatalog';
import {
  businessModeScopeLabels, capabilityGroupNote, capabilityKindLabels, catalogExpansion, catalogGaps, catalogKeys, catalogLabel, catalogSummary,
  catalogTermTypeLabels, catalogTermTypeOptions, eventGroupNote, groupCatalog, groupExpandedByDefault, productExpandedByDefault,
} from '../commercial/catalogTree';
import type { CatalogEventRow, CatalogProductNode, GroupNote } from '../commercial/catalogTree';
import '../configuration/configuration.css';
import '../commercial/commercial.css';
import '../commercial/catalog.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 条款类型筛选的「全部」值。 */
const allTermTypes = 'ALL';

/** 展开状态：用户点过的键覆盖默认规则；键按产品代码，筛选切换后保持。 */
function useExpansion() {
  const [overrides, setOverrides] = useState<Record<string, boolean>>({});
  const isOpen = (key: string, fallback: boolean) => overrides[key] ?? fallback;
  const set = (key: string, open: boolean) => setOverrides(current => ({ ...current, [key]: open }));
  return { isOpen, set, setAll: setOverrides };
}

/** 计数文字：筛选时为「命中 / 全部」。 */
function countText(matched: number, total: number, filtered: boolean): string {
  return filtered ? `${matched} / ${total}` : `${total}`;
}

/** 可折叠分组：标题行为 ▾ / ▸ + 名称 + 数量；数量为 0 时标题行写原因，不可展开。 */
function CatalogGroup({ title, count, note, open, onToggle, children }: {
  title: ReactNode; count: number; note: GroupNote | null; open: boolean; onToggle: () => void; children: ReactNode;
}) {
  const expandable = count > 0;
  const shown = open && expandable;
  const head = <>
    <span className="catalog-group-arrow" aria-hidden>{shown ? '▾' : '▸'}</span>
    <span className="catalog-group-title">{title}</span>
    {note && <span className={`catalog-group-note${note.warning ? ' is-warning' : ''}`}>· {note.text}</span>}
  </>;
  return <section className={`catalog-group${shown ? ' is-open' : ''}`}>
    {expandable ? <button type="button" className="catalog-group-head" aria-expanded={shown} onClick={onToggle}>{head}</button>
      : <div className="catalog-group-head is-static">{head}</div>}
    {shown && <div className="catalog-group-body">{children}</div>}
  </section>;
}

/** 可开通项子表。 */
function CapabilityTable({ items }: { items: CatalogCapability[] }) {
  return <Table<CatalogCapability> rowKey="productCapabilityId" size="small" pagination={false} dataSource={items} scroll={{ x: 760 }} columns={[
    { title: '能力码 / 名称', render: (_, row) => <div className="commercial-cell">
      <span className="mono catalog-code">{row.capabilityCode}</span><span className="commercial-cell-meta">{row.capabilityName}</span></div> },
    { title: '种类', width: 110, render: (_, row) => catalogLabel(capabilityKindLabels, row.capabilityKind) },
    { title: '支付方式', width: 130, render: (_, row) => row.paymentMethodCode ? <span className="mono">{row.paymentMethodCode}</span> : '—' },
    { title: '维度条件', width: 220, render: (_, row) => row.dimensionCode
      ? <span className="mono">{`${row.dimensionCode} = ${row.dimensionValue ?? ''}`}</span> : '—' },
    { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} /> },
  ]} />;
}

/** 计费事件子表：同产品同事件一行，条款类型为标签，命中筛选的标签高亮。 */
function EventTable({ rows, termType }: { rows: CatalogEventRow[]; termType?: string }) {
  return <Table<CatalogEventRow> rowKey="key" size="small" pagination={false} dataSource={rows} scroll={{ x: 680 }} columns={[
    { title: '事件代码', width: 200, render: (_, row) => <span className="mono catalog-code">{row.feeEventCode}</span> },
    { title: '可配价条款类型', render: (_, row) => <span className="catalog-tags">{row.termTypes.map(type =>
      <Tag key={type} color={type === termType ? 'blue' : undefined}>{catalogLabel(catalogTermTypeLabels, type)}</Tag>)}</span> },
    { title: '计费事实类型', width: 180, render: (_, row) => <span className="mono">{row.billingFactTypes.join(' / ')}</span> },
  ]} />;
}

/**
 * 产品目录（只读）：以产品为主线，展开后上下两组平级分组（可开通项 / 计费事件），数据来自 commercial 三个目录接口（随产品发版维护）。
 * <li>计费事件按产品 × 事件合并；条款类型筛选只作用于计费事件与主表，可开通项不受影响。</li>
 * <li>三接口任一失败整页显示统一失败，不显示半套；目录缺口在工具栏下方提示。</li>
 */
export function CommercialCatalog({ onAuthorizationChanged }: Props) {
  const products = useCatalogProducts(onAuthorizationChanged);
  const capabilities = useCatalogCapabilities(onAuthorizationChanged);
  const feeEvents = useCatalogFeeEvents(undefined, onAuthorizationChanged);
  const [selected, setSelected] = useState(allTermTypes);
  const expansion = useExpansion();
  const termType = selected === allTermTypes ? undefined : selected;
  const filtered = termType !== undefined;
  const all = useMemo(() => groupCatalog(products.items, capabilities.items, feeEvents.items),
    [products.items, capabilities.items, feeEvents.items]);
  const nodes = useMemo(() => termType ? groupCatalog(products.items, capabilities.items, feeEvents.items, termType) : all,
    [all, termType, products.items, capabilities.items, feeEvents.items]);
  const options = useMemo(() => catalogTermTypeOptions(feeEvents.items), [feeEvents.items]);
  const gaps = useMemo(() => catalogGaps(products.items, capabilities.items, feeEvents.items),
    [products.items, capabilities.items, feeEvents.items]);
  const loading = products.loading || capabilities.loading || feeEvents.loading;
  const heading = <div className="page-heading"><h1>产品目录</h1><Tag>只读 · 随产品发版维护</Tag></div>;
  if (products.failed || capabilities.failed || feeEvents.failed) {
    return <div className="configuration-page commercial-page">{heading}<QueryFailure failure={undefined} /></div>;
  }
  const total = catalogSummary(all);
  const hit = catalogSummary(nodes);
  const unregistered = filtered && options.find(option => option.termType === termType)?.eventCount === 0;
  const productKey = (node: CatalogProductNode) => catalogKeys.product(node.product.productCode);
  const renderGroups = (node: CatalogProductNode) => {
    const code = node.product.productCode;
    const capabilityKey = catalogKeys.capabilities(code); const eventKey = catalogKeys.events(code);
    const capabilityOpen = expansion.isOpen(capabilityKey, groupExpandedByDefault(node.capabilities.length));
    const eventOpen = expansion.isOpen(eventKey, groupExpandedByDefault(node.events.length));
    return <div className="catalog-expanded">
      <CatalogGroup title={`可开通项 ${node.capabilities.length}`} count={node.capabilities.length} note={capabilityGroupNote(node)}
        open={capabilityOpen} onToggle={() => expansion.set(capabilityKey, !capabilityOpen)}><CapabilityTable items={node.capabilities} /></CatalogGroup>
      <CatalogGroup title={`计费事件 ${countText(node.eventCount, node.totalEventCount, filtered)}`} count={node.events.length}
        note={eventGroupNote(node)} open={eventOpen} onToggle={() => expansion.set(eventKey, !eventOpen)}>
        <EventTable rows={node.events} termType={termType} /></CatalogGroup>
    </div>;
  };
  return <div className="configuration-page commercial-page catalog-page">
    {heading}
    <div className="catalog-toolbar">
      <span className="catalog-summary">
        {`${total.products} 个产品 · ${total.capabilities} 个可开通项 · ${total.events} 个计费事件`}
        {filtered && !loading && <span className="catalog-summary-hit">{` · 筛选命中 ${hit.products} 个产品 · ${hit.events} 个计费事件`}</span>}
      </span>
      <span className="commercial-spacer" />
      <label className="catalog-filter"><span>条款类型</span>
        <Select value={selected} onChange={setSelected} popupMatchSelectWidth={false} options={[{ value: allTermTypes, label: '全部' },
          ...options.map(option => ({ value: option.termType, label: `${option.label} ${option.eventCount}` }))]} /></label>
      <Button onClick={() => expansion.setAll(catalogExpansion(all, true))}>全部展开</Button>
      <Button onClick={() => expansion.setAll(catalogExpansion(all, false))}>全部收起</Button>
    </div>
    {!loading && gaps.length > 0 && <Alert type="warning" showIcon className="commercial-banner" title={`目录提示：${gaps
      .map(gap => `${gap.productCode} 有 ${gap.capabilityCount} 个可开通项但没有计费事件`).join('；')}，开通后不会产生费用。`} />}
    {!loading && unregistered && <Alert type="info" showIcon className="commercial-banner" title="该条款类型尚未登记计费事件，无法创建该类方案" />}
    <Table<CatalogProductNode> rowKey={productKey} size="middle" pagination={false} loading={loading} dataSource={loading ? [] : nodes}
      scroll={{ x: 900 }} rowClassName="clickable-row" locale={{ emptyText: filtered ? '没有命中该条款类型的产品' : '目录为空' }}
      expandable={{
        expandRowByClick: true, expandedRowRender: renderGroups,
        expandedRowKeys: nodes.filter(node => expansion.isOpen(productKey(node), productExpandedByDefault(node))).map(productKey),
        onExpand: (open, node) => expansion.set(productKey(node), open),
        expandIcon: ({ expanded, onExpand, record }) => <button type="button" className="catalog-expand" aria-expanded={expanded}
          aria-label={expanded ? '收起产品' : '展开产品'} onClick={event => { event.stopPropagation(); onExpand(record, event); }}>{expanded ? '▾' : '▸'}</button>,
      }}
      columns={[
        { title: '产品代码', width: 180, render: (_, row) => <span className="mono catalog-code">{row.product.productCode}</span> },
        { title: '名称', render: (_, row) => row.product.productName },
        { title: '业务模式', width: 110, render: (_, row) => catalogLabel(businessModeScopeLabels, row.product.businessModeScope) },
        { title: '基础产品', width: 100, render: (_, row) => row.product.baseProduct ? <Tag color="blue">默认具备</Tag> : <Tag>可订阅</Tag> },
        { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.product.status} /> },
        { title: '子项', width: 230, render: (_, row) => <span className={`catalog-counts${row.gap ? ' is-warning' : ''}`}>
          {row.gap && '⚠ '}{`可开通项 ${row.capabilities.length} · 计费事件 ${countText(row.eventCount, row.totalEventCount, filtered)}`}</span> },
      ]} />
    <CommercialNote>可开通项与计费事件是同一产品下的两组平级目录，彼此没有归属关系，只在价格里交叉：定价画像的条件对应可开通项的细分维度，价格组件的触发事件指向计费事件。目录来自 commercial 产品目录接口，与 commercial 初始化数据一致。</CommercialNote>
  </div>;
}
