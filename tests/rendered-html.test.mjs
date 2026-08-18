import test from "node:test";
import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";

const root = new URL("../", import.meta.url);

test("page renders the requirements application instead of the starter", async () => {
  const page = await readFile(new URL("app/page.tsx", root), "utf8");
  const layout = await readFile(new URL("app/layout.tsx", root), "utf8");

  assert.match(page, /RequirementsSite/);
  assert.match(layout, /MoR Platform Requirements/);
  assert.doesNotMatch(`${page}\n${layout}`, /SkeletonPreview|codex-preview/);
});

test("starter preview directory has been removed", async () => {
  await assert.rejects(access(new URL("app/_sites-preview", root)));
});

test("the page exposes all primary section ids", async () => {
  const requirementsSite = await readFile(
    new URL("components/requirements-site.tsx", root),
    "utf8",
  );
  for (const id of [
    "overview",
    "business-model",
    "architecture",
    "domain-map",
    "end-to-end",
    "use-cases",
    "systems",
    "entry-applications",
    "payment-model",
    "finance-model",
    "decisions",
    "open-questions",
  ]) {
    assert.match(requirementsSite, new RegExp(`id=[\"']${id}[\"']`));
  }
});
