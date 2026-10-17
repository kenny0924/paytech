package com.paytech.acquiring.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Order 创建时冻结的商品或服务行。 */
@Getter
@Setter
@Builder
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public class OrderItem {
    /** 本库预生成的 Item 主键。 */
    private long orderItemId;

    /** 所属 Commerce Order 主键。 */
    private long orderId;

    /** Order 内大小写敏感且唯一的 Client Item 标识。 */
    private String clientItemId;

    /** 创建时冻结的项目名称。 */
    private String itemName;

    /** 可选的项目详细说明。 */
    private String itemDescription;

    /** Commerce 产品类型目录代码。 */
    private String productTypeCode;

    /** Finance 税务分类目录代码。 */
    private String taxCategoryCode;

    /** 冻结计量数量，正数且可含小数；不受币种小数位限制，存储精度 DECIMAL(24,8)。 */
    private BigDecimal quantity;

    /** 冻结单价，以订单币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal unitAmount;

    /** 优惠与税务前行金额，等于单价乘数量；以币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal lineGrossAmount;

    /** 整行优惠金额，不再乘数量；以币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal lineDiscountAmount;

    /** 优惠后行净额，以币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal lineNetAmount;

    /** 行税额，以币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal lineTaxAmount;

    /** 行最终应付金额，以币种主单位表示，存储精度 DECIMAL(24,8)。 */
    private BigDecimal lineTotalAmount;

    /** ISO 4217 三位币种代码。 */
    private String currency;

    /** 保持请求原始呈现顺序的零基序号。 */
    private int sortNo;

    /** 本不可变记录创建 UTC 时刻。 */
    private Instant createTime;
}
