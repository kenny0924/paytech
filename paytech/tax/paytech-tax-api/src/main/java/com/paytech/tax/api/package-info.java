/**
 * 税务服务对外合同：算税（POST /tax/v1/calculations）、按 ID 查计算、按订单号查税务全貌（GET /tax/v1/orders/{orderNo}）。
 * <li>金额、ID 为十进制字符串；时间为 UTC ISO-8601；法定日期为 yyyy-MM-dd；枚举字段以 String 承载 {@code api.enums} 中的 Code。</li>
 */
package com.paytech.tax.api;
