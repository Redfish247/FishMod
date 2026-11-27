#version 330

uniform sampler2D Sampler0;

in vec2 vLocal;
flat in vec2 vHalf;
flat in vec4 vRad;
flat in vec4 vColA;
flat in vec4 vColB;
flat in vec4 vParams;
in vec2 vUv;

out vec4 fragColor;

float sdBox(vec2 p, vec2 b, vec4 r) {
    // r = (tl, tr, br, bl), y grows downward
    float rr = p.x > 0.0 ? (p.y > 0.0 ? r.z : r.y) : (p.y > 0.0 ? r.w : r.x);
    rr = min(rr, min(b.x, b.y));
    vec2 q = abs(p) - b + rr;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rr;
}

vec4 pm(vec4 c) { return vec4(c.rgb * c.a, c.a); }

void main() {
    float mode = vParams.x;
    // Sample outside branches so derivatives stay defined
    float glyph = texture(Sampler0, vUv).r;
    float d = sdBox(vLocal, vHalf, vRad);
    float aa = max(fwidth(d), 1e-4);
    if (mode > 1.5) { fragColor = pm(vColA); return; }
    if (mode > 0.5) { fragColor = pm(vColA) * glyph; return; }
    float feather = vParams.z;
    if (feather > 0.0) {
        float t = clamp((d + feather * 0.5) / feather, 0.0, 1.0);
        fragColor = pm(vColA) * (1.0 - t);
        return;
    }
    float cov = clamp(0.5 - d / aa, 0.0, 1.0);
    vec4 fill = vColA;
    float g = vParams.w;
    if (g > 0.5) {
        float t = g > 1.5 ? (vLocal.x / (2.0 * vHalf.x) + 0.5) : (vLocal.y / (2.0 * vHalf.y) + 0.5);
        fill = mix(vColA, vColB, clamp(t, 0.0, 1.0));
    }
    float stroke = vParams.y;
    if (stroke > 0.0) {
        float inner = clamp(0.5 - (d + stroke) / aa, 0.0, 1.0);
        fragColor = pm(fill) * inner + pm(vColB) * max(cov - inner, 0.0);
        return;
    }
    fragColor = pm(fill) * cov;
}
