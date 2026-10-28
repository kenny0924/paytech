package com.paytech.operations.console.bff.integration;

import java.util.Map;
import java.util.SortedSet;

/**
 * Platform 运营账号显示名的本地端口；显示名取值（登录名优先、否则脱敏邮箱）与 OPERATIONS Realm 限定归 Platform
 * <li>只读，供页面把操作人账号 ID 展示为可读名称；不返回完整邮箱、账号状态或角色。</li>
 *
 * @author Claude
 */
public interface OperationsAccountLabelClient {

    /**
     * 按账号 ID 批量查询显示名
     *
     * @param accountIds 十进制账号 ID，非空；超过 Platform 单次上限时分批查询
     * @return 账号 ID 到显示名；Platform 未返回的账号不在结果中
     */
    Map<String, String> findLabels(SortedSet<String> accountIds);
}
