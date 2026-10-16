# Alipay+ 接入交付记录

## 1. 阶段划分

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；沙箱直连探测 | **已确认**（2026-10-16） |
| 2 | 引擎补函数（技术方案 T1） | **已交付并发布 mor-test，待确认**（提交 `59459ba`，未推送） |
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
- **未执行（交付时）：** 未提交、未发布、未改 mor-test。

## 5. 阶段 2 提交与发布（2026-10-16）

- **授权：** 用户“授权”（提交与发布）、“你执行吧”（执行 Platform 替换）。
- **提交：** `59459ba` feat(platform): urlEncode/urlDecode and MONEY_MAJOR_UNITS for Alipay+ integration（16 个文件，私有暂存区提交，只含本任务改动）；未推送。
- **构建：** 在 `59459ba` 的干净 worktree 离线打包 `platform-support-web`（跳过测试，测试已在阶段 2 跑过），jar SHA-256 前缀 `f8c8288690ddcce8`，确认包含 3 个新函数类；镜像 `mor-test/platform-support-service:alipay-plus-20261016` = `sha256:ee17f35adfb9c9b6f097573e5682280e69acc5f758ce3fc0f4752b4b613a3de7`。
- **差异：** 发布前运行的 `63ae4cde…`（Webhook 发布，构建自 `3cbbfce`）到 `59459ba`，Platform 只差本次 3 个函数；无 SQL、无配置变更。
- **备份：** `~/Code-MoR-backups/20261016-alipay-plus-platform/20261016T065022Z/`（Compose 文件、容器 inspect、SHA256SUMS）。
- **发布：** `./test-environment/compose.sh -f "$PWD/requirements/20261016/platform-support-AlipayPlus接入/release/compose-candidate.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service`（[compose-candidate.yaml](./release/compose-candidate.yaml) 只替换 Platform 镜像；`compose.sh` 已自带 `compose.yaml` 与 `compose.applications.yaml`，重复传入会因 `security_opt` 重复而校验失败）。07:50:18Z 启动，healthy，镜像核对为 `ee17f35a…`；启动日志无 ERROR，仅常规 BeanPostProcessor WARN；其余 mor-test 容器状态不变。
- **回归：** PayBy UAT（[uat_cases.py](../../20261014/platform-support-PayBy接入/assets/harness/uat_cases.py)）5 个不依赖已有订单的用例全部 PASSED（`62004`×3、`62007`、余额查询 `0`），带签名响应验签通过；`UAT_UNPAID_QUERY_CANCEL` 需要新建未付款订单，本次未跑。
- **回退：** 把覆盖文件换成 [Webhook 发布的 compose-candidate.yaml](../../20261015/platform-support-Webhook投递/release/compose-candidate.yaml)（`63ae4cde…`）后执行同一命令。
