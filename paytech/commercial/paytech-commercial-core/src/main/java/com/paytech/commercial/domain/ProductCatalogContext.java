package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;

import lombok.Builder;
import lombok.Getter;

/**
 * 产品目录写入的请求上下文：由目录写 Processor 在目录锁内查询校验后一次构造，贯穿本地保存与响应
 * <li>持有本模块 DataObject；各命令只填所需部分。目录 BCR Root 首次写入时为空，由保存事务创建。</li>
 */
@Getter
@Builder
public class ProductCatalogContext {
    /** 目录 BCR Root 的固定聚合 ID。 */
    public static final long ROOT_ID = 1L;

    /** 本次命令时间（UTC 微秒）。 */
    private final Instant now;

    /** 操作者 Account ID。 */
    private final long actorId;

    /** 已读取的目录 BCR Root；首次写入为空。 */
    private final BusinessChangeRecord root;

    /** 新增或修改的可开通项；新增时为待插入新行。 */
    private final ProductCapability capability;

    /** 可开通项写入后的完整可配维度（新行）。 */
    private final List<ProductCapabilityDimension> dimensions;

    /** 修改后的名称。 */
    private final String nextCapabilityName;

    /** 修改后的归属产品。 */
    private final ProductCode nextProductCode;

    /** 停用 / 启用的目标状态。 */
    private final ProductStatus nextStatus;

    /** 修改挂接的计费事件。 */
    private final ProductFeeEvent feeEvent;

    /** 计费事件挂接的目标种类，可空。 */
    private final CapabilityKind nextCapabilityKind;

    /** 计费事件挂接的目标功能开通项代码，可空。 */
    private final String nextCapabilityCode;
}
