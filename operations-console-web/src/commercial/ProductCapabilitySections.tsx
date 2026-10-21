import { Alert, Button, Empty, Table, Tag } from 'antd';
import { capabilityStatusLabels, localTime } from './commercial';
import type { Agreement, AgreementCapability } from './commercial';
import { conditionTags, require3dsTag, reserveText } from './capabilityConditions';
import type { DimensionDefinition } from './capabilityConditions';
import { CommercialNote, StatusTag } from './CommercialParts';
import type { CatalogCapability, CatalogProduct } from './commercialConsole';
import { capabilityDisplayName, productFootnote, productSections, sectionTitle, showEntityColumn } from './productCapabilities';
import type { ProductSection } from './productCapabilities';
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
  const sections = productSections(agreement.productCodes, agreement.capabilities, products, catalog);
  const footnote = productFootnote(agreement.productCodes, products);
  const entityColumn = showEntityColumn(agreement);
  if (!agreement.productCodes.length && !sections.length) {
    return <>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未订阅可选产品：先订阅产品，再在产品下开通支付方式与功能">
        {canSubscribe && <Button type="primary" onClick={onSubscribe}>修改订阅</Button>}</Empty>
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
  return <section className={`commercial-block cc-product-block${section.other ? ' is-other' : ''}`}>
    <div className="commercial-block-head"><h3>{sectionTitle(section)}</h3><Tag>{section.capabilities.length} 个开通项</Tag>
      <span className="commercial-spacer" />
      {canAdd && section.productCode && <Button size="small" onClick={() => onAdd(section.productCode!)}>+ 开通该产品的项</Button>}</div>
    {section.capabilities.length === 0
      ? <Alert type="warning" showIcon title="已订阅，尚未开通任何项"
        description={section.availableNames.length ? `可开通：${section.availableNames.join('、')}` : '该产品目录下暂无启用的可开通项'} />
      : <Table<AgreementCapability> rowKey="agreementCapabilityId" size="middle" pagination={false} dataSource={section.capabilities}
        scroll={{ x: entityColumn ? 1040 : 950 }} columns={[
          { title: '可开通项', width: 190, render: (_, row) => {
            const name = capabilityDisplayName(row.capabilityCode, catalog);
            return <div className="commercial-cell">{name && <span>{name}</span>}<span className="mono commercial-cell-meta">{row.capabilityCode}</span></div>;
          } },
          ...(entityColumn ? [{ title: 'OE', dataIndex: 'entityNo', width: 90 }] : []),
          { title: '接受条件', render: (_, row) => <div className="commercial-limits">{conditionTags(row, definitions).map(tag =>
            <Tag key={tag} color={tag === require3dsTag ? 'blue' : undefined}>{tag}</Tag>)}</div> },
          { title: '滚动保证金', width: 130, render: (_, row) => reserveText(row) },
          { title: '生效期', width: 220, render: (_, row) => `${localTime(row.effectiveFrom)} ～ ${row.effectiveTo ? localTime(row.effectiveTo) : '未定'}` },
          { title: '状态', width: 90, render: (_, row) => <StatusTag status={row.status} labels={capabilityStatusLabels} /> },
          { title: '操作', width: 80, render: (_, row) => canEdit ? <Button size="small" onClick={() => onEdit(row)}>编辑</Button> : null },
        ]} />}
    {section.other && <CommercialNote tone="warning">这些开通项在产品目录中查不到归属产品（目录读取失败或已从目录移除），仍可编辑。</CommercialNote>}
  </section>;
}
