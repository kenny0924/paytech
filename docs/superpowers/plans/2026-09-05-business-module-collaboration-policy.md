# Business Module Collaboration Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Finance 已确认的模块协作结论抽象为所有复杂核心 Java 服务可复用、可自动注入且可静态验证的公共规范。

**Architecture:** `agent-docs/JAVA_PROJECT_STRUCTURE.md` 单独拥有完整模块协作语义；决策记录保存原因与取代范围，`DEVELOPMENT_CONVENTIONS.md` 和 Cursor rule 只做薄投影。Finance 文档只映射模块、表和允许的依赖，文档门禁验证各投影没有漂移。

**Tech Stack:** Markdown、Cursor `.mdc` rule、Node.js `node:test` 静态内容门禁。

**Spec:** `agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md`

## Global Constraints

- 仅适用于同一可部署 Java 服务内部的业务能力 Maven JAR；不改变跨微服务 HTTP 和 Foundation JAR 规则。
- 业务能力模块默认无直接依赖，只允许满足稳定业务能力、Owner 接口、单向且无环四项条件的依赖。
- DataObject 只在 Owner 业务能力 JAR 内可见，跨模块合同只使用 Owner Domain / Application Command / Result。
- 所有业务 Service 和 Domain Service 使用 `interface + *Impl`，调用方不得依赖实现类。
- 跨模块用例、循环依赖风险和跨模块写事务提升到 `web` Processor，并使用一个本地 `TransactionTemplate`。
- 保留工作区已有改动；只用增量补丁，不提交、不创建分支、不修改生产 Java 或数据库资产。

---

### Task 1: Add a failing documentation boundary gate

**Files:**
- Modify: `requirements-site/tests/site-content.test.mjs`
- Test: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**
- Consumes: 新决策中 D1–D4 的稳定关键词和目标文件路径。
- Produces: `business module collaboration policy is owned once and projected consistently` 静态门禁。

- [ ] **Step 1: Load the new decision, Finance mapping, and focused Cursor rule**

```js
const businessModuleCollaborationDecisionSource = readFileSync(
  new URL(
    "../../agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md",
    import.meta.url,
  ),
  "utf8",
);
const financeModularCodeDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-09-04-finance-modular-code-design.md",
    import.meta.url,
  ),
  "utf8",
);
const javaBusinessModuleBoundariesRuleSource = readFileSync(
  new URL("../../.cursor/rules/java-business-module-boundaries.mdc", import.meta.url),
  "utf8",
);
```

- [ ] **Step 2: Assert the common owner, thin projections, and Finance mapping**

```js
test("business module collaboration policy is owned once and projected consistently", () => {
  assert.match(businessModuleCollaborationDecisionSource, /Decision status: Confirmed/u);
  assert.match(javaProjectStructureSource, /业务能力模块默认不建立彼此的直接 Maven 依赖/u);
  assert.match(javaProjectStructureSource, /稳定.*业务能力[\s\S]*单向依赖/u);
  assert.match(javaProjectStructureSource, /形成.*循环[\s\S]*web.*Processor/u);
  assert.match(developmentConventionsSource, /调用方只依赖.*接口.*不依赖.*\*Impl/u);
  assert.match(javaBusinessModuleBoundariesRuleSource, /DataObject[\s\S]*Owner.*JAR/u);
  assert.match(financeModularCodeDesignSource, /paytech-finance-accounting/u);
  assert.match(financeModularCodeDesignSource, /默认不建立直接 Maven 依赖/u);
  assert.match(agentReadmeSource, /2026-09-04-finance-modular-code-design\.md/u);
});
```

- [ ] **Step 3: Run the focused test and confirm RED**

Run: `node --test --test-name-pattern="business module collaboration policy" requirements-site/tests/site-content.test.mjs`

Expected: FAIL because the new Cursor rule and synchronized public/Finance wording do not yet exist.

### Task 2: Publish the common authority and coding projections

**Files:**
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify: `.cursor/rules/java-business-module-boundaries.mdc`
- Modify: `agent-docs/DOCUMENT_GOVERNANCE.md`

**Interfaces:**
- Consumes: D1–D4 from the decision record.
- Produces: one complete architecture owner plus concise human and Agent projections.

- [ ] **Step 1: Clarify the default and exception in the architecture owner**

Add to the `允许和禁止的依赖` section:

