import type { ReactNode } from 'react';
import { Empty, Table, Tag, Tooltip } from 'antd';
import { componentSummary, groupRowSpan, joinList, priceMatrix } from './commercial';
import type { AgreementCapability, MatrixRow, Price } from './commercial';
import { benchmarkDetail, benchmarkMark, groupPricesByCapability, relevanceText, sourceLabel } from './benchmark';
import type { PriceGroup } from './benchmark';
import { conditionTags } from './capabilityConditions';
import type { DimensionDefinition } from './capabilityConditions';
import type { CatalogCapability } from './commercialConsole';
import { CapabilityStatusTags } from './CommercialParts';
import type { RelevanceWarning } from './termReview';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

/** 分组依据：合同开通项、目录（支付方式代码）与 commercial 相关性提示；不传则不分组（Partner 条款、标准价目）。 */
export interface MatrixGrouping {
  capabilities: readonly AgreementCapability[]; catalog: readonly CatalogCapability[]; warnings: readonly RelevanceWarning[];
  definitions: readonly DimensionDefinition[];
}

/**
 * 价格矩阵：OE × 币种 × 画像 一行，每个计费事件一列；组件格显示摘要与破标标记（悬停看模板值），画像格显示来源标签。
 * <li>给出 grouping 时按开通项分组，每组一张表；挂不上开通项的价格单列「未对应开通项」并标红。</li>
 */
export function PriceMatrixView({ prices, settlement, grouping, templateLabel, emptyText }: {
  prices: readonly Price[]; settlement: boolean; grouping?: MatrixGrouping; templateLabel: string | null; emptyText: ReactNode;
}) {
  const t = useT('commercial');
  const matrix = priceMatrix(prices);
  const table = (rows: MatrixRow[]) => <Table<MatrixRow> rowKey="key" size="middle" pagination={false} dataSource={rows}
    scroll={{ x: 640 + matrix.events.length * 200 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} /> }}
    columns={[
      { title: 'OE', dataIndex: 'entityNo', width: 80, fixed: 'left', onCell: (_, index = 0) => ({ rowSpan: groupRowSpan(rows, index, row => row.entityNo) }) },
      { title: t('matrix.column.currency'), dataIndex: 'currency', width: 70, fixed: 'left',
        onCell: (_, index = 0) => ({ rowSpan: groupRowSpan(rows, index, row => `${row.entityNo}|${row.currency}`) }) },
      { title: t('matrix.column.profile'), width: 240, render: (_, row) => {
        const source = sourceLabel(row.price, templateLabel);
        return <div className="commercial-cell"><span className="cc-cell-line"><span className="mono">{row.pricingProfileCode}</span>
          {source && <Tooltip title={t('matrix.sourceTooltip', { id: row.price.sourceTemplatePriceId ?? '' })}><Tag color="geekblue" className="cc-source">{source}</Tag></Tooltip>}</span>
          <span className="commercial-cell-meta">{row.price.paymentMethodCode ?? '—'} · {t('matrix.priority', { priority: row.priorityNo })}
            {row.price.conditions.length ? ` · ${row.price.conditions.map(c => `${c.dimensionCode}=${c.conditionValue}`).join(', ')}` : ''}</span></div>;
      } },
      ...matrix.events.map(event => ({ title: event, key: event, width: 200, render: (_: unknown, row: MatrixRow) => row.events[event]
        ? <div className="commercial-cell">{row.events[event].map(component => {
          const mark = benchmarkMark(component.benchmark);
          return <span key={component.componentCode} className={`cc-cell-line${mark?.below ? ' cc-below' : ''}`}>
            <Tag>{component.componentCode}</Tag>{componentSummary(component)}
            {mark && <Tooltip title={benchmarkDetail(component.benchmark)}><Tag color={mark.color}>{mark.label}</Tag></Tooltip>}</span>;
        })}</div>
        : <span className="commercial-cell-meta">—</span> })),
      ...(settlement ? [{ title: t('matrix.column.settlement'), key: 'settlement', width: 150, render: (_: unknown, row: MatrixRow) => row.price.settlement
        ? `T+${row.price.settlement.settlementBusinessDays} · ${row.price.settlement.settlementTimezone}` : '—' }] : []),
    ]} />;
  if (!grouping || matrix.rows.length === 0) return table(matrix.rows);
  const groups = groupPricesByCapability(matrix.rows, grouping.capabilities, grouping.catalog, grouping.warnings);
  return <>{groups.map(group => <section key={group.key} className={`cc-group${group.unmatched ? ' is-unmatched' : ''}`}>
    <GroupHead group={group} definitions={grouping.definitions} />
    {table(group.rows)}
  </section>)}</>;
}

/** 分组标题：开通项（状态 + 接受条件）或「未对应开通项」（列出 commercial 提示）。 */
function GroupHead({ group, definitions }: { group: PriceGroup; definitions: readonly DimensionDefinition[] }) {
  const t = useT('commercial');
  return <div className="cc-group-head">
    <span className="cc-group-title">{group.unmatched ? '⚠ ' : ''}{group.title}</span>
    <Tag>{t('matrix.priceCount', { count: group.rows.length })}</Tag>
    {group.capability && <CapabilityStatusTags status={group.capability.status} executionStatus={group.capability.executionStatus} />}
    {group.capability && <span className="cc-tags">{conditionTags(group.capability, definitions).map(tag => <Tag key={tag}>{tag}</Tag>)}</span>}
    {group.unmatched && <span className="commercial-cell-meta">{group.warnings.length ? joinList(group.warnings.map(relevanceText), 'clauses')
      : t('matrix.unmatchedHint')}</span>}
  </div>;
}
