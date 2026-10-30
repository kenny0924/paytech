# W5 任务卡：本机联调用组合构建（main 022da95a + payout，ops-bff 另加 REQ-04）

- 工作树 A：`/Users/kenny/Desktop/Workspace/Code-MoR-po-e2e`（main `022da95a`，主会话已 `git apply --3way` 了 payout 全部差异，11 个文件有冲突标记，见 `git diff --name-only --diff-filter=U`）。payout 差异的来源是 `/Users/kenny/Desktop/Workspace/Code-MoR-po-int`（基于 `30361be7`）。
- 不提交、不推送、不碰 mor-test；不启动 / 停止任何进程；不执行 SQL；不打印密钥。

## 范围

1. **解决工作树 A 的冲突：** 两边的改动都要保留（main 这段新增了 tax、多语言发布变更记录 `1c8341a2`、联合查询 `d673817f`）。逐个文件合并：`CURRENT_IMPLEMENTATION.md`（两条都留）、ops-web 三个 i18n / 配置测试文件、ops-bff 两个 pom 与三个测试基座 / 配置文件、`platform-support-initial-data.sql`（payout 的块 147 追加在 main 现有最后一个块之后，保持块注释格式）、arch-rules `TableOwnershipAutoTest`（表数 = main 的数 + payout 新增表数，按实际计算）。合并后 `git diff --check` 无冲突标记。
2. **工作树 B（ops-bff 组合）：** 新建 `/Users/kenny/Desktop/Workspace/Code-MoR-po-e2e-ops`，从工作树 A 当前文件复制（`git worktree add --detach … 022da95a` 后用 rsync 复制 A 的工作区改动，排除 target、node_modules），再叠加 REQ-04 的改动：REQ-04 的权威源码在 `/Users/kenny/Desktop/Workspace/Code-MoR-rq04`（只读，基于 `30361be7`），只取它相对 `30361be7` 的改动文件（`git -C …rq04 diff 30361be7 --name-only` 加未跟踪文件）中构建 operations-console-bff 需要的部分（ops-bff 模块本身及其依赖的 `*-api` 模块，例如 risk-decision-api），按三方合并叠加；不要动 REQ-04 工作树。冲突同样两边都保留。
3. **构建 exec JAR（`package -DskipTests`，只建需要的模块 `-am`）：**
   - 工作树 A：`paytech-platform-support-app`、`paytech-account-app`、`paytech-client-console-bff-app`、`paytech-payout-app` 的 `*-exec.jar`。
   - 工作树 B：`paytech-operations-console-bff-app` 的 `*-exec.jar`。
   - 在每个 JAR 上 `unzip -l` 核对：Platform 含 `TenantPermissionRecipient*` 与 i18n 发布变更类（`1c8341a2` 的类名在 main 中查）、data-query 联合查询类；ops-bff 含 `PayoutAdminController` 与 REQ-04 的限额档位控制器；account 含 positions。
4. **相关测试：** 工作树 A 跑 payout（`-Dtest='com/paytech/payout/**/*'`）与 arch-rules；工作树 B 跑 ops-bff 测试模块（`-Dtest='com/paytech/operations/console/**/*'`）。用 `package`，环境变量从主工作树 `local-environment/.env.local` 读取，`-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`；JDK 25、`./mvnw -o …`（同 W1 卡）。共享本机库被其他会话写入导致的前置检查失败，重跑受影响类即可，在报告中写明。

## 交付

工作树 A 内 `REPORT-W5.md`：冲突逐文件处理方式、REQ-04 叠加的文件清单、五个 JAR 的绝对路径与 sha256、`unzip -l` 核对结果、测试结果。前台执行，不空等后台事件。回复一句摘要与路径。
