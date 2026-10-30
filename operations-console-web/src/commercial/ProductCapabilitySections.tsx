import { useEffect, useRef, useState } from 'react';
import type { RefObject } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Empty, Space, Spin, Table, Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, MinusCircleOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { joinList, localTime } from './commercial';
import type { Agreement, AgreementCapability, CapabilityPrerequisites } from './commercial';
import { conditionTags, require3dsTag, reserveText } from './capabilityConditions';
import type { DimensionDefinition } from './capabilityConditions';
import {
  capabilityGateActions, effectiveExecutionStatus, isGateMember, lineCounts, prerequisiteLines, prerequisiteShortReason, sectionCounts,
} from './capabilityGate';
import type { PrerequisiteLine, PrerequisiteLink, PrerequisiteMark } from './capabilityGate';
import { CapabilityStatusTags, CommercialNote } from './CommercialParts';
import type { CatalogCapability, CatalogProduct } from './commercialConsole';
import { capabilityDisplayName, productFootnote, productSections, sectionTitle, showEntityColumn } from './productCapabilities';
import type { ProductSection } from './productCapabilities';
import type { CapabilityPrerequisitesState } from './useCapabilityPrerequisites';
import { channelRoutes } from '../channel/channel';
import { clientRoutes } from '../masterData/clients';
import { masterDataRoutes } from '../masterData/masterData';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

/** 行内前置门禁所需的状态与回调（由合同详情提供；核对状态在详情页持有，供「开通项生效」卡片共用）。 */
export interface CapabilityGateProps {
  /** 有合同查看权限：可展开「还差什么」与「检查前置」 */
  canView: boolean;
  prerequisites: CapabilityPrerequisitesState;
  onSuspend: (capability: AgreementCapability) => void;
  onResume: (capability: AgreementCapability) => void;
  /** 「去定价」：切到本合同的定价页签 */
  onGoPricing: () => void;
  /** 当前运营可打开 MID 页面：否则渠道身份不满足时不显示「去配置 MID」 */
  canViewMids: boolean;
}

/**
 * 合同详情「产品与开通」：每个已订阅产品一个区块（目录产品顺序），区内列该产品的开通项；归属不明的开通项进最后的「其他」区块。
 * <li>区头：产品名 + 数量摘要（开通项 / 已开通 / 等前置 / 暂停）、「上次检查」时间与「检查前置」（CLIENT 合同、有查看权限）、「+ 新增开通项」（canAdd）。</li>
 * <li>行：执行状态徽标（前置失效暂停下方小字原因；关闭的项显示成员资格「已关闭」）；行首折叠展开「还差什么」，展开即核对整份合同（一次调用）。</li>
 * <li>行操作：编辑 + 暂停 / 编辑 + 恢复（canEdit，只读用户不显示）。</li>
 * <li>没有订阅任何产品时显示订阅引导；合同还没有开通项时在区块上方提示；页底一行列出未订阅产品与默认具备的基础产品。</li>
 */
export function ProductCapabilitySections({ agreement, products, catalog, definitions, canAdd, canEdit, canSubscribe, onAdd, onEdit, onSubscribe, gate }: {
  agreement: Agreement; products: CatalogProduct[]; catalog: CatalogCapability[]; definitions: DimensionDefinition[]; canAdd: boolean; canEdit: boolean;
  canSubscribe: boolean; onAdd: (productCode: string) => void; onEdit: (capability: AgreementCapability) => void; onSubscribe: () => void;
  gate: CapabilityGateProps;
}) {
  const t = useT('commercial');
  const { prerequisites } = gate;
  const [expanded, setExpanded] = useState<string[]>([]);
  const runRef = useRef(prerequisites.run);
  runRef.current = prerequisites.run;
  // 展开中的行在结果缺失或过期（之后有写入）时自动核对一次；失败后等用户重试，不自动重复。
  const needsCheck = expanded.length > 0 && prerequisites.stale && !prerequisites.loading && !prerequisites.failure;
  useEffect(() => { if (needsCheck) void runRef.current(); }, [needsCheck]);
  const toggle = (capabilityId: string, open: boolean) =>
    setExpanded(keys => open ? [...new Set([...keys, capabilityId])] : keys.filter(key => key !== capabilityId));
  const sections = productSections(agreement.productCodes, agreement.capabilities, products, catalog);
  const footnote = productFootnote(agreement.productCodes, products);
  const entityColumn = showEntityColumn(agreement);
  if (!agreement.productCodes.length && !sections.length) {
    return <>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('productSections.noProducts')}>
        {canSubscribe && <Button type="primary" onClick={onSubscribe}>{t('productSections.changeSubscription')}</Button>}</Empty>
      {footnote && <div className="commercial-cell-meta cc-product-footnote">{footnote}</div>}
    </>;
  }
  const row: RowGate = { agreement, canEdit, gate, expanded, toggle };
  return <>
    {agreement.capabilities.length === 0 && <CommercialNote>{t('productSections.noCapabilities')}</CommercialNote>}
    {sections.map(section => <ProductBlock key={section.key} section={section} entityColumn={entityColumn} catalog={catalog} definitions={definitions}
      canAdd={canAdd && !section.other && !!section.productCode} onAdd={onAdd} onEdit={onEdit} row={row} />)}
    {footnote && <div className="commercial-cell-meta cc-product-footnote">{footnote}</div>}
  </>;
}

