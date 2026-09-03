#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;

out vec4 fragColor;

float vanillaA(vec4 sampleColor) {
    return sampleColor.a > 0.92 ? 1.0 : 0.0;
}

float espA(vec4 sampleColor) {
    return (sampleColor.a > 0.001 && sampleColor.a <= 0.92) ? sampleColor.a : 0.0;
}

vec4 vanillaSobel(vec2 oneTexel) {
    vec4 center = texture(MaskSampler, texCoord);
    vec4 left = texture(MaskSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(MaskSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture(MaskSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture(MaskSampler, texCoord + vec2(0.0, oneTexel.y));
    float ca = vanillaA(center);
    float la = vanillaA(left);
    float ra = vanillaA(right);
    float ua = vanillaA(up);
    float da = vanillaA(down);
    float total = clamp(abs(ca - la) + abs(ca - ra) + abs(ca - ua) + abs(ca - da), 0.0, 1.0);
    vec3 outColor = center.rgb * ca + left.rgb * la + right.rgb * ra + up.rgb * ua + down.rgb * da;
    return vec4(outColor * 0.2, total);
}

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec4 blur = texture(InSampler, texCoord);
    vec4 mask = texture(MaskSampler, texCoord);
    float inside = espA(mask);

    float n = inside;
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(oneTexel.x, 0.0))));
    n = max(n, espA(texture(MaskSampler, texCoord - vec2(oneTexel.x, 0.0))));
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(0.0, oneTexel.y))));
    n = max(n, espA(texture(MaskSampler, texCoord - vec2(0.0, oneTexel.y))));
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(oneTexel.x, oneTexel.y))));
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(-oneTexel.x, oneTexel.y))));
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(oneTexel.x, -oneTexel.y))));
    n = max(n, espA(texture(MaskSampler, texCoord + vec2(-oneTexel.x, -oneTexel.y))));
    float rim = max(n - inside, 0.0);

    float outer = max(blur.a - inside, 0.0);
    outer = pow(clamp(outer, 0.0, 1.0), 0.62);
    outer = smoothstep(0.0, 0.85, outer);

    vec3 color = blur.rgb / max(blur.a, 0.001);
    if (inside > 0.001) {
        color = mask.rgb / max(mask.a, 0.001);
    }

    float alpha = (outer * 1.05 + rim * 0.92) * (1.0 - inside);
    vec4 esp = vec4(color, clamp(alpha, 0.0, 1.0));
    vec4 vanilla = vanillaSobel(oneTexel);

    fragColor = vec4(esp.rgb * esp.a + vanilla.rgb * vanilla.a, clamp(esp.a + vanilla.a, 0.0, 1.0));
}
