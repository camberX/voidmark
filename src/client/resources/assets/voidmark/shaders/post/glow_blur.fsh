#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 stepDir = oneTexel * BlurDir;
    float radius = clamp(round(Radius), 1.0, 24.0);
    float sigma = max(radius * 0.42, 0.8);
    float twoSigmaSq = 2.0 * sigma * sigma;

    vec4 acc = vec4(0.0);
    float wsum = 0.0;
    for (float i = -radius; i <= radius; i += 1.0) {
        float w = exp(-(i * i) / twoSigmaSq);
        acc += texture(InSampler, texCoord + stepDir * i) * w;
        wsum += w;
    }
    fragColor = acc / max(wsum, 0.0001);
}
