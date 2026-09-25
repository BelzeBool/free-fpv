"""Generates the drone and remote controller item models, their textures and the GUI sprites.

Run from the repo root: python3 tools/gen_assets.py  (needs Pillow and numpy)

Vanilla style: one texture pixel per model unit, like vanilla block and item models; every material is a small
hand-picked palette (light, base, shade, dark) with sparse palette noise instead of gradients; parts are blocky and
snapped to the half-pixel grid. Each visible face gets its own patch in a per-model atlas so edges can carry a one
pixel rim and small pixel-art details (lenses, vents, screens). Propellers and the status LED are frame animations.
"""
import json
import math
import os
import zlib

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "freefpv")
TEX_ITEM = os.path.join(ROOT, "textures", "item")
TEX_GUI = os.path.join(ROOT, "textures", "gui", "sprites")


def hexes(*values):
    return [((v >> 16) & 255, (v >> 8) & 255, v & 255) for v in values]


# light, base, shade, dark
PALETTES = {
    "plastic_light": hexes(0xE6E6E6, 0xC9C9C9, 0xA4A4A4, 0x7C7C7C),
    "plastic_mid": hexes(0xA3A3A3, 0x8A8A8A, 0x6F6F6F, 0x565656),
    "plastic_dark": hexes(0x5E5E5E, 0x4C4C4C, 0x3C3C3C, 0x2C2C2C),
    "black": hexes(0x3C3C3C, 0x2C2C2C, 0x212121, 0x161616),
    "carbon": hexes(0x44444C, 0x33333A, 0x26262C, 0x1A1A1F),
    "orange": hexes(0xFFA544, 0xEB7F24, 0xC45F16, 0x8F410D),
    "silver": hexes(0xF0F0F0, 0xCFCFCF, 0xA2A2A2, 0x707070),
    "purple": hexes(0xBC94FF, 0x9562EE, 0x7040C8, 0x4E2A94),
    "yellow": hexes(0xFFE77A, 0xF4C534, 0xC99C1F, 0x916E14),
    "red": hexes(0xFF6B5E, 0xE0382B, 0xB0241B, 0x7C1812),
    "pcb": hexes(0x5BB066, 0x358A45, 0x256933, 0x174723),
    "glass": hexes(0x8FB4E8, 0x3E6AA8, 0x243F6B, 0x121F38),
    "screen": hexes(0x1E2126, 0x15171B, 0x0E1013, 0x08090B),
    "led_red": hexes(0xFFC4BE, 0xFF4A3D, 0xE0281C, 0xB0180F),
    "led_green": hexes(0xC8FFC8, 0x3CEB5A, 0x1FC240, 0x10902C),
    "led_cyan": hexes(0xC8FAFF, 0x3CD8F0, 0x1FB0CC, 0x10849C),
}

FACES = ("north", "south", "east", "west", "up", "down")


def face_size(face, size):
    dx, dy, dz = size
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


# --------------------------------------------------------------------------------------------- painting

