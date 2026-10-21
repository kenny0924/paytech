package com.paytech.operations.console.bff.service.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 产品目录运营维护的页面请求模型（commercial ProductCatalogApi 写入的 BFF 投影）
 * <li>字段与 commercial 合同一一对应，由 Client 适配器严格转换；回执与 {@link CommercialListModels} 的目录行字段相同。</li>
 * <li>BFF 只做结构性约束，代码格式、种类与支付方式、维度可用性等业务规则由 commercial 执行，其 422 字段路径投影回本次 Body。</li>
 *
 * @author Claude
 */
public final class CommercialCatalogModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialCatalogModels() {
    }

    /** 新增可开通项（ACTIVE）及其可配维度。 */
    @Getter
    @Setter
    public static class CreateProductCapabilityRequestBody {
        /** 所属产品代码 */
        @NotBlank(message = "所属产品必填")
        private String productCode;

        /** 可开通项代码，创建后不可修改 */
        @NotBlank(message = "可开通项代码必填")
        private String capabilityCode;

        /** 可开通项名称 */
        @NotBlank(message = "可开通项名称必填")
        @Size(max = 128, message = "可开通项名称最长 128 字符")
        private String capabilityName;

        /** 可开通项种类：PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE，创建后不可修改 */
        @NotBlank(message = "可开通项种类必填")
        private String capabilityKind;

        /** 支付方式代码，PAYMENT_METHOD 必填，其他种类必须为空 */
        private String paymentMethodCode;

        /** 可配维度，整体提交，最多 20 个 */
        @NotNull(message = "可配维度必填，可为空列表")
        @Size(max = 20, message = "可配维度最多 20 个")
        @Valid
        private List<CapabilityDimensionEntry> dimensions;
    }

    /** 整体改写可开通项的名称、归属产品与可配维度；代码与种类不可改。 */
    @Getter
    @Setter
    public static class UpdateProductCapabilityRequestBody {
        /** 页面读取到的目录行 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 可开通项名称 */
        @NotBlank(message = "可开通项名称必填")
        @Size(max = 128, message = "可开通项名称最长 128 字符")
        private String capabilityName;

        /** 所属产品代码 */
        @NotBlank(message = "所属产品必填")
        private String productCode;

        /** 可配维度，整体提交，最多 20 个 */
        @NotNull(message = "可配维度必填，可为空列表")
        @Size(max = 20, message = "可配维度最多 20 个")
        @Valid
        private List<CapabilityDimensionEntry> dimensions;
    }

    /** 可开通项停用或启用。 */
    @Getter
    @Setter
    public static class ProductCatalogTransitionRequestBody {
        /** 页面读取到的目录行 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;
    }

    /** 改写计费事件挂接的开通项种类。 */
    @Getter
    @Setter
    public static class UpdateFeeEventCapabilityRequestBody {
        /** 页面读取到的计费事件 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 挂接的开通项种类；为空表示无需开通的基础产品事件 */
        private String capabilityKind;

        /** 挂接的功能开通项代码，仅 FEATURE */
        private String capabilityCode;
    }

    /** 可开通项写入回执：与目录行字段相同。 */
    public static class CatalogCapabilityResponseBody extends CommercialListModels.CatalogCapability {
    }

    /** 计费事件挂接回执：与目录行字段相同。 */
    public static class CatalogFeeEventResponseBody extends CommercialListModels.CatalogFeeEvent {
    }

    /** 可开通项的一个可配维度。 */
    @Getter
    @Setter
    public static class CapabilityDimensionEntry {
        /** 维度代码（须可用于开通接受条件） */
        @NotBlank(message = "维度代码必填")
        private String dimensionCode;

        /** 合同开通时是否必须给出该维度条件 */
        @NotNull(message = "是否必填必须给出")
        private Boolean required;
    }
}
