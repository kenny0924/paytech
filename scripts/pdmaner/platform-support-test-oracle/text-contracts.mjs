const ENGLISH_NEGATED_HMAC = Object.freeze([
  /\b(?:with\s+)?no\s+(?:redis\s+)?hmac\b/giu,
  /\bwithout\b[^.;!?]{0,80}\bhmac(?:[-\s](?:transform(?:ation|ed)?|derivation|pseudonymization))?\b/giu,
  /\b(?:must|shall|should|does|do)\s+not\b[^.;!?]{0,100}\bhmac(?:[-\s](?:transform(?:ation|ed)?|derivation|pseudonymization))?\b/giu,
  /\bnever\b[^.;!?]{0,80}\bhmac(?:[-\s](?:transform(?:ation|ed)?|derivation|pseudonymization))?\b/giu,
]);

const CHINESE_NEGATED_HMAC = Object.freeze([
  /(?:没有|无需|不需要|不使用|不做|不得|禁止|不应)[^。；！？]{0,80}HMAC/giu,
]);

function removeNegatedHmacClauses(clause) {
  return [...ENGLISH_NEGATED_HMAC, ...CHINESE_NEGATED_HMAC]
    .reduce((remaining, pattern) => remaining.replace(pattern, ""), clause);
}

function isAffirmativeRedisIdentityHmac(clause) {
  const candidate = removeNegatedHmacClauses(clause);
  const hasRedisAndHmac = /\bredis\b/iu.test(candidate) && /\bhmac\b/iu.test(candidate);
  if (!hasRedisAndHmac) return false;

  const englishIdentity = /\b(?:identity|inputs?|subjects?|keys?|limiter)\b/iu.test(candidate);
  const englishAction = /\b(?:must|shall|should|required|uses?|using|apply|applying|applied|transform(?:ation|ed)?|derive|derived|derivation)\b/iu.test(candidate);
  const chineseIdentity = /(?:身份|输入|主体|限流|键)/u.test(candidate);
  const chineseAction = /(?:必须|应当|需要|采用|使用|执行|先做|转换|派生)/u.test(candidate);
  return (englishIdentity && englishAction) || (chineseIdentity && chineseAction);
}

export function redisIdentityHmacContradictions(content) {
  if (typeof content !== "string") throw new TypeError("Redis identity contract content must be a string");
  return content
    .replace(/\s+/gu, " ")
    .split(/[.;!?。；！？]+/u)
    .map((clause) => clause.trim())
    .filter(Boolean)
    .filter(isAffirmativeRedisIdentityHmac);
}
