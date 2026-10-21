import type { Agreement, AgreementCapability } from './commercial.ts';
import type { CatalogCapability, CatalogProduct } from './commercialConsole.ts';

// ---------- 合同详情「产品与开通」：按订阅产品分组开通项 ----------

/** 一个产品区块；other=true 为归属产品无法从目录判定的开通项（目录读取失败或已从目录移除）。 */
export interface ProductSection {
  key: string; productCode: string | null; productName: string | null; capabilities: AgreementCapability[]; availableNames: string[]; other: boolean;
}

const otherKey = 'OTHER';

/**
 * 按订阅产品分组开通项：
 * <li>区块顺序为目录产品顺序；目录里没有的订阅产品（目录读取失败）按合同订阅顺序追加，只显示代码。</li>
 * <li>开通项按目录归属产品落到对应区块；目录查不到或归属产品未订阅的，放进最后的「其他」区块，不丢。</li>
 * <li>没有开通项的区块列出该产品下启用的可开通项名称，供「已订阅，尚未开通任何项」提示。</li>
 */
export function productSections(productCodes: readonly string[], capabilities: readonly AgreementCapability[], products: readonly CatalogProduct[],
  catalog: readonly CatalogCapability[]): ProductSection[] {
  const ordered = [...products.filter(product => productCodes.includes(product.productCode)).map(product => product.productCode),
    ...productCodes.filter(code => !products.some(product => product.productCode === code))];
  const productOf = (capability: AgreementCapability) => catalog.find(item => item.capabilityCode === capability.capabilityCode)?.productCode;
  const sections: ProductSection[] = ordered.map(code => {
    const own = capabilities.filter(capability => productOf(capability) === code);
    return { key: code, productCode: code, productName: products.find(product => product.productCode === code)?.productName ?? null, capabilities: own,
      availableNames: own.length ? [] : catalog.filter(item => item.productCode === code && item.status === 'ACTIVE').map(item => item.capabilityName),
      other: false };
  });
  const rest = capabilities.filter(capability => {
    const code = productOf(capability);
    return !code || !ordered.includes(code);
  });
  return rest.length ? [...sections, { key: otherKey, productCode: null, productName: null, capabilities: rest, availableNames: [], other: true }] : sections;
}

/** 区块标题：中文名 + 代码；目录读取失败时只有代码；「其他」区块固定文字。 */
export function sectionTitle(section: Pick<ProductSection, 'productCode' | 'productName' | 'other'>): string {
  if (section.other) return '其他（归属产品无法从目录判定）';
  return section.productName && section.productName !== section.productCode ? `${section.productName} ${section.productCode}` : section.productCode ?? '';
}

/**
 * 页底说明：「未订阅：出款、外汇；默认具备：税票、平台」。可订阅产品与基础产品的判定同 subscribableProductOptions（启用且非基础 / 基础产品）。
 * 目录读取失败时为 null；某一部分为空时省略该部分。
 */
export function productFootnote(productCodes: readonly string[], products: readonly CatalogProduct[]): string | null {
  if (!products.length) return null;
  const name = (product: CatalogProduct) => product.productName || product.productCode;
  const unsubscribed = products.filter(product => product.status === 'ACTIVE' && !product.baseProduct && !productCodes.includes(product.productCode)).map(name);
  const base = products.filter(product => product.baseProduct).map(name);
  const parts = [unsubscribed.length ? `未订阅：${unsubscribed.join('、')}` : null, base.length ? `默认具备：${base.join('、')}` : null].filter(Boolean);
  return parts.length ? parts.join('；') : null;
}

/** OE 列：只在 PARTNER 合同覆盖多个 OE 时显示（CLIENT 合同只有一个 OE）。 */
export function showEntityColumn(agreement: Pick<Agreement, 'agreementType' | 'operatingEntityNos'>): boolean {
  return agreement.agreementType === 'PARTNER' && agreement.operatingEntityNos.length > 1;
}

/** 开通项显示名：目录中文名 + 代码；目录查不到时只有代码。 */
export function capabilityDisplayName(capabilityCode: string, catalog: readonly Pick<CatalogCapability, 'capabilityCode' | 'capabilityName'>[]): string | null {
  const name = catalog.find(item => item.capabilityCode === capabilityCode)?.capabilityName;
  return name && name !== capabilityCode ? name : null;
}
