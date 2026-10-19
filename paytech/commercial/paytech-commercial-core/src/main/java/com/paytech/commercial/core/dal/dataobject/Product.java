package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_product：L0 产品，即可售模块目录。
 * <li>随产品发版维护（初始化 DML），不接入 BCR；product_code 创建后不可修改。</li>
 */
@Getter
@Setter
public class Product {
    /** 产品目录行的内部唯一标识 */
    private Long productId;

    /** 可售产品模块的稳定代码，uk_product_code */
    private ProductCode productCode;

    /** 供 Operations 识别产品的展示名称 */
    private String productName;

    /** 该产品允许被哪种合同业务模式订阅 */
    private ProductBusinessModeScope businessModeScope;

    /**
     * 是否基座产品
     * <li>true 表示所有实例必装、合同无需订阅且不写 t_agreement_product；false 表示可选模块。</li>
     */
    private Boolean baseProduct;

    /**
     * 生命周期状态
     * <li>RETIRED 阻止新合同订阅，历史订阅继续可读。</li>
     */
    private ProductStatus status;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
