package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationProvider {

    /** 不可复用的具体发送账号身份。 */
    private Long providerId;

    /** 与出站适配器绑定的账号编码。 */
    private String providerCode;

    /** 支持的发送媒介。 */
    private NotificationMedium medium;

    /**
     * 服务商类型，由发送适配器声明
     * <li>如 AMAZON_SES；创建后不可改，发送时按此精确选择唯一适配器</li>
     */
    private String providerType;

    /** 停用阻止新发送，不阻止历史查询。 */
    private NotificationConfigurationStatus status;

    /**
     * 服务商区域
     * <li>如 me-central-1；为空表示未填写，配置不完整</li>
     */
    private String region;

    /**
     * 发件地址
     * <li>已按平台邮箱规则规范化；须为该区域已验证身份，为空表示未填写</li>
     */
    private String fromAddress;

    /** SES Configuration Set，可空。 */
    private String configurationSet;

    /**
     * 密钥对在 Vault 中的 Token
     * <li>为空表示未录入；只在发送前 Reveal，禁止日志、接口返回与审计</li>
     */
    private String credentialVaultToken;

    /** 最近一次录入或更换密钥的 UTC 微秒时间，未录入为空。 */
    private Instant credentialUpdateTime;

    /**
     * 接收投递事件的 SQS 队列 URL
     * <li>可空；为空时不拉取事件</li>
     */
    private String eventQueueUrl;

    /**
     * 最近一次成功拉取事件队列的时间
     * <li>运行状态，由拉取任务更新，不推进 version</li>
     */
    private Instant eventPollTime;

    /**
     * 最近一次拉取失败的安全错误码
     * <li>成功拉取后清空；运行状态，不推进 version</li>
     */
    private String eventPollError;

    /**
     * 最近一次从事件队列收到消息的时间
     * <li>运行状态，不推进 version</li>
     */
    private Instant eventReceiveTime;

    /** 当前配置的并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /**
     * 缺失的发送配置
     * <li>按字段名返回：region、fromAddress、credential；空列表表示配置完整</li>
     */
    public List<String> missingConfiguration() {
        var missing = new ArrayList<String>();
        if (region == null) {
            missing.add("region");
        }
        if (fromAddress == null) {
            missing.add("fromAddress");
        }
        if (credentialVaultToken == null) {
            missing.add("credential");
        }
        return missing;
    }

    /** 密钥不进入默认字符串表示。 */
    @Override
    public String toString() {
        return "NotificationProvider[providerCode=" + providerCode + ", providerType=" + providerType + "]";
    }
}
