#version 450

/*
   Author: Gigaherz
   License: Public domain

   The older lcd3x maths, as the nx-redux handheld's glsl/lcd3x.glsl has it: strong scanlines, a faint LCD stripe.
*/

layout(push_constant) uniform Push
{
	vec4 SourceSize;
	vec4 OriginalSize;
	vec4 OutputSize;
	uint FrameCount;
	float brighten_scanlines;
	float brighten_lcd;
} params;

#pragma parameter brighten_scanlines "Brighten Scanlines" 6.0 1.0 32.0 0.5
#define brighten_scanlines params.brighten_scanlines
#pragma parameter brighten_lcd "Brighten LCD" 24.0 1.0 32.0 0.5
#define brighten_lcd params.brighten_lcd

layout(std140, set = 0, binding = 0) uniform UBO
{
	mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
   gl_Position = global.MVP * Position;
   vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;

void main()
{
    vec3 res = texture(Source, vTexCoord).xyz;

    vec2 angle = vTexCoord * 6.283185307 * params.SourceSize.xy;

    float yfactor = (brighten_scanlines + sin(angle.y)) / (brighten_scanlines + 1.0);
    vec3 xfactors = (brighten_lcd + sin(angle.x + vec3(0.0, 0.0, -3.141592654))) / (brighten_lcd + 1.0);

    FragColor = vec4(yfactor * xfactors * res, 1.0);
}
