package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 商业条款方案 / 版本写入的请求上下文：由各写入 Processor 在锁内查询校验后一次构造，贯穿本地保存与响应
 * <li>持有本模块 DataObject；各命令只填所需部分，未用到的集合为空、对象为 null。</li>
 * <li>expectedChangeVersion 为方案 BCR 根的 CAS 令牌：运营命令来自请求，系统命令（定时激活）为锁内读取的当前值。</li>
 * <li>BCR 身份、快照与 triggerId 由 BusinessChangeHistoryWriter 在保存事务内生成；Context 只携带已读取的方案 Root。</li>
 */
@Getter
@Builder
public class CommercialTermVersionContext {
    /** 本次命令时间（UTC 微秒）。 */
    private final Instant now;

    /** 操作人账号 ID；系统命令为 0。 */
    private final long operatorAccountId;

    /** 是否系统命令（定时激活）。 */
    private final boolean systemCommand;

    /** 方案 BCR 根的期望版本；创建方案时为 0。 */
    private final long expectedChangeVersion;

    /** 方案。 */
    private final CommercialTermPlan plan;

    /** 已读取的方案 BCR Root；创建方案时为空（由保存事务创建）。 */
    private final BusinessChangeRecord planRoot;

    /** 方案或版本引用的合同。 */
    private final Agreement agreement;

    /** 合同覆盖的准确 OE（CLIENT 合同 entity_no 与覆盖表的并集）。 */
    @Singular
    private final List<String> coveredEntityNos;

    /** 本次操作的版本；创建方案时为空。 */
    private final CommercialTermVersion version;

    /** 版本价格集合：创建版本复制或整体替换时为新写入行，排期 / 激活时为读取的现有行。 */
    @Singular
    private final List<CommercialTermPrice> prices;

    /** 价格集合的组件（含义同 prices）。 */
    @Singular
    private final List<CommercialTermComponent> components;

    /** 价格集合的档位（含义同 prices）。 */
    @Singular
    private final List<CommercialTermComponentBand> bands;

    /** 整体替换前的价格 ID。 */
    @Singular
    private final List<Long> replacedPriceIds;

    /** 整体替换前的组件 ID。 */
    @Singular
    private final List<Long> replacedComponentIds;

    /** 批准 / 驳回时提交快照记录的 Maker。 */
    private final Long makerAccountId;

    /** 排期写入的生效起点。 */
    private final Instant effectiveFrom;

    /** 前一 ACTIVE 版本（排期关闭其窗口、激活将其替代），可空。 */
    private final CommercialTermVersion supersededVersion;

    /** 激活校验：价格引用的画像。 */
    @Singular
    private final List<PricingProfile> profiles;

    /** 激活校验：画像条件。 */
    @Singular
    private final List<PricingProfileCond> profileConditions;

    /** 激活校验：合同全部开通项。 */
    @Singular
    private final List<AgreementCapability> capabilities;

    /** 激活校验：开通项约束。 */
    @Singular
    private final List<AgreementCapabilityLimit> capabilityLimits;

    /** 激活校验：开通项对应的可开通项目录。 */
    @Singular
    private final List<ProductCapability> productCapabilities;

    /** 本方案条款类型登记的计费事件。 */
    @Singular
    private final List<ProductFeeEvent> feeEvents;

    /** 激活校验：同方案全部版本（含本版本）。 */
    @Singular
    private final List<CommercialTermVersion> planVersions;

    /** 本次命令提交后的方案根版本。 */
    public long nextChangeVersion() {
        return expectedChangeVersion + 1;
    }

    /** 方案是否 Client 条款（Owner 为 Client）。 */
    public boolean clientTerm() {
        return plan.getClientId() != null;
    }

    /** 某准确 OE 是否被合同覆盖。 */
    public boolean covers(String entityNo) {
        return coveredEntityNos.contains(entityNo);
    }

    /** 版本与方案的当前视图（写入成功后同步的对象）。 */
    public CommercialTermVersionView toVersionView() {
        return new CommercialTermVersionView(plan, version, planRoot.getCurrentVersion());
    }
}
