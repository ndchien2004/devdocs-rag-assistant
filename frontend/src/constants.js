export const TOPICS = [
  { value: 'JAVA', label: 'Java' },
  { value: 'SPRING', label: 'Spring' },
  { value: 'HIBERNATE', label: 'Hibernate' },
  { value: 'DATABASE', label: 'Database' },
  { value: 'REACT', label: 'React' },
  { value: 'OTHER', label: 'Khác' },
]

export const topicLabel = (value) => TOPICS.find((t) => t.value === value)?.label ?? value

/** Cách thực hiện RAG phía backend (RagMode). MANUAL là cấu hình được chọn sau Phase 4 (E5). */
export const RAG_MODES = [
  { value: 'MANUAL', label: 'Thủ công', hint: 'Mặc định. Không tìm thấy đoạn liên quan thì không gọi LLM.' },
  { value: 'RAG_ADVISOR', label: 'RAG Advisor', hint: 'RetrievalAugmentationAdvisor, cùng prompt và cách trích dẫn.' },
  { value: 'QA_ADVISOR', label: 'QA Advisor', hint: 'QuestionAnswerAdvisor mặc định, không có trích dẫn [n].' },
]

export const DEFAULT_CHAT_OPTIONS = { topK: '', mode: 'MANUAL' }
export const TOP_K_MAX = 10
