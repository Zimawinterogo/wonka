#version 150
uniform sampler2D uOpaqueDepth;
uniform sampler2D uRawDepth;
uniform mat4 uProjection;
uniform vec2 uViewport;
uniform float uVisibility;
uniform float uLamp;
uniform float uDaylight;
uniform float uOpacity;
uniform float uSnow;
uniform float uStorm;
in vec2 flakeLocal;
in vec3 centerView;
in float snowAlpha;
in float scaleMotion;
in vec3 flakeTraits;
out vec4 fragColor;
void main() {
    if(snowAlpha<=0.001) discard;
    vec2 screenUV=gl_FragCoord.xy/uViewport;
    // World object distance reconstructed from opaque depth texture. Transparent
    // layers (glass, snowfall, water) are NOT allowed to punch holes in fog.
    float opaqueDepth=texture(uOpaqueDepth,screenUV).r;
    float ndc=opaqueDepth*2.0-1.0;
    // Camera forward = negative Z. Compute linear eye-space distance.
    float denominator=ndc+uProjection[2][2];
    float worldZ = abs(denominator)>0.00001 ? -uProjection[3][2]/denominator : -10000.0;
    float particleDist=max(0.01,length(centerView));
    float gap=(-worldZ)-(-centerView.z); // positive if wall lies behind snow
    float softDepth = opaqueDepth>0.999999 ? 1.0 : smoothstep(-0.06,0.55,gap);
    if(softDepth<=0.001) discard;

    // The raw depth includes translucent glass with Iris, unlike uOpaqueDepth.
    // A screen-space corner alone is NOT enough to identify the first-person
    // hand: doing so cuts rectangular holes in the snowfall behind windows.
    // Only reject fragments when the raw occluder is very close to the camera.
    bool handRegion=screenUV.y<0.26 && (screenUV.x<0.28 || screenUV.x>0.72);
    if(handRegion) {
        float raw=texture(uRawDepth,screenUV).r;
        if(raw+0.02<opaqueDepth) {
            float rawNdc=raw*2.0-1.0;
            float rawDenom=rawNdc+uProjection[2][2];
            float rawEyeZ=abs(rawDenom)>0.00001
                ? -uProjection[3][2]/rawDenom : -10000.0;
            // Reject only the near-field hand, not windows 1+ blocks away.
            if(rawEyeZ<0.0 && -rawEyeZ<0.70) discard;
        }
    }
    float forward=clamp(-centerView.z/particleDist,0.0,1.0);
    float angularRadius=sqrt(max(0.0,1.0-forward*forward))/max(forward,0.001);
    float hotspot=exp(-1.30*pow(angularRadius/0.15,2.0));
    float spill=1.0-smoothstep(0.20,0.48,angularRadius);
    float beam=clamp(0.60*hotspot+0.40*spill,0.0,1.0)*clamp(uLamp,0.0,1.0);
    float limit=uVisibility>255.0 ? 128.0 : max(1.0,uVisibility)+5.0*beam;
    float distanceAlpha=1.0-smoothstep(max(0.1,limit-3.0),limit,particleDist);
    // Soft elliptical streak: no jagged white squares.
    float q=length(flakeLocal);
    float blizzard=smoothstep(0.65,1.0,uStorm);
    float softShape=1.0-smoothstep(0.27,1.0,q);
    // Hard pixel silhouette: no feathering, derivative antialiasing or blur.
    // Different elongations and offsets avoid identical stamps or round dots.
    float angle=flakeTraits.x*6.2831853;
    vec2 local=mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*flakeLocal;
    local=(floor(local*6.0)+0.5)/6.0; // nearest-cell sampling, hard pixels
    float variation=flakeTraits.y;
    float d0=length(local/vec2(mix(0.32,0.46,variation),0.56));
    float d1=length((local-vec2(0.25,0.22))/vec2(0.28,mix(0.28,0.42,variation)));
    float d2=length((local+vec2(0.22,0.18))/vec2(mix(0.22,0.35,variation),0.30));
    float d3=length((local-vec2(-0.10,0.50))/vec2(0.20,0.24));
    float outline=min(min(d0,d1),min(d2,d3));
    float grain=outline<=1.0 ? 0.90 : 0.0;
    float shape=mix(softShape,grain,blizzard);
    float alpha=shape*snowAlpha*softDepth*distanceAlpha*uOpacity
                * clamp(uSnow,0.0,1.0)*(0.72+0.28*uStorm);
    // Gentle tumbling changes scattering rather than flashing whole particles.
    alpha*=mix(1.0,flakeTraits.z,blizzard);
    if(alpha<=0.005) discard;
    vec3 night=vec3(0.39,0.47,0.59);
    vec3 day=vec3(0.83,0.87,0.94);
    vec3 color=mix(night,day,clamp((uDaylight-0.23)/0.77,0.0,1.0));
    // Existing flashlight cone lights the grains locally, with distance falloff.
    float scatter=beam*(1.0-smoothstep(2.0,24.0,particleDist));
    color=mix(color,mix(color,vec3(0.85,0.87,0.90),0.45*scatter),blizzard);
    fragColor=vec4(color*alpha,alpha); // premultiplied alpha blending
}
