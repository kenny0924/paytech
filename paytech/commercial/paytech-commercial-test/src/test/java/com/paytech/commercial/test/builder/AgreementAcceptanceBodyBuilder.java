package com.paytech.commercial.test.builder;

import com.paytech.commercial.api.agreement.request.AgreementFileEntry;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;

/**
 * POST /agreements/v1/{id}/parties/{partyId}/acceptance 请求体 Builder
 * <li>click() 为在线点击接受（带实际接受 Actor，无执行文件）；eSignature() 为电子签（带执行文件）。build() 不修复负例。</li>
 */
public final class AgreementAcceptanceBodyBuilder {
    /** 实际接受的外部 Actor */
    public static final String ACCEPTED_BY = "930000000000931";

    /** 执行文件 */
    public static final String EXECUTED_FILE_ID = "930000000000935";

    /** 执行文件 SHA-256 */
    public static final String EXECUTED_HASH = "b".repeat(64);

    private final RecordAgreementAcceptanceRequestBody body = new RecordAgreementAcceptanceRequestBody();

    private AgreementAcceptanceBodyBuilder() {
    }

    /**
     * 点击接受，接受时刻早于固定业务时点一小时
     *
     * @param expectedVersion 期望 Root 版本
     * @param reference 接受事件编号
     * @return 新 Builder
     */
    public static AgreementAcceptanceBodyBuilder click(long expectedVersion, String reference) {
        AgreementAcceptanceBodyBuilder builder = new AgreementAcceptanceBodyBuilder();
        builder.body.setExpectedChangeVersion(expectedVersion);
        builder.body.setAcceptanceReference(reference);
        builder.body.setAcceptanceMethod(AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE.getCode());
        builder.body.setAcceptedByActorId(ACCEPTED_BY);
        builder.body.setAcceptedAt("2026-09-11T11:00:00Z");
        return builder;
    }

    /**
     * 电子签，带唯一执行文件
     *
     * @param expectedVersion 期望 Root 版本
     * @param reference 接受事件编号
     * @return 新 Builder
     */
    public static AgreementAcceptanceBodyBuilder eSignature(long expectedVersion, String reference) {
        AgreementAcceptanceBodyBuilder builder = click(expectedVersion, reference);
        builder.body.setAcceptanceMethod(AgreementPartyAcceptanceMethod.E_SIGNATURE.getCode());
        builder.body.setAcceptedByActorId(null);
        builder.body.setAcceptedAt("2026-09-11T11:30:00Z");
        builder.executedDocument(EXECUTED_FILE_ID, EXECUTED_HASH);
        return builder;
    }

    /** 设置接受时刻。 */
    public AgreementAcceptanceBodyBuilder acceptedAt(String value) {
        body.setAcceptedAt(value);
        return this;
    }

    /** 设置实际接受 Actor。 */
    public AgreementAcceptanceBodyBuilder acceptedByActorId(String value) {
        body.setAcceptedByActorId(value);
        return this;
    }

    /** 设置执行文件；fileId 为空时清除。 */
    public AgreementAcceptanceBodyBuilder executedDocument(String fileId, String hash) {
        if (fileId == null) {
            body.setExecutedDocument(null);
            return this;
        }
        AgreementFileEntry file = new AgreementFileEntry();
        file.setFileId(fileId);
        file.setContentHash(hash);
        body.setExecutedDocument(file);
        return this;
    }

    /** 返回请求体。 */
    public RecordAgreementAcceptanceRequestBody build() {
        return body;
    }
}
