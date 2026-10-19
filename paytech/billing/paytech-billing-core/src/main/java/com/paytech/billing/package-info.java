/**
 * 计费服务（billing-service）业务实现：计费事实（唯一金额事实来源，决定 2026-10-19 计费事实引擎）的人工登记、Maker-Checker 批准、
 * BillingFactRecorded 发布与读取，以及渠道成本"组件 → 金额"本地纯计算器。
 * <li>渠道成本方案 / 版本 / 范围 / 组件的配置与审批归 channel-connector；自动事实生成不在本期。</li>
 */
package com.paytech.billing;
