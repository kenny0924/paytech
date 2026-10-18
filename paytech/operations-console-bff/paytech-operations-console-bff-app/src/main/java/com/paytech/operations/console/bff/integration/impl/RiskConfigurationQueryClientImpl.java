package com.paytech.operations.console.bff.integration.impl;
import java.util.HashSet;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskConfigurationQueryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskConfigurationQueryModels.*;
import com.paytech.risk.decision.api.DecisionConfigurationQueryApi;
import static com.paytech.operations.console.bff.integration.impl.RiskConfigurationResponsePolicy.*;
/** 历史与发布查询保留 Owner 精确版本和全根使用事实。 */
@Component
@AllArgsConstructor
public class RiskConfigurationQueryClientImpl implements RiskConfigurationQueryClient {
    private final DecisionConfigurationQueryApi api;
    /** 单次查询 Risk 历史页并验证根、上界和版本顺序，不自动重试。 */
    @Override public PagingResponseBody<RuleSetHistoryItem> history(OperatorContext o, String rootId, int maxVersion, int page, int size) {
        var owner = api.history(o.accountId(), o.sessionId(), rootId, maxVersion, page, size); page(owner, page, size);
        var result = owner.map(RiskConfigurationConverter::history);
        long last = (long) maxVersion + 1; var ids = new HashSet<String>();
        for (var row : result.items()) {
            RiskConfigurationResponsePolicy.history(row); equal(rootId, row.ruleSetId()); require(row.versionNo() < last); last = row.versionNo();
            require(ids.add(row.ruleSetVersionId()));
        }
        return result;
    }
    /** 单次查询 Risk 发布引用并校验分页筛选及全根使用事实。 */
    @Override public RuleSetReleaseReferencesResponseBody releases(OperatorContext o, String rootId, int page, int size,
        String environment, String status, String versionId) {
        var owner = api.releases(o.accountId(), o.sessionId(), rootId, page, size, environment, status, versionId);
        require(owner != null); equal(rootId, owner.getRuleSetId()); page(owner.getReferences(), page, size);
        var references = owner.getReferences().map(RiskConfigurationConverter::release);
        var ids = new HashSet<String>(); java.math.BigInteger lastRelease = null; java.math.BigInteger lastVersion = null;
        for (var row : references.items()) {
            release(row); filter(environment, row.environmentCode()); filter(status, row.status()); filter(versionId, row.ruleSetVersionId());
            require(ids.add(row.releaseId() + ":" + row.ruleSetVersionId()));
            var releaseId = new java.math.BigInteger(row.releaseId()); var version = new java.math.BigInteger(row.ruleSetVersionId());
            require(lastRelease == null || releaseId.compareTo(lastRelease) < 0 || releaseId.equals(lastRelease) && version.compareTo(lastVersion) < 0);
            lastRelease = releaseId; lastVersion = version;
            if (row.status().equals("ACTIVE")) require(owner.isInUse());
        }
        return new RuleSetReleaseReferencesResponseBody(rootId, owner.isInUse(), references);
    }
}
