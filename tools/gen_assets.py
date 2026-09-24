"""Generates the drone and remote controller item models, their textures and the GUI sprites.

Run from the repo root: python3 tools/gen_assets.py  (needs Pillow and numpy)

Every visible face of a model gets its own patch in a per-model texture atlas, painted at a fixed texel density:
a material pattern, a soft bevel and ambient occlusion, plus decals for the parts that need detail (lenses, motor
tops, vents, labels, screens). Propellers use animated motion-blur textures, status LEDs blink.
"""
import json
import math
import os
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "freefpv")
TEX_ITEM = os.path.join(ROOT, "textures", "item")
TEX_GUI = os.path.join(ROOT, "textures", "gui", "sprites")

# Minecraft item models accept element rotations in 22.5 degree steps only.
COS22 = math.cos(math.radians(22.5))


# --------------------------------------------------------------------------------------------- materials

def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], np.float32)


MATERIALS = {
    # name: (base colour, noise amplitude, pattern)
    "plastic_light": (rgb(0xC3C8CE), 3.0, None),
    "plastic_mid": (rgb(0x8E949C), 3.0, None),
    "plastic_dark": (rgb(0x4A4F57), 3.0, None),
    "plastic_rc": (rgb(0x3A3E44), 3.0, "grain"),
    "black": (rgb(0x1E2023), 2.5, None),
    "rubber": (rgb(0x19191B), 3.0, "knurl"),
    "carbon": (rgb(0x23252A), 0.0, "carbon"),
    "orange": (rgb(0xFF7A1A), 3.0, "layers"),
    "silver": (rgb(0xB4BAC2), 2.0, "brushed"),
    "motor_bell": (rgb(0xA9AFB8), 2.0, "slots"),
    "graphite": (rgb(0x33373D), 2.0, "brushed"),
    "purple": (rgb(0x7B4DFF), 2.0, "brushed"),
    "red_anod": (rgb(0xD8323C), 2.0, "brushed"),
    "yellow": (rgb(0xF2C12E), 3.0, None),
    "lipo": (rgb(0x202226), 2.0, None),
    "foam": (rgb(0x5A5E64), 6.0, None),
    "pcb": (rgb(0x1F6B3E), 3.0, "pcb"),
    "pcb_black": (rgb(0x16181B), 2.0, "pcb"),
    "glass": (rgb(0x0D1B2A), 1.0, None),
    "led_red": (rgb(0xFF3030), 0.0, None),
    "led_green": (rgb(0x2DFF6A), 0.0, None),
    "led_cyan": (rgb(0x33E6FF), 0.0, None),
    "wire_red": (rgb(0xC62828), 2.0, None),
    "wire_black": (rgb(0x141414), 2.0, None),
    "screen": (rgb(0x0A0C10), 0.0, None),
}

FACES = ("north", "south", "east", "west", "up", "down")


