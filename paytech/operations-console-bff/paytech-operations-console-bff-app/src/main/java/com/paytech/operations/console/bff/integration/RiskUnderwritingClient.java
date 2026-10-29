package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingHistoryResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

/**
 * risk-decision Client 承保的出站端口（REQ-03）；单次调用，不自动重试
 * <li>写命令的 Owner 字段错误投影到本次转发给 Risk 的 Body 路径（input.*、decision.* 等），不传播远端文案。</li>
 * <li>回执须对应请求的承保版本或 Client，否则按上游无效响应处理。</li>
 */
public interface RiskUnderwritingClient {
    /** U1 承保版本分页；筛选均可为空。 */
    PagingResponseBody<UnderwritingSummaryBody> list(OperatorContext operator, String status, String crr,
        String decisionSource, String keyword, String clientId, String pendingReviewFor, int page, int size);

    /** U2 单个承保版本。 */
    UnderwritingResponseBody detail(OperatorContext operator, String underwritingId);

    /** U3 Client 当前的有效、进行中与最新版本。 */
    ClientUnderwritingCurrentResponseBody current(OperatorContext operator, String clientId);

    /** U4 Client 的版本历史。 */
    UnderwritingHistoryResponseBody history(OperatorContext operator, String clientId);

    /** U5 建草稿。 */
    UnderwritingResponseBody create(OperatorContext operator, String clientId, CreateUnderwritingDraftRequestBody body);

    /** U6 保存草稿。 */
    UnderwritingResponseBody save(OperatorContext operator, String underwritingId, SaveUnderwritingDraftRequestBody body);

    /** U7 提交。 */
    UnderwritingResponseBody submit(OperatorContext operator, String underwritingId, SubmitUnderwritingRequestBody body);

    /** U8 复核。 */
    UnderwritingResponseBody review(OperatorContext operator, String underwritingId, ReviewUnderwritingRequestBody body);

    /** U9 自动承保。 */
    UnderwritingResponseBody auto(OperatorContext operator, String clientId, AutoUnderwritingRequestBody body);

    /** U10 修订。 */
    UnderwritingResponseBody revise(OperatorContext operator, String underwritingId, ReviseUnderwritingRequestBody body);

    /** U11 自动承保判定与估算敞口预览。 */
    UnderwritingPreviewResponseBody preview(OperatorContext operator, UnderwritingPreviewRequestBody body);

    /** U12 列表统计。 */
    UnderwritingStatisticsResponseBody statistics(OperatorContext operator);
}
