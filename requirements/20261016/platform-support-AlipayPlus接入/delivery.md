# Alipay+ 接入交付记录

## 1. 阶段划分

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；沙箱直连探测 | **已确认**（2026-10-16） |
| 2 | 引擎补函数（技术方案 T1） | **已交付，待确认**；提交与发布未授权 |
| 3 | mor-test 配置与不需付款的用例 | 待定 |
| 4 | 已付款链路（用户付款） | 待定 |

## 2. 阶段 1 结果（2026-10-16）

- **已读：** 对接包全部文件（凭据只按结构读取，未输出）、Alipay+ cancelPayment 官方页、引擎函数白名单与脚本运行源码、mor-test 现行 4 个标准 API 的字段。
- **沙箱直连探测**（本机 Python，未经 Platform；凭据只在进程内从仓库外读取）：
  - 签名被接受，响应验签通过；签名值未 URL 编码会被拒绝；Request-Time 用 UTC `Z`（含毫秒）可用。
  - `ALIPAY_CN`/USD、`ALIPAY_HK`/HKD、`TRUEMONEY`/THB 收银台下单都返回 `PAYMENT_IN_PROCESS` + `normalUrl`，没有 `paymentUrl`；缺少 `paymentMethod` 返回 `PARAM_ILLEGAL`。
  - 查询、退款、关单的返回码见需求第 2 节；沙箱没有 `inquiryRefund`。
  - 探测单号前缀 `MORPROBE`，共 7 笔，均已关单并查询确认 `ORDER_IS_CLOSED`。
- **未执行：** 未改代码、mor-test 配置、数据库；未提交。

## 3. 阶段 1 确认（2026-10-16）

用户：“按你的建议来，函数由你来写”。T1～T7 按建议；T1 改 Platform 代码，由本会话实现。

## 4. 阶段 2 结果（2026-10-16）

- **代码**（`platform-support-integration`）：
  - `ScriptFunctions`：新增 `urlEncode`（RFC 3986，UTF-8，大写十六进制）与 `urlDecode`（`+` 保持原样；截断、非十六进制、非法 UTF-8 → 节点 ERROR）。
  - 新增 `MoneyMajorUnitsFunction`（`MONEY_MAJOR_UNITS`）：最小单位非负整数字符串 + 币种 → 主单位十进制，小数位数等于币种精度；币种规则复用 `MoneyMinorUnitsFunction.fractionDigits`。
  - `RegisteredFunctionCatalog` 登记 3 个函数，编辑器目录随之出现。
- **测试**（`platform-support-test`）：
  - `ScriptSigningFunctionsTest`：编码字符集、多字节字符、往返、大小写十六进制解码、非法序列。
  - `ExecutionValueTest`：USD/JPY/KWD 换算与非法输入。
  - 新增 `StandardApiUrlSignedChannelTest`（经标准 API 执行入口、真实出站 Client 与 Mock 渠道）：渠道独立验证 URL 编码签名与签名原文；响应验签、最小单位金额输出 `10.50`；签名有效的拒绝 → FAIL；他人私钥、缺少签名、非法百分号序列 → ERROR；金额超精度 → ERROR 且不发请求。夹具 `url-sign.av`、`url-verify.av`、`url-signed-create-order.json`、`call-url-signed-create-order.json`。
  - `ExternalApiEditorTest` 期望的非脚本函数目录加入 `MONEY_MAJOR_UNITS`。
- **验证：** `./mvnw -o test -pl platform-support-test -am`，`auto.ext.http.standardapi`、`auto.ext.http.externalapi`、`auto.domain` 三个包共 125 个测试通过。未跑整个模块（工作区内有其他会话未提交的 Client 测试）。
- **发现：** 标准 API 的 DECIMAL 输入进入流程后是数值，`MONEY_MINOR_UNITS` 需要 `str(...)` 转换，阶段 3 配置照此书写。
- **未执行：** 未提交、未发布、未改 mor-test。
