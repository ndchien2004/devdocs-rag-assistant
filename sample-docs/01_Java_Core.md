# Java Core — ghi chú ôn tập

Tài liệu tóm tắt các khái niệm Java Core hay gặp khi phỏng vấn và khi đi làm. Mỗi mục là một chủ đề độc lập.

## Bốn tính chất của lập trình hướng đối tượng

- **Đóng gói (Encapsulation):** ẩn trạng thái bên trong object, chỉ cho phép thay đổi qua method công khai. Field để `private`, kiểm tra dữ liệu hợp lệ trong setter hoặc constructor.
- **Kế thừa (Inheritance):** lớp con dùng lại và mở rộng hành vi của lớp cha bằng `extends`. Java chỉ cho phép đơn kế thừa với class, nhưng một class có thể `implements` nhiều interface.
- **Đa hình (Polymorphism):** cùng một lời gọi method nhưng hành vi khác nhau tùy kiểu thực tế của object lúc runtime (overriding). Overloading — cùng tên method, khác tham số — là đa hình lúc compile.
- **Trừu tượng (Abstraction):** chỉ lộ ra "làm được gì" (interface, abstract class), giấu "làm như thế nào".

Nguyên tắc thực tế: ưu tiên **composition hơn inheritance** — kế thừa tạo ràng buộc chặt giữa lớp cha và lớp con, còn composition cho phép thay thế thành phần dễ dàng.

## equals và hashCode

`equals()` mặc định của `Object` so sánh địa chỉ (hai tham chiếu có trỏ cùng một object không). Khi hai object được coi là "bằng nhau về mặt giá trị" thì phải override `equals()`.

**Hợp đồng quan trọng:** nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` bắt buộc phải đúng. Ngược lại hai object có cùng hashCode chưa chắc đã equals (đụng độ băm).

Nếu override `equals()` mà quên `hashCode()`, object sẽ "biến mất" trong `HashMap`/`HashSet`: `map.get(key)` tìm sai bucket nên trả về `null` dù đã `put` một key bằng giá trị.

```java
public record Point(int x, int y) { }   // record tự sinh equals/hashCode/toString theo mọi field
```

Không nên dùng field có thể thay đổi để tính hashCode của object đang nằm trong HashSet — sửa field xong object nằm sai bucket.

## String bất biến và String pool

`String` là **immutable**: mọi method như `concat`, `replace`, `toUpperCase` đều trả về một String mới, object cũ không đổi. Lợi ích: an toàn khi dùng chung giữa nhiều thread, dùng làm key của HashMap an toàn, và cho phép **String pool**.

String pool là vùng nhớ lưu các String literal. Hai literal giống nhau dùng chung một object:

```java
String a = "java";
String b = "java";
String c = new String("java");
System.out.println(a == b);       // true  — cùng object trong pool
System.out.println(a == c);       // false — new luôn tạo object mới trên heap
System.out.println(a.equals(c));  // true  — so sánh nội dung
```

Luôn so sánh nội dung String bằng `equals()`, không dùng `==`.

Nối chuỗi trong vòng lặp bằng `+` tạo ra rất nhiều object trung gian. Dùng `StringBuilder` (không đồng bộ, nhanh) thay thế; `StringBuffer` là phiên bản synchronized, hiện ít dùng.

## ArrayList và LinkedList

| Thao tác | ArrayList | LinkedList |
|---|---|---|
| Truy cập theo index `get(i)` | O(1) | O(n) |
| Thêm vào cuối | O(1) khấu hao | O(1) |
| Chèn/xóa ở giữa | O(n) (dịch phần tử) | O(1) nếu đã có vị trí, nhưng tìm vị trí mất O(n) |
| Bộ nhớ | Mảng liên tục, gọn | Mỗi node thêm 2 con trỏ |

`ArrayList` dùng mảng bên trong; khi đầy sẽ cấp mảng mới lớn hơn khoảng 1,5 lần và copy sang. Trong thực tế `ArrayList` gần như luôn nhanh hơn nhờ tận dụng CPU cache; `LinkedList` hiếm khi là lựa chọn đúng. Nếu cần hàng đợi hai đầu, dùng `ArrayDeque`.

## HashMap hoạt động thế nào

`HashMap` lưu dữ liệu trong một mảng các **bucket**. Khi `put(key, value)`:

1. Tính `key.hashCode()`, trộn bit cao xuống bit thấp, rồi lấy chỉ số bucket `(n - 1) & hash` (n là số bucket, luôn là lũy thừa của 2).
2. Nếu bucket trống, đặt node mới vào.
3. Nếu đã có node (đụng độ), duyệt các node trong bucket, so sánh bằng `equals()`; trùng key thì ghi đè value, không trùng thì thêm node mới.

Từ Java 8, khi một bucket có **từ 8 node trở lên** (và mảng có ít nhất 64 bucket), danh sách liên kết được chuyển thành **cây đỏ-đen** (treeify), nên trường hợp xấu nhất giảm từ O(n) xuống O(log n).

**Load factor** mặc định là 0,75: khi số phần tử vượt `capacity × 0.75`, HashMap **resize** — gấp đôi số bucket và phân bổ lại các node. Nếu biết trước số phần tử, khởi tạo capacity phù hợp để tránh resize nhiều lần.

`HashMap` không thread-safe và cho phép một key `null`. Trong môi trường đa luồng, dùng `ConcurrentHashMap`.

## Checked và unchecked exception

- **Checked exception** (kế thừa `Exception` nhưng không phải `RuntimeException`), ví dụ `IOException`, `SQLException`: compiler bắt buộc phải `catch` hoặc khai báo `throws`. Dùng cho lỗi có thể lường trước và phục hồi được.
- **Unchecked exception** (kế thừa `RuntimeException`), ví dụ `NullPointerException`, `IllegalArgumentException`: không bắt buộc xử lý. Thường thể hiện lỗi lập trình hoặc vi phạm điều kiện.
- **Error** (`OutOfMemoryError`, `StackOverflowError`): lỗi nghiêm trọng của JVM, không nên catch.

**try-with-resources** tự động đóng tài nguyên implements `AutoCloseable`, kể cả khi có exception:

```java
try (var reader = Files.newBufferedReader(path)) {
    return reader.readLine();
}   // reader.close() được gọi tự động
```

Không nuốt exception bằng khối `catch` rỗng; tối thiểu phải log hoặc bọc lại (`throw new MyException("...", e)`) để giữ nguyên nguyên nhân gốc.

## Generics và type erasure

Generics cho phép viết code an toàn kiểu với nhiều kiểu dữ liệu: `List<String>` bắt lỗi kiểu lúc compile thay vì `ClassCastException` lúc runtime.

**Type erasure:** thông tin kiểu generic bị xóa sau khi compile — lúc runtime `List<String>` và `List<Integer>` đều chỉ là `List`. Hệ quả: không thể viết `new T()`, không thể `instanceof List<String>`, không thể tạo mảng `new T[10]`.

Wildcard và nguyên tắc **PECS — Producer Extends, Consumer Super**:

- `List<? extends Number>`: chỉ **đọc** ra được Number (producer), không thêm vào được.
- `List<? super Integer>`: **ghi** Integer vào được (consumer), đọc ra chỉ là Object.

```java
static double sum(List<? extends Number> numbers) { ... }      // đọc
static void fill(List<? super Integer> target) { target.add(1); } // ghi
```

## Stream API

Stream xử lý tập dữ liệu theo phong cách khai báo: `filter`, `map`, `sorted`, `collect`...

- **Intermediate operation** (`filter`, `map`, `flatMap`, `sorted`, `distinct`) trả về Stream mới và **lazy** — chưa chạy gì cho tới khi có terminal operation.
- **Terminal operation** (`collect`, `forEach`, `reduce`, `count`, `findFirst`) kích hoạt pipeline. Một stream chỉ dùng được **một lần**.

```java
Map<String, Long> countByCity = users.stream()
        .filter(u -> u.age() >= 18)
        .collect(Collectors.groupingBy(User::city, Collectors.counting()));
