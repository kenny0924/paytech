package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按新顺序整体重写全部键类型的顺序号：第 n 项（从 1 开始）的 sort_no = n × 10。 */
@Getter
@AllArgsConstructor
public final class ReorderQueryKeyTypesCommand {

    /** 全部键类型按新顺序排列。 */
    private final List<Item> items;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;

    /** 一个键类型及其原版本。 */
    @Getter
    @AllArgsConstructor
    public static final class Item {

        /** 键类型 ID。 */
        private final long keyTypeId;

        /** 调用方读取的原版本。 */
        private final int expectedVersion;
    }
}
