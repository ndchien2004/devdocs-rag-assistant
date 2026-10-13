/**
 * Đọc Server-Sent Events từ body của một fetch() — dùng thay EventSource vì EventSource chỉ gửi được GET.
 * Mỗi sự kiện: { event, data } với data đã JSON.parse (backend luôn gửi data dạng JSON).
 */
export async function* readSse(body) {
  const reader = body.getReader()
  // stream: true — ký tự UTF-8 nhiều byte (tiếng Việt) có thể bị cắt đôi giữa hai chunk mạng.
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      // Spring gửi "\n"; bỏ "\r" để không phải xử lý "\r\n" bị cắt đôi giữa hai chunk.
      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
      let end
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const event = parseEvent(buffer.slice(0, end))
        buffer = buffer.slice(end + 2)
        if (event) yield event
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function parseEvent(block) {
  let event = 'message'
  const data = []
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue // dòng comment / keep-alive
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  return data.length ? { event, data: JSON.parse(data.join('\n')) } : null
}
