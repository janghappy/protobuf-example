# protobuf-example

Protocol Buffers(Protobuf)의 기본 사용법, 바이너리 구조, JSON 대비 크기, 스키마 진화, 고급 타입(oneof, map 등)을 테스트 코드로 확인하는 실습 프로젝트입니다.

```bash
./gradlew test   # .proto → Java 코드 생성 후 테스트 실행 (결과 리포트: build/reports/tests/test/index.html)
```

---

## Protobuf란

Google이 만든 **스키마 기반 바이너리 직렬화 포맷**입니다. `.proto` 파일로 메시지 구조를 정의하면 `protoc` 컴파일러가 각 언어의 클래스를 만들어 줍니다.

| | JSON | Protobuf |
|---|---|---|
| 형식 | 텍스트 | 바이너리 |
| 스키마 | 없음 (자유 형식) | `.proto` 필수 |
| 필드 식별 | 키 이름 (`"customerName"`) | 필드 번호 (`2`) |
| 크기 / 속도 | 큼 / 느림 | 작음 / 빠름 |
| 사람이 읽기 | 쉬움 | 어려움 (도구 필요) |
| 주 사용처 | REST API, 설정 | gRPC, Kafka 메시지, 내부 서비스 간 통신 |

### `.proto`는 Java가 지원하는 확장자인가?

아닙니다.

- `.proto`는 특정 언어에 속하지 않는 **스키마 정의 파일(IDL)**입니다.
- Java는 이 파일을 직접 읽지 않습니다.
- `protoc` 컴파일러가 `.proto`를 **Java 소스로 변환**하고, Java는 그 생성된 클래스를 씁니다.

```
order.proto  ──(protoc)──▶  Order.java, OrderItem.java ...  ──(javac)──▶  .class
 스키마 정의                  생성된 Java 소스                      일반 Java 코드처럼 사용
```

| 구성 요소 | 정체 | 쓰이는 시점 | 역할 |
|---|---|---|---|
| `.proto` | 스키마 파일 | - | 메시지 구조만 정의 (Protobuf 문법, 언어 중립) |
| `protoc` | **C++ 네이티브 실행 파일** (Java 아님) | 빌드 시 | `.proto` → 각 언어 코드 생성 (Java, Kotlin, Go, Python, C++, C# 등) |
| `protobuf-java` | Java 라이브러리 (jar) | 실행 시 | 생성된 클래스가 상속·호출하는 런타임 |
| `com.google.protobuf` 플러그인 | Gradle 플러그인 | 빌드 시 | `protoc`를 내려받아 실행 |

**변환은 `protoc` 혼자 합니다.**

- `protoc` 안에 Java 코드 생성기가 내장되어 있어서 `.java` 파일을 직접 써 냅니다.
- `protobuf-java`는 변환에 관여하지 않습니다.
- `protobuf-java`는 생성된 코드가 의존하는 기반 클래스와 직렬화 로직을 제공하는 런타임입니다.

```java
// protoc가 생성한 Order.java (일부)
public final class Order extends com.google.protobuf.GeneratedMessage   // ← protobuf-java의 클래스
        implements OrderOrBuilder {

    public void writeTo(com.google.protobuf.CodedOutputStream output) { ... }  // ← 바이트 쓰기도 protobuf-java
}
```

그래서 `protobuf-java` 없이는 생성된 코드가 컴파일되지 않고, 반대로 실행 중에는 `protoc`가 필요 없습니다. (`protoc`는 애플리케이션 jar에 포함되지 않음)

`protoc`는 Java 라이브러리가 아닌데도 Maven 좌표(`com.google.protobuf:protoc`)로 받습니다. Google이 OS·CPU별 실행 파일을 Maven Central에 올려 두고, 플러그인이 환경에 맞는 것을 골라 받기 때문입니다. 따로 설치할 필요가 없습니다.

| 환경 | 받는 파일 |
|---|---|
| Linux x86_64 | `protoc-4.31.1-linux-x86_64.exe` (확장자만 `.exe`, 실제로는 리눅스 ELF 실행 파일) |
| macOS Apple Silicon | `protoc-4.31.1-osx-aarch_64.exe` |
| Windows | `protoc-4.31.1-windows-x86_64.exe` |

`protoc`를 직접 설치했다면 Gradle 없이도 변환할 수 있습니다: `protoc --proto_path=src/main/proto --java_out=build/gen src/main/proto/order/order.proto`

이 프로젝트에서는 Gradle protobuf 플러그인이 `src/main/proto/**/*.proto`를 찾아 빌드 때 자동으로 변환합니다. (`generateProto` → `compileJava`)

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.5"
}
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.31.1" }   // Java 코드 생성기
}
dependencies {
    implementation("com.google.protobuf:protobuf-java:4.31.1")   // 생성 코드의 런타임
}
```

같은 `.proto`로 서버는 Java, 클라이언트는 Go·Python 코드를 생성해도 같은 바이너리를 주고받을 수 있습니다. 그래서 `.proto`는 서비스 간 **계약 파일** 역할을 합니다.
IntelliJ에서 문법 강조가 필요하면 **Protocol Buffers** 플러그인을 설치하면 됩니다. 빌드와는 무관합니다.

## step1. 스키마 정의와 기본 사용법

```protobuf
message Order {
  int64 id = 1;                  // = 1 은 값이 아니라 "필드 번호"
  string customer_name = 2;
  OrderStatus status = 3;
  repeated OrderItem items = 4;  // 리스트
}

