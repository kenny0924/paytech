package com.paytech.operations.console.bff.service.model;

import java.util.List;
import java.util.Map;

import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;

import lombok.Getter;
import lombok.Setter;

/**
 * 商务变更历史与版本对比的页面模型（第二切片 I2：F3 版本对比、F5 变更历史）
 * <li>变更历史行在 commercial 快照之外补充与同一受审计行上一快照的字段级比较；比较由 BFF 完成，commercial 只返回快照。</li>
 * <li>版本对比按价格业务键（OE + 币种 + 定价画像，即 uk_term_price_profile）配对，输出新增 / 删除 / 修改 / 未变四组。</li>
 *
 * @author Claude
 */
public final class CommercialChangeModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialChangeModels() {
    }

    /** 变更历史的一行：commercial 快照 + 字段级比较。 */
    @Getter
    @Setter
    public static class BusinessChangeEntry {
        /** 受审计行的历史身份 ID */
        private String historyId;

        /** 受审计对象类型，例如 AGREEMENT、AGREEMENT_CAPABILITY、COMMERCIAL_TERM_VERSION */
        private String recordType;

        /** 受审计业务行 ID */
        private String recordId;

        /** 本次变化提交后的聚合根版本 */
        private Long versionNo;

        /** 行操作类型：CREATE、UPDATE、DELETE */
        private String operationType;

        /** 操作者 Account ID；系统命令为 0 */
        private String createBy;

        /** 快照创建时间，UTC ISO 8601 */
        private String createTime;

        /** 触发信息 */
        private BusinessChangeTrigger meta;

        /** 白名单审计视图（commercial 原样） */
        private Map<String, Object> data;

        /**
         * 比较结果（BusinessChangeComparison）
         * <li>FIRST_VERSION：该行最早的快照，changedFields 为全部字段；COMPARED：与 previousVersionNo 的快照比较；
         * NOT_COMPARED：上一快照超出 BFF 的回溯上限，changedFields 与 previousData 为 null。</li>
         */
        private String comparison;

        /** 同一受审计行上一快照的聚合版本；FIRST_VERSION 与 NOT_COMPARED 为 null */
        private Long previousVersionNo;

        /** 上一快照的审计视图；只在 COMPARED 时非空 */
        private Map<String, Object> previousData;

        /** 变化的字段名，按字典序；NOT_COMPARED 为 null */
        private List<String> changedFields;
    }

    /** 快照触发信息。 */
    @Getter
    @Setter
    public static class BusinessChangeTrigger {
        /** 触发类型：COMMAND、SYSTEM */
        private String triggerType;

        /** 同一命令所有快照共享的触发标识 */
        private String triggerId;

        /** 业务原因代码，例如 AGREEMENT_ACTIVATED */
        private String reasonCode;
    }

    /** 同一方案两个版本的价格对比。 */
    @Getter
    @Setter
    public static class CommercialTermVersionDiffResponseBody {
        /** 基准版本（通常为较早版本） */
        private CommercialTermVersionResponseBody base;

        /** 目标版本 */
        private CommercialTermVersionResponseBody target;

        /** 版本级口径差异：舍入方式、定价模型、费用税务处理 */
        private List<FieldDifference> versionDifferences;

        /** 目标版本新增的价格（基准版本没有该业务键） */
        private List<CommercialPrice> added;

        /** 目标版本删除的价格（目标版本没有该业务键） */
        private List<CommercialPrice> removed;

        /** 两版都有、内容不同的价格 */
        private List<PriceChange> changed;

        /** 两版都有、内容相同的价格（取目标版本） */
        private List<CommercialPrice> unchanged;
    }

    /** 一条修改的价格：业务键、两版原值与字段级差异。 */
    @Getter
    @Setter
    public static class PriceChange {
        /** OE 编号 */
        private String entityNo;

        /** 币种 */
        private String currency;

        /** 定价画像代码 */
        private String pricingProfileCode;

        /** 基准版本的价格 */
        private CommercialPrice base;

        /** 目标版本的价格 */
        private CommercialPrice target;

        /** 字段级差异，按价格字段、结算、组件（componentCode）与档位（bandNo）顺序 */
        private List<FieldDifference> differences;
    }

    /** 一个字段的差异。 */
    @Getter
    @Setter
    public static class FieldDifference {
        /**
         * 字段路径
         * <li>例如 priorityNo、settlement.settlementBusinessDays、components[MDR].rate、components[TIER].bands[2].rate；
         * 组件或档位整体新增 / 删除时路径指向该组件或档位，缺失一侧的值为 null。</li>
         */
        private String field;

        /** 基准版本的值；不存在为 null */
        private String baseValue;

        /** 目标版本的值；不存在为 null */
        private String targetValue;

        /** 创建字段差异。 */
        public static FieldDifference of(String field, String baseValue, String targetValue) {
            var difference = new FieldDifference();
            difference.setField(field);
            difference.setBaseValue(baseValue);
            difference.setTargetValue(targetValue);
            return difference;
        }
    }
}
