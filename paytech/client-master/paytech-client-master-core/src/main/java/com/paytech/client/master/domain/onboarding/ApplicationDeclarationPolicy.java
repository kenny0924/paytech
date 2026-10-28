package com.paytech.client.master.domain.onboarding;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;

/**
 * 声明证据的时效规则（technique 3.3）
 * <li>声明绑定作出时的申请资料版本；之后资料再有实际改动，版本不等，声明失效。</li>
 * <li>声明配置了文档版本（DOCUMENT_VERSION）时，还须针对该版本；文档版本变化后旧声明失效。</li>
 */
public final class ApplicationDeclarationPolicy {
    /** 满足要求的声明取值。 */
    public static final String TRUE = "TRUE";

    private ApplicationDeclarationPolicy() {
    }

    /**
     * 声明是否针对申请当前的资料版本与文档版本，不论取值
     *
     * @param row 某类型最新一条声明
     * @param draftRevision 申请当前资料版本
     * @param documentVersion 该声明类型配置的文档版本；未配置为 null
     * @return 针对当前版本时为 true；存量行资料版本为空按 0 比较
     */
    public static boolean isCurrent(ApplicationDeclaration row, long draftRevision, String documentVersion) {
        long declaredRevision = row.getDraftRevision() == null ? 0L : row.getDraftRevision();
        if (declaredRevision != draftRevision) {
            return false;
        }
        return documentVersion == null || documentVersion.equals(row.getTermsVersion());
    }
}
