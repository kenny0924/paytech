# 任务卡 R：mor-test 发布准备（阶段 6，只准备不变更环境）

## 授权边界

- 可以：只读检查 mor-test（`docker inspect` / `docker exec … printenv` / 只读 SELECT / 只读 HTTP GET）、在**新建的干净 worktree** 中构建 JAR 与镜像（显式 tag，不改任何 `:development` tag）、写发布包文件。
- 不可以：停 / 起 / 替换任何 mor-test 容器；执行任何写 SQL；调用 mor-test 写接口；`docker cp` 进容器；提交 / 推送；`mvn install` 进共享 `~/.m3`（Maven 一律 `-Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository`，两个 -D 分开写）。
- 环境变更、SQL 与验收执行在阶段 7 由主会话按你的发布包执行；你只写清楚每一步。

## 源码

- 本需求最终源码：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n-main`（main `bfb17bc9` + 本需求未提交改动）。**只读**，不要在里面改文件；构建在你自己新建的 worktree 中进行（`git worktree add --detach /Users/kenny/Desktop/Workspace/Code-MoR-i18n-rel <基线>` 后复制改动，绝对路径）。
- 需求、技术方案、交付记录、各卡报告：主工作区 `requirements/20261022/platform-support-多语言管理/`（尤其 `tasks/B-report.md`、`F-report.md` 第 7、9 节、`db/`）。

## 必读的环境经验（照做）

- 前例：`requirements/20261022/platform-support-文件公开桶/release/release.md`（结构、基线证明方法、窗口安排、验收方式全部照它的格式写）与 `requirements/20261022/platform-support-数据查询服务/release/release.md`。
- 镜像必须钉 `sha256:` ID；回滚目标取运行容器 `.Image`，不要用 tag。
- 基线证明：在干净 worktree 按 main 构建，与运行容器内 JAR 逐条目比较（`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目，第三方 JAR 整包 SHA-256），Web 比较 `dist/` 与 `nginx.conf` 逐字节；找出"现网有、main 没有"的差异（其他会话已上线但未提交的改动，例如公开桶发布记录提到的 ops-web 16 个未提交文件、工作流体验优化等），候选必须把这些带上，不能回退别人已上线的东西，也不能夹带未上线的别人改动。
- 覆盖文件：`test-environment/compose.sh -f <candidate> …` 只传覆盖文件；候选 / 回滚文件对每个服务携带历次覆盖文件里出现过的全部 `environment` 键（grep `requirements/*/release/compose-candidate*.yaml`），并与运行容器 `printenv` 逐键比较（只比相等，不输出值），做到 0 缺 0 多。
- 不要 `docker cp` 进运行容器；需要读管理端口时用 sidecar（`docker run --rm --network container:<target> …`）。

## 发布包内容（写到 `requirements/20261022/platform-support-多语言管理/release/`）

1. **release.md**（主文档）：
   - 涉及服务与基线证明：platform-support-service、operations-console-bff、client-console-bff、operations-console-web、client-console-web（先确认 mor-test 是否已有 client-console-web / client-console-bff 容器；没有则写明并给出首次上线的 compose 方案与依赖，交主会话裁决）。每个服务：运行镜像 ID、与 main 的差异结论、候选镜像 tag（`i18n-20261023a`）与 sha256、相对基线的改动摘要。
   - 环境变量核对结果；本需求是否新增变量（预期无；若有说明来源）。
   - mor-test 前提检查（只读）：`mor_platform` 无 `t_i18n_*` 表、块 135 ID 段为空、`I18N_BUNDLE_JSON` / `OPERATIONS_BFF_IMPORT` 不存在、`platform-support` × PUBLIC 路由不存在、ops-bff 私有路由为 `239087906591948800 / CARD_BIN_CSV / 900`（记 version）、`MINIO/mor-platform-public` 为 ACTIVE PUBLIC。
   - **浏览器可达性**：从 Operations / Client 控制台的浏览器来源读取公开桶语言包需要 (a) MinIO 对这两个 Origin 返回 CORS 允许头（用 `curl -H 'Origin: …' -I` 对 `https://minio.mor-test.localhost:29400/mor-platform-public/<已有公开对象>` 只读验证），(b) 浏览器信任 MinIO 证书（查公开桶需求的验收是否用浏览器打开过公开地址）。不满足时写出需要的环境配置与风险，交主会话裁决，不要自行修改。
   - **执行顺序**（窗口外 / 窗口内，逐步写命令、预期输出与核对 SQL）：备份 `mor_platform` → 执行 `changes/2026-10-23-localization.sql` → 块 135 第 1、2 段（休眠）→ 替换 Platform → 块 135 第 3 段（策略、路由、ops-bff 路由改指，带 ROW_COUNT 核对）→ 触发存储快照刷新 → 替换 ops-bff、client-bff → 替换 ops-web、client-web → 激活块 135（`db/` 中的激活 SQL，若无请补写 `release/sql/` 文件）→ 首批语言包（见下）→ 验收 → 通知 code-mor-6b 并跑其 `requirements/20261022/payment-3DS全链路/release/acceptance/accept_bin.py`。
   - **首批语言包**：两个应用（`operations-console`、`client-console`）各自建命名空间（ops：`i18n-admin`、`enum`；client：`common`、`auth`、`onboarding`、`workspace`、`team`、`invitation`、`developer`、`enum`）、上传导入英文 key（各 web `npm run i18n:export-source` 的 `i18n-out/*.en.json`）、导入中文（`*.zh-CN.json`）、发布并等待 READY。写成可执行脚本（经 Gateway 走真实接口，管理员账号用 mor-test 现有验收账号的取得方式，参照数据查询 / 公开桶验收脚本），产物文件随发布包保存。
   - 回滚：镜像回滚（覆盖文件）、SQL 回滚（`db/dml-rollback.sql` 与 DDL 回滚，按逆序）、语言包数据如何处理。
   - 风险与窗口影响（哪些页面在什么时段不可用）。
2. **compose-candidate.yaml / compose-rollback.yaml**：钉 sha256。
3. **sql/**：本次执行用到但 `db/` 里没有的 SQL（激活、核对、回滚补充）。
4. **acceptance/**：验收脚本（API + Playwright 浏览器）。覆盖需求 AC-01—AC-13 中可在环境验证的项：Locale / 应用 / 命名空间、导入预览与确认（含未创建命名空间阻止、文件检查中 409）、翻译保存与 ICU 错误、批量粘贴、发布 → 检查中 → 上线、manifest 返回公开地址且浏览器能直接拉到语言包、切回、client-web 中英文切换与无发布时隐藏切换、ops-web 新页面与设计稿（`assets/prototype/`）对照截图。脚本在阶段 7 由主会话运行，你只写好并做语法 / 干跑检查（干跑不得调用写接口）。
5. **只读预检脚本**：阶段 7 开始前一键复核上述前提，输出 PASS/FAIL 计数。

## 交付

写完后在 `release.md` 末尾列"需要主会话裁决"的问题；在主工作区本需求 `delivery.md` **不要**写（由主会话写）。最终回复简述：候选镜像清单、基线结论、浏览器可达性结论、待裁决问题。**全程前台执行，不要等待后台事件；完成后直接结束。**