```markdown
- 业务能力模块默认不建立彼此的直接 Maven 依赖。只有稳定、可复用的 Owner 业务能力，同时满足窄接口、单向和无环条件时，才允许调用方依赖 Owner 模块。
- 调用方只依赖 Owner 发布的业务 Service 接口和 Domain / Application Command / Result；不得依赖 `*Impl` 或内部 Domain Service。
```

- [ ] **Step 2: Keep the coding convention as a thin projection**

State the same default/exception briefly in `DEVELOPMENT_CONVENTIONS.md`, retaining the existing Service, DataObject and transaction rules and linking to the full owner.

- [ ] **Step 3: Create the focused Java rule**

Update the existing `.cursor/rules/java-business-module-boundaries.mdc` thin projection. Keep its POM/Java/Mapper XML globs, `alwaysApply: false`, fewer than 50 lines, and add the approved default/exception plus actionable allowed/forbidden examples; do not create a duplicate rule for the same concern.

- [ ] **Step 4: Register the decision without creating a second owner**

Link the decision from `DOCUMENT_GOVERNANCE.md` and explicitly retain `JAVA_PROJECT_STRUCTURE.md` as the unique owner.

### Task 3: Convert Finance into a system mapping

**Files:**
- Modify: `agent-docs/plans/2026-09-04-finance-modular-code-design.md`
- Modify: `finance-service/README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`

**Interfaces:**
- Consumes: the common collaboration rule.
- Produces: accurate `paytech-finance-*` module mapping and visible navigation/status.

- [ ] **Step 1: Correct Finance document metadata and scope**

Replace stale worktree-only wording with decision/implementation/deployment metadata and point current implementation claims to the status ledger and `finance-service/README.md`.

- [ ] **Step 2: Use actual module names and current database constraint language**

Use `paytech-finance-api`, `paytech-finance-accounting`, `paytech-finance-treasury`, `paytech-finance-tax`, `paytech-finance-web`, and `paytech-finance-test`. Replace “保留当前本库外键” with logical relationships and the project-wide prohibition on physical FK/CHECK creation.

- [ ] **Step 3: Apply the approved hybrid dependency model**

Document that Accounting, Treasury, and Tax default to no direct Maven dependency; allow only stable one-way Owner Service interfaces and Domain/Application models; route cycles and cross-module use cases to `paytech-finance-web` Processors. Require `interface + *Impl` and forbid cross-module implementation/persistence access. In `finance-service/README.md`, keep “no current direct dependency” as an implementation fact and link future changes to the shared policy.

- [ ] **Step 4: Route and ledger the standard**

Add the Finance modular design to the Finance row in `agent-docs/README.md`. Add a `JAVA-03` migration item and link the decision/plan from `CURRENT_IMPLEMENTATION.md` without claiming code compliance.

### Task 4: Verify the documentation system

**Files:**
- Test: `requirements-site/tests/site-content.test.mjs`
- Verify: all changed Markdown and `.mdc` files

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: reproducible evidence that the rule is consistent and linked.

- [ ] **Step 1: Run the focused gate and confirm GREEN**

Run: `node --test --test-name-pattern="business module collaboration policy" requirements-site/tests/site-content.test.mjs`

Expected: PASS.

- [ ] **Step 2: Run the full requirements-site content suite**

Run: `npm --prefix requirements-site test`

Expected: PASS, or report unrelated pre-existing failures separately with exact output.

- [ ] **Step 3: Run lint for the touched JavaScript test**

Run: `npm --prefix requirements-site run lint`

Expected: PASS, or report unrelated pre-existing failures separately.

- [ ] **Step 4: Audit links, forbidden wording, and diff scope**

Run: `rg -n "三个业务模块不相互依赖|保留当前本库外键|finance-(api|accounting|treasury|tax|web|test)" agent-docs/plans/2026-09-04-finance-modular-code-design.md`

Expected: no stale absolute-dependency, physical-FK, or unprefixed artifact wording.

Run: `git diff --check`

Expected: no whitespace errors.

Run: `git diff -- agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md agent-docs/JAVA_PROJECT_STRUCTURE.md agent-docs/DEVELOPMENT_CONVENTIONS.md agent-docs/DOCUMENT_GOVERNANCE.md agent-docs/plans/2026-09-04-finance-modular-code-design.md finance-service/README.md agent-docs/README.md agent-docs/CURRENT_IMPLEMENTATION.md .cursor/rules/java-business-module-boundaries.mdc requirements-site/tests/site-content.test.mjs docs/superpowers/plans/2026-09-05-business-module-collaboration-policy.md`

Expected: only the approved public rule, projections, Finance mapping, navigation, status and gates.
