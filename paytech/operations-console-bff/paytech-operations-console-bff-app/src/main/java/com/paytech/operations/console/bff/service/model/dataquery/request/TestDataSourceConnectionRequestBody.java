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

/** 测试草稿连接配置：单个连接执行 SELECT 1 并检查账号权限，不保存配置。 */
@Getter
@Setter
public class TestDataSourceConnectionRequestBody {

    /**
     * 已保存数据源 ID
     * <li>可选，规范正整数字符串；带上时检查结果回写该数据源的最后一次检查，且 password 为空时使用已保存的密码。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "dataSourceId 必须为规范正整数字符串")
    private String dataSourceId;

    /**
     * 引擎类型
     * <li>MYSQL 或 DORIS。</li>
     */
    @NotBlank
    @Pattern(regexp = "MYSQL|DORIS", message = "engineType 只能为 MYSQL 或 DORIS")
    private String engineType;

    /** 主机名或 IP，1..255 字符。 */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "host 只能包含字母、数字、点、冒号、下划线与连字符")
    private String host;

    /** 端口，1..65535。 */
    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    /** 默认库，可选；为空表示连接整个实例。 */
    @Pattern(regexp = "[A-Za-z0-9_$]{1,128}", message = "databaseName 只能包含字母、数字、下划线与 $")
    private String databaseName;

    /** 只读账号，1..128 字符。 */
    @NotBlank
    @Size(max = 128)
    private String username;

    /**
     * 密码
     * <li>不带 dataSourceId 时必填；带 dataSourceId 且为空时使用已保存的密码。</li>
     */
    @Size(max = 256)
    private String password;

    /** 驱动参数，可选；只允许五项白名单参数。 */
    private Map<String, String> connectionParams;
}
