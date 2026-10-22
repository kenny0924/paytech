package com.paytech.platform.support.api.dataquery.request;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 按原版本修改数据源；编码不可修改，ACTIVE 数据源修改前先做连接检查。 */
@Getter
@Setter
public class UpdateDataSourceRequestBody {

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
     * <li>1..255 字符；不接受 JDBC URL。</li>
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
     * <li>可选；为空表示连接整个实例。格式 [A-Za-z0-9_$]{1,128}。</li>
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
     * 新密码
     * <li>可选；不传或为空表示保留原密码，传入时 1..256 字符，只保存 Vault Token。</li>
     */
    @Size(max = 256)
    private String password;

    /**
     * 驱动参数
     * <li>可选；只允许 sslMode、connectTimeout、socketTimeout、connectionTimeZone、characterEncoding 五项。</li>
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

    /**
     * 调用方读取的原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
