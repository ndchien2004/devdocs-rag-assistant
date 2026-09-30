export const TOPICS = [
  { value: 'JAVA', label: 'Java' },
  { value: 'SPRING', label: 'Spring' },
  { value: 'HIBERNATE', label: 'Hibernate' },
  { value: 'DATABASE', label: 'Database' },
  { value: 'REACT', label: 'React' },
  { value: 'OTHER', label: 'Khác' },
]

export const topicLabel = (value) => TOPICS.find((t) => t.value === value)?.label ?? value
