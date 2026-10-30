# 联合查询 · BFF 合并后核对（jq-bff，2026-10-30）

工作树：Code-MoR-jq-m（已合并最新 main）。只新增了 1 个测试文件，生产代码没有改。没有提交，没有写数据库。

## 构建与测试
命令：JDK 25，`./mvnw -q -o -Dmaven.repo.local=~/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -pl operations-console-bff/paytech-operations-console-bff-test -am install -Dtest='com/paytech/operations/**/*' -Dsurefire.failIfNoSpecifiedTests=false`

结果：exit 0。BFF 测试模块 65 个类、2497 个测试，0 失败、0 错误、0 跳过。其中：
- `DataQueryJointBrowserAutoTest`：33 个测试。
- `DataQueryJointRegistrationAutoTest`（新增）：5 个测试。
- `DataQueryRegistrationAutoTest`（块 131）：仍然通过。

合并没有带来编译或测试问题。Platform `JointQueryApi` 的 `entryKeyType` 已改为 `required=false`，这不影响 BFF：BFF 入口仍把它作为必填参数，并按格式在本地校验，不合格时返回 400，不访问 Platform。

## 块 144 核对
- 路由 Code 一致：`operations-query-key-type-list/-create/-update/-status/-order` 与 `operations-joint-query-settings/-graph/-step`，都与 `data-query-joint-boundary.json` 相同。
- 新增 `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/DataQueryJointRegistrationAutoTest.java`，写法照块 131 的测试。它核对以下内容：
  - 行数：键类型 26、菜单 2、路由 8、Header 36、Query 白名单 5、Guard 48。ID 全部在 144001—144999 内且不重复。
  - 键类型：编码唯一、格式合规，状态全部 ACTIVE。
  - 菜单：两个菜单挂在 131301 下，绑定 EXECUTE / READ，状态 DISABLED。
  - 路由与入口一一对应：8 条路由恰好对应 BFF 两个 Controller 的 8 个入口，路径相同，外部路径加 `/api/operations` 前缀。
  - 路由的权限、Target 与 Body：权限复用块 131 的 READ / TEMPLATE_MANAGE / EXECUTE；Target 为 `@joint_query_target_id`；Body 上限 0 / 65536。
  - 路由的超时与状态：接收超时 5000、响应超时 10000，状态 DISABLED。
  - Query 白名单等于入口声明的 Query 参数。
  - Header 按块 131 的读写形态。
  - Guard 每条路由六条；操作人限流为查询 30、写 10、单层执行 60（每分钟）。
  - 边界清单中的路由 Code 都已在块中登记。
