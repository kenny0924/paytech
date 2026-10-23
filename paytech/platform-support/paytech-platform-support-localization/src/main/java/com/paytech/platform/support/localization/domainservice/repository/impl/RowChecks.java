package com.paytech.platform.support.localization.domainservice.repository.impl;

import org.springframework.dao.DuplicateKeyException;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

/** 仓储共用的行数检查与唯一键冲突转换。 */
final class RowChecks {

    private RowChecks() {
    }

    /**
     * 条件更新必须恰好命中一行。
     *
     * @param rows 影响行数
     * @param subject 业务对象描述
     */
    static void requireUpdated(int rows, String subject) {
        if (rows != 1) {
            throw new ConcurrentRequestException("已被其他操作修改，请刷新后重试: " + subject);
        }
    }

    /**
     * 插入必须写入预期行数。
     *
     * @param rows 影响行数
     * @param expected 预期行数
     * @param subject 业务对象描述
     */
    static void requireInserted(int rows, int expected, String subject) {
        if (rows != expected) {
            throw new ErrorException("插入行数异常: " + subject + "，预期 " + expected + "，实际 " + rows);
        }
    }

    /**
     * 只把指定唯一索引的冲突转换为业务拒绝，其他冲突保留原异常。
     *
     * @param failure 唯一键冲突
     * @param indexName 唯一索引名
     * @param message 业务拒绝消息
     * @return 转换后的异常
     */
    static RuntimeException duplicate(DuplicateKeyException failure, String indexName, String message) {
        if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains(indexName)) {
            return failure;
        }
        return new BizCheckFailException(message, failure);
    }

    /**
     * 版本已到整型上限时拒绝，避免溢出。
     *
     * @param version 当前版本
     * @param subject 业务对象描述
     */
    static void requireVersionAvailable(int version, String subject) {
        if (version == Integer.MAX_VALUE) {
            throw new BizCheckFailException("版本已达上限，不能继续修改: " + subject);
        }
    }
}