/** 区块内每行共用的门禁上下文。 */
interface RowGate {
  agreement: Agreement; canEdit: boolean; gate: CapabilityGateProps; expanded: string[]; toggle: (capabilityId: string, open: boolean) => void;
}

/** 一个产品区块：区头（名称 + 代码、数量摘要、上次检查、检查前置、新增）与该产品的开通项表；空产品显示提示与可开通项名称。 */
function ProductBlock({ section, entityColumn, catalog, definitions, canAdd, onAdd, onEdit, row }: {
  section: ProductSection; entityColumn: boolean; catalog: CatalogCapability[]; definitions: DimensionDefinition[]; canAdd: boolean;
  onAdd: (productCode: string) => void; onEdit: (capability: AgreementCapability) => void; row: RowGate;
}) {
  const t = useT('commercial');
  const { agreement, canEdit, gate } = row;
  const { prerequisites } = gate;
  const actions = (capability: AgreementCapability) => capabilityGateActions(agreement,
    { status: capability.status, executionStatus: effectiveExecutionStatus(capability, prerequisites.fresh) }, gate.canView, canEdit);
  const counts = sectionCounts(section.capabilities, prerequisites.fresh);
  const checkable = gate.canView && section.capabilities.some(capability => isGateMember(agreement, capability));
  const check = () => {
    for (const capability of section.capabilities) if (actions(capability).expandable && effectiveExecutionStatus(capability, prerequisites.fresh) !== 'ENABLED')
      row.toggle(capability.agreementCapabilityId, true);
    void prerequisites.run();
  };
  const name = (item: AgreementCapability) => capabilityDisplayName(item.capabilityCode, catalog);
  return <section className={`commercial-block cc-product-block${section.other ? ' is-other' : ''}`}>
    <div className="commercial-block-head"><h3>{sectionTitle(section)}</h3>
      <span className="commercial-cell-meta cc-section-counts">{t('productSections.summary', { ...counts })}</span>
      <span className="commercial-spacer" />
      {checkable && prerequisites.checkedAt && <span className="commercial-cell-meta">{t('productSections.lastChecked', { time: clockTime(prerequisites.checkedAt) })}</span>}
      {checkable && <Button size="small" icon={<SyncOutlined />} loading={prerequisites.loading} onClick={check}>{t('prerequisite.action.check')}</Button>}
      {canAdd && section.productCode && <Button size="small" type={section.capabilities.length ? 'default' : 'primary'}
        onClick={() => onAdd(section.productCode!)}>{t('productSections.add')}</Button>}</div>
    {section.capabilities.length === 0
      ? <Alert type="warning" showIcon title={t('productSections.noneEnabled')}
        description={section.availableNames.length ? t('productSections.available', { names: joinList(section.availableNames) }) : t('productSections.noneAvailable')} />
      : <Table<AgreementCapability> rowKey="agreementCapabilityId" size="middle" pagination={false} dataSource={section.capabilities}
        scroll={{ x: entityColumn ? 1180 : 1090 }}
        rowClassName={item => row.expanded.includes(item.agreementCapabilityId) ? 'cc-row-expanded' : ''}
        expandable={{
          expandedRowKeys: row.expanded.filter(key => section.capabilities.some(item => item.agreementCapabilityId === key)),
          onExpand: (open, capability) => row.toggle(capability.agreementCapabilityId, open),
          rowExpandable: capability => actions(capability).expandable,
          expandedRowRender: capability => <PrerequisitePanel agreement={agreement} capability={capability}
            checked={prerequisites.fresh.get(capability.agreementCapabilityId)} prerequisites={prerequisites} onGoPricing={gate.onGoPricing}
            canViewMids={gate.canViewMids} />,
        }}
        columns={[
          { title: t('productSections.column.capability'), width: 190, render: (_, item) => <div className="commercial-cell">
            <span className="mono cc-capability-code">{item.capabilityCode}</span>{name(item) && <span className="commercial-cell-meta">{name(item)}</span>}</div> },
          ...(entityColumn ? [{ title: 'OE', dataIndex: 'entityNo', width: 90 }] : []),
          { title: t('productSections.column.conditions'), render: (_, item) => <div className="commercial-limits">{conditionTags(item, definitions).map(tag =>
            <Tag key={tag} color={tag === require3dsTag() ? 'blue' : undefined}>{tag}</Tag>)}</div> },
          { title: t('productSections.column.reserve'), width: 130, render: (_, item) => reserveText(item) },
          { title: t('shared.column.effectivePeriod'), width: 200, render: (_, item) => t('shared.period', { from: localTime(item.effectiveFrom),
            to: item.effectiveTo ? localTime(item.effectiveTo) : t('shared.openEnded') }) },
          { title: t('productSections.column.executionStatus'), width: 170, render: (_, item) =>
            <CapabilityStatusTags status={item.status} executionStatus={effectiveExecutionStatus(item, prerequisites.fresh)}
              reason={lapsedReason(agreement, item, prerequisites.fresh.get(item.agreementCapabilityId))} /> },
          ...(canEdit ? [{ title: t('shared.column.actions'), width: 120, render: (_: unknown, item: AgreementCapability) => {
            const allowed = actions(item);
            return <Space size={0} wrap>
              <Button type="link" size="small" onClick={() => onEdit(item)}>{t('shared.edit')}</Button>
              {allowed.suspend && <Button type="link" size="small" danger onClick={() => gate.onSuspend(item)}>{t('prerequisite.action.suspend')}</Button>}
              {allowed.resume && <Button type="link" size="small" onClick={() => gate.onResume(item)}>{t('prerequisite.action.resume')}</Button>}
            </Space>;
          } }] : []),
        ]} />}
    {section.other && <CommercialNote tone="warning">{t('productSections.otherNote')}</CommercialNote>}
  </section>;
}

