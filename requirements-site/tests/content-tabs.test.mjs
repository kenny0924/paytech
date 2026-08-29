import test from "node:test";
import assert from "node:assert/strict";

const navigationItems = [
  { id: "overview", group: "architecture" },
  { id: "entry-applications", group: "architecture" },
  { id: "use-cases", group: "use-cases" },
  { id: "systems", group: "use-cases" },
];

async function loadNavigation() {
  try {
    return await import("../lib/content-navigation.mjs");
  } catch {
    return null;
  }
}

test("use case deep links open the use case view", async () => {
  const navigation = await loadNavigation();

  assert.ok(navigation, "content navigation helper must exist");
  assert.equal(navigation.resolveContentView("#use-cases", navigationItems), "use-cases");
  assert.equal(navigation.resolveContentView("#systems", navigationItems), "use-cases");
  assert.equal(navigation.resolveContentView("#entry-applications", navigationItems), "architecture");
  assert.equal(navigation.resolveContentView("#unknown", navigationItems), "architecture");
});

test("each top-level tab receives only its own sidebar navigation", async () => {
  const navigation = await loadNavigation();

  assert.ok(navigation, "content navigation helper must exist");
  assert.deepEqual(
    navigation.navigationForView(navigationItems, "architecture").map((item) => item.id),
    ["overview", "entry-applications"],
  );
  assert.deepEqual(
    navigation.navigationForView(navigationItems, "use-cases").map((item) => item.id),
    ["use-cases", "systems"],
  );
});