```

Nhờ lazy, `findFirst()` dừng ngay khi tìm được phần tử đầu tiên. `parallelStream()` chỉ đáng dùng với tập dữ liệu lớn và tác vụ nặng CPU, không chia sẻ trạng thái; với tập nhỏ nó thường chậm hơn vì chi phí chia việc.

## Record và sealed class

**Record** (Java 16+) là class bất biến dùng để mang dữ liệu. Compiler tự sinh constructor, accessor (`name()`), `equals`, `hashCode`, `toString`. Có thể thêm compact constructor để kiểm tra dữ liệu:

```java
public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount.signum() < 0) throw new IllegalArgumentException("amount < 0");
    }
}
```

**Sealed class/interface** (Java 17) giới hạn những lớp nào được phép kế thừa: `sealed interface Shape permits Circle, Square`. Kết hợp với **pattern matching cho switch** (Java 21), compiler kiểm tra switch đã xử lý đủ mọi trường hợp mà không cần nhánh `default`.

## Virtual threads (Java 21)

**Platform thread** truyền thống ánh xạ 1-1 với thread của hệ điều hành: tốn khoảng 1 MB stack, số lượng giới hạn ở mức vài nghìn. Mô hình "mỗi request một thread" vì thế nhanh chóng cạn thread khi request phải chờ I/O (gọi DB, gọi API).

**Virtual thread** do JVM quản lý, rất nhẹ — có thể tạo hàng triệu virtual thread. Khi virtual thread chờ I/O, JVM tháo nó khỏi carrier thread (platform thread bên dưới) để carrier chạy việc khác. Nhờ vậy code blocking viết kiểu tuần tự đơn giản vẫn đạt throughput cao như code bất đồng bộ.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    urls.forEach(url -> executor.submit(() -> fetch(url)));
}
```

Lưu ý: virtual thread không làm tác vụ nặng CPU chạy nhanh hơn; không cần pool virtual thread (tạo mới mỗi task); trong Spring Boot bật bằng `spring.threads.virtual.enabled=true`.

## synchronized, volatile và happens-before

- **synchronized** bảo đảm tại một thời điểm chỉ một thread chạy đoạn code được bảo vệ (mutual exclusion), đồng thời bảo đảm thay đổi của thread trước khi nhả lock được thread sau nhìn thấy (visibility).
- **volatile** chỉ bảo đảm visibility: ghi vào biến volatile được các thread khác thấy ngay, không bị cache riêng. Nó **không** bảo đảm tính nguyên tử cho thao tác phức hợp như `count++` (đọc–tăng–ghi).
- Với bộ đếm dùng chung, dùng `AtomicInteger`/`LongAdder`; với cấu trúc dữ liệu, dùng collection đồng thời như `ConcurrentHashMap`.

Quan hệ **happens-before** trong Java Memory Model mô tả khi nào một thao tác ghi chắc chắn được thao tác đọc ở thread khác nhìn thấy: nhả lock → lấy lại cùng lock, ghi volatile → đọc volatile đó, `Thread.start()`, `Thread.join()`.
