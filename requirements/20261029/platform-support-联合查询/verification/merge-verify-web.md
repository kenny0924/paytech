# Merge check: Operations web (Code-MoR-jq-m, 2026-10-30)

This covers the joint-query web work in this tree, which is uncommitted changes on top of HEAD 30361be7 (main). Everything was run in `operations-console-web`. No source files were changed while checking, so there was nothing to fix after the merge.

## Results

| Check | Result |
|---|---|
| `npx tsc --noEmit` | passes, no errors |
| `npm test` | 1640 of 1640 pass, 0 fail |
| `node --experimental-strip-types --test src/i18nSourceGuard.test.ts src/i18nCatalog.test.ts` | 7 of 7 pass |
| `npm run build` | builds (`dist` removed afterwards) |

## Points checked

- **Page count.** The value 81 in `configurationModel.test.ts` is correct. `pageCapabilities` now lists 81 pages: 78 from the base we started on, plus 1 added by main in 9d272ae6 (underwriting records, REQ-03), plus our 2 (`OPERATIONS_JOINT_QUERY` and `OPERATIONS_QUERY_KEY_TYPES`). Every component key is unique, and the test passes.
- **Our two pages are fully registered.** Each appears once in `pageCapabilities.ts`, `ConsolePage.tsx`, `src/i18n/en/nav.json` and `i18n/zh-CN/nav.json`.
- **Our files merged unchanged.** Every file under `src/dataQuery/`, both new pages and `i18n/zh-CN/data-query.json` are byte-for-byte the same as in the Code-MoR-jq-ops worktree. The remaining differences against that worktree come only from main (underwriting, client review, master data, and menu entries in `menu.ts` and `nav.json`).
- **Main's workflow node panel change (30361be7).** It touches `src/workflow/EditorPanels.tsx` and `src/workflow/workflow.css`; we change neither file (`git diff HEAD -- src/workflow` is empty). Our CSS classes (`jq-*`, `kt-*`, `dq-joint-*`) do not appear in `workflow.css` or `styles.css`, so the styles can't collide. Both the workflow canvas and our link graph import `@xyflow/react/dist/style.css`, which is unchanged.

## Not covered

No browser pass on this merged tree, and still no run against the real backend.
