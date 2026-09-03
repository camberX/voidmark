#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    // Vanilla GLOWING is written fully opaque. ESP and block outline stay at
    // config opacity (0.15–0.90), so drop the opaque samples before the ESP blur.
    if (color.a > 0.92) {
        fragColor = vec4(0.0);
    } else {
        fragColor = color;
    }
}
