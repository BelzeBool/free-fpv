"""Generates the drone item models and textures. Run from the repo root: python3 tools/gen_assets.py"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "freefpv")

PALETTE = [
    (0x8A, 0x90, 0x99), (0x4B, 0x50, 0x58), (0x1A, 0x1C, 0x1F), (0x26, 0x28, 0x2B),
    (0xFF, 0x7A, 0x1A), (0xE8, 0xE8, 0xE8), (0x0D, 0x1B, 0x2A), (0xFF, 0x2D, 0x2D),
    (0x2D, 0xFF, 0x6A), (0xF2, 0xC1, 0x2E), (0xB8, 0xBE, 0xC6), (0x6A, 0x70, 0x78),
    (0x3A, 0x6E, 0xA5), (0x33, 0x37, 0x3D), (0x7B, 0x4D, 0xFF), (0x00, 0x00, 0x00),
]
LIGHT, DARK, BLACK, CARBON, ORANGE, WHITE, LENS, RED, GREEN, YELLOW, SILVER, MID, GLASS, GRAPHITE, PURPLE = range(15)


def palette_texture(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for i, (r, g, b) in enumerate(PALETTE[:15]):
        cx, cy = (i % 4) * 4, (i // 4) * 4
        for x in range(4):
            for y in range(4):
                shade = 1.0 - 0.06 * ((x + y) % 2) if i in (CARBON,) else 1.0
                px[cx + x, cy + y] = (int(r * shade), int(g * shade), int(b * shade), 255)
    img.save(path)


def prop_texture(path, tint):
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    c = size / 2
    for r in range(int(c), 0, -1):
        t = r / c
        alpha = int(70 * (0.35 + 0.65 * t) * (1 if t < 0.97 else 0.4))
        draw.ellipse([c - r, c - r, c + r, c + r], fill=tint + (alpha,))
    # motion streaks, the blur a camera catches on spinning blades
    for k in range(2):
        a = k * math.pi
        for w in range(-3, 4):
            ang = a + w * 0.05
            draw.line([c, c, c + math.cos(ang) * c * 0.95, c + math.sin(ang) * c * 0.95], fill=tint + (60,), width=2)
    draw.ellipse([c - 5, c - 5, c + 5, c + 5], fill=(30, 30, 30, 220))
    img = img.filter(ImageFilter.GaussianBlur(1.2)).resize((16, 16), Image.LANCZOS)
    img.save(path)


def uv(color):
    x, y = (color % 4) * 4, (color // 4) * 4
    return [x + 0.5, y + 0.5, x + 3.5, y + 3.5]


def box(frm, to, color, rotation=None, texture="#p"):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        faces[face] = {"uv": uv(color) if texture == "#p" else [0, 0, 16, 16], "texture": texture}
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def prop(cx, cz, y, size):
    h = size / 2
    faces = {"up": {"uv": [0, 0, 16, 16], "texture": "#prop"}, "down": {"uv": [0, 0, 16, 16], "texture": "#prop"}}
    return {"from": [cx - h, y, cz - h], "to": [cx + h, y + 0.05, cz + h], "faces": faces}


def camera_drone():
    e = []
    e.append(box([5, 7, 3.5], [11, 9.5, 12.5], LIGHT))
    e.append(box([6, 9.5, 5], [10, 10.3, 11.5], DARK))
    e.append(box([6, 7.2, 2.6], [10, 9.2, 3.5], GRAPHITE))
    e.append(box([5.5, 7.3, 12.5], [10.5, 9.2, 13.4], MID))
    e.append(box([6.5, 9.6, 12.2], [9.5, 9.9, 13.3], BLACK))
    # gimbal camera under the nose
    e.append(box([7.4, 6.6, 2.9], [8.6, 7.2, 3.6], GRAPHITE))
    e.append(box([7, 5.6, 2.2], [9, 6.8, 3.4], BLACK))
    e.append(box([7.5, 5.9, 2.1], [8.5, 6.6, 2.2], LENS))
    # front obstacle sensors
    e.append(box([6.5, 8.3, 2.5], [7.3, 8.9, 2.6], GLASS))
    e.append(box([8.7, 8.3, 2.5], [9.5, 8.9, 2.6], GLASS))
    # X arms
    for angle in (45, -45):
        e.append(box([0.6, 8.1, 7.5], [15.4, 8.8, 8.5], DARK, {"origin": [8, 8, 8], "axis": "y", "angle": angle}))
    tip = 7.4 / math.sqrt(2)
    tips = [(8 - tip, 8 - tip), (8 + tip, 8 - tip), (8 - tip, 8 + tip), (8 + tip, 8 + tip)]
    for (x, z) in tips:
        e.append(box([x - 0.9, 8.0, z - 0.9], [x + 0.9, 9.4, z + 0.9], SILVER))
        e.append(box([x - 0.5, 9.4, z - 0.5], [x + 0.5, 9.7, z + 0.5], DARK))
        e.append(box([x - 0.4, 7.0, z - 0.4], [x + 0.4, 8.0, z + 0.4], DARK))
        e.append(prop(x, z, 9.8, 6.4))
    # navigation lights: red left front, green right front
    e.append(box([tips[0][0] - 0.3, 7.5, tips[0][1] - 1.0], [tips[0][0] + 0.3, 7.9, tips[0][1] - 0.9], RED))
    e.append(box([tips[1][0] - 0.3, 7.5, tips[1][1] - 1.0], [tips[1][0] + 0.3, 7.9, tips[1][1] - 0.9], GREEN))
    return e


def fpv_drone():
    e = []
    e.append(box([4.5, 7.2, 4], [11.5, 7.7, 12], CARBON))
    e.append(box([5.5, 9.2, 5], [10.5, 9.6, 11], CARBON))
    e.append(box([6, 7.7, 6], [10, 9.2, 10], GRAPHITE))
    e.append(box([6.4, 7.7, 6.4], [9.6, 8.1, 9.6], PURPLE))
    for angle in (45, -45):
        e.append(box([0.2, 7.2, 7.3], [15.8, 7.7, 8.7], CARBON, {"origin": [8, 8, 8], "axis": "y", "angle": angle}))
    tip = 7.8 / math.sqrt(2)
    tips = [(8 - tip, 8 - tip), (8 + tip, 8 - tip), (8 - tip, 8 + tip), (8 + tip, 8 + tip)]
    for (x, z) in tips:
        e.append(box([x - 1.0, 7.7, z - 1.0], [x + 1.0, 9.0, z + 1.0], SILVER))
        e.append(box([x - 0.7, 9.0, z - 0.7], [x + 0.7, 9.35, z + 0.7], ORANGE))
        e.append(box([x - 0.15, 9.35, z - 0.15], [x + 0.15, 9.7, z + 0.15], BLACK))
        e.append(prop(x, z, 9.5, 7.0))
    # battery with strap
    e.append(box([5.8, 9.6, 5.4], [10.2, 11.9, 10.6], YELLOW))
    e.append(box([5.6, 9.6, 7.6], [10.4, 12.1, 8.4], BLACK))
    e.append(box([7.4, 11.9, 9.8], [8.6, 12.3, 10.6], RED))
    # FPV camera tilted up
    cam_rot = {"origin": [8, 8.7, 4], "axis": "x", "angle": 22.5}
    e.append(box([6.9, 7.7, 3.4], [9.1, 9.7, 5.0], ORANGE, cam_rot))
    e.append(box([7.1, 7.9, 3.1], [8.9, 9.5, 3.4], BLACK, cam_rot))
    e.append(box([7.5, 8.3, 3.0], [8.5, 9.1, 3.1], LENS, cam_rot))
    # antenna in an orange TPU mount at the back
    e.append(box([7.2, 7.7, 11.2], [8.8, 9.9, 12.4], ORANGE))
    e.append(box([7.8, 9.9, 11.6], [8.2, 12.8, 12.0], BLACK))
    e.append(box([7.6, 12.8, 11.4], [8.4, 13.4, 12.2], BLACK))
    return e


def model(elements, prop_texture_name):
    return {
        "textures": {"p": "freefpv:item/drone_palette", "prop": "freefpv:item/" + prop_texture_name,
                     "particle": "freefpv:item/drone_palette"},
        "elements": elements,
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    tex = os.path.join(ROOT, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    palette_texture(os.path.join(tex, "drone_palette.png"))
    prop_texture(os.path.join(tex, "prop_gray.png"), (200, 205, 210))
    prop_texture(os.path.join(tex, "prop_dark.png"), (60, 62, 66))

    write(os.path.join(ROOT, "models", "item", "drone_camera.json"), model(camera_drone(), "prop_gray"))
    write(os.path.join(ROOT, "models", "item", "drone_fpv.json"), model(fpv_drone(), "prop_dark"))
    # 1.21.4+ item model definitions (assets/<ns>/items)
    for name in ("drone_camera", "drone_fpv"):
        write(os.path.join(ROOT, "items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": "freefpv:item/" + name}})


if __name__ == "__main__":
    main()