/** 前置失效暂停徽标下方的小字：有效核对结果中第一条不满足的短原因。 */
function lapsedReason(agreement: Agreement, capability: AgreementCapability, checked: CapabilityPrerequisites | undefined): string | null {
  if (checked?.executionStatus !== 'SUSPENDED_BY_PREREQUISITE') return null;
  const blocking = checked.checks.find(check => check.result === 'UNSATISFIED');
  return blocking?.reasonCode ? prerequisiteShortReason(blocking.reasonCode, blocking.params,
    { clientId: agreement.clientId, entityNo: capability.entityNo, agreementStatus: agreement.status }, blocking.code) : null;
}

/** 本地时间 HH:mm（「上次检查」「核对于」）。 */
function clockTime(value: Date): string {
  return localTime(value.toISOString()).slice(11);
}

const markIcons: Readonly<Record<PrerequisiteMark, ReactNode>> = {
  satisfied: <CheckCircleOutlined />, unsatisfied: <CloseCircleOutlined />, unknown: <ExclamationCircleOutlined />, skipped: <MinusCircleOutlined />,
};

/**
 * 行内展开区「还差什么」：头部摘要（核对时间、满足 / 未满足 / 检查失败数、按执行状态的结尾说明），逐项（图标、名称、说明、跳转链接）；
 * 核对中、失败与手动暂停各有提示。
 */
