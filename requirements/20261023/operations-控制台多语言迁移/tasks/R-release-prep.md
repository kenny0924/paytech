# 任务卡 R：mor-test 发布准备（阶段 6，只准备、不变更环境）

## 授权边界

- 可以：只读检查 mor-test（`docker inspect`、`docker exec … printenv` 只比较不输出值、只读 SELECT、只读 HTTP GET）；在**新建的干净目录**中构建 JAR 与镜像（显式 tag `opsi18n-20261023a`，不改任何 `:development` tag）；写发布包文件；本地干跑脚本。
- 不可以：停 / 起 / 替换任何 mor-test 或本机联调容器 / 进程；执行写 SQL；调用 mor-test 写接口（登录除外，且干跑阶段不需要登录）；`docker cp` 进容器；提交 / 推送；`mvn install` 进共享 `~/.m3`。Maven 用 `paytech/mvnw`，`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，`-Dmaven.repo.local=/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/0cc285ca-0776-4a38-9e68-e0b8c88eaaec/scratchpad/m2` 与 `-Dmaven.repo.local.tail=$HOME/.m3/repository` 分开写。
- 不调用 Superpowers skill，不派生子代理；全程前台执行，完成后直接结束。

## 源码

- 候选：集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（main `9f210b5c` + 本需求未提交改动，**只读**，不要在里面改文件）。在你新建的干净目录（绝对路径 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-rel`，`git worktree add --detach … 9f210b5c` 后用集成目录 `git diff --cached --binary 9f210b5c` 补丁应用；集成目录的 index 已 `add -A`）中构建。
- 基线：`/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-base`（`9f210b5c`，已有 node_modules）可直接用于基线构建。
- 需求、方案、交付：主工作区 `requirements/20261023/operations-控制台多语言迁移/`（`delivery.md`、`technique.md` 3.7、`tasks/F9-report.md`）。

## 必读前例（照格式与方法做）

- `requirements/20261022/platform-support-多语言管理/release/release.md` 与 `tasks/R-release-prep.md`（结构、基线证明、执行步骤、回滚、验收的写法）；该目录 `acceptance/i18n7_common.py`、`first_bundles.py`、`stage7_browser.py`（登录、Gateway 调用、文件上传导入、发布轮询的现成实现，**复用**它们）。
- 记忆中的经验：镜像钉 `sha256:`，回滚目标取运行容器 `.Image`；`test-environment/compose.sh -f <覆盖文件>` 只传覆盖文件；覆盖文件对每个服务携带历次覆盖文件出现过的全部 `environment` 键（grep `requirements/*/release/compose-candidate*.yaml`），并与运行容器 `printenv` 逐键比较做到 0 缺 0 多；不 `docker cp`；ops-web 发布要证明不回退别人已上线的东西。

## 发布包（写到 `requirements/20261023/operations-控制台多语言迁移/release/`）

