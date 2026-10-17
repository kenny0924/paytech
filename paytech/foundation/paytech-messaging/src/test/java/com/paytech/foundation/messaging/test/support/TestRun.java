package com.paytech.foundation.messaging.test.support;

import java.security.SecureRandom;
import java.util.Set;

/**
 * 本次测试 JVM 的唯一命名空间：共享 Broker 上的交换机与队列都带此前缀，结束时整体删除。
 */
public final class TestRun {

    /** 运行前缀，例如 {@code mtk3f9a2}。 */
    public static final String PREFIX = "mt" + randomSuffix();

    /** 测试发布服务名。 */
    public static final String PRODUCER = PREFIX + "-billing";

    /** 测试订阅服务名。 */
    public static final String CONSUMER = PREFIX + "-ledger";

    /** 未被任何服务订阅的发布服务名（用于退回场景）。 */
    public static final String ORPHAN_PRODUCER = PREFIX + "-orphan";

    /** 从未声明交换机的发布服务名（用于 Broker 拒绝场景）。 */
    public static final String MISSING_PRODUCER = PREFIX + "-missing";

    /** 测试目录允许的服务名。 */
    public static final Set<String> SERVICES = Set.of(PRODUCER, CONSUMER, ORPHAN_PRODUCER, MISSING_PRODUCER);

    /** 严格有序测试事件。 */
    public static final String STRICT_EVENT = "TestFactRecorded";

    /** 非严格测试事件。 */
    public static final String LOOSE_EVENT = "TestNoteRecorded";

    /** 本服务不订阅的测试事件。 */
    public static final String UNSUBSCRIBED_EVENT = "TestAuditRecorded";

    /** 分区数。 */
    public static final int PARTITIONS = 4;

    private TestRun() {
    }

    private static String randomSuffix() {
        SecureRandom random = new SecureRandom();
        StringBuilder suffix = new StringBuilder();
        for (int index = 0; index < 6; index++) {
            suffix.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
        }
        return suffix.toString();
    }
}
