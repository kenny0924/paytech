package com.paytech.instance.manifest;

/**
 * 实例清单校验失败的类别。
 *
 * @author Claude
 */
public enum ManifestViolationCode {

    /**
     * 文件不存在、不是合法 YAML、存在重复键或为空。
     */
    UNREADABLE,

    /**
     * 缺少必填字段。
     */
    REQUIRED,

    /**
     * 出现 Schema 未声明的字段。
     */
    UNKNOWN_PROPERTY,

    /**
     * modules 中出现 design/01 §5 以外的可选模块。
     */
    UNKNOWN_MODULE,

    /**
     * 服务名不属于 design/03 §1.2 的服务清单。
     */
    UNKNOWN_SERVICE,

    /**
     * 字段类型与 Schema 不符。
     */
    TYPE_MISMATCH,

    /**
     * 取值不在枚举范围内。
     */
    ENUM_MISMATCH,

    /**
     * 字符串不符合格式。
     */
    PATTERN_MISMATCH,

    /**
     * 字符串为空或只含空白。
     */
    BLANK_VALUE,

    /**
     * 数值超出范围。
     */
    OUT_OF_RANGE,

    /**
     * 列表或映射元素少于下限。
     */
    TOO_FEW_ITEMS,

    /**
     * 要求唯一的列表出现重复元素。
     */
    DUPLICATE_ITEM,

    /**
     * 业务端口或管理端口（业务端口 + 1000）在服务之间重复。
     */
    DUPLICATE_PORT,

    /**
     * Snowflake worker id 在服务之间重复。
     */
    DUPLICATE_WORKER_ID,

    /**
     * 品牌 code 重复。
     */
    DUPLICATE_BRAND,

    /**
     * 启用的可选模块缺少版本。
     */
    MODULE_VERSION_REQUIRED,

    /**
     * 服务数据库名与服务清单不一致。
     */
    DATABASE_MISMATCH,

    /**
     * versions.schema 登记了未部署的服务。
     */
    SCHEMA_VERSION_SERVICE_NOT_DEPLOYED
}
