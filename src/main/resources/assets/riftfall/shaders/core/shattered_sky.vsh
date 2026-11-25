#version 150
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 skyDirection;
void main() {
    skyDirection = Position;
    vec4 p = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = p.xyww;
}
