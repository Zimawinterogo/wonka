#version 150
uniform sampler2D Color;
uniform sampler2D Depth;
uniform sampler2D OpaqueDepth;
uniform mat4 InverseProjection;
uniform float Fog;
uniform float Visibility;
uniform float Lamp;
uniform float Daylight;
uniform float ShaderPack;
in vec2 uv;
out vec4 fragColor;

void main() {
    vec4 original = texture(Color, uv);
    float rawDepth = texture(Depth, uv).r;
    // Weather must use the scene behind glass, not the nearer translucent depth.
    // Custom snow is drawn AFTER this pass, so it cannot pollute this depth.
    float opaqueDepth = texture(OpaqueDepth, uv).r;
    // A translucent window can have a closer raw depth than the opaque scene.
    // Do not mistake it for a first-person hand just because it is in a corner.
    bool handRegion = uv.y < 0.26 && (uv.x < 0.28 || uv.x > 0.72);
    bool handPixel = false;
    if (handRegion && rawDepth < 0.999999
            && (ShaderPack < 0.5 || rawDepth + 0.02 < opaqueDepth)) {
        vec4 rawProjected = InverseProjection * vec4(
            uv * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
        float rawEyeDepth = abs(rawProjected.z) / max(abs(rawProjected.w), 0.00001);
        // Compare eye-space Z in both shaders (not non-linear depth-buffer
        // units). A near-field hand is exempt; glass farther away is not.
        handPixel = rawEyeDepth < 0.70;
    }
    float depth = opaqueDepth; // Weather distance is the scene behind translucent windows.
    vec4 projected = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 view = projected.xyz / max(abs(projected.w), 0.00001);
    float dist = length(view);
    float forward = clamp(-view.z / max(dist, 0.00001), 0.0, 1.0);
    float angularRadius = sqrt(max(0.0, 1.0 - forward * forward)) / max(forward, 0.001);

    float hotspot = exp(-1.30 * pow(angularRadius / 0.15, 2.0));
    float spill = 1.0 - smoothstep(0.20, 0.48, angularRadius);
    float beam = clamp(0.60 * hotspot + 0.40 * spill, 0.0, 1.0) * clamp(Lamp, 0.0, 1.0);
    float reach = 1.0 - smoothstep(8.0, 38.0, dist);

    float fogStrength = clamp(Fog, 0.0, 1.0);
    float zoneVisibility = max(1.0, Visibility);
    bool boundedFog = Visibility <= 255.0;
    // Zone visibility is authoritative; flashlight extends it by up to five blocks.
    float limit = zoneVisibility + 5.0 * beam;


    float density = (0.001 + fogStrength * 0.055) * (1.0 - 0.30 * beam * reach);
    float haze = (1.0 - exp(-dist * density)) * fogStrength;
    float wall = boundedFog ? smoothstep(limit * 0.58, limit, dist) : 0.0;
    float fog = max(haze, wall);
    if (handPixel) fog = 0.0;

    vec3 fogColor = vec3(0.29, 0.345, 0.39) * Daylight;
    vec3 rgb = mix(original.rgb, fogColor, clamp(fog, 0.0, 1.0));

    // Flashlight only lights existing surfaces. Do not re-expose distant geometry.
    float shaderDimmer = mix(1.0, 0.66, clamp(ShaderPack, 0.0, 1.0));
    if (depth < 0.999999 && !handPixel) {
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        float darkness = 1.0 - smoothstep(0.18, 0.80, luma);
        float light = beam * reach * (0.45 + 0.55 * darkness) * shaderDimmer;
        light *= 1.0 - 0.72 * fog;
        vec3 lift = vec3(0.21, 0.204, 0.19) * (vec3(1.0) - clamp(rgb, 0.0, 1.0));
        vec3 bounce = rgb * vec3(0.25, 0.23, 0.21);
        rgb = min(vec3(1.0), rgb + light * (lift + bounce));
    }

    fragColor = vec4(rgb, original.a);
}