1. **release.md**：
   - **服务与基线**：只涉及 `operations-console-bff`、`operations-console-web`。运行镜像：ops-bff `sha256:e4d913e0948b…`、ops-web `sha256:9fb809dfef26…`。基线证明：`9f210b5c` 构建与运行容器比较——ops-bff 逐条目（`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目，第三方 JAR 整包 SHA-256），ops-web `dist/` 与 `nginx.conf` 逐字节。找出"现网有、`9f210b5c` 没有"的差异（预期为 0；若有，说明来源并让候选带上）。
   - **候选镜像**：tag `opsi18n-20261023a`，给出 sha256 与相对基线的改动摘要（ops-bff 预计只有 3 处：权限树 `code`、审计去中文标签、试算说明结构化；ops-web 为全量多语言迁移）。镜像构建方式照前例（Dockerfile / 镜像构建命令以前例与仓库现有方式为准）。
   - **环境变量**核对（预期本需求不新增）。
   - **只读预检**：mor-test Platform 中 `operations-console` 应用存在且 ENABLED、当前命名空间（预期只有 `i18n-admin`、`enum`）、当前发布号与状态、默认语言（现为 `zh-CN`，需改为 `en`）、必须语言与覆盖率阈值；ops manifest 当前返回；公开桶可达。写成一键脚本 `acceptance/precheck_readonly.py`，输出 PASS / FAIL 计数。
   - **执行顺序**（逐步命令、预期输出、核对点）：① 通知其他会话（主会话做）→ ② 语言包数据：`operations-console` 默认语言改为 `en`；新建 19 个命名空间（common、auth、nav、access、gateway、channel、commercial、finance、recon、risk、workflow、data-query、file、notification、webhook、vault、card、client-review、currency）；导入英文 key（候选目录 `npm run i18n:export-source` 的 `i18n-out/operations-console.en.json`，8513 key，≤ 2 MiB）；导入 zh-CN；预检 100% → 发布 → 等 READY。说明这一步对**现网旧 ops-web** 无影响的依据（旧前端只读 `i18n-admin`、`enum`，且新语言包对旧 key 的影响要核对：F9 改了既有命名空间的英文措辞时，旧页面英文显示会随之变化——列出受影响 key 与可接受性）→ ③ 替换 ops-bff（覆盖文件）→ 健康、日志 ERROR 0 → ④ 替换 ops-web → 健康 → ⑤ 验收 → ⑥ 通知。
   - **回滚**：两个镜像回滚（覆盖文件）；语言包发布用"切回上一发布"恢复（说明命令）；默认语言改回 `zh-CN` 的方法；新建命名空间与 key 是否清理（建议保留，说明理由）。
   - **风险与窗口影响**（替换期间哪些页面短暂不可用；默认语言改为英文后，未存本地选择的用户首次打开为英文——这是需求本意，需在通知中说明）。
   - 末尾"需要主会话裁决"。
2. **compose-candidate.yaml / compose-rollback.yaml**：钉 sha256。
3. **i18n-bundles/**：候选导出的 `operations-console.en.json`、`operations-console.zh-CN.json` 与 `SHA256SUMS`。
4. **acceptance/**（阶段 7 由主会话运行；你只写好并做语法 / 干跑检查，干跑不得调用写接口）：
   - `ops_bundles.py`：执行第 ② 步（复用 `first_bundles.py` 的实现，可直接 import 前例目录的 `i18n7_common.py` 或复制改写）；`--dry-run` 只核对本地输入与计划。
   - `stage7_api.py`：manifest（en / zh-CN）列出 21 个命名空间、每个语言包公开地址可读且摘要一致、key 集合与上传文件一致；新 BFF：角色权限树节点带 `code`、"其他权限"分组为 `OTHER_PERMISSIONS`；审计详情无 `label`；价格试算返回 `notes[{code,params}]`（找一个只读可调用的试算请求，若试算为写接口则改为浏览器验证）。
   - `stage7_browser.py`：真实 manifest 下：默认英文与语言按钮出现；切到简体中文后刷新保持；角色编辑权限树英文 / 中文标题正确（资源名、"其他权限"、权限编码）；审计详情字段名中英文；价格试算说明中英文；断网（拦截 manifest 与公开桶）刷新仍为所选语言；AC-07：在翻译页改一条 ops 文案（用一个专门的测试 key 或可恢复的 key）→ 发布 → 刷新可见 → 切回上一发布 → 恢复（这一项写操作在阶段 7 由主会话执行并负责恢复）。中文零变化复核：复用 `requirements/20261023/operations-控制台多语言迁移/verification/zh_compare.py` 的思路，比较基线 Vite（`Code-MoR-opsi18n-base`）与发布后 mor-test 现网 ops-web 的中文可见文本（写成可运行脚本）。
5. **只读预检脚本**同上。

## 交付

写完后在 `release.md` 末尾列"需要主会话裁决"的问题。不要写 `delivery.md`。最终回复简述：基线结论、候选镜像清单与 sha256、语言包数据步骤要点、对现网旧 ops-web 的影响结论、待裁决问题。
