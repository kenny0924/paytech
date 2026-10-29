package com.paytech.client.master.domain.onboarding.pack;

/**
 * 引用外部 Owner 目录的保留目录码（参考数据迁移 S-04；业务信息补采 S-05）
 * <li>以 REF_ 开头的 catalog_code 只出现在 Schema 字段与地址组件上，表示取值来自 Platform 参考数据；
 * 不从 t_catalog_item 加载，t_catalog_item 也不得出现这些码（守卫测试）。</li>
 * <li>form-schema 的 catalogs 不含 REF_ 目录，由 client-console-bff 向 Platform 取选项补全；提交时向 Platform 校验存在且 ACTIVE。</li>
 * <li>COMMERCIAL_PAYMENT_METHOD 表示 commercial 产品能力目录中 ACTIVE 的支付方式（capability_code）：同样不进 t_catalog_item；
 * 由 client-master 自己在事务外向 commercial 读取，补入 form-schema 的 catalogs，并在需求清单与提交时校验。</li>
 */
public final class ReferenceCatalogCodes {
    /** 保留前缀。 */
    public static final String PREFIX = "REF_";

    /** Platform 币种（ISO 4217）。 */
    public static final String REF_CURRENCY = "REF_CURRENCY";

    /** Platform 一级行政区（ISO 3166-2，如 AE-DU）；地址组件使用时须与地址国家一致。 */
    public static final String REF_COUNTRY_SUBDIVISION = "REF_COUNTRY_SUBDIVISION";

    /** commercial 外部目录码的保留前缀；t_catalog_item 不得出现。 */
    public static final String COMMERCIAL_PREFIX = "COMMERCIAL_";

    /** commercial 产品能力目录中 ACTIVE 的支付方式，选项代码为 capability_code。 */
    public static final String COMMERCIAL_PAYMENT_METHOD = "COMMERCIAL_PAYMENT_METHOD";

    /** 仅常量。 */
    private ReferenceCatalogCodes() {
    }

    /** 是否为引用 Platform 参考数据的保留目录码。 */
    public static boolean isReference(String catalogCode) {
        return catalogCode != null && catalogCode.startsWith(PREFIX);
    }

    /** 是否为外部 Owner 目录码（Platform 参考数据或 commercial 支付方式）；外部目录不从 t_catalog_item 加载。 */
    public static boolean isExternal(String catalogCode) {
        return isReference(catalogCode) || COMMERCIAL_PAYMENT_METHOD.equals(catalogCode);
    }
}
