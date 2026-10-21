package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_commercial_term_plan：商业条款方案根。
 * <li>Owner XOR：CLIENT_PRICING 填 agreement/entity/client，Partner 类只填 local_partner_id；term_type 与 Owner 创建后不可修改。</li>
 * <li>PLATFORM_TEMPLATE 为标准价目模板：只填 entity_no 与 template_name，不参与运行时取价。</li>
 */
@Getter
@Setter
public class CommercialTermPlan {
    /** 方案标识 */
    private Long termPlanId;

    /** 方案唯一稳定业务编号 */
    private String planCode;

    /** 商业条款类型；开放目录，值域为 t_product_fee_event.term_type */
    private String termType;

    /** 方案 Owner 类型 */
    private CommercialTermOwnerType ownerType;

    /** 标准价目模板显示名；非模板为空 */
    private String templateName;

    /** CLIENT_PRICING 所属合同 */
    private Long agreementId;

    /** CLIENT_PRICING 的准确 OE，或模板适用的 OE */
    private String entityNo;

    /** CLIENT_PRICING 的 Client */
    private Long clientId;

    /** Partner 类方案的 Local Partner */
    private Long localPartnerId;

    /** 方案状态 */
    private CommercialTermPlanStatus status;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
