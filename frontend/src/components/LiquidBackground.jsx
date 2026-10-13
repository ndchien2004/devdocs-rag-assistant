/** Nền "lỏng" phía sau các lớp kính: vài vệt xám bạc rất mờ trôi chậm — nền vẫn trắng, nhưng kính có thứ để khúc xạ (đứng yên khi bật giảm chuyển động). */
const BLOBS = [
  { color: 'rgb(148 163 184 / 0.16)', top: '-24vmax', left: '-16vmax', drift: '62s', dx: '10vw', dy: '8vh' },
  { color: 'rgb(203 213 225 / 0.22)', top: '-12vmax', left: '50vw', drift: '74s', dx: '-12vw', dy: '10vh' },
  { color: 'rgb(148 163 184 / 0.12)', top: '48vh', left: '56vw', drift: '68s', dx: '-8vw', dy: '-10vh' },
  { color: 'rgb(226 232 240 / 0.5)', top: '50vh', left: '-18vmax', drift: '80s', dx: '12vw', dy: '-6vh' },
]

export default function LiquidBackground() {
  return (
    <div className="liquid-bg" aria-hidden="true">
      {BLOBS.map((b, i) => (
        <span
          key={i}
          className="liquid-blob"
          style={{
            top: b.top,
            left: b.left,
            background: `radial-gradient(closest-side, ${b.color}, transparent)`,
            '--drift': b.drift,
            '--dx': b.dx,
            '--dy': b.dy,
            animationDelay: `-${i * 11}s`,
          }}
        />
      ))}
      <span className="liquid-noise" />
    </div>
  )
}