def face_size(face, size):
    dx, dy, dz = size
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def paint_material(name, w, h, face, rng):
    base, noise, pattern = MATERIALS[name]
    img = np.tile(base, (h, w, 1)).astype(np.float32)
    if noise:
        img += rng.normal(0, noise, (h, w, 1))
    yy, xx = np.mgrid[0:h, 0:w]
    side = face not in ("up", "down")
    if pattern == "carbon":
        # 2x2 twill: diagonal steps of light and dark tows, with a soft sheen
        cell = ((xx // 2) + (yy // 2)) % 4
        tone = np.where(cell < 2, 1.0, 0.72)[..., None]
        img = img * tone + np.array([10, 11, 13], np.float32) * (cell == 0)[..., None]
        sheen = 1 + 0.10 * np.cos((xx - yy) / max(w, h) * math.pi)[..., None]
        img *= sheen
    elif pattern == "layers" and side:
        img *= np.where(yy % 2 == 0, 1.0, 0.9)[..., None]
    elif pattern == "brushed":
        streak = rng.normal(0, 4, (h, 1, 1))
        img += np.tile(streak, (1, w, 1))
    elif pattern == "slots" and side:
        img *= np.where((xx % 3 == 1) & (yy > h * 0.25) & (yy < h * 0.8), 0.45, 1.0)[..., None]
    elif pattern == "knurl":
        img *= np.where((xx + yy) % 2 == 0, 1.12, 0.92)[..., None]
    elif pattern == "grain":
        img += rng.normal(0, 2, (h, w, 1))
    elif pattern == "pcb":
        dots = rng.random((h, w)) > 0.93
        img[dots] = [210, 170, 60]
    return img


def bevel(img, face):
    """Light top-left edge, dark bottom-right edge and a little ambient occlusion toward the bottom of side faces."""
    h, w = img.shape[:2]
    if w >= 3 and h >= 3:
        img[0, :] *= 1.14
        img[:, 0] *= 1.08
        img[-1, :] *= 0.80
        img[:, -1] *= 0.88
    if face not in ("up", "down") and h >= 3:
        ao = np.linspace(1.0, 0.86, h)[:, None, None]
        img *= ao
    if face == "down":
        img *= 0.92
    return img


# --------------------------------------------------------------------------------------------- decals

def circle_mask(w, h, cx, cy, r):
    yy, xx = np.mgrid[0:h, 0:w]
    return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r


def decal(name, img, face, rng):
    h, w = img.shape[:2]
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2
    if name == "lens":
        img[circle_mask(w, h, cx, cy, r * 0.98)] = [18, 19, 22]
        img[circle_mask(w, h, cx, cy, r * 0.78)] = [40, 44, 52]
        img[circle_mask(w, h, cx, cy, r * 0.62)] = [14, 22, 48]
        img[circle_mask(w, h, cx, cy, r * 0.40)] = [34, 26, 78]
        img[circle_mask(w, h, cx, cy, r * 0.18)] = [8, 8, 16]
        img[circle_mask(w, h, cx - r * 0.25, cy - r * 0.28, max(0.7, r * 0.14))] = [205, 225, 255]
    elif name == "motor_top":
        img[circle_mask(w, h, cx, cy, r * 0.95)] *= 0.92
        img[circle_mask(w, h, cx, cy, r * 0.30)] = [30, 30, 34]
        img[circle_mask(w, h, cx, cy, r * 0.14)] = [200, 205, 210]
        for k in range(4):
            a = k * math.pi / 2 + math.pi / 4
            img[circle_mask(w, h, cx + math.cos(a) * r * 0.58, cy + math.sin(a) * r * 0.58, max(0.6, r * 0.10))] = [40, 40, 44]
    elif name == "shell_top":
        # DJI-style top shell: two vent grilles, a panel seam and a darker GPS window
        yy, xx = np.mgrid[0:h, 0:w]
        seam = (yy == int(h * 0.30)) | (yy == int(h * 0.31))
        img[seam] *= 0.78
        for side in (0.18, 0.82):
            vent = (np.abs(xx - w * side) < w * 0.10) & (yy > h * 0.42) & (yy < h * 0.80) & (yy % 2 == 0)
            img[vent] *= 0.45
        gps = (np.abs(xx - cx) < w * 0.16) & (yy > h * 0.10) & (yy < h * 0.24)
        img[gps] = img[gps] * 0.6 + np.array([40, 44, 50]) * 0.4
    elif name == "sensors":
        for sx in (0.28, 0.72):
            m = circle_mask(w, h, w * sx, h * 0.45, min(w, h) * 0.22)
            img[m] = [10, 16, 26]
            img[circle_mask(w, h, w * sx - 0.4, h * 0.40, max(0.5, min(w, h) * 0.07))] = [120, 150, 190]
    elif name == "down_sensors":
        for sx in (0.35, 0.65):
            img[circle_mask(w, h, w * sx, h * 0.35, min(w, h) * 0.10)] = [10, 16, 26]
        img[circle_mask(w, h, cx, h * 0.62, min(w, h) * 0.08)] = [60, 10, 10]
    elif name == "rear":
        yy, xx = np.mgrid[0:h, 0:w]
        for k in range(4):
            img[(np.abs(xx - w * (0.35 + k * 0.1)) < 0.6) & (np.abs(yy - h * 0.72) < 0.6)] = [40, 220, 90] if k < 3 else [60, 60, 60]
        latch = (np.abs(xx - cx) < w * 0.25) & (yy < h * 0.3)
        img[latch] *= 0.7
    elif name == "battery_top":
        yy, xx = np.mgrid[0:h, 0:w]
        img[(yy % 3 == 0) & (yy > h * 0.55)] *= 0.75
    elif name == "lipo_label":
        yy, xx = np.mgrid[0:h, 0:w]
        band = (yy > h * 0.25) & (yy < h * 0.75)
        img[band] = [226, 60, 40]
        img[band & (yy > h * 0.42) & (yy < h * 0.58) & (xx % 4 < 2) & (xx > w * 0.15) & (xx < w * 0.55)] = [245, 245, 245]
        img[(yy > h * 0.3) & (yy < h * 0.7) & (xx > w * 0.7) & (xx < w * 0.9)] = [250, 210, 60]
    elif name == "carbon_top":
        yy, xx = np.mgrid[0:h, 0:w]
        slot = (np.abs(xx - cx) < w * 0.08) & (yy > h * 0.2) & (yy < h * 0.8)
        img[slot] = [8, 8, 9]
    elif name == "pcb_top":
        img[circle_mask(w, h, cx, cy, min(w, h) * 0.18)] = [30, 30, 34]
        img[circle_mask(w, h, w * 0.2, h * 0.2, 0.7)] = [60, 140, 255]
    elif name == "stick_well":
        img[circle_mask(w, h, cx, cy, r * 0.95)] *= 0.55
        img[circle_mask(w, h, cx, cy, r * 0.55)] *= 0.8
    elif name == "rc_face":
        # DJI RC-N style face: two stick wells, a row of buttons, power LEDs
        for sx in (0.22, 0.78):
            m = circle_mask(w, h, w * sx, h * 0.55, min(w, h) * 0.33)
            img[m] *= 0.55
        for k in range(4):
            img[circle_mask(w, h, w * (0.43 + k * 0.047), h * 0.80, 0.55)] = [80, 230, 120]
        img[circle_mask(w, h, w * 0.5, h * 0.40, min(w, h) * 0.11)] = [58, 62, 68]
        img[circle_mask(w, h, w * 0.5, h * 0.40, min(w, h) * 0.05)] = [230, 230, 230]
    elif name == "tx_face":
        yy, xx = np.mgrid[0:h, 0:w]
        for sx in (0.24, 0.76):
            m = (np.abs(xx - w * sx) < w * 0.15) & (np.abs(yy - h * 0.52) < h * 0.34)
            img[m] = img[m] * 0.6 + np.array([80, 84, 90]) * 0.4
        img[(np.abs(xx - cx) < w * 0.06) & (np.abs(yy - h * 0.82) < 1)] = [200, 60, 60]
    elif name == "phone_screen":
        paint_phone_screen(img)
    elif name == "lcd":
        yy, xx = np.mgrid[0:h, 0:w]
        img[...] = [40, 70, 140]
        img[(yy % 3 == 1) & (xx > 1) & (xx < w - 2) & (rng.random((h, w)) > 0.35)] = [220, 235, 255]
    elif name == "grip":
        yy, xx = np.mgrid[0:h, 0:w]
        img[(xx + yy) % 3 == 0] *= 0.85
    return img


def paint_phone_screen(img):
    """A tiny DJI Fly view: sky, horizon, a field, and the top/bottom HUD strips."""
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    sky = np.stack([95 + yy * 60 / h, 150 + yy * 50 / h, 225 - yy * 10 / h], -1)
    ground = np.stack([70 + 0 * yy, 120 + (yy % 2) * 8, 60 + 0 * yy], -1)
    horizon = h * 0.52
    img[...] = np.where((yy < horizon)[..., None], sky, ground)
    img[(yy > horizon) & (yy < horizon + 1.2)] = [150, 160, 170]
    img[yy < max(1, h * 0.12)] = [20, 22, 26]
    img[yy > h - max(1, h * 0.14)] = [20, 22, 26]
    img[(yy < max(1, h * 0.12)) & (xx > w * 0.42) & (xx < w * 0.58)] = [60, 200, 110]
    img[(np.abs(xx - w / 2) < 1) & (np.abs(yy - h * 0.55) < 1.5)] = [255, 255, 255]


# --------------------------------------------------------------------------------------------- model builder

class Model:
    def __init__(self, name, density):
        self.name = name
        self.density = density
        self.elements = []
        self.extra_textures = {}
        self.display = None

    def box(self, frm, to, mat, rot=None, decals=None, faces=FACES, texture=None, emissive=False, uv_full=False):
        el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to]}
        if rot:
            el["rotation"] = rot
        self.elements.append({"el": el, "mat": mat, "decals": decals or {}, "faces": faces,
                              "texture": texture, "emissive": emissive, "uv_full": uv_full})
        return el

    def cyl(self, cx, cz, y0, y1, r, mat, top=None, faces=FACES):
        """Octagonal prism with apothem r: the union of two crossed slabs and the same pair turned 45 degrees.
        A decal for the top goes on a thin square cap inscribed in the octagon."""
        b = r * math.tan(math.radians(22.5))
        sides = tuple(f for f in faces if f in ("north", "south", "east", "west"))
        caps = tuple(f for f in faces if f in ("up", "down"))
        for k, rot in enumerate((None, {"origin": [cx, (y0 + y1) / 2, cz], "axis": "y", "angle": 45})):
            lift = 0.004 * k
            self.box([cx - r, y0 + lift, cz - b], [cx + r, y1 - lift, cz + b], mat, rot=rot, faces=sides + caps)
            self.box([cx - b, y0 + lift + 0.002, cz - r], [cx + b, y1 - lift - 0.002, cz + r], mat, rot=rot, faces=sides + caps)
        if top:
            s = r * 0.7
            self.box([cx - s, y1, cz - s], [cx + s, y1 + 0.02, cz + s], mat, decals={"up": top}, faces=("up",))

    def prop(self, cx, cz, y, radius, texture):
        faces = {"up": {"uv": [0, 0, 16, 16], "texture": "#" + texture},
                 "down": {"uv": [0, 0, 16, 16], "texture": "#" + texture}}
        el = {"from": [round(cx - radius, 3), round(y, 3), round(cz - radius, 3)],
              "to": [round(cx + radius, 3), round(y + 0.05, 3), round(cz + radius, 3)], "faces": faces}
        self.elements.append({"raw": el})

    def led(self, frm, to, texture_or_mat, blink=False):
        if blink:
            faces = {f: {"uv": [0, 0, 16, 16], "texture": "#" + texture_or_mat} for f in FACES}
            el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces,
                  "shade": False, "light_emission": 15}
            self.elements.append({"raw": el})
        else:
            self.box(frm, to, texture_or_mat, emissive=True)

    # ------------------------------------------------------------------ baking

    def bake(self):
        rng = np.random.default_rng(zlib.crc32(self.name.encode()))
        patches = []
        for i, item in enumerate(self.elements):
            if "raw" in item:
                continue
            el = item["el"]
            size = [el["to"][k] - el["from"][k] for k in range(3)]
            for face in item["faces"]:
                fw, fh = face_size(face, size)
                if fw <= 0.001 or fh <= 0.001:
                    continue
                w = max(2, int(round(fw * self.density)))
                h = max(2, int(round(fh * self.density)))
                img = paint_material(item["mat"], w, h, face, rng)
                if not item["emissive"]:
                    img = bevel(img, face)
                else:
                    img = glow(img)
                d = item["decals"].get(face)
                if d:
                    img = decal(d, img, face, rng)
                patches.append((i, face, np.clip(img, 0, 255).astype(np.uint8)))

        atlas_size, placement = pack([(p[2].shape[1] + 2, p[2].shape[0] + 2) for p in patches])
        atlas = np.zeros((atlas_size, atlas_size, 4), np.uint8)
        uvs = {}
        for (i, face, img), (x, y) in zip(patches, placement):
            h, w = img.shape[:2]
            padded = np.pad(img, ((1, 1), (1, 1), (0, 0)), mode="edge")
            atlas[y:y + h + 2, x:x + w + 2, :3] = padded
            atlas[y:y + h + 2, x:x + w + 2, 3] = 255
            s = 16 / atlas_size
            uvs[(i, face)] = [round((x + 1) * s, 4), round((y + 1) * s, 4), round((x + 1 + w) * s, 4), round((y + 1 + h) * s, 4)]
        Image.fromarray(atlas, "RGBA").save(os.path.join(TEX_ITEM, self.name + ".png"))

        elements = []
        for i, item in enumerate(self.elements):
            if "raw" in item:
                elements.append(item["raw"])
                continue
            el = dict(item["el"])
            el["faces"] = {f: {"uv": uvs[(i, f)], "texture": "#a"} for f in item["faces"] if (i, f) in uvs}
            if item["emissive"]:
                el["shade"] = False
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


