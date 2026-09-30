// Kiểm thử end-to-end giao diện + chụp screenshot / GIF cho README.
// Cần: backend (8080) đã nạp tài liệu mẫu, frontend dev server (5173), Chrome hoặc Edge đã cài.
//   node scripts/e2e-screenshots.mjs
// Biến môi trường: APP_URL (mặc định http://localhost:5173), CHROME_PATH, OUT_DIR (mặc định ../docs/screenshots)
import fs from 'node:fs'
import path from 'node:path'
import puppeteer from 'puppeteer-core'
import gifenc from 'gifenc'
import pngjs from 'pngjs'

const { GIFEncoder, quantize, applyPalette } = gifenc
const { PNG } = pngjs

const APP_URL = process.env.APP_URL ?? 'http://localhost:5173'
const OUT_DIR = path.resolve(process.env.OUT_DIR ?? '../docs/screenshots')
const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean)

const QUESTIONS = [
  'Khác nhau giữa propagation REQUIRED và REQUIRES_NEW trong @Transactional là gì?',
  'N+1 query problem trong Hibernate là gì và cách khắc phục?',
  'Vì sao phải override hashCode khi override equals?',
]
const OUT_OF_SCOPE = 'Thời tiết Hà Nội hôm nay thế nào?'

function assert(condition, message) {
  if (!condition) throw new Error('E2E assertion failed: ' + message)
  console.log('  ✓ ' + message)
}

/** Ghi lại các khung hình (PNG) rồi ghép thành GIF. */
class GifRecorder {
  constructor(page) {
    this.page = page
    this.frames = []
  }
  async frame(delayMs = 500) {
    const bytes = await this.page.screenshot({ type: 'png' }) // Uint8Array
    this.frames.push({ png: PNG.sync.read(Buffer.from(bytes)), delayMs })
  }
  async hold(totalMs, stepMs = 700) {
    for (let t = 0; t < totalMs; t += stepMs) {
      await this.frame(stepMs)
      await new Promise((r) => setTimeout(r, stepMs))
    }
  }
  save(file) {
    const gif = GIFEncoder()
    for (const { png, delayMs } of this.frames) {
      const palette = quantize(png.data, 128)
      gif.writeFrame(applyPalette(png.data, palette), png.width, png.height, { palette, delay: delayMs })
    }
    gif.finish()
    fs.writeFileSync(file, gif.bytes())
  }
}

async function typeSlowly(page, selector, text, recorder) {
  await page.click(selector)
  const chunks = text.match(/.{1,8}/gu)
  for (const chunk of chunks) {
    await page.type(selector, chunk)
    await recorder?.frame(120)
  }
}

async function waitForNewAnswer(page, countBefore) {
  await page.waitForFunction(
    (n) => document.querySelectorAll('[data-role="assistant"], [data-role="error"]').length > n,
    { timeout: 120_000 },
    countBefore,
  )
}

const executablePath = CHROME_CANDIDATES.find((p) => fs.existsSync(p))
if (!executablePath) throw new Error('Không tìm thấy Chrome/Edge — đặt CHROME_PATH')
fs.mkdirSync(OUT_DIR, { recursive: true })

const browser = await puppeteer.launch({
  executablePath,
  headless: true,
  defaultViewport: { width: 1100, height: 760 },
  args: ['--lang=vi-VN'],
})
try {
  const page = await browser.newPage()
  const recorder = new GifRecorder(page)

  console.log('Chat page')
  // 1. Câu hỏi trong phạm vi: có câu trả lời Markdown, trích dẫn bấm được, danh sách nguồn.
  // LLM không tất định và có câu model hay quên ghi [n] (E5: 13% câu trả lời thiếu trích dẫn)
  // → thử lần lượt vài câu hỏi thật; nếu vẫn không có trích dẫn thì chỉ cảnh báo (giới hạn của LLM, không phải lỗi UI).
  let answer
  let citations = []
  for (const question of QUESTIONS) {
    recorder.frames = []
    await page.goto(APP_URL, { waitUntil: 'networkidle0' })
    await recorder.frame(1200)
    await typeSlowly(page, 'textarea', question, recorder)
    await page.keyboard.press('Enter')
    await recorder.hold(1400)
    await waitForNewAnswer(page, 0)
    await recorder.frame(2500)
    answer = await page.$('[data-role="assistant"]')
    citations = answer ? await answer.$$('button[title="Xem nguồn"]') : []
    if (citations.length > 0) break
    console.log(`  (LLM answered "${question}" without [n] citations, trying another question)`)
  }
  assert(answer, 'assistant answer is rendered')
  const sources = await answer.$$('li[id^="src-"]')
  assert(sources.length > 0, `source list is shown (${sources.length} sources)`)
  if (citations.length > 0) {
    assert(true, `answer has clickable citations (${citations.length})`)
    await citations[0].click()
  } else {
    console.warn('  ⚠ no answer contained [n] citations — citation links not exercised')
  }
  await new Promise((r) => setTimeout(r, 400))
  const toggle = await answer.$('li[id^="src-"] button')
  await toggle.click()
  await recorder.hold(2100)
  const snippet = await answer.$('li[id^="src-"] p')
  assert(snippet, 'clicking "xem đoạn trích" shows the snippet')
  await page.screenshot({ path: path.join(OUT_DIR, 'chat.png'), fullPage: false })

  // 2. Câu hỏi ngoài phạm vi: found = false hiển thị kiểu khác (xám, ℹ️).
  await typeSlowly(page, 'textarea', OUT_OF_SCOPE, recorder)
  await page.keyboard.press('Enter')
  await waitForNewAnswer(page, 1)
  await recorder.hold(2800)
  const notFound = await page.$('[data-role="assistant"][data-found="false"]')
  assert(notFound, 'out-of-scope question shows the "not found" style')
  await page.screenshot({ path: path.join(OUT_DIR, 'chat-not-found.png') })

  // 3. Trang Tài liệu: bảng danh sách + hộp thoại xác nhận xóa.
  console.log('Documents page')
  const [docsTab] = await page.$$('xpath/.//nav/button[contains(., "Tài liệu")]')
  await docsTab.click()
  await page.waitForSelector('tbody tr td .rounded-full', { timeout: 10_000 })
  const rows = await page.$$('tbody tr')
  assert(rows.length >= 5, `documents table lists the sample documents (${rows.length} rows)`)
  await recorder.hold(2100)
  await page.screenshot({ path: path.join(OUT_DIR, 'documents.png') })

  const [deleteButton] = await page.$$('xpath/.//tbody//button[contains(., "Xóa")]')
  await deleteButton.click()
  await page.waitForSelector('dialog[open]')
  assert(true, 'delete asks for confirmation')
  await recorder.hold(1400)
  await page.screenshot({ path: path.join(OUT_DIR, 'documents-confirm.png') })
  const [cancel] = await page.$$('xpath/.//dialog//button[contains(., "Hủy")]')
  await cancel.click()
  await recorder.frame(800)

  recorder.save(path.join(OUT_DIR, 'demo.gif'))
  console.log(`Saved screenshots and demo.gif (${recorder.frames.length} frames) to ${OUT_DIR}`)
} finally {
  await browser.close()
}
