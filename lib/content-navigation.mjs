export const DEFAULT_CONTENT_VIEW = "architecture";

export function resolveContentView(hash, navigationItems) {
  const sectionId = hash.replace(/^#/, "");
  const item = navigationItems.find((navigationItem) => navigationItem.id === sectionId);
  return item?.group === "use-cases" ? "use-cases" : DEFAULT_CONTENT_VIEW;
}

export function navigationForView(navigationItems, view) {
  return navigationItems.filter((item) => item.group === view);
}
