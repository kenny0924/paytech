package com.paytech.operations.console.bff.integration;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskConfigurationQueryModels.*;
/** 已授权的规则历史和发布引用查询。 */
public interface RiskConfigurationQueryClient {
    /** 单次查询 Risk 历史页并验证根、上界和版本顺序，不自动重试。 */
    PagingResponseBody<RuleSetHistoryItem> history(OperatorContext operator, String rootId, int maxVersion, int page, int size);
    /** 单次查询 Risk 发布引用并校验分页筛选及全根使用事实。 */
    RuleSetReleaseReferencesResponseBody releases(OperatorContext operator, String rootId, int page, int size, String environment, String status, String versionId);
}
