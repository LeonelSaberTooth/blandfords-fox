import numpy as np, math
from PIL import Image
M = np.uint64
def jhash(seed, ix, iz):
    with np.errstate(over='ignore'):
        h = M(seed) * M(0x9E3779B97F4A7C15) + ix.astype(np.int64).astype(M) * M(0xC2B2AE3D27D4EB4F) + iz.astype(np.int64).astype(M) * M(0x165667B19E3779F9)
        h = (h ^ (h >> M(31))) * M(0xBF58476D1CE4E5B9)
        h = h ^ (h >> M(29))
    return (h >> M(11)).astype(np.float64) * 2.0 ** -53
def jperlin(seed, x, z, scale):
    x = x / scale; z = z / scale
    x0 = np.floor(x).astype(np.int64); z0 = np.floor(z).astype(np.int64)
    fx = x - x0; fz = z - z0
    def dot(ix, iz, dx, dz):
        a = jhash(seed, ix, iz) * 2 * math.pi
        return np.cos(a) * dx + np.sin(a) * dz
    n00 = dot(x0, z0, fx, fz); n10 = dot(x0 + 1, z0, fx - 1, fz); n01 = dot(x0, z0 + 1, fx, fz - 1); n11 = dot(x0 + 1, z0 + 1, fx - 1, fz - 1)
    u = fx * fx * fx * (fx * (fx * 6 - 15) + 10); v = fz * fz * fz * (fz * (fz * 6 - 15) + 10)
    return ((n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v) * 1.6
def sstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def slip(p, lee): return np.where(p < 1 - lee, p / (1 - lee), (1 - p) / lee)
def smax(a, b, k):
    m = np.maximum(a, b); return m + k * np.log(np.exp((a - m) / k) + np.exp((b - m) / k))
def dune(seed, X, Z):
    P = lambda k, sc: jperlin(seed + k, X, Z, sc)
    t0 = math.radians(100); c, s = math.cos(t0), math.sin(t0)
    bend = 55 * P(50, 430) + 22 * P(53, 210)
    u = X * c + Z * s + bend; v = -X * s + Z * c + 0.6 * bend
    warp = 10 * P(0, 140) + 3 * P(1, 45)
    # spacing drifts (about 24-38 blocks) through a phase offset, not by dividing the coordinate
    # by a varying period (that blows up far from the world origin and curls ridges into rings)
    ph1 = (u + warp) / 30.0 + 1.1 * P(51, 350)
    ph2 = (u + warp * 1.2 + 11) / 40.5 + 0.8 * P(54, 350)
    r1 = slip(ph1 % 1.0, 0.45) ** 1.6
    r2 = slip(ph2 % 1.0, 0.45) ** 1.6
    hand = sstep(-0.25, 0.25, P(52, 160))
    rid = np.floor(ph1)
    per = 0.8 + 0.25 * np.sin(rid * 12.9898) * np.cos(rid * 4.1414)
    ridges = smax(r1 * per * (1 - 0.75 * hand), r2 * (0.25 + 0.75 * hand), 0.12)
    cross = slip(((v + 9 * P(6, 60) + 2.5 * P(8, 20)) / 23.0) % 1.0, 0.3) ** 1.3
    cross = cross * sstep(-0.5, 0.2, P(7, 220)) * sstep(-0.35, 0.3, P(9, 34)) * (0.35 + 0.65 * np.sqrt(np.clip(1 - ridges * 1.3, 0, 1)))
    amp = (0.7 + 0.3 * P(2, 180)) * (0.78 + 0.22 * P(3, 38))
    height = np.maximum(0, amp * smax(9.0 * ridges, 5.5 * cross, 0.9) - 0.6)
    swell = 3 * P(4, 260) + 1 * P(5, 90)
    return height, swell
if __name__ == '__main__':
    N = 768
    Z, X = np.mgrid[0:N, 0:N].astype(float)
    import sys
    off = float(sys.argv[1]) if len(sys.argv) > 1 else 3000
    X += off; Z -= off * 1.5      # away from the origin, like a real desert
    h, sw = dune(123456789, X, Z)
    H = np.round(66 + sw + h)
    gz, gx = np.gradient(H)
    shade = np.clip(0.9 - 0.18 * (gx + gz), 0.45, 1.25); hn = (H - H.min()) / (H.max() - H.min())
    col = np.array([219, 206, 160])[None, None, :] * (0.78 + 0.3 * hn[..., None]) * shade[..., None]
    Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)).save(f'java_port_map_{int(off)}.png')
    print('height range', h.min(), h.max(), 'surface', H.min(), H.max())
