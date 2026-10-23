import { Alert, Button, Empty, Table, Tag } from 'antd';
import { joinList, localTime } from './commercial';
import type { Agreement, AgreementCapability } from './commercial';
import { conditionTags, require3dsTag, reserveText } from './capabilityConditions';
import type { DimensionDefinition } from './capabilityConditions';
import { CommercialNote, StatusTag } from './CommercialParts';
import type { CatalogCapability, CatalogProduct } from './commercialConsole';
import { capabilityDisplayName, productFootnote, productSections, sectionTitle, showEntityColumn } from './productCapabilities';
import type { ProductSection } from './productCapabilities';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

/**
 * 合同详情「产品与开通」：每个已订阅产品一个区块（目录产品顺序），区内列该产品的开通项；归属不明的开通项进最后的「其他」区块。
 * <li>canAdd：区头「+ 开通该产品的项」（按产品打开新增抽屉）；canEdit：行内「编辑」。</li>
 * <li>没有订阅任何产品时显示订阅引导；页底一行列出未订阅产品与默认具备的基础产品。</li>
 */
export function ProductCapabilitySections({ agreement, products, catalog, definitions, canAdd, canEdit, canSubscribe, onAdd, onEdit, onSubscribe }: {
  agreement: Agreement; products: CatalogProduct[]; catalog: CatalogCapability[]; definitions: DimensionDefinition[]; canAdd: boolean; canEdit: boolean;
  canSubscribe: boolean; onAdd: (productCode: string) => void; onEdit: (capability: AgreementCapability) => void; onSubscribe: () => void;
}) {
  const t = useT('commercial');
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
  return <>
    {sections.map(section => <ProductBlock key={section.key} section={section} entityColumn={entityColumn} catalog={catalog} definitions={definitions}
      canAdd={canAdd && !section.other && !!section.productCode} canEdit={canEdit} onAdd={onAdd} onEdit={onEdit} />)}
    {footnote && <div className="commercial-cell-meta cc-product-footnote">{footnote}</div>}
  </>;
}

/** 一个产品区块：区头（名称 + 代码、开通项数、开通按钮）与该产品的开通项表；空产品显示提示与可开通项名称。 */
function ProductBlock({ section, entityColumn, catalog, definitions, canAdd, canEdit, onAdd, onEdit }: {
  section: ProductSection; entityColumn: boolean; catalog: CatalogCapability[]; definitions: DimensionDefinition[]; canAdd: boolean; canEdit: boolean;
  onAdd: (productCode: string) => void; onEdit: (capability: AgreementCapability) => void;
}) {
  const t = useT('commercial');
  return <section className={`commercial-block cc-product-block${section.other ? ' is-other' : ''}`}>
    <div className="commercial-block-head"><h3>{sectionTitle(section)}</h3><Tag>{t('productSections.capabilityCount', { count: section.capabilities.length })}</Tag>
      <span className="commercial-spacer" />
      {canAdd && section.productCode && <Button size="small" onClick={() => onAdd(section.productCode!)}>{t('productSections.add')}</Button>}</div>
    {section.capabilities.length === 0
      ? <Alert type="warning" showIcon title={t('productSections.noneEnabled')}
        description={section.availableNames.length ? t('productSections.available', { names: joinList(section.availableNames) }) : t('productSections.noneAvailable')} />
      : <Table<AgreementCapability> rowKey="agreementCapabilityId" size="middle" pagination={false} dataSource={section.capabilities}
        scroll={{ x: entityColumn ? 1040 : 950 }} columns={[
          { title: t('productSections.column.capability'), width: 190, render: (_, row) => {
            const name = capabilityDisplayName(row.capabilityCode, catalog);
            return <div className="commercial-cell">{name && <span>{name}</span>}<span className="mono commercial-cell-meta">{row.capabilityCode}</span></div>;
          } },
          ...(entityColumn ? [{ title: 'OE', dataIndex: 'entityNo', width: 90 }] : []),
          { title: t('productSections.column.conditions'), render: (_, row) => <div className="commercial-limits">{conditionTags(row, definitions).map(tag =>
            <Tag key={tag} color={tag === require3dsTag() ? 'blue' : undefined}>{tag}</Tag>)}</div> },
          { title: t('productSections.column.reserve'), width: 130, render: (_, row) => reserveText(row) },
          { title: t('shared.column.effectivePeriod'), width: 220, render: (_, row) => t('shared.period', { from: localTime(row.effectiveFrom),
            to: row.effectiveTo ? localTime(row.effectiveTo) : t('shared.openEnded') }) },
          { title: t('shared.column.status'), width: 90, render: (_, row) => <StatusTag status={row.status} kind="capabilityStatus" /> },
          { title: t('shared.column.actions'), width: 80, render: (_, row) => canEdit ? <Button size="small" onClick={() => onEdit(row)}>{t('shared.edit')}</Button> : null },
        ]} />}
    {section.other && <CommercialNote tone="warning">{t('productSections.otherNote')}</CommercialNote>}
  </section>;
}
