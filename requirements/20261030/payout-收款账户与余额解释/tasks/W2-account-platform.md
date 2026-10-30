# W2 任务卡：account 余额列表接口 + Platform 通知按租户权限解析收件人 + Platform 注册块与模板

- 工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-po-plat`（detached，基于 main `30361be7`）。只在这里改；不提交、不推送、不碰 mor-test。
- 权威材料（只读，主工作树）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/payout-收款账户与余额解释/technique.md` 第 8.2、8.3、8.6、9 节与 `db/platform-support/dml.sql`（结构清单）。规范同 W1 卡（AGENTS.md、DEVELOPMENT_CONVENTIONS、JAVA_PROJECT_STRUCTURE、WRITE_API_EXAMPLE、UNIT_TEST_GUIDE、~/.claude/CLAUDE.md）。

## 范围

1. **account：** `BalanceQueryApi` 新增 `GET /balances/v1/clients/{clientId}/positions`、`GET /balances/v1/partners/{partnerId}/positions`（technique 8.2）；用 `idx_owner_account_owner` 一次查询后按 entityNo × currency 聚合；Owner 无账户返回空数组；调用方白名单加 `payout-service`（按 account 现有调用方配置方式）；auto 测试。原单查询行为不变。
2. **Platform 通知：** `SubmitNotificationRequestBody` 加可选 `tenantRecipients{tenantType: CLIENT|PARTNER, ownerRefId, permissionCode, locale?}`，与 `recipients` 二选一（都给 / 都不给 → 422）；受理前在本库解析：`t_access_tenant(tenant_type, owner_ref_id)` → ACTIVE `t_tenant_member` → 当前有效 `t_tenant_member_role` → ACTIVE `t_role_permission` 含该权限 → `t_user_account.email_vault_token`（已验证邮箱）→ 每人一条 delivery，直接复用账号邮箱 Token（确认与 delivery 的 `EMAIL_ADDRESS_V1` 数据类型一致，不一致时停下写入报告）；0 人 → 200 且不建 delivery（响应带 `deliveryCount`）；上限 100。`application.yml` 的 `notification.http.callers` 注释示例加 payout-service（默认仍为空），test-environment 不改。auto 测试覆盖解析各分支（成员停用、角色过期、权限撤销、邮箱未验证、无租户）。
3. **Platform 注册块（生成可执行 SQL，不执行）：** 按 `requirements/20261029/tax-结账算税与事件登记/release/sql/mor_platform-block-145*.sql` 六件套模板，生成 `/Users/kenny/Desktop/Workspace/Code-MoR-po-plat/release-draft/mor_platform-block-146{,-precheck,-verify,-activate,-deactivate,-rollback}.sql`：权限 8（technique 8.6）、Client 系统角色与平台管理员角色授权（休眠 REVOKED，激活改 ACTIVE；先查 Client 系统角色 OWNER / ADMIN / MEMBER 在 initial-data 中的 role_id）、菜单与页面资源（Client「资金」组 + 收款账户、余额；Operations「财务 · 出款」下收款账户核验、Owner 账户与余额；页面动作资源按 technique 8.6）、Gateway 路由 Client 7 条 `/client-payout/v1/**`（technique 8.4）与 Operations 11 条 `/payout-admin/v1/**`（8.5）及其头与守卫（照块 145 / 最近 Client 路由块的写法，目标为对应 BFF）、通知模板 4 个 × zh-CN / en（technique 8.3，正文参数 bankName、accountLast4、currency、availableAt、reasonText，不含原值）。块号写 146 并在文件头注明"阶段 6 只读核对现网最大块号后替换"。同时把这批行追加进 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（保持该文件既有块注释格式）。
4. **语言包：** operations-console 命名空间新页面文案 key 与 Client `payout` 命名空间 key 的 en / zh-CN 初始化数据——若语言包是 Platform 初始化 DML，则加入上面块；若是前端仓库 JSON，则只列 key 清单写进报告，交给 W3。

## 本机约束

同 W1 卡：只连 `mor-local-mysql-1`；JDK 25；`./mvnw -o …`；只跑 account、platform-support 模块（`package`），不跑全 reactor；环境变量从 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 读取不打印。**不执行**注册块 SQL（主会话在审阅后执行到本机 mor_platform）。

## 交付

工作树内 `REPORT-W2.md`：改动清单、合同对应、测试命令与结果、生成的 SQL 文件与行数、偏离与待决。回复一句摘要与报告路径。
