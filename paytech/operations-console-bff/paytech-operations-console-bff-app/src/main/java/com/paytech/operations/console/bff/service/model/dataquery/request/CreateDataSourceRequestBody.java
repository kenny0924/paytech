package com.paytech.operations.console.bff.service.model.dataquery.request;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新建数据源；新建后状态为 INACTIVE，启用前必须通过连接检查。 */
@Getter
@Setter
public class CreateDataSourceRequestBody {

    /**
     * 数据源编码
     * <li>全局唯一，创建后不可修改，删除后也不能复用；格式 [A-Z][A-Z0-9_]{1,63}。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "dataSourceCode 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String dataSourceCode;

    /**
     * 数据源名称
     * <li>1..128 字符，保存时去除首尾空白。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String dataSourceName;

    /**
     * 引擎类型
     * <li>MYSQL 或 DORIS。</li>
     */
    @NotBlank
    @Pattern(regexp = "MYSQL|DORIS", message = "engineType 只能为 MYSQL 或 DORIS")
    private String engineType;

    /**
     * 主机名或 IP
     * <li>1..255 字符；不接受 JDBC URL，不做主机白名单。</li>
     */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "host 只能包含字母、数字、点、冒号、下划线与连字符")
    private String host;

    /**
     * 端口
     * <li>1..65535。</li>
     */
    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    /**
     * 默认库
     * <li>可选；为空表示连接整个实例，模板必须写 库.表。格式 [A-Za-z0-9_$]{1,128}。</li>
     */
    @Pattern(regexp = "[A-Za-z0-9_$]{1,128}", message = "databaseName 只能包含字母、数字、下划线与 $")
    private String databaseName;

    /**
     * 只读账号
     * <li>1..128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String username;

    /**
     * 账号密码
     * <li>1..256 字符；只保存 Vault Token，不回显、不进日志。</li>
     */
    @NotBlank
    @Size(max = 256)
    private String password;

    /**
     * 驱动参数
     * <li>可选；只允许 sslMode、connectTimeout、socketTimeout、connectionTimeZone、characterEncoding 五项，值 1..64 字符。</li>
     * <li>allowMultiQueries、allowLoadLocalInfile 等安全参数由服务固定，不能通过此处覆盖。</li>
     */
    private Map<String, String> connectionParams;

    /**
     * 连接池最大连接数
     * <li>1..data-query.limits.max-pool-size（默认 10）。</li>
     */
    @NotNull
    @Min(1)
    private Integer maxPoolSize;

    /**
     * 说明
     * <li>可选，最多 512 字符。</li>
     */
    @Size(max = 512)
    private String description;
}
