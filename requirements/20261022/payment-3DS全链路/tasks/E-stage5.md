# 阶段 5：两项需求本机端到端验收（3DS 全链路 + 卡 BIN 库）

- 代码：集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-int`（main `1d39cd94` + 六张卡合并结果，已暂存，未提交）。**只读代码；发现缺陷时记录并通知主会话，由主会话决定修复，不自行改代码。**
- 公共规则：[00-common.md](./00-common.md)（第 2、3 节适用；第 3 节的“本机测试库”授权在本卡改为下面的 e2e 授权）。
- 设计与验收：两项需求的 `requirement.md`（AC）、`technique.md` 第 3 节；3DS `technique.md` §3.6“综合验证”；BIN `technique.md` §3.7（页面）。
- 先例与工具：`requirements/20261020/channel-connector-统一对外渠道出口/verification/`（`stage5p2-report.md`、`local-e2e/run-service.sh` 启动器、`stage5p2_*.py` 脚本、`stage5p2_screens.mjs` 无头浏览器截图）。本机 e2e 用 `*_v2e2e` 库、RabbitMQ vhost `/mor-e2e`、Redis db 3、PID / 日志 / 运行 JAR 在主工作区 `local-environment/e2e/`。
- 产物目录：`requirements/20261022/payment-3DS全链路/verification/`（脚本、JSON 结果、截图、`stage5-report.md`）。BIN 部分的结果也写在这里，报告分两章。

## 用户授权（2026-10-22，仅限以下动作）

1. 本机 e2e 库执行两项需求的 DDL / DML：card、payment、channel-connector（含 PAYERMAX / PAYERMAX_MOCK 回调定义 DML）、risk-decision（服务仓库增量文件，含 220003 与变量）、platform（国家区域 DDL + 种子、CSV 扫描策略、块 132、块 133）。**执行前对每个涉及的 `*_v2e2e` 库整库 mysqldump 到 `local-environment/e2e/backup/<时间>-card3ds-stage5/`**，执行后核对。
2. 在本机 e2e 用集成目录打包的 JAR 替换受影响服务（card、payment、channel-connector、risk-decision、acquiring、counterparty、platform、api-gateway、ops-bff、ops-web），旧 JAR 改名 `*.pre-card3ds` 保留；未运行而链路需要的服务（payment、acquiring、risk-decision、counterparty、commercial、client-master 等）按启动器拉起。注意本机 e2e 当前 platform / ops-bff / ops-web 是工作流版本（`*.pre-workflow` 是更早版本），集成目录已包含工作流，可直接替换。
3. 对本机 channel-connector 运行 `requirements/20261022/payment-3DS全链路/assets/harness/configure_3ds.py`（PAYERMAX_MOCK、PAYERMAX）。
4. 浏览器验收卡 BIN 库页面（无头 Chromium 截图即可）。
5. 3DS 端到端：合成渠道（PAYERMAX_MOCK，需要时扩展合成 PayerMax 返回挑战与查单结果）走挑战 → 回跳 → 刷新确认成功；按 PayerMax 签名规则构造合成通知经 Gateway `CHANNEL_CALLBACK` 入站；**PayerMax UAT 测试卡真实沙箱调用**（只用 PayerMax 公开测试卡；人工 ACS 步骤若无法自动完成，记录到获得挑战 URL 为止并说明，交主会话与用户手动完成）。
6. `npm --prefix requirements-site test`（在主工作区运行；失败项与基线对照，区分既有失败）。

不得：连接 mor-test 或任何非本机环境（PayerMax UAT 沙箱除外）；提交 / 推送；修改其他会话的进程。开始换 JAR 前先 `run-service.sh status all` 记录现状。

## 验收范围

- 3DS：AC-01—AC-12（AC-12 视人工步骤情况）；AC-11 检索日志、MQ、补偿表中的跳转地址与卡号。
- BIN：AC-01—AC-12：导入合成 CSV（含 8/7/6 位前缀与一份有错误行的文件）、校验失败明细、激活、回切、409 并发、BIN 查询；经 Payment 建交易工具（识别 / 未识别）看 Token 快照、Risk 请求字段、`schemeCode`、`PaymentSucceeded` 字段；AC-11 检索长数字。BIN CSV 上传依赖文件存储第二段：若本机 Platform 内部文件接口因 internal-auth 模式不可用，记录阻塞并改用测试替代路径（例如直接经 Card 运营接口用已上传文件 ID），在报告中写清证据边界。
- 回归：两项需求涉及服务的主链路冒烟（参照 stage5p2 的回归集合中与这些服务相关的部分）。

## 报告

`verification/stage5-report.md`：执行时间线、SQL 与备份、JAR 版本、逐项 AC 结果（通过 / 失败 / 未执行及原因）、证据位置、发现的缺陷（编号 S5-xx，严重度、复现）、证据边界（真实 / 合成）。结束时保留新版本运行，Health 全部 UP。最后回复 ≤20 行摘要。
