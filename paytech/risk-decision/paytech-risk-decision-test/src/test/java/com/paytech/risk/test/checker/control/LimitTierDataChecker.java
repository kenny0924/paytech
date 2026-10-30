package com.paytech.risk.test.checker.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper;

/**
 * 从真实数据库核对限额档位事实与 TIER 名单投影
 * <li>投影一致：任一时刻 Client 至多在一张 TIER 名单中有生效成员，且就是当前档位对应的名单。</li>
 */
@TestComponent
public class LimitTierDataChecker {
    private final LimitTierTestMapper store;

    public LimitTierDataChecker(LimitTierTestMapper store) {
        this.store = store;
    }

    /**
     * 档位行与投影一致
     *
     * @param clientId Client ID
     * @param tier 期望档位
     * @param version 期望并发版本
     * @return 档位行
     */
    public Map<String, Object> assertTier(long clientId, LimitTier tier, int version) {
        Map<String, Object> row = store.tier(clientId);
        assertNotNull(row, "Client " + clientId + " 应有档位");
        assertAll("档位行与名单投影",
            () -> assertEquals(tier.getCode(), row.get("tier"), "tier"),
            () -> assertEquals(version, ((Number)row.get("version")).intValue(), "version"),
            () -> assertEquals(List.of(tier.listCode()), store.activeTierLists(clientId), "生效 TIER 名单"));
        return row;
    }

    /** Client 没有档位，也不在任何 TIER 名单中。 */
    public void assertNoTier(long clientId) {
        assertAll("无档位", () -> assertNull(store.tier(clientId), "档位行"),
            () -> assertEquals(List.of(), store.activeTierLists(clientId), "生效 TIER 名单"));
    }
}
