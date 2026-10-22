# 阶段 7 卡 I：mor-test 接口层配置与验收（S14、S16、S17）

- 现状（主会话已完成）：S0—S13 十个服务已替换为候选镜像（全部 healthy、0 重启、0 ERROR）；S3 SQL、S8 扫描策略、S10 回调定义（PAYERMAX 1 行）、S11 块 133 注册；S15 块 132 / 133 已激活；事后核对只差第 36 项（存储写入路由）。备份目录见 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/B.txt`。
- 先读：`release/release.md`（第 6、7、9 节）、`release/acceptance-plan.md`、`verification/stage5-report.md`（可复用其脚本思路）、`test-environment/private/access.md`（运营账号；只在内存使用，不打印口令、不写入任何文件）。
- 授权（用户 2026-10-22“继续”）：经 mor-test 的**公开入口与运营接口**操作：
  1. **S14：** 运营台「存储管理」API 新建写入路由 `operations-console-bff` → 桶 `mor-platform-files`、扫描策略 `CARD_BIN_CSV`、`maxUploadSeconds=900`。不改、不删任何已有存储实例 / 桶 / 路由（包括用户手工登记的 `S3_TEST` 与 `client-console-bff` 路由）。完成后主会话会重跑事后核对。
  2. **S16（N2-a）：** 运营台为 AE001 建 PAYERMAX 技术账号（UAT 凭据，录入方式参照已有 PAYERMAX 沙箱验证账号与 `requirements/20261014/payment-PayerMax卡直付对接/` 交付记录；凭据只在私有 0700 目录与内存中，不入库不打印）、MID、`CARD/*/*/USD/<MCC>` 能力（`ACTION.PAY=true`、`SECURITY.3DS_MODE=SUPPORTED`）；按 release.md S16 准备私有运行目录后依次运行 `configure_card.py PAYERMAX`、`configure_3ds.py PAYERMAX`，再跑一次 `configure_3ds.py` 确认无修改。核对 REFUND / CAPTURE 的 `paymentChannelRef` 仍在。找不到 UAT 凭据时停在这一步并报告主会话。
  3. **S17：** 按 `acceptance-plan.md` 执行验收（3DS 与 BIN 的接口与浏览器验收；BIN 页面在新数据查询样式下布局检查；工作流内嵌预览回归可用 `RUN=C node requirements/20261021/platform-support-工作流/release-inline/acceptance/inline_mortest.mjs`）。合成数据按计划清理或登记保留。
  4. **AC-12：** 用 PayerMax UAT 公开测试卡发起一笔需要 3DS 的支付，拿到挑战跳转 URL 后**停下来**，把 URL（只给主会话，不写文件）和交易标识发给主会话（SendMessage team-lead），由用户在浏览器完成 ACS；收到“ACS 已完成”后再刷新确认结果并记录。
- **禁止：** 停启 / 替换容器、执行 SQL 写入（只读 SELECT 可以）、写 XXL、修改 Risk 支付点发布（N7 已定不做）、修改与本次无关的渠道或配置、提交代码。发现缺陷：记录 S7-xx 并通知主会话，不改代码。
- 产物：`release/acceptance/`（脚本、结果 JSON、截图），在 `release/release.md` 第 11 节填执行记录（S14、S16、S17 部分）。最后回复 ≤20 行摘要。