enum OrderStatus {
  ORDER_STATUS_UNSPECIFIED = 0;  // proto3 enum은 0번이 기본값
  ORDER_STATUS_CREATED = 1;
  ORDER_STATUS_PAID = 2;
  ORDER_STATUS_SHIPPED = 3;
}
```

```java
Order order = Order.newBuilder()          // 생성된 클래스는 불변, Builder로 만든다
        .setId(1L)
        .setCustomerName("kim")
        .setStatus(OrderStatus.ORDER_STATUS_PAID)
        .addItems(OrderItem.newBuilder().setProductName("keyboard").setQuantity(1).setPrice(50_000))
        .build();

byte[] bytes = order.toByteArray();       // 직렬화
Order parsed = Order.parseFrom(bytes);    // 역직렬화

Order paid = order.toBuilder().setStatus(OrderStatus.ORDER_STATUS_SHIPPED).build();  // 수정은 복사본으로
```

- **null이 없습니다.** 값을 넣지 않은 필드는 기본값(`""`, `0`, enum 0번, 빈 리스트)을 돌려줍니다.
- `JsonFormat`(protobuf-java-util)으로 JSON과 서로 변환할 수 있습니다. 이때 `int64`는 JavaScript 숫자 정밀도 문제 때문에 `"id": "1"`처럼 문자열로 나옵니다.

## step2. 바이너리(wire format) 구조

각 필드는 `[태그][값]`으로 기록되고, 태그는 `(필드 번호 << 3) | wire type`입니다.

### wire type

태그의 하위 3비트로, 파서에게 **"이 필드의 값이 몇 바이트인지"**를 알려 줍니다.
`int64`인지 `sint64`인지 같은 실제 타입은 알려 주지 않습니다. 실제 타입은 `.proto`에서 알 수 있습니다.

| wire type | 이름 | 값 크기 | 쓰이는 타입 |
|---|---|---|---|
| **0** | VARINT | 가변 (1~10바이트) | `int32`, `int64`, `uint32`, `uint64`, `sint32`, `sint64`, `bool`, `enum` |
| **1** | I64 | 고정 8바이트 | `fixed64`, `sfixed64`, `double` |
| **2** | LEN (length-delimited) | 길이 + 내용 | `string`, `bytes`, 중첩 메시지, packed `repeated` |
| 3 | SGROUP | - | group 시작 (deprecated, proto2 전용) |
| 4 | EGROUP | - | group 끝 (deprecated, proto2 전용) |
| **5** | I32 | 고정 4바이트 | `fixed32`, `sfixed32`, `float` |

- 3비트라 0~7을 표현할 수 있지만 6, 7은 쓰지 않습니다.
- 3, 4는 proto3에서 쓸 수 없으므로 실제로는 **0, 1, 2, 5**만 보게 됩니다.
- 이 프로젝트의 `Order`는 정수와 문자열·메시지만 써서 0과 2만 나옵니다.

**wire type이 있어서 모르는 필드도 건너뛸 수 있습니다.**

| wire type | 파서가 다음 태그로 넘어가는 방법 |
|---|---|
| 0 | MSB가 0인 바이트가 나올 때까지 읽음 |
| 1 | 8바이트 읽음 |
| 2 | 길이를 읽고 그만큼 읽음 |
| 5 | 4바이트 읽음 |

- step4에서 v1이 모르는 `phone`(4번)을 unknown field로 보관할 수 있었던 이유입니다.
- 필드 타입 변경이 위험한 이유이기도 합니다. `string`(type 2)을 `int64`(type 0)로 바꾸면 이전 데이터와 wire type이 달라 파싱에 실패하거나 엉뚱한 값이 나옵니다.

### 태그 해석

**태그를 10진수로 바꾸고 8로 나누면, 몫이 필드 번호이고 나머지가 wire type입니다.** (`<< 3`은 곧 ×8)

| 태그 (16진수) | 10진수 | ÷ 8 | 필드 번호 | wire type |
|---|---|---|---|---|
| `08` | 8 | 1 … 0 | 1 (`id`) | 0 (VARINT) |
| `12` | 18 | 2 … 2 | 2 (`customer_name`) | 2 (LEN) |
| `18` | 24 | 3 … 0 | 3 (`status`) | 0 (VARINT) |
| `22` | 34 | 4 … 2 | 4 (`items`) | 2 (LEN) |

태그도 varint라서 필드 번호 1~15는 태그가 1바이트, 16 이상은 2바이트 이상입니다. 자주 쓰는 필드에 1~15번을 주는 이유입니다.

### 실제 바이트

`WireFormatTest`에서 실제 바이트를 확인합니다.

| 메시지 | 바이트 (hex) | 해석 |
|---|---|---|
| `id = 1` | `08 01` | 태그 `08` = 1번·varint, 값 `1` |
| `id = 150` | `08 96 01` | 값이 7비트를 넘어 varint 2바이트 |
| `customer_name = "kim"` | `12 03 6b 69 6d` | 태그 `12` = 2번·length-delimited, 길이 `3`, `kim` |
| `id = 0, customer_name = ""` | (0바이트) | 기본값은 아예 기록하지 않음 |

**필드 이름은 바이너리에 들어가지 않습니다.** 그래서 작고, 그래서 스키마(`.proto`) 없이는 읽을 수 없습니다.

## step3. JSON과 크기 비교

상품 10개가 들어 있는 같은 주문을 직렬화한 결과입니다. (`SizeComparisonTest`, JSON은 공백 제거)

| 포맷 | 크기 |
|---|---|
| JSON | 633 bytes |
| Protobuf | 190 bytes (**약 30%**) |

JSON은 항목마다 `"productName"`, `"quantity"`, `"price"` 키 문자열을 반복하지만, Protobuf는 1바이트 태그만 씁니다.

## step4. 스키마 진화

서비스는 한꺼번에 배포되지 않습니다. 이전 스키마를 쓰는 쪽과 새 스키마를 쓰는 쪽이 공존해도 통신이 되는지가 중요합니다.

```protobuf
// v1                          // v2
message User {                 message User {
  int64 id = 1;                  int64 id = 1;
  string name = 2;               string display_name = 2;  // 이름 변경
  string email = 3;              reserved 3;               // 필드 삭제
                                 reserved "email";
}                                string phone = 4;         // 필드 추가
                               }
