# 交付记录：Vault 数据类型值格式约束

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-15 | 1 需求评审 | 用户提出数据类型应声明接收的值格式；讨论后确定“内置格式 + 正则”两层都保留，正则在页面上直接改（方案 A），Operations 同步改造。[requirement.md](./requirement.md) 评审稿：范围、Q1/Q2、现有类型的建议格式、AC-1～AC-12 | 待确认 |
| 2026-10-15 | 1 需求评审 | 用户决定只保留 TEXT、JSON、正则三种，其余都用正则：值格式改为 `TEXT`/`JSON`/`REGEX` 三选一，正则仅 `REGEX` 时必填；邮箱、手机改用正则（已用 Go RE2 实测样例）；原 Q1（邮箱域名是否须 ASCII）由正则放行两种写法而消除，原 Q2 改为 Q1 | 待确认 |
| 2026-10-15 | 1 需求评审 | 用户回复“继续”：第 2 版需求与 Q1（现有类型按第 7 节设置）按建议确认 | **已确认** |
| 2026-10-15 | 2 现状差异与影响 | [technique.md](./technique.md) 评审稿：差异 7 项；写入方核对 8 个（规范输入均满足约束，CCT `@Email` 边界输入除外）；逐系统影响 8 项；风险 R1～R6（旧 Java 客户端的错误码白名单、CCT 校验比正则宽、发布顺序等） | 待确认 |
| 2026-10-15 | 2 现状差异与影响 | 用户回复“继续” | **已确认** |
| 2026-10-15 | 3 方案与设计 | technique.md 第 6～11 节：规则定义与修改语义（兼容旧 BFF）、Vault/客户端/BFF 接口、4 个新错误码、[Vault DDL](./db/vault/ddl.sql)（2 列）、DML 不涉及（现有类型经页面设置）、页面设计、研发验证计划、发布顺序 | 待确认 |
| 2026-10-15 | 3 方案与设计 | 用户回复“继续” | **已确认** |
| 2026-10-15 | 4 研发 | 用户授权后，备份（`~/Code-MoR-backups/20261015-vault-value-format-local/20261015T173721Z/`）并在本机 `mor_vault` 执行 [ddl.sql](./db/vault/ddl.sql)，2 列已加、现有 5 行均为 TEXT。Vault：值规则（TEXT/JSON/REGEX，JSON 查重复键）、快照预编译与失败关闭、Protect 授权后校验、管理接口格式/正则/样例（不带或为 null 时保持原值）、4 个错误码、sqlc、OpenAPI、Schema SQL、PDManer（`value_format` 默认值为批准例外）、架构文档补注。Java 客户端：4 个错误码与 DTO 字段。BFF：3 个字段形状校验与转发。Web：列表值格式列、编辑抽屉、手动加密格式提示与 JSON 预检、4 个错误码固定文案。验证：Vault `verify.sh` 13 项全部通过（含 Go 入口测试、race、golangci-lint 0 issues、客户端 42、真实 Go Vault 黑盒 11）；BFF 全量 1334、Web 全量 1025 通过，Web 构建通过。未做：浏览器联调（阶段 5） | 待确认 |
| 2026-10-15 | 4 研发 | 用户回复“继续” | **已确认** |
| 2026-10-15 | 5 本机联调 | 重建本机 Docker Vault、重打包并重启 Operations BFF（Web 为 Vite 热更新）。[API 验收](./verification/value_format_acceptance.py)：A 段 20 项（临时 `E2E_VF_*` 类型：维护规则、写入校验、生效时间 3.6～5.6 秒），清理后（30/56/8/2/6/12 行，计数核对后提交）记录数与策略版本回到 A 段前；B 段 13 项：经管理接口为 4 个现有类型设置值格式（策略版本 12～15），设置前后同一邮箱的 STABLE Token 不变、存量 Token 可解密，CCT 联系人（含 Unicode 域名）、Platform 登录、渠道账号配置与 SES 凭据读取回归通过，回归数据已清理（52/44/7）。[浏览器验收](./verification/value_format_browser.py) 14 项（AC-9～12）。发现并修正：BFF 把空正则序列化为 `null`，Web 原校验会把所有非 REGEX 类型判为无效响应（已改为 `null` 视为无并补测试）；列表加列后“更新时间”被固定列遮挡（收紧列宽）。Web 全量 1025 通过、构建通过；Vault 与 BFF 日志未出现验收原值、样例 | 待确认 |
| 2026-10-15 | 5 本机联调 | 用户回复“继续” | **已确认** |
| 2026-10-15 | 6 test 发布准备 | [发布清单](./release/release.md)：只读核对 test（Vault `9842248f`/`34ecac9`，BFF `68e939f5`、Web `5fb6911b`/`d02c7dd`，`mor_vault` 6 类型 11 列、策略版本 7）；候选为 `34ecac9`/`d02c7dd` 加本需求，需先本地提交后从干净 worktree 构建；顺序 DDL → Vault → BFF → Web → 设格式，验收 A/B 段、浏览器与日志检查，回退按镜像逆序 | 待确认 |

当前实现与部署状态只见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
