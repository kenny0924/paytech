/**
 * 计费服务（billing-service）业务实现：经济事实（唯一经济事实来源，D37）与渠道成本方案。
 * <li>经济事实三表本期只有读代码与登记不变量校验，没有生产写入；登记接口与 EconomicFactRecorded 事件在第 11a 步实现。</li>
 */
package com.paytech.billing;