def paint_face(mat, w, h, rng, rim=True):
    """Base colour with sparse light/shade pixels and a one-pixel rim: light top-left, shade bottom-right."""
    light, base, shade, dark = PALETTES[mat]
    img = np.zeros((h, w, 3), np.uint8)
    img[...] = base
    r = rng.random((h, w))
    img[r < 0.05] = light
    img[(r >= 0.05) & (r < 0.12)] = shade
    if mat == "carbon":
        yy, xx = np.mgrid[0:h, 0:w]
        img[((xx + yy) // 1) % 4 == 0] = shade
        img[((xx + yy) // 1) % 4 == 2] = light
    if rim and w >= 3 and h >= 3:
        img[0, :] = light
        img[:, 0] = light
        img[-1, :] = shade
        img[:, -1] = shade
        img[-1, 0] = base
        img[0, -1] = base
    return img


def put(img, x, y, color):
    h, w = img.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        img[y, x] = color


def decal(name, img, rng):
    """Pixel-art details drawn onto a face patch."""
    h, w = img.shape[:2]
    cx, cy = (w - 1) // 2, (h - 1) // 2
    gl, gb, gs, gd = PALETTES["glass"]
    if name == "lens":
        # dark ring, glass centre, one white glint
        for y in range(h):
            for x in range(w):
                img[y, x] = (22, 22, 24)
        x0, y0 = max(0, cx - (1 if w >= 4 else 0)), max(0, cy - (1 if h >= 4 else 0))
        x1, y1 = min(w - 1, x0 + (1 if w >= 2 else 0)), min(h - 1, y0 + (1 if h >= 2 else 0))
        img[y0:y1 + 1, x0:x1 + 1] = gb
        put(img, x1, y1, gs)
        put(img, x0, y0, (235, 240, 255))
    elif name == "sensors":
        for x in (1, w - 2):
            put(img, x, cy, gs)
            put(img, x, cy - 1 if h > 2 else cy, gb)
    elif name == "shell_top":
        # a seam across the front and two short vent grilles
        light, base, shade, dark = PALETTES["plastic_light"]
        for x in range(1, w - 1):
            put(img, x, 2, shade)
        for y in range(4, h - 2, 2):
            put(img, 1, y, dark)
            put(img, w - 2, y, dark)
    elif name == "rear":
        put(img, cx - 1, h - 2, (60, 225, 90))
        put(img, cx, h - 2, (60, 225, 90))
        put(img, cx + 1, h - 2, (60, 225, 90))
        put(img, cx + 2, h - 2, (80, 80, 80))
    elif name == "down":
        put(img, cx, 2, gd)
        put(img, cx + 1, 2, gd)
    elif name == "motor_top":
        light, base, shade, dark = PALETTES["silver"]
        img[...] = base
        img[0, :] = light
        img[:, 0] = light
        img[-1, :] = shade
        img[:, -1] = shade
        put(img, cx, cy, (40, 40, 44))
        if w >= 3:
            put(img, cx + 1, cy, (40, 40, 44))
    elif name == "motor_top_purple":
        light, base, shade, dark = PALETTES["purple"]
        img[...] = base
        img[0, :] = light
        img[:, 0] = light
        put(img, cx, cy, (40, 40, 44))
    elif name == "lipo":
        rl, rb, rs, rd = PALETTES["red"]
        for y in range(h):
            for x in range(w):
                if 0 < y < h - 1:
                    img[y, x] = rb if (y - 1) % 3 else rs
        for x in range(1, min(w - 1, 4)):
            put(img, x, cy, (245, 245, 245))
    elif name == "pcb_top":
        img[cy:cy + 2, cx:cx + 2] = (28, 28, 30)
        put(img, 1, 1, (230, 195, 90))
        put(img, w - 2, h - 2, (230, 195, 90))
        put(img, 1, h - 2, (80, 160, 255))
    elif name == "carbon_top":
        for y in range(1, h - 1):
            put(img, cx, y, (14, 14, 16))
    elif name == "rc_face":
        light, base, shade, dark = PALETTES["plastic_dark"]
        for sx in (2, w - 5):
            img[1:4, sx:sx + 3] = dark
            put(img, sx + 1, 2, shade)
        put(img, cx, h - 2, (70, 230, 110))
        put(img, cx + 1, 1, (200, 200, 200))
    elif name == "tx_face":
        light, base, shade, dark = PALETTES["black"]
        for sx in (1, w - 4):
            img[1:h - 1, sx:sx + 3] = dark
        # little LCD in the middle
        for y in range(1, min(h - 1, 4)):
            for x in range(cx - 1, cx + 2):
                put(img, x, y, (64, 104, 180) if (x + y) % 3 else (210, 230, 255))
    elif name == "phone_screen":
        for y in range(h):
            for x in range(w):
                if y == 0 or y == h - 1:
                    img[y, x] = (20, 20, 22)
                elif y < h // 2:
                    img[y, x] = (120, 168, 240) if y < h // 4 + 1 else (148, 190, 250)
                else:
                    img[y, x] = (96, 160, 64) if (x + y) % 3 else (84, 142, 54)
        put(img, cx, h // 2, (255, 255, 255))
        for x in range(cx - 1, cx + 2):
            put(img, x, 0, (70, 210, 110))
    elif name == "grip":
        light, base, shade, dark = PALETTES["plastic_dark"]
        for y in range(1, h - 1):
            for x in range(1, w - 1):
                if (x + y) % 2 == 0:
                    img[y, x] = shade
    return img


# --------------------------------------------------------------------------------------------- model builder

class Model:
    def __init__(self, name):
        self.name = name
        self.elements = []
        self.extra_textures = {}
        self.display = None

    def box(self, frm, to, mat, rot=None, decals=None, faces=FACES, emissive=False, rim=True):
        el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to]}
        if rot:
            el["rotation"] = rot
        self.elements.append({"el": el, "mat": mat, "decals": decals or {}, "faces": faces, "emissive": emissive, "rim": rim})
        return el

    def raw(self, frm, to, texture, faces=FACES, emissive=False):
        el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
              "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#" + texture} for f in faces}}
        if emissive:
            el["light_emission"] = 15
        self.elements.append({"raw": el})

    def prop(self, cx, cz, y, radius, texture):
        self.raw([cx - radius, y, cz - radius], [cx + radius, y, cz + radius], texture, faces=("up", "down"))

    def bake(self):
        rng = np.random.default_rng(zlib.crc32(self.name.encode()))
        patches = []
        for i, item in enumerate(self.elements):
            if "raw" in item:
                continue
            size = [item["el"]["to"][k] - item["el"]["from"][k] for k in range(3)]
            for face in item["faces"]:
                fw, fh = face_size(face, size)
                if fw <= 0.001 or fh <= 0.001:
                    continue
                w, h = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                if item["emissive"]:
                    img = np.zeros((h, w, 3), np.uint8)
                    img[...] = PALETTES[item["mat"]][1]
                    img[0, 0] = PALETTES[item["mat"]][0]
                else:
                    img = paint_face(item["mat"], w, h, rng, item["rim"])
                d = item["decals"].get(face)
                if d:
                    img = decal(d, img, rng)
                patches.append((i, face, img, fw, fh))

        atlas_size, placement = pack([(p[2].shape[1] + 2, p[2].shape[0] + 2) for p in patches])
        atlas = np.zeros((atlas_size, atlas_size, 4), np.uint8)
        uvs = {}
        s = 16 / atlas_size
        for (i, face, img, fw, fh), (x, y) in zip(patches, placement):
            h, w = img.shape[:2]
            padded = np.pad(img, ((1, 1), (1, 1), (0, 0)), mode="edge")
            atlas[y:y + h + 2, x:x + w + 2, :3] = padded
            atlas[y:y + h + 2, x:x + w + 2, 3] = 255
            # One pixel per model unit: a 1.5 unit face shows a pixel and a half, like vanilla models do.
            uvs[(i, face)] = [round((x + 1) * s, 4), round((y + 1) * s, 4), round((x + 1 + fw) * s, 4), round((y + 1 + fh) * s, 4)]
        Image.fromarray(atlas, "RGBA").save(os.path.join(TEX_ITEM, self.name + ".png"))

        elements = []
        for i, item in enumerate(self.elements):
            if "raw" in item:
                elements.append(item["raw"])
                continue
            el = dict(item["el"])
            el["faces"] = {f: {"uv": uvs[(i, f)], "texture": "#a"} for f in item["faces"] if (i, f) in uvs}
            if item["emissive"]:
                el["light_emission"] = 15
            elements.append(el)
        textures = {"a": "freefpv:item/" + self.name, "particle": "freefpv:item/" + self.name}
        textures.update(self.extra_textures)
        data = {"textures": textures, "elements": elements}
        if self.display:
            data["gui_light"] = "side"
            data["display"] = self.display
        write_json(os.path.join(ROOT, "models", "item", self.name + ".json"), data)
        write_json(os.path.join(ROOT, "items", self.name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "freefpv:item/" + self.name}})


