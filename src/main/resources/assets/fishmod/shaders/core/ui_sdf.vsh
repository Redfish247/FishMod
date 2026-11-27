#version 330

layout(std140) uniform UiView {
    vec4 View;
};

in vec4 PosLocal;
in vec4 HalfUv;
in vec4 Radius;
in vec4 ColorA;
in vec4 ColorB;
in vec4 Params;

out vec2 vLocal;
flat out vec2 vHalf;
flat out vec4 vRad;
flat out vec4 vColA;
flat out vec4 vColB;
flat out vec4 vParams;
out vec2 vUv;

void main() {
    gl_Position = vec4(PosLocal.x / View.x * 2.0 - 1.0, 1.0 - PosLocal.y / View.y * 2.0, 0.0, 1.0);
    vLocal = PosLocal.zw;
    vHalf = HalfUv.xy;
    vUv = HalfUv.zw;
    vRad = Radius;
    vColA = ColorA;
    vColB = ColorB;
    vParams = Params;
}
