// CCT's physical projection of COMPENSATION_EVENT_GUIDE section 3.
// This does not change business event envelopes or command idempotency.
export const CLIENT_COMPENSATION_FIELD_STORAGE = Object.freeze(Object.fromEntries([
  ["id", "BIGINT", "", false],
  ["event_key", "VARCHAR", 128, false, "", "utf8mb4"],
  ["event_type", "VARCHAR", 64, false, "", "utf8mb4"],
  ["sub_event_type", "VARCHAR", 64, false, "", "utf8mb4"],
  ["payload_json", "VARCHAR", 4096, true, "", "utf8mb4"],
  ["execute_status", "VARCHAR", 16, false, "'INIT'", "ascii"],
  ["execute_count", "INT", "", false, "0"],
  ["next_execute_at", "DATETIME", 6, false, "CURRENT_TIMESTAMP(6)"],
  ["lock_owner", "VARCHAR", 64, true],
  ["lock_until", "DATETIME", 6, true],
  ["last_error_code", "VARCHAR", 64, true],
  ["last_error_summary", "VARCHAR", 1000, true],
  ["lock_version", "BIGINT", "", false, "1"],
  ["create_time", "DATETIME", 6, false, "CURRENT_TIMESTAMP(6)"],
  ["update_time", "DATETIME", 6, false, "CURRENT_TIMESTAMP(6)"],
].map(([key, type, length, nullable, defaultValue = "", characterSet]) => [
  `compensation_event.${key}`,
  Object.freeze({ type, length, scale: "", nullable, defaultValue,
    extProps: characterSet ? { characterSet, collation: characterSet === "ascii" ? "ascii_bin" : "utf8mb4_0900_bin" } : {},
  }),
])));