```

| 상황 | 결과 |
|---|---|
| v2가 v1 데이터를 읽음 | `id`, `display_name` 정상. `email`(3번)은 무시, `phone`은 기본값 `""` |
| v1이 v2 데이터를 읽음 | `id`, `name` 정상. 모르는 `phone`(4번)은 **unknown field로 보관** |
| v2 → v1(수정 후 재전송) → v2 | v1이 모르는 `phone`도 **유실 없이 그대로 전달** |

### unknown field의 `toString()` 출력이 이상한 이유

v1으로 v2 데이터를 읽고 `System.out.println(v1)`을 하면 `phone`이 문자열이 아니라 이상한 메시지로 나옵니다.

```
id: 1
name: "kim"
4: {
  6: 49
  6: 45
  6: 0x383736352d343332
}
```

**1) v1이 아는 것은 wire type뿐**

```
22 0d 30 31 30 2d 31 32 33 34 2d 35 36 37 38
│  │  └───────────── "010-1234-5678" (13바이트) ─────────────┘
│  └ 길이 13
└ 태그 22 = 34 → 34 / 8 = 4 나머지 2 → 4번 필드, LEN
```

- v1의 `.proto`에는 4번 필드가 없어서, v1은 "4번 필드, wire type 2(LEN), 13바이트"라는 것만 압니다.
- LEN은 `string`일 수도, `bytes`일 수도, 중첩 메시지일 수도 있습니다.
- `toString()`(TextFormat)은 LEN인 unknown field를 **먼저 메시지로 파싱해 보고**, 성공하면 `4: { ... }`로 출력합니다.

**2) "010-1234-5678"을 메시지로 읽으면**

문자 코드를 태그로 해석합니다. ([태그 해석](#태그-해석): 10진수로 바꾸고 8로 나누기)

| 바이트 | 문자 | 해석 |
|---|---|---|
| `30` | `0` | 태그 48 → 48 / 8 = 6 … 0 → **6번 필드, VARINT** |
| `31` | `1` | 값 49 → `6: 49` |
| `30` | `0` | 태그 48 → 6번 필드, VARINT |
| `2d` | `-` | 값 45 → `6: 45` |
| `31` | `1` | 태그 49 → 49 / 8 = 6 … 1 → **6번 필드, I64** (8바이트) |
| `32 33 34 2d 35 36 37 38` | `234-5678` | 8바이트 값 → `6: 0x383736352d343332` |

- 13바이트가 정확히 맞아떨어져 파싱이 "성공"했고, 그래서 세 필드를 가진 메시지처럼 보입니다.
- 마지막 값은 I64가 little-endian이라 바이트 순서가 뒤집혀 출력됩니다. (`32 33 ... 38` → `0x38 37 ... 32`)
- 메시지로 파싱이 안 되는 문자열이었다면 `4: "010-1234-5678"`처럼 문자열로 출력됐을 것입니다.

**3) 데이터는 망가지지 않았다**

- 이 모양은 **출력할 때의 추측**일 뿐이고, unknown field에는 원래 13바이트가 그대로 보관되어 있습니다.
- 그래서 v2로 다시 읽으면 `"010-1234-5678"`이 그대로 나옵니다. (`이전_버전을_거쳐도_모르는_필드가_유실되지_않는다`)
- 테스트도 `toString()`이 아니라 원래 바이트를 꺼내서 검증합니다.

```java
String unknownPhone = v1.getUnknownFields().getField(4).getLengthDelimitedList().get(0).toStringUtf8();
```

스키마 없이 바이트를 해석하면 틀릴 수 있다는 것을 보여 주는 예입니다.

### v1 클래스에 없는 필드가 유실되지 않는 이유

v1 `User`에 선언된 필드는 `id_`, `name_`, `email_`뿐입니다. 그런데도 4번 `phone`이 왕복 후 살아남는 것은 **Java 클래스 안에 모르는 필드를 담는 공간이 따로 있기 때문**입니다. `.proto`를 참고하는 것이 아닙니다. v1 클래스는 4번 필드의 존재를 전혀 모릅니다.

**1) 담는 공간: `GeneratedMessage.unknownFields`**

모든 생성 메시지가 상속하는 `GeneratedMessage`(protobuf-java)에 이 필드가 있습니다.

```java
protected com.google.protobuf.UnknownFieldSet unknownFields;
public com.google.protobuf.UnknownFieldSet getUnknownFields();
```

`UnknownFieldSet`은 "필드 번호 → wire type별 값 목록" 구조라서 `getField(4).getLengthDelimitedList()`로 꺼낼 수 있습니다.

**2) 담는 시점: 파싱할 때 `switch`의 `default`**

생성된 v1 `User.java`의 파싱 코드입니다. `case` 값이 곧 [태그](#태그-해석)입니다.

```java
int tag = input.readTag();
switch (tag) {
    case 8:  { id_ = input.readInt64(); ... }                 // 8  = 1번, VARINT
    case 18: { name_ = input.readStringRequireUtf8(); ... }   // 18 = 2번, LEN
    case 26: { email_ = input.readStringRequireUtf8(); ... }  // 26 = 3번, LEN
    default: {
        if (!super.parseUnknownField(input, extensionRegistry, tag)) { ... }  // 모르는 태그 → unknownFields에 보관
    }
}
```

- `phone`의 태그 34(4번, LEN)는 `case`에 없으므로 `default`로 갑니다.
- 부모의 `parseUnknownField`가 wire type(LEN)을 보고 13바이트를 읽어 `unknownFields`에 넣습니다.
- wire type이 값의 길이를 알려 주기 때문에, 타입을 몰라도 정확히 잘라 담을 수 있습니다.

**3) 유지되는 이유: 복사와 직렬화에 포함**

```java
// toBuilder()로 수정할 때: 기존 메시지의 unknownFields도 같이 복사
this.mergeUnknownFields(other.getUnknownFields());

