# 阶段 6 卡 H：构建候选镜像、基线证明（N8）与 Gateway 回滚镜像（N1）

- 源码：`/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-rel`（基线 `73a2460d` + 两项需求，已暂存，未提交）。**不改代码**。
- 先读：`release/release.md`（本需求发布清单，尤其第 0 节 N1、N6、N8）、`requirements/20261022/platform-support-文件多存储并存/release-s2/release.md` 第 1 节（构建方法、比较方法、回滚镜像重建先例）、`test-environment/` 下构建脚本与 Dockerfile、`AGENTS.md`。
- 授权：本机构建 JAR 与 Docker 镜像（显式 tag，不覆盖 `:development` 或任何现网在用 tag）；对 mor-test 容器只做只读动作：`docker inspect`、`docker cp <容器>:<路径> <本机目录>`（从容器读出 JAR / dist，**不得向容器写入**）、`docker exec ... printenv`（只记键名）。**禁止**：停启 / 替换容器、`compose up`、写 SQL、写 XXL、推送镜像到任何仓库、`install` 到 `~/.m3`、提交。

## 任务

1. **候选镜像**（tag `card3ds-20261022`）：card、payment、channel-connector、risk-decision、acquiring、counterparty、platform-support、api-gateway、operations-console-bff、operations-console-web。构建前在 rel 目录 `test-compile` 确认干净。
   - **ops-web（N6）：** 构建源 = rel 的 `operations-console-web` + 主工作区 code-mor-42 的 6 个文件当前内容（`src/pages/ApiCredentials.tsx`、`src/pages/Audit.tsx`、`src/pages/GatewayConfiguration.tsx`、`src/configuration/configuration.css`、`src/styles.css`、`src/components/RefreshButton.tsx`）。**不要改 rel 工作区**：把 `operations-console-web` 复制到会话临时目录再覆盖这 6 个文件后构建。先做校验：`1d39cd94` 的 ops-web + 这 6 个文件构建出的 `dist/` 应与现网 `7551dc3b` 逐字节一致；不一致则停止并报告。
2. **基线证明（N8）：** 对上面 10 个服务，从现网容器读出 JAR（Web 读 `dist/` 与 `nginx.conf`），与“候选源码去掉两项需求改动”的基线构建逐条目比较（方法同 release-s2 第 1.2 节）。确定每个现网容器对应的源码基线；列出候选相对现网带上的**非本需求**改动（含 `1d39cd94..73a2460d` 及更早未上 mor-test 的已提交改动），每项写清提交号、影响。基线构建可在会话临时目录的独立 worktree 里做（例如 `git worktree add --detach <scratch>/base 73a2460d`）。
3. **Gateway 回滚镜像（N1）：** 现网 `sha256:4cf14912…` 本机已丢失。从 `e119cbd1`（或比较后确定的提交）构建 api-gateway，与运行容器 JAR 逐条目比较到 0 差异，打 tag `rollback-gateway-<提交>`，记下镜像 ID。比不到 0 差异时停止并报告差异。
4. 把候选镜像 ID 填入 `release/compose-candidate.yaml`（替换 `<待构建>`），把 Gateway 回滚镜像 ID 填入 `release/compose-rollback.yaml`；两个文件 `docker compose config --quiet` 通过（按 `test-environment/compose.sh` 的方式只叠加 release override）。
5. 在 `release/release.md` 新增第 2.4 节（或已有的对应节）“候选镜像与差异”，格式参照 release-s2 第 1.4 节；N1、N6、N8 在第 0 节标注处理结果。

报告写进 release.md；最后回复 ≤15 行摘要。
