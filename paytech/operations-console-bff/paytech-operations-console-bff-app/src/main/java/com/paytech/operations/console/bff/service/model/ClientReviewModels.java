package com.paytech.operations.console.bff.service.model;

import java.util.List;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryCategoryResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Client 入驻审核页面的请求与聚合响应；Owner 事实原样保留，BFF 只标记各部分是否可用。 */
public final class ClientReviewModels {
    /** 原因代码格式，与 Risk 一致。 */
    static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";

    private ClientReviewModels() {
    }

    /** 一条行业与 MCC 分类。 */
    public record ClassificationItem(@NotBlank @Pattern(regexp = CODE) String industryCode,
        @NotBlank @Pattern(regexp = "^[0-9]{4}$") String mccCode, @NotNull Boolean primary) {
    }

    /** O03 整体替换分类。 */
    public record ClassificationSetRequestBody(@NotEmpty @Size(max = 20) List<@Valid @NotNull ClassificationItem> items) {
    }

    /**
     * O02 审核详情：Risk 任务为主体，CCT 申请资料与分类、Platform 行业树与 MCC 各自标记可用性
     * <li>*Status 为 AVAILABLE 或 UNAVAILABLE；不可用时对应字段为 null 或空列表，页面不得推断为“无数据”。</li>
     * <li>industries 为 Platform ACTIVE 行业：大类带细类，细类带建议 MCC。</li>
     * <li>mccs 为当前分类与各细类建议 MCC 涉及的编码（任意状态），供页面显示名称与停用标记。</li>
     */
    public record ReviewCaseDetailResponseBody(ClientReviewCaseResponseBody review, String applicationStatus,
        ClientDraftResponseBody application, String classificationStatus, ClassificationSetResponseBody classification,
        String industriesStatus, List<IndustryCategoryResponseBody> industries, String mccsStatus,
        List<MccResponseBody> mccs) {
        /** 申请资料含个人信息，不进入日志。 */
        @Override
        public String toString() {
            return "ReviewCaseDetailResponseBody[REDACTED]";
        }
    }
}
