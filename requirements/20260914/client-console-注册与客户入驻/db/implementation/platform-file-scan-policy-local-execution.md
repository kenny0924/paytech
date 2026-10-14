# 本机入驻材料格式检查策略（执行记录）

- 授权：用户 2026-10-14 确认[文件存储与检查适配器确认稿](../../file-storage-adapter-proposal.md)（“可以”），包含向本机 `mor_platform` 写入一条扫描策略与一条检查要求。
- 范围：`127.0.0.1:13306/mor_platform`，账号 `platform_app`；只写 `t_file_scan_policy`、`t_file_scan_requirement` 各一行，见 [SQL](./platform-file-scan-policy-local.sql)。
- 前置只读核对：两表为空；策略代码 `CLIENT_ONBOARDING_FORMAT` 与 ID `9200000000000001/2` 不存在。
- 配置对应：Platform `files.onboarding.scan-policy-code=CLIENT_ONBOARDING_FORMAT`，检查器 `PLATFORM_FORMAT`（`FormatFileScanner`）。
- 回滚：见 SQL 文件头；已有文件引用该策略时不得回滚。
- 不包含：病毒扫描、生产环境、其他表。
- 执行结果（2026-10-14T06:12Z）：前置核对两项均为 0；执行后回读 1 条 ACTIVE 策略（PDF/JPEG/PNG，10485760 字节）与 1 条 FORMAT 要求（接受 FORMAT_VALID）。
