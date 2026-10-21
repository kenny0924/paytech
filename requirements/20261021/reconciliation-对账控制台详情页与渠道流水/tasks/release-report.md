# 阶段 6：mor-test 发布包报告

> 2026-10-21。只做了发布包、只读核对，并在干净 worktree 里构建镜像。没有改 mor-test（容器、SQL、队列、XXL 都没有写），没有提交或推送，没有调用 Superpowers，没有改生产源码，没有使用共享 git index。`Code-MoR-currency-rel` 只读。发布包在 [release/](../release/)。

## 1. 源码

- **BASE = main `13c02c87`**：币种会话的提交，父提交是 `5a103f36`（商务提交）。
- **B（基线差异，不属于本需求）**：商务会话还没提交的 `operations-console-web/src/commercial/commercialPreview.ts`（以及对应的 test）。运行中的 ops-web `c6d2118f` 已经包含这个修复。
- **本需求**：70 个文件，新增 22、修改 48。
  - reconciliation 28
  - ops-bff 15
  - platform 2（能力清单 JSON，加上块 126 文档 SQL）
  - ops-web 25
  - 清单见 [source-overlay.txt](../release/source-overlay.txt)，每个文件都附了 blob 哈希。
- **共享文件只取本需求的 hunk**：ops-bff 的 README 只取 20261021 那一段。其余共享文件我逐个核对过，它们相对 13c02c87 的差异全部属于本需求。channel-connector 迁移、platform-support 删除、arch-rules、compose 这些其他会话的改动都没有带进来。

## 2. 复现证明（先证明基线，再叠加本需求）

| 服务 | 只用基线构建，与运行版比较 |
|---|---|
| platform `b2a63ff4` | 用 13c02c87 构建的 JAR 逐项比较，包括嵌套 JAR 里每个条目的内容哈希：76,974 项，0 差异 |
| ops-bff `fddb1993` | 13c02c87：36,599 项，0 差异 |
| reconciliation `f181a04c` | 13c02c87：61,710 项，0 差异 |
| ops-web `c6d2118f` | 只用 13c02c87 构建时，只有 `commercialPreview` 一个 chunk 不同；加上 B 之后，`dist/` 158 个文件逐字节一致，`nginx.conf` 也一致 |

## 3. 候选镜像（tag `recon-detail-20261021`，部署时只用 ID）

| 服务 | 候选 ID | 与运行版相比的差异 |
|---|---|---|
| reconciliation-service | `sha256:b31a853da15c90e6e6f7b0e95a681ac6f5c0784aa97b1a4513244998343501e3` | 新增 12 项、修改 18 项，都是本需求的类和 Mapper XML |
| platform-support-service | `sha256:3d8bc3752c37da4f57e6bfad764a6712758bef45b484980262ddbe68414ebd72` | 只有能力清单 JSON 不同：多了 1 个组件，原有 48 项逐项相同 |
| operations-console-bff | `sha256:e822301cf35cab0dbce8ea87619d37d86c6ca5bed51c04fb2b3fa5b16968eab4` | 新增 7 项、修改 14 项，都是对账相关的类 |
| operations-console-web | `sha256:70829a3a9213f680b4ce8c100115996799fd3a800997faa1a85414dcfca19ef8` | 新增对账详情和渠道流水的 chunk；其余 chunk 只是 Vite 哈希引用级联变化 |

所有镜像的 Entrypoint、User、Env、端口都与运行镜像一致。health-probe 直接取自运行容器，逐字节一致。

## 4. 只读核对（mor-test）

- **块 126**：七张注册表、路由码、外部路径、资源码都是 0 行。
- **块 123**：路由 38、资源 10、授权 3 都是 ACTIVE；父菜单和读权限都在。
- **PLATFORM_ADMIN 版本**：当前 39，激活后应为 40。
- **`mor_reconciliation`**：15 张表都在，演示数据：
  - 渠道账单 7、账单行 25
  - 银行对账单 4、银行流水 5
  - 异常 11
- **覆盖文件**：两个覆盖文件都通过了 `compose.sh config --quiet`。渲染出的环境变量和运行容器逐键比较，完全一致。
- **阅读副本**：`registration-dml.sql` 的块 126 与主文件末尾逐行一致。

## 5. 风险与阻塞

1. **Web 测试会失败（需要改测试，不阻塞发布）**：`configurationModel.test.ts` 断言页面数 57（13c02c87 的值），加上渠道流水后实际是 58。候选 worktree 的结果是 1241/1242，主工作区同样会失败。镜像不受影响。阶段 8 前需要把 57 改成 58。
2. **基线漂移**：ops-web 依赖商务会话还没提交的 `commercialPreview.ts`，platform 和 ops-bff 依赖币种会话的镜像。执行前必须先和 code-mor-98、code-mor-50 协调。如果任何一方又发布了这三个服务中的一个，这个包就作废，要重建。
3. **环境变量来源**：切片 1 的 reconciliation 和 BFF 变量还只是主工作区 compose 文件里没提交的 hunk。我已经把它们写进覆盖文件兜底（值与现值相同）。阶段 8 需要提交这段。
4. **浏览器验收脚本还没跑过**：路由要激活后才能用，所以 `stage7_browser.py` 只做了语法检查。脚本是只读的，主键从列表页现场取。演示数据如果缺某类对象，会记为"跳过"。
5. **限流**：每路由每人 30 次/分钟，脚本每次导航间隔 2.5 秒。
6. **构建 worktree**：在会话 scratchpad 的 `wt`（13c02c87 加叠加文件），阶段 7 结束后删除。镜像保留。
