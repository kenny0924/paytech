package com.paytech.commercial.domainservice.repository.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 写入前把时间截断到 DATETIME(6) 的微秒精度，使对象与库内值一致，后续以时间为条件的更新可以命中。 */
final class PersistenceTimes {
    private PersistenceTimes() {
    }

    /**
     * 截断到微秒。
     *
     * @param value 时间，可空
     * @return 截断后的时间，入参为空时为空
     */
    static Instant micros(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
