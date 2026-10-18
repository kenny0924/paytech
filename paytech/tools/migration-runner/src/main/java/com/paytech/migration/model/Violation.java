package com.paytech.migration.model;

/**
 * 一条迁移校验失败。
 *
 * @param service 服务名
 * @param code 失败类别
 * @param version 相关版本号；与具体版本无关时为 null
 * @param message 中文说明
 * @author Claude
 */
public record Violation(String service, ViolationCode code, Integer version, String message) {

    @Override
    public String toString() {
        return service + (version == null ? "" : " V" + version) + " [" + code + "] " + message;
    }
}
