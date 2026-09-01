#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec4 blur = texture(InSampler, texCoord);
    vec4 mask = texture(MaskSampler, texCoord);
    float inside = mask.a;

    // Neighbour max for a 1px silhouette rim.
    float n = inside;
    n = max(n, texture(MaskSampler, texCoord + vec2(oneTexel.x, 0.0)).a);
    n = max(n, texture(MaskSampler, texCoord - vec2(oneTexel.x, 0.0)).a);
    n = max(n, texture(MaskSampler, texCoord + vec2(0.0, oneTexel.y)).a);
    n = max(n, texture(MaskSampler, texCoord - vec2(0.0, oneTexel.y)).a);
    n = max(n, texture(MaskSampler, texCoord + vec2(oneTexel.x, oneTexel.y)).a);
    n = max(n, texture(MaskSampler, texCoord + vec2(-oneTexel.x, oneTexel.y)).a);
    n = max(n, texture(MaskSampler, texCoord + vec2(oneTexel.x, -oneTexel.y)).a);
    n = max(n, texture(MaskSampler, texCoord + vec2(-oneTexel.x, -oneTexel.y)).a);
    float rim = max(n - inside, 0.0);

    // Blur minus the filled body = glow only outside the model.
    float outer = max(blur.a - inside, 0.0);
    outer = pow(clamp(outer, 0.0, 1.0), 0.62);
    outer = smoothstep(0.0, 0.85, outer);

    vec3 color = blur.rgb / max(blur.a, 0.001);
    if (mask.a > 0.001) {
        color = mask.rgb / max(mask.a, 0.001);
    }

    float alpha = (outer * 1.05 + rim * 0.92) * (1.0 - inside);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
