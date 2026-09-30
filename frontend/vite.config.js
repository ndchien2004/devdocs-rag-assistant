import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // strictPort: nếu 5173 đã bận thì báo lỗi thay vì lặng lẽ chuyển sang 5174 —
  // backend chỉ bật CORS cho http://localhost:5173 (app.cors.allowed-origins).
  server: { port: 5173, strictPort: true },
})