// toByteArray()로 직렬화할 때: 아는 필드를 쓴 뒤 unknownFields도 그대로 씀
public void writeTo(CodedOutputStream output) {
    if (id_ != 0L) output.writeInt64(1, id_);
    if (!isStringEmpty(name_)) writeString(output, 2, name_);
    if (!isStringEmpty(email_)) writeString(output, 3, email_);
    getUnknownFields().writeTo(output);   // ← 4번 phone의 원래 바이트를 그대로 기록
}
```

v1이 `name`만 바꿔 다시 보내도 `phone`의 원래 바이트(`22 0d 30 31 ...`)가 뒤에 붙어 나갑니다. v2는 그 바이트를 자기 `case 34`로 읽어 `"010-1234-5678"`을 복원합니다.

| 단계 | 코드 | 4번 `phone` |
|---|---|---|
| 파싱 | `switch`의 `default` → `parseUnknownField` | `unknownFields`에 바이트째로 보관 |
| 수정 | `toBuilder()` → `mergeUnknownFields` | 새 메시지로 복사 |
| 직렬화 | `writeTo` → `getUnknownFields().writeTo` | 원래 바이트 그대로 기록 |

- 생성 코드의 `equals`와 `hashCode`에도 `getUnknownFields()`가 포함됩니다. 아는 필드가 같아도 unknown field가 다르면 다른 메시지입니다.
- proto3 초기 버전(3.0~3.4)은 unknown field를 버렸고, 3.5부터 다시 보관하도록 바뀌었습니다.

### 호환성 규칙

| 변경 | 안전한가 | 이유 |
|---|---|---|
| 필드 추가 (새 번호) | O | 모르는 쪽은 무시하거나 보관, 없는 쪽은 기본값 |
| 필드 삭제 + `reserved` | O | 번호 재사용만 막으면 됨 |
| 필드 이름 변경 | O (바이너리) | 바이너리에는 번호만 기록됨. 단 JSON 변환은 이름을 쓰므로 깨짐 |
| 필드 번호 변경 | **X** | 다른 필드로 해석됨 |
| 삭제한 번호를 다른 의미로 재사용 | **X** | 이전 데이터가 엉뚱한 필드로 읽힘 → `reserved`로 막는 이유 |
| 타입 변경 (예: `string` → `int64`) | **X** | wire type이 달라 파싱 실패 또는 잘못된 값 |

## step5. 고급 타입

oneof, map를 주제별 문서로 나눠 다룹니다: [docs/basics/step5-advanced-types.md](docs/basics/step5-advanced-types.md)

---

## 정리

- `.proto`가 곧 **API 계약**입니다. 필드 번호는 한 번 정하면 바꾸지 않습니다.
- 바이너리라 작고 빠르지만 사람이 바로 읽을 수 없으므로, 디버깅할 때는 `toString()`이나 `JsonFormat`을 씁니다.
- proto3는 "값이 0인 것"과 "값을 안 넣은 것"을 구분하지 못합니다. 구분이 필요하면 `optional` 키워드를 씁니다.
- 실무에서는 gRPC의 메시지 포맷으로, 또는 Kafka + Schema Registry와 함께 스키마 호환성을 강제하는 형태로 많이 씁니다.
