/** Nền "lỏng" phía sau các lớp kính: vài đốm màu trôi rất chậm (đứng yên khi bật giảm chuyển động). */
const BLOBS = [
  { color: 'rgb(34 211 238 / 0.32)', top: '-22vmax', left: '-14vmax', drift: '62s', dx: '10vw', dy: '8vh' },
  { color: 'rgb(14 165 233 / 0.24)', top: '-10vmax', left: '48vw', drift: '74s', dx: '-12vw', dy: '10vh' },
  { color: 'rgb(167 139 250 / 0.2)', top: '46vh', left: '58vw', drift: '68s', dx: '-8vw', dy: '-10vh' },
  { color: 'rgb(125 211 252 / 0.26)', top: '52vh', left: '-18vmax', drift: '80s', dx: '12vw', dy: '-6vh' },
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
