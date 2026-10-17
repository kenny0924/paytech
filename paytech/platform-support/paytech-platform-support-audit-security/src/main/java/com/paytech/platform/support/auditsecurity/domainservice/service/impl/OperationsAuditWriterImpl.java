package com.paytech.platform.support.auditsecurity.domainservice.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.auditsecurity.configuration.OperationsAuditProperties;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditChainClosedException;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Event;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditChainStatus;
import com.paytech.platform.support.auditsecurity.domainservice.repository.OperationsAuditWriteRepository;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditWriter;

import tools.jackson.databind.ObjectMapper;

/** 独立审计事务只仲裁发生日链头，不读取或锁定业务身份。 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public class OperationsAuditWriterImpl implements OperationsAuditWriter {
    private final Clock clock;
    private final IdGenerator ids;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final OperationsAuditWriteRepository repository;

    public OperationsAuditWriterImpl(Clock clock, IdGenerator ids, ObjectMapper json,
        @Qualifier("auditWriteTransactionManager") PlatformTransactionManager manager,
        OperationsAuditProperties budgets, OperationsAuditWriteRepository repository) {
        this.clock = clock;
        this.ids = ids;
        this.json = json;
        this.repository = repository;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(budgets.getTransactionTimeoutSeconds());
    }

    @Override
    public void write(Event event) {
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> append(event, receivedAt));
    }

    private void append(Event event, Instant receivedAt) {
        Instant occurredAt = event.occurredAt().truncatedTo(ChronoUnit.MICROS);
        var date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        repository.ensureChain(ids.nextId(), "OPERATIONS-" + date, date, receivedAt);
        var chain = repository.lockChain(date);
        if (chain.status() != OperationsAuditChainStatus.OPEN) {
            throw new AuditChainClosedException();
        }
        int sequence = chain.sequence() + 1;
        long eventId = ids.nextId();
        List<HashedAttribute> attributes = IntStream.range(0, event.attributes().size()).mapToObj(index -> {
            var attribute = event.attributes().get(index);
            return new HashedAttribute(index + 1, attribute.key(), attribute.value(), hash(attribute.value()));
        }).toList();
        String attributeHash = attributeRoot(attributes);
        String eventHash = eventHash(event, eventId, chain.id(), sequence, chain.lastHash(), attributeHash, occurredAt);
        repository.insertEvent(eventId, chain.id(), sequence, chain.lastHash(), attributeHash, eventHash,
            event.identity().actorType().getCode(), event.identity().actorId(), event.identity().tenantId(), event.action(), event.target().type(),
            event.target().id(), event.result().getCode(), event.requestId(), occurredAt, receivedAt);
        attributes.forEach(attribute -> repository.insertAttribute(ids.nextId(), eventId, attribute.number(),
            attribute.key(), attribute.value(), attribute.hash(), receivedAt));
        finishEvent(event, chain.id(), sequence, eventHash, eventId, occurredAt, receivedAt);
    }

    private String eventHash(Event event, long eventId, long chainId, int sequence, String previous,
        String attributeHash, Instant occurredAt) {
        return hash(json.writeValueAsString(List.of("OPERATIONS_AUDIT_V1", eventId, chainId, sequence,
            previous == null ? "" : previous, attributeHash, event.identity().actorId(),
            event.identity().tenantId() == null ? "" : event.identity().tenantId().toString(),
            event.action(), event.target().type(), event.target().id() == null ? "" : event.target().id(),
            event.result().getCode(), event.requestId(), occurredAt.toString())));
    }

    private void finishEvent(Event event, long chainId, int sequence, String eventHash, long eventId,
        Instant occurredAt, Instant receivedAt) {
        if (repository.advanceChain(chainId, sequence, eventHash, receivedAt) != 1) {
            throw new IllegalStateException("Operations 审计链序号冲突");
        }
        if (event.result() != Result.SUCCEEDED) {
            repository.insertSecurity(ids.nextId(), event.action(), event.identity().actorId(),
                event.identity().tenantId(), Long.toString(eventId), eventHash, occurredAt, receivedAt);
        }
    }

    private String attributeRoot(List<HashedAttribute> attributes) {
        var inputs = attributes.stream()
            .map(attribute -> List.of(attribute.number(), attribute.key(), "INTERNAL", attribute.hash())).toList();
        // 历史单属性使用平铺四元组，多属性按 attr_no 编码；不能变更已有 Canonical 版本。
        return hash(json.writeValueAsString(inputs.size() == 1 ? inputs.getFirst() : inputs));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private record HashedAttribute(int number, String key, String value, String hash) {
    }
}
