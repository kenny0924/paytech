package com.paytech.card.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.card.integration.impl.CardSecurityCodeClientImpl;
import com.paytech.card.test.dal.CardTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

import lombok.RequiredArgsConstructor;

/**
 * mor_card 的空基线与清理：只触碰本库 12 张表（10 §3.1 card 行加 S3 票据表、BIN 库 4 表与消费记录表）与本库卡工具的 Redis CVV 键。
 */
@TestComponent
@RequiredArgsConstructor
public class CardDataMocker implements Clear {
    private static final String DATABASE = "mor_card";

    private final CardTestMapper mapper;
    private final StringRedisTemplate redis;
    private boolean reusable = true;
    private boolean cleanupOwned;

    /** 只允许本服务专用测试库 mor_card，或并行任务隔离用的 mor_card_{后缀}（经 SPRING_DATASOURCE_URL 指定）。 */
    void assertDedicatedDatabase() {
        String name = mapper.databaseName();
        assertTrue(DATABASE.equals(name) || name != null && name.matches(DATABASE + "_[a-z0-9]{1,16}"),
            "card 测试只允许本地 mor_card 或其隔离副本: " + name);
    }

    /** 拒绝共享库和脏基线；缺表时明确提示手工初始化入口。 */
    public void assertEmpty() {
        assertTrue(reusable, "测试库清理失败，须人工核查后重新运行");
        try {
            assertDedicatedDatabase();
            assertEquals(0, mapper.countRows(), "专用库非空，不自动删除原有数据");
            cleanupOwned = true;
        } catch (DataAccessException failure) {
            throw new AssertionError("请先人工审查并以 root 应用 paytech/card/paytech-card-test/src/test/resources/init/ddl.sql "
                + "到 local-environment MySQL（mor_card / card_app）", failure);
        }
    }

    /** 按依赖顺序清理本库全部表，任一失败即禁止环境复用。 */
    @Override
    public void clear() {
        if (!cleanupOwned) {
            return;
        }
        assertDedicatedDatabase();
        try {
            for (Long instrumentId : mapper.cardInstrumentIds()) {
                redis.delete(CardSecurityCodeClientImpl.key(instrumentId));
            }
            assertAll(mapper::clearConsumedVersions, mapper::clearConsumedEvents, mapper::clearCompensationEvents,
                mapper::clearTickets, mapper::clearStoredCards, mapper::clearCardInstruments,
                mapper::clearCardCredentials, mapper::clearCards, mapper::clearBinActivations,
                mapper::clearBinImportErrors, mapper::clearBinRanges, mapper::clearBinVersions);
            assertEquals(0, mapper.countRows());
            cleanupOwned = false;
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }
}