def pack(sizes):
    """Shelf packer; returns the smallest power-of-two square atlas that fits and the placements."""
    order = sorted(range(len(sizes)), key=lambda k: -sizes[k][1])
    size = 16
    while True:
        placement = [None] * len(sizes)
        x = y = shelf = 0
        ok = True
        for k in order:
            w, h = sizes[k]
            if x + w > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > size or w > size:
                ok = False
                break
            placement[k] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            return size, placement
        size *= 2


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


# --------------------------------------------------------------------------------------------- animated textures

def prop_frames(path, blades, blade, disc, frames=4, size=16):
    """Spinning propeller as hard-pixel frames: a faint disc and the blades one step further round each frame."""
    sheet = np.zeros((size * frames, size, 4), np.uint8)
    c = (size - 1) / 2
    for f in range(frames):
        frame = np.zeros((size, size, 4), np.uint8)
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - c, y - c)
                if d <= c + 0.3:
                    frame[y, x] = disc + (46 if d > c - 1.2 else 30,)
        base = f * (360 / blades) / frames
        for b in range(blades):
            a = math.radians(base + b * 360 / blades)
            for k in range(1, int(c) + 1):
                x, y = round(c + math.cos(a) * k), round(c + math.sin(a) * k)
                frame[y, x] = blade + (150,)
        hub = int(round(c))
        frame[hub - 1:hub + 1, hub - 1:hub + 1] = (40, 40, 44, 255)
        sheet[f * size:(f + 1) * size] = frame
    Image.fromarray(sheet, "RGBA").save(path)
    write_json(path + ".mcmeta", {"animation": {"frametime": 1}})


