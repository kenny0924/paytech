package com.paytech.risk.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.core.dal.dataobject.CommandIdempotency;
import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domain.enums.CommandOperationType;

/**
 * 命令幂等记录的 SQL 映射。
 */
public interface CommandIdempotencyMapper {

    void upsertClaimCandidate(@Param("record") CommandIdempotency record);

    CommandIdempotency selectByIdentityForUpdate(
        @Param("callerScope") String callerScope,
        @Param("operationType") CommandOperationType operationType,
        @Param("idempotencyKey") String idempotencyKey
    );

    int updateProcessingResponseReference(
        @Param("callerScope") String callerScope,
        @Param("operationType") CommandOperationType operationType,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("responseRef") String responseRef,
        @Param("now") Instant now,
        @Param("processingStatus") CommandIdempotencyStatus processingStatus
    );

    int markSucceeded(
        @Param("callerScope") String callerScope,
        @Param("operationType") CommandOperationType operationType,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("responseRef") String responseRef,
        @Param("now") Instant now,
        @Param("processingStatus") CommandIdempotencyStatus processingStatus,
        @Param("succeededStatus") CommandIdempotencyStatus succeededStatus
    );
}
