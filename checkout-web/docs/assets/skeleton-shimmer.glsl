precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/** @time */
uniform float u_time;

/**
 * @label Animate skeleton
 * @default 1
 * @range 0, 1
 */
uniform float u_animate;

void main() {
  vec2 uv = gl_FragCoord.xy / max(u_resolution, vec2(1.0));
  float position = mod((u_time + 0.9) / 1.8, 1.0) * 2.0 - 0.5;
  float distanceFromBand = abs(uv.x - position);
  float highlight = (1.0 - smoothstep(0.0, 0.42, distanceFromBand)) * u_animate;
  vec3 base = vec3(0.898, 0.918, 0.941);
  vec3 light = vec3(0.969, 0.976, 0.988);
  gl_FragColor = vec4(mix(base, light, highlight * 0.7), 1.0);
}