def blink_frames(path, on, pattern, size=4):
    """Status LED, one frame per tick; pattern is a string of 1/0."""
    sheet = Image.new("RGBA", (size, size * len(pattern)), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    for i, bit in enumerate(pattern):
        col = on if bit == "1" else tuple(int(v * 0.2) for v in on)
        d.rectangle([0, i * size, size - 1, i * size + size - 1], fill=col + (255,))
    sheet.save(path)
    write_json(path + ".mcmeta", {"animation": {"frametime": 1}})


# --------------------------------------------------------------------------------------------- models

def tips(reach):
    d = reach / math.sqrt(2)
    return [(8 - d, 8 - d), (8 + d, 8 - d), (8 - d, 8 + d), (8 + d, 8 + d)]


def camera_drone():
    m = Model("drone_camera")
    m.box([5, 7, 4], [11, 9, 12], "plastic_light", decals={"up": "shell_top", "down": "down"})
    m.box([6, 7, 3], [10, 9, 4], "plastic_light", decals={"north": "sensors"})
    m.box([6, 9, 6], [10, 10, 11], "plastic_mid")
    m.box([6, 7, 12], [10, 9, 13], "plastic_mid", decals={"south": "rear"})
    m.box([7, 6, 3], [9, 7, 4], "plastic_dark")
    m.box([7, 5, 2], [9, 7, 3], "black", decals={"north": "lens"})
    for angle in (45, -45):
        m.box([1, 8, 7.5], [15, 9, 8.5], "plastic_light", rot={"origin": [8, 8, 8], "axis": "y", "angle": angle})
    for (x, z) in tips(7.4):
        m.box([x - 1, 8, z - 1], [x + 1, 10, z + 1], "silver", decals={"up": "motor_top"})
        m.box([x - 0.5, 6, z - 0.5], [x + 0.5, 8, z + 0.5], "plastic_light", rim=False)
        m.prop(x, z, 10.1, 3.5, "prop")
    fl, fr, rl, rr = tips(7.4)
    m.box([fl[0] - 0.5, 7, fl[1] - 1.5], [fl[0] + 0.5, 8, fl[1] - 1], "led_red", emissive=True)
    m.box([fr[0] - 0.5, 7, fr[1] - 1.5], [fr[0] + 0.5, 8, fr[1] - 1], "led_green", emissive=True)
    m.raw([rl[0] - 0.5, 7, rl[1] + 1], [rl[0] + 0.5, 8, rl[1] + 1.5], "led", emissive=True)
    m.raw([rr[0] - 0.5, 7, rr[1] + 1], [rr[0] + 0.5, 8, rr[1] + 1.5], "led", emissive=True)
    m.extra_textures = {"prop": "freefpv:item/prop_camera", "led": "freefpv:item/led_status"}
    m.display = drone_display(0.62)
    return m


def fpv_drone():
    m = Model("drone_fpv")
    m.box([5, 7, 4], [11, 8, 12], "carbon")
    for angle in (45, -45):
        m.box([0, 7, 7], [16, 8, 9], "carbon", rot={"origin": [8, 7.5, 8], "axis": "y", "angle": angle})
    for (x, z) in ((6, 5), (10, 5), (6, 11), (10, 11)):
        m.box([x - 0.5, 8, z - 0.5], [x + 0.5, 10, z + 0.5], "purple", faces=("north", "south", "east", "west"), rim=False)
    m.box([6, 8, 6], [10, 9, 10], "pcb", decals={"up": "pcb_top"})
    m.box([5, 10, 5], [11, 11, 11], "carbon", decals={"up": "carbon_top"})
    m.box([5, 8, 3], [6, 10, 6], "orange")
    m.box([10, 8, 3], [11, 10, 6], "orange")
    m.box([6, 8, 3], [10, 10, 5], "black", rot={"origin": [8, 9, 4], "axis": "x", "angle": 22.5}, decals={"north": "lens"})
    m.box([6, 11, 5], [10, 13, 11], "black", decals={"east": "lipo", "west": "lipo", "up": "lipo"})
    m.box([5.5, 10.5, 7], [10.5, 13.5, 8], "plastic_dark", rim=False)
    m.box([7, 11, 11], [9, 12, 12], "yellow")
    m.box([7, 8, 11], [9, 10, 12], "orange")
    ant = {"origin": [8, 10, 11.5], "axis": "x", "angle": -22.5}
    m.box([7.5, 10, 11], [8.5, 13, 12], "black", rot=ant, rim=False)
    m.box([7, 13, 10.5], [9, 14, 12.5], "black", rot=ant)
    for (x, z) in tips(7.8):
        m.box([x - 1, 8, z - 1], [x + 1, 10, z + 1], "silver", decals={"up": "motor_top_purple"})
        m.prop(x, z, 10.1, 4, "prop")
        m.box([x - 0.5, 6.5, z - 0.5], [x + 0.5, 7, z + 0.5], "led_cyan", emissive=True)
    m.extra_textures = {"prop": "freefpv:item/prop_fpv"}
    m.display = drone_display(0.6)
    return m


def drone_display(gui_scale):
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0.5, 0], "scale": [gui_scale] * 3},
        "fixed": {"rotation": [0, 0, 0], "scale": [0.75] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [0.4] * 3},
        "head": {"translation": [0, 8, 0], "scale": [0.8] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4] * 3},
    }


