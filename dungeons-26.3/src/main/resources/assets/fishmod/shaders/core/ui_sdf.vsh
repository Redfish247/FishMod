#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform UiView {
    vec4 View;
};

layout(location = 0) in vec4 PosLocal;
layout(location = 1) in vec4 HalfUv;
layout(location = 2) in vec4 Radius;
layout(location = 3) in vec4 ColorA;
layout(location = 4) in vec4 ColorB;
layout(location = 5) in vec4 Params;

layout(location = 0) out vec2 vLocal;
layout(location = 1) flat out vec2 vHalf;
layout(location = 2) flat out vec4 vRad;
layout(location = 3) flat out vec4 vColA;
layout(location = 4) flat out vec4 vColB;
layout(location = 5) flat out vec4 vParams;
layout(location = 6) out vec2 vUv;

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
