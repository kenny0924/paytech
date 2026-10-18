package com.paytech.card.auto.purge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.card.ext.job.CardInstrumentPurgeJobHandler;
import com.paytech.card.test.base.AbstractStoredCardTest;

/**
 * 孤立卡交易工具回收（design/05 §2.2 第 3 点）：Payment 两段创建的第二段失败时 card 侧留下从未签发票据的工具，由 XXL-Job 入口
 * {@link CardInstrumentPurgeJobHandler} 经真实 Processor 在过期超过宽限期（测试配置 1 天）后条件删除。
 */
class OrphanInstrumentPurgeTest extends AbstractStoredCardTest {
    private static final long BEYOND_GRACE_SECONDS = 2 * 24 * 3600;

    @Autowired
    private CardInstrumentPurgeJobHandler purgeJob;

    /** 过期超过宽限期且从未签票的孤立工具被回收；签过票的、作为已存卡来源的、尚在宽限期内的都保留；卡与凭证不删除。 */
    @Test
    void test_purgeExpiredOrphans() {
        String orphan = newInstrument();
        String ticketed = newInstrument();
        issueTicket(ticketed);
        String recent = newInstrument();
        String storedSource = paySucceeded("70001", true, EXPIRY_YEAR);
        for (String ref : List.of(orphan, ticketed, storedSource)) {
            mapper.expireInstrument(Long.parseLong(ref), BEYOND_GRACE_SECONDS);
        }
        mapper.expireInstrument(Long.parseLong(recent), 60);

        int purged = purgeJob.runOnce();

        var remaining = mapper.cardInstruments().stream().map(row -> String.valueOf(row.get("instrumentId"))).toList();
        assertAll(() -> assertEquals(1, purged), () -> assertFalse(remaining.contains(orphan), "孤立工具未回收"),
            () -> assertTrue(remaining.containsAll(List.of(ticketed, recent, storedSource)), "非孤立工具被删除"),
            () -> assertEquals(1, mapper.cards().size()));
    }

    /** 没有可回收的工具时返回 0，可重复执行。 */
    @Test
    void test_noOrphanFound() {
        newInstrument();

        assertAll(() -> assertEquals(0, purgeJob.runOnce()), () -> assertEquals(0, purgeJob.runOnce()),
            () -> assertEquals(1, mapper.cardInstruments().size()));
    }

    /** 新建一个手输卡工具，返回工具引用。 */
    String newInstrument() {
        return (String)createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false))
            .get("instrumentRef");
    }
}
