package com.paytech.client.master.service.processor;

import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.master.domain.onboarding.ClientDraftPatch;
import com.paytech.foundation.common.error.FailException;

import jakarta.validation.Validator;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * 入驻写入口共用的输入规范化与写事务唯一键处理
 * <li>唯一键冲突回滚整个事务；只有约定的索引转换为明确结果，其他冲突原样抛出。</li>
 */
final class ClientOnboardingCommands {

    /** 账号未完成申请名额的唯一索引。 */
    private static final String ACTIVE_SLOT_INDEX = "uk_client_app_account_active";

    /** 同一组织、人员与类型的关系唯一索引。 */
    private static final String RELATION_INDEX = "uk_party_relation_identity";

    /** 同一申请、主体、要求、类型与文件的材料唯一索引。 */
    private static final String DOCUMENT_INDEX = "uk_app_doc_client";

    /** 仅提供静态步骤。 */
    private ClientOnboardingCommands() { }

    /** 规范化人员资料补丁并要求是完整人员。 */
    static ClientDraftPatch personPatch(DraftPatchRequestBody request, Validator validator) {
        ClientDraftPatch person = ClientDraftPatch.normalize(request, validator);
        person.requirePerson();
        return person;
    }

    /**
     * 账号名额竞争：Redis 租约失效时，由账号未完成名额唯一索引回滚竞争事务，再重试读取胜出的申请。
     *
     * @param transaction 一次完整的本地写事务
     * @return 事务结果
     *         <li>最多重试两次；其他唯一键冲突或重试用尽时原样抛出。</li>
     */
    static <T> T retryActiveSlotCollision(Supplier<T> transaction) {
        for (int attempt = 0; ; attempt++) {
            try {
                return transaction.get();
            } catch (DuplicateKeyException collision) {
                String reason = collision.getMostSpecificCause().getMessage();
                if (attempt >= 2 || reason == null || !reason.contains(ACTIVE_SLOT_INDEX)) {
                    throw collision;
                }
            }
        }
    }

    /** 申请锁失效时的并发同一关系由唯一键回滚并明确冲突。 */
    static <T> T relationWrite(TransactionTemplate writes, Supplier<T> action) {
        try {
            return writes.execute(tx -> action.get());
        } catch (DuplicateKeyException collision) {
            String reason = collision.getMostSpecificCause().getMessage();
            if (reason != null && reason.contains(RELATION_INDEX)) {
                throw new FailException(RELATION_ALREADY_EXISTS);
            }
            throw collision;
        }
    }

    /** 申请锁失效时的并发同一材料关联由唯一键回滚并明确冲突。 */
    static <T> T documentWrite(TransactionTemplate writes, Supplier<T> action) {
        try {
            return writes.execute(tx -> action.get());
        } catch (DuplicateKeyException collision) {
            String reason = collision.getMostSpecificCause().getMessage();
            if (reason != null && reason.contains(DOCUMENT_INDEX)) {
                throw new FailException(DOCUMENT_ALREADY_EXISTS);
            }
            throw collision;
        }
    }
}
