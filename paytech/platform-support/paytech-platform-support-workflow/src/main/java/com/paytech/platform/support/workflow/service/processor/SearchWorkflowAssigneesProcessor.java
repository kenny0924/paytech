package com.paytech.platform.support.workflow.service.processor;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelCandidate;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsPersonnelService;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateListResponseBody;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;

/**
 * 选人查询（技术方案第 7 节接口 25）：设计流程与发起、转交时按关键字找 ACTIVE 的 Operations 人员，最多 20 条。
 * <li>关键字含 @：规范化为登录邮箱后经 Vault 取稳定 Token 精确匹配（与登录解析一致，无事务）；格式无效时返回空列表。</li>
 * <li>其他关键字：登录名前缀或脱敏邮箱片段；为空时返回按登录名排序的前 20 人。</li>
 * <li>只返回账号、登录名、脱敏邮箱与显示名，不返回邮箱原文或 Token。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SearchWorkflowAssigneesProcessor {

    /** 最多条数。 */
    static final int LIMIT = 20;

    /** 关键字最长字符数。 */
    static final int KEYWORD_LIMIT = 128;

    private final WorkflowProcessorSupport support;
    private final OperationsPersonnelService personnel;
    private final LoginEmailVault vault;
    private final OperationsPasswordPolicy emails;

    /**
     * 装配依赖。
     *
     * @param support 工作流公共支持
     * @param personnel 人员窄查询
     * @param vault Operations 登录邮箱 Vault
     * @param emails Operations 登录邮箱规范化
     */
    public SearchWorkflowAssigneesProcessor(WorkflowProcessorSupport support, OperationsPersonnelService personnel,
        @Qualifier("loginEmailVault") LoginEmailVault vault,
        @Qualifier("operationsPasswordPolicy") OperationsPasswordPolicy emails) {
        this.support = support;
        this.personnel = personnel;
        this.vault = vault;
        this.emails = emails;
    }

    /**
     * 查询候选人。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param keyword 关键字，可空
     * @return 候选人
     */
    public WorkflowAssigneeCandidateListResponseBody search(String actor, String session, String keyword) {
        support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE, WorkflowPermissions.DEFINITION_MANAGE);
        String text = keyword == null ? null : keyword.strip();
        if (text != null && text.length() > KEYWORD_LIMIT) {
            throw new InvalidParameterException("keyword 不能超过 " + KEYWORD_LIMIT + " 个字符");
        }
        List<OperationsPersonnelCandidate> rows = text == null || text.isEmpty()
            ? personnel.searchActive(null, null, LIMIT)
            : text.contains("@") ? byEmail(text) : personnel.searchActive(text, null, LIMIT);
        return new WorkflowAssigneeCandidateListResponseBody(rows.stream()
            .map(row -> new WorkflowAssigneeCandidateBody(Long.toString(row.accountId()), row.loginName(),
                row.emailMasked(), row.displayName()))
            .toList());
    }

    /**
     * 完整登录邮箱的精确匹配。
     *
     * @param text 关键字
     * @return 0 或 1 人
     */
    List<OperationsPersonnelCandidate> byEmail(String text) {
        String canonical;
        try {
            canonical = emails.canonicalEmail(text);
        } catch (FailException invalid) {
            return List.of();
        }
        return personnel.searchActive(null, vault.protectCanonicalEmail(canonical), LIMIT);
    }
}