def remote_display(gui_scale):
    # Face (sticks) points +Y, the top edge -Z. Held in both hands in front of the chest.
    return {
        "gui": {"rotation": [58, 0, 0], "translation": [0, 1.5, 0], "scale": [gui_scale] * 3},
        "fixed": {"rotation": [90, 0, 0], "scale": [0.75] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [0.45] * 3},
        "thirdperson_righthand": {"rotation": [-15, 0, 0], "translation": [-3.5, 2.5, -1.5], "scale": [0.5] * 3},
        "thirdperson_lefthand": {"rotation": [-15, 0, 0], "translation": [3.5, 2.5, -1.5], "scale": [0.5] * 3},
        "firstperson_righthand": {"rotation": [48, 0, 0], "translation": [-8.5, 4.8, 0], "scale": [0.55] * 3},
        "firstperson_lefthand": {"rotation": [48, 0, 0], "translation": [8.5, 4.8, 0], "scale": [0.55] * 3},
    }


def remote_camera():
    """DJI RC-N style controller with a phone in the top clamp."""
    m = Model("remote_camera")
    m.box([3, 6, 7], [13, 8, 11], "plastic_dark", decals={"up": "rc_face"})
    m.box([2, 6, 8], [5, 8, 13], "plastic_dark", decals={"up": "grip"})
    m.box([11, 6, 8], [14, 8, 13], "plastic_dark", decals={"up": "grip"})
    for x in (6, 10):
        m.box([x - 0.5, 8, 8.5], [x + 0.5, 9, 9.5], "silver", rim=False)
        m.box([x - 1, 9, 8], [x + 1, 10, 10], "black")
    m.box([3, 8, 6], [13, 9, 7], "plastic_dark")
    m.box([3, 9, 6], [13, 14, 7], "black", rot={"origin": [8, 9, 6.5], "axis": "x", "angle": -22.5}, decals={"south": "phone_screen"})
    m.display = remote_display(0.95)
    return m


