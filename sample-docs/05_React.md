# React — ghi chú ôn tập

Các khái niệm React cốt lõi cho người làm fullstack.

## Component và props

Component là một hàm JavaScript nhận **props** và trả về JSX mô tả giao diện. Tên component viết hoa chữ cái đầu để JSX phân biệt với thẻ HTML.

```jsx
function Greeting({ name }) {
  return <h1>Xin chào, {name}!</h1>;
}

<Greeting name="Chiến" />
```

Props là **chỉ đọc**: component không được sửa props của chính nó. Dữ liệu đi **một chiều** từ cha xuống con; con muốn báo cho cha thì gọi hàm callback được truyền qua props (ví dụ `onChange`). Prop đặc biệt `children` chứa nội dung lồng bên trong thẻ component.

## State với useState

State là dữ liệu của component có thể thay đổi theo thời gian; khi state đổi, React **render lại** component.

```jsx
const [count, setCount] = useState(0);
<button onClick={() => setCount(c => c + 1)}>{count}</button>
```

Quy tắc quan trọng:

- **Không sửa state trực tiếp** (`items.push(x)` hay `user.name = 'A'`). React so sánh tham chiếu, sửa tại chỗ thì tham chiếu không đổi và giao diện không cập nhật. Luôn tạo object/mảng mới: `setItems([...items, x])`, `setUser({ ...user, name: 'A' })`.
- Cập nhật state là **bất đồng bộ và được gom lại (batching)**: sau `setCount(count + 1)` biến `count` trong lần render hiện tại vẫn là giá trị cũ. Khi giá trị mới phụ thuộc giá trị cũ, dùng dạng hàm `setCount(c => c + 1)`.
- Không lưu vào state những gì tính được từ props/state khác; tính trực tiếp khi render.

## useEffect và mảng phụ thuộc

`useEffect` chạy **side effect** sau khi component render: gọi API, đăng ký sự kiện, đặt timer, thao tác DOM trực tiếp.

```jsx
useEffect(() => {
  const id = setInterval(() => setNow(Date.now()), 1000);
  return () => clearInterval(id);   // cleanup
}, []);
```

Mảng phụ thuộc quyết định khi nào effect chạy lại:

- Không truyền mảng: chạy sau **mọi** lần render.
- `[]`: chỉ chạy một lần sau lần render đầu tiên (mount).
- `[userId]`: chạy lại mỗi khi `userId` thay đổi.

Hàm **cleanup** được trả về chạy trước khi effect chạy lại và khi component bị gỡ (unmount), dùng để hủy timer, hủy đăng ký, hủy request. Khai báo **đầy đủ** mọi giá trị effect sử dụng vào mảng phụ thuộc; thiếu phụ thuộc gây lỗi dùng giá trị cũ (stale closure). Trong chế độ Strict Mode khi phát triển, React cố ý chạy effect hai lần để lộ ra effect thiếu cleanup.

## Key khi render danh sách

Khi render danh sách bằng `map`, mỗi phần tử cần prop `key` **ổn định và duy nhất** trong danh sách anh em:

```jsx
{todos.map(todo => <TodoItem key={todo.id} todo={todo} />)}
```

React dùng `key` để biết phần tử nào được thêm, xóa hay đổi chỗ giữa hai lần render (reconciliation). **Không dùng index của mảng làm key** khi danh sách có thể chèn, xóa hoặc sắp xếp lại: phần tử bị gán nhầm key, state nội bộ (ví dụ nội dung ô input) "nhảy" sang dòng khác. Cũng không dùng `Math.random()` vì key đổi sau mỗi render khiến React tạo lại toàn bộ.

## Form và controlled component

**Controlled component:** giá trị của ô input do state của React quản lý; mỗi lần gõ phím gọi `onChange` để cập nhật state.

```jsx
const [email, setEmail] = useState('');
<input value={email} onChange={e => setEmail(e.target.value)} />
```

Ưu điểm: dễ validate ngay khi gõ, dễ định dạng, dễ reset form. **Uncontrolled component** để DOM tự giữ giá trị và đọc ra bằng `ref` khi cần — ít code hơn, phù hợp form đơn giản hoặc input file.

Khi submit form, gọi `e.preventDefault()` để trình duyệt không tải lại trang. Với form lớn, thư viện như React Hook Form giảm số lần render.

## useMemo và useCallback

- `useMemo(() => tinhToanNang(a, b), [a, b])` **ghi nhớ kết quả** một phép tính, chỉ tính lại khi phụ thuộc thay đổi.
- `useCallback(fn, [deps])` **ghi nhớ chính hàm**, giữ nguyên tham chiếu hàm giữa các lần render.

Mỗi lần render, các hàm và object khai báo trong component đều được tạo mới. Nếu truyền chúng xuống component con được bọc `React.memo`, con vẫn render lại vì props "khác tham chiếu". `useCallback`/`useMemo` giữ tham chiếu ổn định để `React.memo` phát huy tác dụng, và giữ ổn định phụ thuộc của `useEffect`.

Đừng bọc mọi thứ bằng memo: bản thân việc ghi nhớ cũng tốn chi phí. Chỉ dùng khi đo thấy render chậm, tính toán thực sự nặng, hoặc cần tham chiếu ổn định. React Compiler (React 19) có thể tự động hóa phần lớn việc này.

## Context API và prop drilling

**Prop drilling** là khi phải truyền một prop qua nhiều tầng component trung gian không dùng tới nó, chỉ để đưa xuống component sâu bên dưới. Context giải quyết việc này:

```jsx
const ThemeContext = createContext('light');

<ThemeContext.Provider value={theme}>
  <App />
</ThemeContext.Provider>

// ở component bất kỳ bên dưới
const theme = useContext(ThemeContext);
```

Context phù hợp với dữ liệu "toàn cục" ít thay đổi: theme, ngôn ngữ, người dùng đang đăng nhập. Lưu ý: khi `value` của Provider thay đổi, **mọi component dùng context đó đều render lại**. Với state phức tạp thay đổi thường xuyên, cân nhắc thư viện quản lý state (Zustand, Redux Toolkit) hoặc tách nhỏ context.

## Custom hook

Custom hook là hàm bắt đầu bằng `use` và gọi các hook khác, dùng để **tái sử dụng logic có state** giữa nhiều component (không phải chia sẻ chính state — mỗi component gọi hook có state riêng).

```jsx
function useFetch(url) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    const controller = new AbortController();
    fetch(url, { signal: controller.signal })
      .then(r => r.json()).then(setData).catch(setError);
    return () => controller.abort();
  }, [url]);
  return { data, error };
}
```

`AbortController` trong cleanup hủy request cũ khi `url` đổi, tránh race condition khi response cũ về sau response mới.

## Quy tắc của hooks

1. **Chỉ gọi hook ở cấp cao nhất** của component hoặc custom hook — không gọi trong vòng lặp, câu điều kiện, hay hàm lồng. React nhận diện từng hook theo **thứ tự gọi**; nếu thứ tự thay đổi giữa các lần render, state bị gán nhầm cho hook khác.
2. **Chỉ gọi hook từ function component hoặc custom hook**, không gọi từ hàm JavaScript thường hay class component.

Plugin ESLint `eslint-plugin-react-hooks` kiểm tra hai quy tắc này và cảnh báo mảng phụ thuộc thiếu trong `useEffect`/`useMemo`/`useCallback`.
