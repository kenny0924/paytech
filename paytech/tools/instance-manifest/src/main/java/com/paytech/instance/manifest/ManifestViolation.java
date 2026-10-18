package com.paytech.instance.manifest;

/**
 * 一条清单校验失败。
 *
 * @param path 字段路径，如 {@code services.ledger.port}、{@code brands[0].legalPartyRef}；整份文件为空字符串
 * @param code 失败类别
 * @param message 中文说明
 * @author Claude
 */
public record ManifestViolation(String path, ManifestViolationCode code, String message) {

    @Override
    public String toString() {
        return (path.isEmpty() ? "<manifest>" : path) + " [" + code + "] " + message;
    }
}