def glow(img):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx + 0.5) / w - 0.5) ** 2 + ((yy + 0.5) / h - 0.5) ** 2)
    return img * (1.15 - 0.5 * d)[..., None] + 30


def pack(sizes):
    """Shelf packer; returns the smallest power-of-two square atlas that fits and the placements."""
    order = sorted(range(len(sizes)), key=lambda k: -sizes[k][1])
    size = 32
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

def prop_frames(path, blades, blade_rgb, disc_rgb, frames=8, size=32):
    """Spinning propeller: a faint disc plus motion-blurred blades, one step of rotation per frame."""
    sheet = Image.new("RGBA", (size, size * frames), (0, 0, 0, 0))
    c = size * 2  # draw at 4x, downsample
    for f in range(frames):
        big = Image.new("RGBA", (c * 2, c * 2), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        # disc: denser toward the tips where the blades sweep faster
        for r in range(c, 0, -2):
            t = r / c
            alpha = int(26 + 34 * t) if t < 0.97 else 22
            d.ellipse([c - r, c - r, c + r, c + r], fill=disc_rgb + (alpha,))
        base = f * (360 / blades) / frames
        for b in range(blades):
            for smear in range(10):
                ang = math.radians(base + b * 360 / blades - smear * 3.2)
                alpha = int(70 * (1 - smear / 10))
                for k in range(3):
                    rr = c * (0.25 + 0.7 * (k + 1) / 3)
                    w = int(c * (0.10 - 0.025 * k))
                    d.line([c, c, c + math.cos(ang) * rr, c + math.sin(ang) * rr], fill=blade_rgb + (alpha // (k + 1),), width=max(2, w))
        d.ellipse([c - c * 0.14, c - c * 0.14, c + c * 0.14, c + c * 0.14], fill=(28, 28, 30, 235))
        d.ellipse([c - c * 0.06, c - c * 0.06, c + c * 0.06, c + c * 0.06], fill=(170, 170, 176, 255))
        frame = big.filter(ImageFilter.GaussianBlur(3)).resize((size, size), Image.LANCZOS)
        sheet.paste(frame, (0, f * size))
    sheet.save(path)
    write_json(path + ".mcmeta", {"animation": {"frametime": 1, "interpolate": True}})


def blink_frames(path, on_rgb, pattern, size=4):
    """Status LED: one frame per tick, pattern is a string of 1/0."""
    sheet = Image.new("RGBA", (size, size * len(pattern)), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    for i, bit in enumerate(pattern):
        col = on_rgb if bit == "1" else tuple(int(v * 0.18) for v in on_rgb)
        d.rectangle([0, i * size, size - 1, i * size + size - 1], fill=col + (255,))
        if bit == "1":
            d.point((1, i * size + 1), fill=(255, 255, 255, 255))
    sheet.save(path)
    write_json(path + ".mcmeta", {"animation": {"frametime": 1}})


# --------------------------------------------------------------------------------------------- models

def camera_drone():
    m = Model("drone_camera", 3)
    # fuselage
    m.box([5.2, 7.2, 4.2], [10.8, 9.6, 12.0], "plastic_light", decals={"up": "shell_top", "down": "down_sensors"})
    m.box([5.8, 7.4, 3.2], [10.2, 9.3, 4.2], "plastic_light", decals={"north": "sensors"})
    m.box([5.9, 9.6, 6.6], [10.1, 10.1, 11.4], "plastic_mid", decals={"up": "battery_top"})
    m.box([5.6, 7.3, 12.0], [10.4, 9.4, 12.8], "plastic_mid", decals={"south": "rear"})
    # three-axis gimbal hanging under the nose
    m.box([7.1, 6.8, 3.2], [8.9, 7.4, 4.4], "plastic_dark")
    m.box([6.7, 5.9, 2.9], [7.1, 7.1, 3.9], "plastic_dark")
    m.box([8.9, 5.9, 2.9], [9.3, 7.1, 3.9], "plastic_dark")
    m.box([7.1, 5.8, 2.5], [8.9, 7.2, 3.9], "black", decals={"north": "lens"})
    # folding arms: front pair high, rear pair low, like a DJI Mini
    arm = 3.9
    for (ix, iz, ang, y0, y1, left) in ((5.5, 5.3, -45, 8.7, 9.4, True), (10.5, 5.3, 45, 8.7, 9.4, False),
                                        (5.5, 10.9, 45, 7.7, 8.4, True), (10.5, 10.9, -45, 7.7, 8.4, False)):
        x0, x1 = (ix - arm, ix) if left else (ix, ix + arm)
        m.box([x0, y0, iz - 0.5], [x1, y1, iz + 0.5], "plastic_light", rot={"origin": [ix, y0, iz], "axis": "y", "angle": ang})
    tips = []
    for (ix, iz, ang, left, front) in ((5.5, 5.3, -45, True, True), (10.5, 5.3, 45, False, True),
                                       (5.5, 10.9, 45, True, False), (10.5, 10.9, -45, False, False)):
        d = (arm - 0.4) / math.sqrt(2)
        tips.append((ix - d if left else ix + d, iz - d if front else iz + d, front))
    for (x, z, front) in tips:
        y0 = 8.9 if front else 7.9
        m.cyl(x, z, y0, y0 + 1.2, 0.95, "motor_bell", top="motor_top")
        m.box([x - 0.3, y0 + 1.2, z - 0.3], [x + 0.3, y0 + 1.45, z + 0.3], "graphite")
        # landing feet
        m.box([x - 0.3, 5.7, z - 0.3], [x + 0.3, y0, z + 0.3], "plastic_light")
        m.prop(x, z, y0 + 1.5, 3.4, "prop")
    fl, fr, rl, rr = tips
    m.led([fl[0] - 0.3, 8.3, fl[1] - 0.95], [fl[0] + 0.3, 8.7, fl[1] - 0.9], "led_red")
    m.led([fr[0] - 0.3, 8.3, fr[1] - 0.95], [fr[0] + 0.3, 8.7, fr[1] - 0.9], "led_green")
    m.led([rl[0] - 0.3, 7.4, rl[1] + 0.9], [rl[0] + 0.3, 7.8, rl[1] + 0.95], "led", blink=True)
    m.led([rr[0] - 0.3, 7.4, rr[1] + 0.9], [rr[0] + 0.3, 7.8, rr[1] + 0.95], "led", blink=True)
    m.extra_textures = {"prop": "freefpv:item/prop_camera", "led": "freefpv:item/led_status"}
    m.display = drone_display(0.62)
    return m


def fpv_drone():
    m = Model("drone_fpv", 3)
    # carbon X frame: bottom plate, two crossing arms, top plate on four standoffs
    m.box([5.2, 7.2, 4.0], [10.8, 7.7, 12.0], "carbon")
    for angle in (45, -45):
        m.box([0.4, 7.2, 7.25], [15.6, 7.7, 8.75], "carbon", rot={"origin": [8, 7.45, 8], "axis": "y", "angle": angle})
    for (x, z) in ((6.0, 5.2), (10.0, 5.2), (6.0, 10.8), (10.0, 10.8)):
        m.cyl(x, z, 7.7, 9.9, 0.32, "purple", faces=("north", "south", "east", "west"))
    m.box([5.6, 9.9, 5.0], [10.4, 10.3, 11.0], "carbon", decals={"up": "carbon_top"})
    # flight stack
    m.box([6.4, 7.9, 6.6], [9.6, 8.3, 9.8], "pcb", decals={"up": "pcb_top"})
    m.box([6.6, 8.7, 6.8], [9.4, 9.0, 9.6], "pcb_black", decals={"up": "pcb_top"})
    # camera between orange TPU side plates, tilted up
    m.box([5.6, 7.7, 3.5], [6.1, 9.9, 6.0], "orange")
    m.box([9.9, 7.7, 3.5], [10.4, 9.9, 6.0], "orange")
    cam = {"origin": [8, 8.8, 4.8], "axis": "x", "angle": 22.5}
    m.box([6.4, 7.9, 3.9], [9.6, 9.7, 5.6], "black", rot=cam)
    m.box([6.9, 8.1, 3.3], [9.1, 9.5, 3.9], "graphite", rot=cam, decals={"north": "lens"})
    # 6S lipo with strap, XT60 lead
    m.box([5.9, 10.3, 5.2], [10.1, 12.5, 10.8], "lipo", decals={"east": "lipo_label", "west": "lipo_label", "up": "lipo_label"})
    m.box([5.75, 10.25, 6.7], [10.25, 12.65, 7.2], "plastic_dark")
    m.box([5.75, 10.25, 8.8], [10.25, 12.65, 9.3], "plastic_dark")
    m.box([7.5, 12.65, 6.6], [8.5, 12.8, 7.3], "silver")
    m.box([7.3, 11.0, 10.8], [8.7, 11.6, 11.7], "yellow")
    m.box([7.5, 10.4, 11.7], [7.9, 10.7, 12.4], "wire_red")
    m.box([8.1, 10.4, 11.7], [8.5, 10.7, 12.4], "wire_black")
    # VTX antenna in an orange TPU mount, leaning back
    m.box([7.2, 10.3, 11.0], [8.8, 11.1, 12.4], "orange")
    ant = {"origin": [8, 11.1, 11.7], "axis": "x", "angle": -22.5}
    m.box([7.85, 11.1, 11.55], [8.15, 13.2, 11.85], "black", rot=ant)
    m.box([7.45, 13.2, 11.3], [8.55, 13.9, 12.1], "black", rot=ant)
    # motors, prop nuts and props
    tip = 7.8 / math.sqrt(2)
    for (x, z) in ((8 - tip, 8 - tip), (8 + tip, 8 - tip), (8 - tip, 8 + tip), (8 + tip, 8 + tip)):
        m.cyl(x, z, 7.7, 8.3, 1.0, "black")
        m.cyl(x, z, 8.3, 9.5, 1.1, "motor_bell", top="motor_top")
        m.box([x - 0.35, 9.5, z - 0.35], [x + 0.35, 9.9, z + 0.35], "purple")
        m.prop(x, z, 9.6, 3.7, "prop")
        m.led([x - 0.5, 7.05, z - 0.5], [x + 0.5, 7.2, z + 0.5], "led_cyan")
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
    """DJI RC-N style controller with a phone in the top clamp showing the drone feed."""
    m = Model("remote_camera", 4)
    m.box([3.0, 6.6, 7.0], [13.0, 8.4, 10.8], "plastic_rc", decals={"up": "rc_face"})
    for (x0, x1) in ((1.8, 4.6), (11.4, 14.2)):
        m.box([x0, 6.3, 8.0], [x1, 8.2, 12.6], "plastic_rc", decals={"up": "grip", "down": "grip"})
    for x in (5.7, 10.3):
        m.cyl(x, 9.1, 8.4, 9.3, 0.3, "silver")
        m.cyl(x, 9.1, 9.3, 9.8, 0.62, "rubber")
    # phone clamp and phone, leaning back
    m.box([3.4, 8.2, 6.2], [12.6, 9.0, 7.1], "plastic_rc")
    tilt = {"origin": [8, 8.9, 6.7], "axis": "x", "angle": -22.5}
    m.box([3.1, 8.9, 6.3], [12.9, 13.6, 7.0], "black", rot=tilt, decals={"south": "phone_screen"})
    # antennas folded under the front edge
    for (x0, x1) in ((3.4, 5.0), (11.0, 12.6)):
        m.box([x0, 6.0, 5.6], [x1, 6.6, 8.0], "plastic_rc")
    m.display = remote_display(0.95)
    return m


def remote_fpv():
    """Radiomaster Boxer style radio: hall gimbals, a small LCD, switches and a folding antenna."""
    m = Model("remote_fpv", 4)
    m.box([2.6, 6.2, 5.6], [13.4, 8.5, 11.4], "black", decals={"up": "tx_face"})
    for (x0, x1) in ((2.0, 5.4), (10.6, 14.0)):
        m.box([x0, 5.2, 9.0], [x1, 8.0, 13.0], "black", decals={"up": "grip", "down": "grip"})
    for x in (5.1, 10.9):
        m.box([x - 1.3, 8.5, 7.4], [x + 1.3, 8.6, 10.0], "graphite", decals={"up": "stick_well"})
        m.cyl(x, 8.7, 8.6, 9.9, 0.25, "silver")
        m.cyl(x, 8.7, 9.9, 10.5, 0.5, "rubber")
    m.box([6.9, 8.5, 8.2], [9.1, 8.6, 10.4], "screen", decals={"up": "lcd"})
    for x in (3.4, 4.8, 11.2, 12.6):
        m.box([x - 0.2, 8.3, 5.3], [x + 0.2, 9.6, 5.7], "silver", rot={"origin": [x, 8.3, 5.5], "axis": "x", "angle": -22.5})
    m.box([7.3, 7.6, 4.8], [8.7, 8.6, 5.7], "graphite")
    m.box([7.55, 8.6, 5.0], [8.45, 13.2, 5.5], "black", rot={"origin": [8, 8.6, 5.25], "axis": "x", "angle": -22.5})
    m.box([7.6, 6.9, 11.4], [8.4, 7.7, 11.9], "red_anod")
    m.display = remote_display(0.95)
    return m


# --------------------------------------------------------------------------------------------- GUI sprites

def gui_sprites():
    os.makedirs(os.path.join(TEX_GUI, "tool"), exist_ok=True)
    # tool slot: 24x24 frame in the hotbar style with an accent corner
    s = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    d.rounded_rectangle([0, 0, 23, 23], radius=3, fill=(20, 22, 26, 200), outline=(10, 10, 12, 255))
    d.rounded_rectangle([1, 1, 22, 22], radius=2, outline=(120, 128, 140, 255))
    d.rectangle([2, 2, 21, 21], outline=(60, 64, 72, 255))
    d.polygon([(16, 22), (22, 22), (22, 16)], fill=(255, 122, 26, 255))
    s.save(os.path.join(TEX_GUI, "tool", "slot.png"))
    # selection frame drawn over the slot while the tool is active
    sel = Image.new("RGBA", (26, 26), (0, 0, 0, 0))
    d = ImageDraw.Draw(sel)
    d.rounded_rectangle([0, 0, 25, 25], radius=4, outline=(255, 255, 255, 255), width=2)
    d.rounded_rectangle([2, 2, 23, 23], radius=3, outline=(255, 160, 70, 255))
    sel.save(os.path.join(TEX_GUI, "tool", "slot_selected.png"))
    # radial wheel pieces, drawn big and scaled down in game
    size = 128
    ring = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(ring)
    c = size * 2
    d.ellipse([c - 250, c - 250, c + 250, c + 250], fill=(12, 14, 18, 150))
    d.ellipse([c - 250, c - 250, c + 250, c + 250], outline=(255, 255, 255, 60), width=6)
    d.ellipse([c - 120, c - 120, c + 120, c + 120], fill=(0, 0, 0, 0))
    d.ellipse([c - 120, c - 120, c + 120, c + 120], outline=(255, 255, 255, 50), width=6)
    ring = ring.resize((size, size), Image.LANCZOS)
    ring.save(os.path.join(TEX_GUI, "tool", "wheel.png"))
    for name, fill, outline in (("bubble", (32, 36, 44, 220), (255, 255, 255, 70)),
                                ("bubble_hover", (255, 122, 26, 235), (255, 230, 200, 255)),
                                ("bubble_locked", (24, 26, 30, 170), (255, 255, 255, 30))):
        b = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
        d = ImageDraw.Draw(b)
        d.ellipse([4, 4, 155, 155], fill=fill, outline=outline, width=8)
        b.resize((40, 40), Image.LANCZOS).save(os.path.join(TEX_GUI, "tool", name + ".png"))
    center = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(center)
    d.ellipse([4, 4, 251, 251], fill=(8, 10, 12, 200), outline=(255, 255, 255, 45), width=6)
    center.resize((64, 64), Image.LANCZOS).save(os.path.join(TEX_GUI, "tool", "center.png"))


def main():
    os.makedirs(TEX_ITEM, exist_ok=True)
    for old in ("drone_palette.png", "prop_gray.png", "prop_dark.png"):
        p = os.path.join(TEX_ITEM, old)
        if os.path.exists(p):
            os.remove(p)
    prop_frames(os.path.join(TEX_ITEM, "prop_camera.png"), 2, (215, 220, 226), (200, 206, 212))
    prop_frames(os.path.join(TEX_ITEM, "prop_fpv.png"), 3, (90, 60, 170), (70, 64, 90))
    blink_frames(os.path.join(TEX_ITEM, "led_status.png"), (45, 255, 106), "1100000011000000000000")
    for model in (camera_drone(), fpv_drone(), remote_camera(), remote_fpv()):
        model.bake()
    gui_sprites()


if __name__ == "__main__":
    main()
