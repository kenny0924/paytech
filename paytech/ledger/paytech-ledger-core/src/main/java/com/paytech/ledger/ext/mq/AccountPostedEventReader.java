package com.paytech.ledger.ext.mq;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.ledger.domain.AccountPostingBatch;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AccountPosted Schema v1 信封到 {@link AccountPostingBatch} 的解析
 * <li>信封的 Schema 版本、聚合类型必须匹配；聚合 ID 必须等于载荷主体、币种与分片生成的过账流 ID，聚合版本必须等于 postingSequence。</li>
 * <li>只做协议解析与载荷校验，不访问数据库；不一致或无法解析视为内容非法（{@link IllegalArgumentException}）。</li>
 */
public final class AccountPostedEventReader {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AccountPostedEventReader() {
    }

    /**
     * 解析并核对信封
     *
     * @param event 已通过信封与哈希校验的事件
     * @return 已校验的入账批次
     * @throws IllegalArgumentException Schema 版本、聚合身份不符，Payload 无法绑定或内容非法
     */
    public static AccountPostingBatch read(DomainEvent event) {
        if (event.schemaVersion() != AccountPostedPayload.SCHEMA_VERSION
            || !AccountPostedPayload.AGGREGATE_TYPE.equals(event.aggregateType())) {
            throw new IllegalArgumentException("不支持的 AccountPosted 信封: schemaVersion=" + event.schemaVersion()
                + ", aggregateType=" + event.aggregateType());
        }
        AccountPostedPayload payload;
        try {
            payload = JSON.treeToValue(event.payload(), AccountPostedPayload.class);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("AccountPosted Payload 无法解析: " + event.eventId(), invalid);
        }
        AccountPostingBatch batch = AccountPostingBatch.of(payload);
        if (!batch.getAggregateKey().equals(event.aggregateKey()) || batch.sequence() != event.aggregateVersion()) {
            throw new IllegalArgumentException("AccountPosted 信封聚合身份与 Payload 不一致: " + event.eventId());
        }
        return batch;
    }
}