def remote_fpv():
    """Radiomaster Boxer style radio: two gimbals, a small LCD, switches and a folding antenna."""
    m = Model("remote_fpv")
    m.box([3, 6, 6], [13, 8, 11], "black", decals={"up": "tx_face"})
    m.box([2, 5, 9], [5, 8, 13], "black", decals={"up": "grip"})
    m.box([11, 5, 9], [14, 8, 13], "black", decals={"up": "grip"})
    for x in (5, 11):
        m.box([x - 0.5, 8, 8], [x + 0.5, 10, 9], "silver", rim=False)
        m.box([x - 1, 10, 7.5], [x + 1, 11, 9.5], "black")
    for x in (4, 12):
        m.box([x - 0.5, 8, 5.5], [x + 0.5, 10, 6.5], "silver", rot={"origin": [x, 8, 6], "axis": "x", "angle": -22.5}, rim=False)
    m.box([7, 7, 5], [9, 8, 6], "plastic_dark")
    m.box([7.5, 8, 5], [8.5, 13, 6], "black", rot={"origin": [8, 8, 5.5], "axis": "x", "angle": -22.5}, rim=False)
    m.display = remote_display(0.95)
    return m


# --------------------------------------------------------------------------------------------- GUI sprites

def gui_sprites():
    """Hotbar-style slot, a selection frame like the hotbar's, and an inventory-style slot for the tool wheel."""
    os.makedirs(os.path.join(TEX_GUI, "tool"), exist_ok=True)
    for old in ("wheel", "bubble", "bubble_hover", "bubble_locked", "center"):
        p = os.path.join(TEX_GUI, "tool", old + ".png")
        if os.path.exists(p):
            os.remove(p)

    slot = Image.new("RGBA", (22, 22), (0, 0, 0, 0))
    d = ImageDraw.Draw(slot)
    d.rectangle([0, 0, 21, 21], fill=(0, 0, 0, 170))
    d.rectangle([1, 1, 20, 20], fill=(60, 60, 60, 150))
    d.line([(1, 1), (20, 1)], fill=(110, 110, 110, 220))
    d.line([(1, 1), (1, 20)], fill=(110, 110, 110, 220))
    d.line([(2, 20), (20, 20)], fill=(32, 32, 32, 220))
    d.line([(20, 2), (20, 20)], fill=(32, 32, 32, 220))
    slot.save(os.path.join(TEX_GUI, "tool", "slot.png"))

    sel = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(sel)
    d.rectangle([0, 0, 23, 23], outline=(0, 0, 0, 255))
    d.rectangle([1, 1, 22, 22], outline=(255, 255, 255, 255))
    d.rectangle([2, 2, 21, 21], outline=(170, 170, 170, 255))
    sel.save(os.path.join(TEX_GUI, "tool", "slot_selected.png"))

    # Inventory slot: grey with a dark top-left and a light bottom-right edge.
    inv = Image.new("RGBA", (26, 26), (0, 0, 0, 0))
    d = ImageDraw.Draw(inv)
    d.rectangle([0, 0, 25, 25], fill=(198, 198, 198, 255))
    d.rectangle([1, 1, 24, 24], fill=(139, 139, 139, 255))
    d.line([(1, 1), (24, 1)], fill=(55, 55, 55, 255))
    d.line([(1, 1), (1, 24)], fill=(55, 55, 55, 255))
    d.line([(2, 24), (24, 24)], fill=(255, 255, 255, 255))
    d.line([(24, 2), (24, 24)], fill=(255, 255, 255, 255))
    inv.save(os.path.join(TEX_GUI, "tool", "wheel_slot.png"))


def main():
    os.makedirs(TEX_ITEM, exist_ok=True)
    prop_frames(os.path.join(TEX_ITEM, "prop_camera.png"), 2, (225, 225, 225), (190, 190, 190))
    prop_frames(os.path.join(TEX_ITEM, "prop_fpv.png"), 3, (60, 60, 66), (40, 40, 46))
    blink_frames(os.path.join(TEX_ITEM, "led_status.png"), (60, 235, 90), "1100000011000000000000")
    for model in (camera_drone(), fpv_drone(), remote_camera(), remote_fpv()):
        model.bake()
    gui_sprites()


if __name__ == "__main__":
    main()
