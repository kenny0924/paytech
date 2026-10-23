import type { Catalog } from './runtime.ts';
import adminNs from './en/i18n-admin.json' with { type: 'json' };
import enumNs from './en/enum.json' with { type: 'json' };

/** English base text bundled at build time; one JSON file per namespace. */
export const builtin: Catalog = {
  'i18n-admin': adminNs,
  enum: enumNs,
};
