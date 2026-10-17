package com.paytech.operations.console.bff.service.model;
import com.paytech.foundation.web.paging.PagingResponseBody;
/** 规则集历史与发布引用的窄查询合同。 */
public final class RiskConfigurationQueryModels {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private RiskConfigurationQueryModels() { }
    /**
     * 规则根下的历史版本摘要，名称和变更信息取自该保存版本。
     * @param ruleSetId 整体规则根 ID，正 BIGINT 字符串
     * @param ruleSetVersionId 精确版本 ID，正 BIGINT 字符串
     * @param versionNo 规则集内递增的业务版本号
     * @param status 该规则版本的 DRAFT、FROZEN 或 RETIRED 状态
     * @param isCurrent 是否等于根当前配置指针，不表示正在使用
     * @param createdBy 创建该版本的 Account ID，正 BIGINT 字符串
     * @param changeReason 保存该版本时填写的变更原因
     * @param createTime 保存时间，UTC 且固定六位小数
     */
    public record RuleSetHistoryItem(String ruleSetId, String ruleSetVersionId, String pointCode, String ruleSetCode, String ruleSetName, int versionNo, String status, boolean isCurrent, String createdBy, String changeReason, String createTime) { }
    /**
     * 发布到点版本、再到规则版本的实际引用，历史引用也可出现在结果中。
     * @param releaseId 发布 ID，正 BIGINT 字符串
     * @param environmentCode 发布所属 TEST 或 PRODUCTION 环境
     * @param status 发布记录的生命周期状态，不是规则版本状态
     * @param pointVersionId 被发布引用的点版本 ID，正 BIGINT 字符串
     * @param pointVersionNo 点内业务版本号
     * @param ruleSetVersionId 该点版本绑定的精确规则版本 ID，正 BIGINT 字符串
     * @param versionNo 规则集内业务版本号
     * @param activatedTime 激活时间，UTC 六位小数；尚未激活时可为 null
     * @param supersededTime 被替换时间，UTC 六位小数；尚未被替换时可为 null
     */
    public record ReleaseReference(String releaseId, String releaseNo, String environmentCode, String status, String pointCode, String pointVersionId, int pointVersionNo, String ruleSetVersionId, int versionNo, String activatedTime, String supersededTime) { }
    /**
     * 同时返回整体使用事实和当前筛选页，图表只能绘制该页中的真实引用。
     * @param ruleSetId 整体规则根 ID，正 BIGINT 字符串
     * @param inUse 覆盖根下所有版本与环境，不受筛选、页码或空页影响
     * @param references 仅包含当前环境、状态及精确版本筛选下的分页结果
     */
    public record RuleSetReleaseReferencesResponseBody(String ruleSetId, boolean inUse, PagingResponseBody<ReleaseReference> references) { }
}