function PrerequisitePanel({ agreement, capability, checked, prerequisites, onGoPricing, canViewMids }: {
  agreement: Agreement; capability: AgreementCapability; checked: CapabilityPrerequisites | undefined; prerequisites: CapabilityPrerequisitesState;
  onGoPricing: () => void; canViewMids: boolean;
}) {
  const t = useT('commercial'), tCommon = useT('common');
  const lines = checked ? prerequisiteLines(checked.checks, { clientId: agreement.clientId, entityNo: capability.entityNo, agreementStatus: agreement.status }) : [];
  const counts = lineCounts(lines);
  const execution = checked?.executionStatus ?? capability.executionStatus;
  const visible = useVisibleTableWidth();
  return <div className="cc-prereq" ref={visible.ref} style={visible.width ? { width: visible.width } : undefined} aria-busy={prerequisites.loading}>
    <div className="cc-prereq-head"><strong>{t('prerequisite.title')}</strong>
      {prerequisites.loading && <span className="commercial-cell-meta"><Spin size="small" /> {t('prerequisite.loading')}</span>}
      {!prerequisites.loading && checked && <span className="commercial-cell-meta">{[
        prerequisites.checkedAt && t('prerequisite.checkedAt', { time: clockTime(prerequisites.checkedAt) }),
        t('prerequisite.counts', { satisfied: counts.satisfied, unsatisfied: counts.unsatisfied }),
        counts.unknown > 0 && t('prerequisite.unknownCount', { count: counts.unknown }),
        execution && execution !== 'SUSPENDED_MANUALLY' && t('prerequisite.tail.' + execution),
      ].filter(Boolean).join(' · ')}</span>}</div>
    {prerequisites.failure && <Alert type="error" showIcon title={prerequisites.failure}
      action={<Button size="small" onClick={() => void prerequisites.run()} disabled={prerequisites.loading}>{tCommon('action.retry')}</Button>} />}
    {execution === 'SUSPENDED_MANUALLY' && <CommercialNote>{t('prerequisite.manualNote')}</CommercialNote>}
    {checked && !lines.length && <div className="commercial-cell-meta">{t('prerequisite.noChecks')}</div>}
    {lines.length > 0 && <ul className="cc-prereq-list">{lines.map(line => <li key={line.code} className={`cc-prereq-item is-${line.mark}`}>
      <span className="cc-prereq-icon" role="img" aria-label={line.result} title={line.result}>{markIcons[line.mark]}</span>
      <span className="cc-prereq-name">{line.title}</span>
      <span className="cc-prereq-detail">{line.detail}</span>
      <PrerequisiteLinkButton line={line} onGoPricing={onGoPricing} onRecheck={() => void prerequisites.run()} loading={prerequisites.loading}
        canViewMids={canViewMids} />
    </li>)}</ul>}
  </div>;
}

/** 前置行右侧的跳转：Client / OE 详情、去定价、去配置 MID（按 OE 筛选，无 MID 页面时不显示）、重新检查。 */
function PrerequisiteLinkButton({ line, onGoPricing, onRecheck, loading, canViewMids }: {
  line: PrerequisiteLine; onGoPricing: () => void; onRecheck: () => void; loading: boolean; canViewMids: boolean;
}) {
  const t = useT('commercial');
  const navigate = useNavigate();
  const link: PrerequisiteLink | null = line.link;
  if (!link || (link.kind === 'mids' && !canViewMids)) return <span />;
  const go = () => {
    if (link.kind === 'client') void navigate(clientRoutes.detail(link.clientId));
    else if (link.kind === 'entity') void navigate(masterDataRoutes.entity(link.entityNo));
    else if (link.kind === 'mids') void navigate(channelRoutes.midsOfEntity(link.entityNo));
    else if (link.kind === 'pricing') onGoPricing();
    else onRecheck();
  };
  return <Button type="link" size="small" className="cc-prereq-link" loading={link.kind === 'recheck' && loading} onClick={go}>
    {t('prerequisite.link.' + link.kind)}</Button>;
}

/**
 * 展开区宽度跟随表格可见区域：表格带横向滚动（scroll.x）时，展开行与整张表同宽，窄屏下说明会被裁到可见区外；
 * 这里读取所在 .ant-table-content 的可见宽度，配合 CSS sticky 让「还差什么」始终落在可见区内并正常换行。
 */
function useVisibleTableWidth(): { ref: RefObject<HTMLDivElement | null>; width: number | null } {
  const ref = useRef<HTMLDivElement | null>(null);
  const [width, setWidth] = useState<number | null>(null);
  useEffect(() => {
    const container = ref.current?.closest('.ant-table-content');
    if (!container) return;
    const measure = () => setWidth(Math.max(container.clientWidth - 32, 240));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(container);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}
