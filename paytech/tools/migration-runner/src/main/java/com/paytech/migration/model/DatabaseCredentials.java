package com.paytech.migration.model;

/**
 * 从环境变量读取的服务库连接信息。
 *
 * <p>
 * {@link #toString()} 不输出 URL 与密码，避免进入日志。
 *
 * @param url JDBC URL
 * @param user 数据库账号
 * @param password 数据库密码
 * @author Claude
 */
public record DatabaseCredentials(String url, String user, String password) {

    @Override
    public String toString() {
        return "DatabaseCredentials[user=" + user + "]";
    }
}
