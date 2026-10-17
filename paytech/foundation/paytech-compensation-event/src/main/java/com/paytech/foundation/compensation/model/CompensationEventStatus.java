package com.paytech.foundation.compensation.model;

import java.util.HashMap;
import java.util.Map;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 持久化补偿事件状态。
 *
 * @author Codex
 */
public enum CompensationEventStatus implements CodeEnum {

    INIT("INIT"),

    PROCESSING("PROCESSING"),

    ERROR("ERROR"),

    FAILED("FAILED");

    private static final Map<String, CompensationEventStatus> VALUES_BY_CODE = indexByCode();

    private final String code;

    CompensationEventStatus(String code) {
        this.code = code;
    }

    /**
     * 根据稳定编码解析状态。
     *
     * @param code
     *            数据库存储的稳定编码
     * @return 对应状态
     */
    public static CompensationEventStatus fromCode(String code) {
        CompensationEventStatus status = VALUES_BY_CODE.get(code);
        if (status == null) {
            throw new IllegalArgumentException("未知补偿事件状态编码: " + code);
        }
        return status;
    }

    private static Map<String, CompensationEventStatus> indexByCode() {
        Map<String, CompensationEventStatus> statuses = new HashMap<>();
        for (CompensationEventStatus status : values()) {
            CompensationEventStatus duplicate = statuses.putIfAbsent(status.getCode(), status);
            if (duplicate != null) {
                throw new IllegalStateException("补偿事件状态编码重复: " + status.getCode());
            }
        }
        return Map.copyOf(statuses);
    }

    @Override
    public String getCode() {
        return code;
    }
}
