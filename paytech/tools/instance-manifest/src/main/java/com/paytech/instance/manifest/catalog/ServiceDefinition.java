package com.paytech.instance.manifest.catalog;

/**
 * 服务清单中的一个逻辑服务（design/03 §1.2）。
 *
 * @param name 服务名，即 {@code paytech/<name>/} 目录名
 * @param applicationName {@code spring.application.name}
 * @param port 默认业务端口
 * @param database 数据库名；没有数据库时为 null
 * @param source 来源现有工程
 * @author Claude
 */
public record ServiceDefinition(String name, String applicationName, int port, String database, String source) {
}
