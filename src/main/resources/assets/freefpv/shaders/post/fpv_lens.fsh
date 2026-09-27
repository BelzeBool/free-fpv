${shader_header}

// Placeholders are filled per Minecraft version when the mod is built (26.3 declares interface locations).
// FPV camera lens and video look: equidistant fisheye over the wide rectilinear frame, chromatic
// aberration towards the edges, colour grading and vignette. Values come from post_effect/fpv_*.json.

uniform sampler2D InSampler;

${shader_in} vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FpvLensConfig {
    float Fisheye;
    float Chroma;
    float Saturation;
    float Contrast;
    vec3 Tint;
    float Vignette;
    float Softness;
};

${shader_out} vec4 fragColor;

// Half diagonal field of view of the rendered frame (about 118 degrees horizontal at 16:9).
const float HALF_DIAGONAL = 1.08;

vec2 lens(vec2 uv) {
    vec2 p = uv * 2.0 - 1.0;
    float aspect = OutSize.x / OutSize.y;
    float corner = length(vec2(aspect, 1.0));
    float r = length(vec2(p.x * aspect, p.y)) / corner;
    float rectilinear = r > 1e-4 ? tan(r * HALF_DIAGONAL) / tan(HALF_DIAGONAL) / r : HALF_DIAGONAL / tan(HALF_DIAGONAL);
    float scale = mix(1.0, rectilinear, Fisheye);
    return p * scale * 0.5 + 0.5;
}

vec3 sampleAt(vec2 uv) {
    vec2 px = 1.0 / InSize;
    vec3 c = texture(InSampler, uv).rgb;
    if (Softness > 0.0) {
        vec3 side = texture(InSampler, uv + vec2(px.x, 0.0)).rgb + texture(InSampler, uv - vec2(px.x, 0.0)).rgb;
        c = mix(c, side * 0.5, Softness);
    }
    return c;
}

void main() {
    vec2 uv = lens(texCoord);
    vec2 fromCentre = uv - 0.5;
    float edge = dot(fromCentre, fromCentre);
    vec2 shift = fromCentre * Chroma * edge;

    vec3 color;
    color.r = sampleAt(uv + shift).r;
    color.g = sampleAt(uv).g;
    color.b = sampleAt(uv - shift).b;

    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    color *= Tint;

    float vignette = 1.0 - Vignette * smoothstep(0.25, 0.75, sqrt(edge) * 1.35);
    fragColor = vec4(clamp(color * vignette, 0.0, 1.0), 1.0);
}
