# 侧栏底部中文化（2026-10-24，语言包 #7）

- 起因：code-mor-42 发布文件上传入口后反馈，中文界面侧栏底部仍显示 "Operations Console" / "Hide Navigation"。原代码（9f210b5c `NavigationCollapseControl.tsx`）本来就硬编码英文，并带 `lang="en"`，按 R2（zh-CN 与原代码中文逐字一致）沿用了英文。
- 用户确认（2026-10-24）：改为中文。只改 Platform 的 zh-CN 译文，不改代码、不换镜像。
- 改动：`nav.sidebar.product` 运营控制台、`nav.sidebar.hide` 收起导航、`nav.sidebar.show` 展开导航（均从 v1 改起，原文为英文）。`collapse` / `expand` 原本就是中文，不变。
- 执行：`python3 sidebar_zh.py` 只读核对（#6 READY、草稿无他人改动）→ `--apply` 保存 3 条 → 预检仅 3 条变化 → 发布 **#7** READY 且为当前 → manifest #7 zh-CN/nav 公开包核对通过（7/7）。运行记录 `~/Code-MoR-backups/opsi18n-stage7/sidebar-zh-2026-10-24T100244Z.json`。
- 浏览器（mor-test，1440 宽，登录后）：zh-CN 侧栏底部 "运营控制台 / 收起导航"，aria "收起导航"；en 不变 "Operations Console / Hide Navigation"。
- 回滚：`POST /applications/operations-console/current-release {"releaseNo": 6}`；如需草稿一并恢复，把 3 条译文改回英文原文。
