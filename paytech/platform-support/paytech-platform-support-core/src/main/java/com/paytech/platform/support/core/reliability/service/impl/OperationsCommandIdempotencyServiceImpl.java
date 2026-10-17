package com.paytech.platform.support.core.reliability.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.core.reliability.repository.CommandIdempotencyRepository;
import com.paytech.platform.support.accesscontrol.integration.idempotency.OperationsCommandIdempotencyService;
import com.paytech.platform.support.accesscontrol.domain.operations.CommandIdempotencyContext;
import com.paytech.platform.support.accesscontrol.integration.signing.OperationsTokenCryptography;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography.HmacInput;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography.VersionedHmacBatch;

import lombok.AllArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** Operations 管理命令共享的调用方隔离、载荷哈希、回放与完成语义。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsCommandIdempotencyServiceImpl implements OperationsCommandIdempotencyService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private final IdGenerator ids;
    private final ObjectMapper json;
    private final OperationsTokenCryptography crypto;
    private final CommandIdempotencyRepository repository;

    @Override
    public CommandIdempotencyContext prepare(String actorId, String operation, String key,
        String requestHashNamespace, Object canonicalRequest, String opaqueReferenceNamespace) {
        requireKey(key);
        if (actorId == null || actorId.isBlank() || operation == null || operation.isBlank()
            || requestHashNamespace == null || requestHashNamespace.isBlank() || canonicalRequest == null) {
            throw new IllegalArgumentException("幂等哈希命名空间和规范请求不能为空");
        }
        var inputs = new ArrayList<HmacInput>(3);
        inputs.add(new HmacInput(requestHashNamespace, json.writeValueAsString(canonicalRequest)));
        inputs.add(new HmacInput("operations-caller-v1", actorId));
        if (opaqueReferenceNamespace != null) {
            inputs.add(new HmacInput(opaqueReferenceNamespace, key));
        }
        VersionedHmacBatch batch = hmacs(inputs);
        if (batch.digests().size() != inputs.size()) {
            throw new ServiceUnavailableException();
        }
        return new PreparedCommandIdempotency(batch.digests().get(1), operation, key,
            batch.digests().get(0), opaqueReferenceNamespace == null ? null : batch.digests().get(2));
    }

    private VersionedHmacBatch hmacs(java.util.List<HmacInput> inputs) {
        try {
            VersionedHmacBatch batch = crypto.versionedHmacs(inputs);
            if (batch == null) {
                throw new ServiceUnavailableException();
            }
            return batch;
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }

    private void requireKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidParameterException("命令标识必须为 8 至 128 位字母、数字或 . _ : - 字符");
        }
    }

    private boolean isDuplicateIndex(DuplicateKeyException exception, String indexName) {
        return String.valueOf(exception.getMostSpecificCause().getMessage()).contains(indexName);
    }

    private final class PreparedCommandIdempotency implements CommandIdempotencyContext {
        private final String callerScope;
        private final String operation;
        private final String key;
        private final String requestHash;
        private final String opaqueReference;

        private PreparedCommandIdempotency(String callerScope, String operation, String key,
            String requestHash, String opaqueReference) {
            this.callerScope = callerScope;
            this.operation = operation;
            this.key = key;
            this.requestHash = requestHash;
            this.opaqueReference = opaqueReference;
        }

        @Override
        public String previous() {
            String result = repository.previousCommand(callerScope, operation, key);
            if (result == null) {
                return null;
            }
            if (!result.startsWith(requestHash + ":")) {
                throw new ConcurrentRequestException("同一命令标识不能用于不同请求");
            }
            return result.substring(requestHash.length() + 1);
        }

        @Override
        public void complete(String responseReference, Instant now) {
            try {
                repository.completeCommand(ids.nextId(), callerScope, operation, key, requestHash, responseReference,
                    now);
            } catch (DuplicateKeyException exception) {
                if (!isDuplicateIndex(exception, "uk_command_idempotency")) {
                    throw exception;
                }
                throw new ConcurrentRequestException("命令标识正在使用，请重新查询结果", exception);
            }
        }

        @Override
        public String opaqueReference() {
            return opaqueReference;
        }

        @Override
        public String toString() {
            return "PreparedCommandIdempotency[REDACTED]";
        }
    }
}
