# Spring REST + Protobuf: 같은 API로 JSON과 바이너리 지원

[← README](../../README.md) · [서비스 간 통신과 JSON 변환기](../communication/service-communication.md)

하나의 API가 요청 헤더에 따라 JSON과 Protobuf 바이너리를 둘 다 주고받는 원리를 정리합니다.

- 예제: `GET /orders/{id}`, `POST /orders` (`src/main/java/com/example/protobuf/rest/`, 실행 `./gradlew bootRun`)
- 테스트: `OrderControllerTest`

## 컨트롤러는 Order만 다루고, 형식은 헤더로 정한다

컨트롤러는 Protobuf 메시지 `Order`를 받고 돌려줄 뿐, JSON인지 바이너리인지 신경 쓰지 않습니다.

```java
@GetMapping("/{id}")
public Order get(@PathVariable long id) { ... }

@PostMapping
public Order create(@RequestBody Order request) { ... }
```

변환은 `ProtobufHttpMessageConverter`가 헤더를 보고 합니다.

| 헤더 값 | 변환 |
|---|---|
| `application/x-protobuf` | `toByteArray()` / `parseFrom()` (JSON 변환기 없음) |
| `application/json` | `JsonFormat` printer / parser |

- 요청 본문은 `Content-Type`을 보고 읽습니다.
- 응답 본문은 `Accept`를 보고 씁니다(content negotiation).
- 그래서 바이너리로 보내고 JSON으로 받는 것도 됩니다.

### Accept와 Content-Type의 차이

둘 다 형식을 나타내는 헤더지만, **어느 본문을 가리키느냐**가 다릅니다.

| 헤더 | 뜻 | 가리키는 본문 | 서버가 처리하지 못하면 |
|---|---|---|---|
| `Content-Type` | "**내가 보내는** 본문은 이 형식이다" | 이 메시지에 실린 본문 | **415** Unsupported Media Type (그 형식은 읽을 수 없음) |
| `Accept` | "**이 형식으로 받고 싶다**" | 앞으로 받을 응답 본문 | **406** Not Acceptable (그 형식으로는 줄 수 없음) |

```
POST /orders
Content-Type: application/x-protobuf   ← 지금 보내는 본문은 바이너리
Accept: application/json               ← 응답은 JSON으로 주세요
<바이너리 본문>

HTTP/1.1 201 Created
Content-Type: application/json         ← 응답 본문은 JSON
{"id":"2",...}
```

- **`Content-Type`은 본문을 실은 쪽이 붙입니다.** 요청에서는 클라이언트가, 응답에서는 서버가 붙이고, 받는 쪽은 이 헤더를 보고 본문을 어떻게 읽을지 정합니다.
- **`Accept`는 요청에만 붙입니다.** 클라이언트가 원하는 응답 형식을 미리 알리는 헤더입니다.
- `GET`처럼 요청 본문이 없으면 `Content-Type`은 필요 없고 `Accept`만 의미가 있습니다.
- 두 헤더는 독립적이라 위처럼 바이너리로 보내고 JSON으로 받을 수 있습니다.

Spring에서는 이렇게 쓰입니다.

- 요청의 `Content-Type`은 `@RequestBody`를 읽을 컨버터를 고르는 데 씁니다. 맞는 컨버터가 없으면 415가 납니다.
- 요청의 `Accept`는 반환값을 쓸 컨버터를 고르는 데 씁니다. 맞는 컨버터가 없으면 406이 납니다.

### 누가 형식을 정하나

**클라이언트가 원하는 형식을 요청하면, 서버가 제공할 수 있는 형식 안에서 그대로 응답합니다.**

- 컨버터를 한 번 등록해 두면, 컨트롤러 코드를 고치거나 형식별로 API를 따로 만들지 않아도 하나의 코드로 두 형식을 모두 처리합니다.
- 형식을 고르는 일은 컨트롤러가 아니라 Spring MVC가 합니다. 컨트롤러가 반환한 `Order`를 쓸 수 있는 컨버터 중에서 `Accept`와 맞는 형식을 찾습니다.

```
클라이언트: Accept: application/json  ("JSON으로 주세요")
     ↓
Spring MVC: Order를 쓸 수 있는 형식 중 Accept와 맞는 것을 찾음
     ↓        (Protobuf 컨버터: x-protobuf, json, text/plain 가능 → json 선택)
응답: Content-Type: application/json
```

| 상황 | 결과 |
|---|---|
| `Accept: application/json` | 요청대로 JSON |
| `Accept: application/x-protobuf` | 요청대로 바이너리 |
| `Accept` 없음 (`*/*`) | 서버가 정함. 지금 구성에서는 바이너리 ([아래](#accept가-없으면-바이너리로-응답한다)) |
| `Accept: application/xml` (서버가 지원하지 않음) | **406 Not Acceptable** |
| 요청 본문 `Content-Type: application/xml` (서버가 읽지 못함) | **415 Unsupported Media Type** |

| 역할 | 하는 일 |
|---|---|
| 클라이언트 | 원하는 응답 형식(`Accept`)과 보내는 본문 형식(`Content-Type`)을 알림 |
| 서버 설정 | 지원할 형식을 정함 (등록한 컨버터, `@GetMapping(produces = ...)` / `consumes`로 제한), 기본값 결정 |
| 컨트롤러 | `Order` 객체만 다룸 |

예를 들어 `@GetMapping(value = "/{id}", produces = "application/json")`으로 제한하면, 바이너리를 요청한 클라이언트는 406을 받습니다.

## 컨버터 등록

```java
@Bean
ProtobufHttpMessageConverter protobufHttpMessageConverter() {
    return new ProtobufJsonFormatHttpMessageConverter(
            JsonFormat.parser(), JsonFormat.printer().omittingInsignificantWhitespace());
}
```

- **필수:** Boot는 Protobuf 컨버터를 자동 등록하지 않습니다. 등록하지 않으면 Jackson이 `Order`를 맡아 `Type definition error`가 납니다. 등록한 Bean은 기본 컨버터들보다 앞에 들어갑니다.
- **Jackson은 그대로:** 기본 Jackson 컨버터를 바꾸거나 대체하지 않고, Protobuf 메시지만 맡는 컨버터를 하나 **추가**합니다. `Order` 같은 Protobuf 메시지는 이 컨버터가, DTO·record·`Map` 같은 일반 객체는 지금처럼 Jackson이 변환합니다. 그래서 두 종류를 반환하는 API를 한 애플리케이션에 섞어 써도 됩니다.
- **한 번이면 충분:** 컨버터는 모든 Protobuf 메시지의 부모 타입인 `Message`를 대상으로 동작합니다. 응답은 객체 자신의 `writeTo()`/descriptor로 쓰고, 요청은 대상 클래스의 `newBuilder()`를 찾아 읽습니다. 그래서 `Order`, `OrderDetail` 등 메시지 클래스마다 설정할 필요 없이 컨트롤러만 추가하면 됩니다.
- **`ProtobufJsonFormatHttpMessageConverter`를 쓴 이유:** 기본 printer는 들여쓰기를 넣어 JSON이 커집니다(상품 10개 기준 633 → 869 bytes). 이 하위 클래스는 printer를 직접 넘길 수 있어서 공백 없는 printer를 지정했습니다.

### 요청 읽기와 응답 쓰기는 같은 컨버터

등록한 Bean 하나가 요청 본문 읽기와 응답 쓰기를 모두 맡습니다.

| 방향 | Spring이 묻는 것 | 기준 헤더 |
|---|---|---|
| 요청 → `@RequestBody Order` | "이 타입을 이 형식에서 **읽을** 수 있나" | `Content-Type` |
| `Order` → 응답 | "이 타입을 이 형식으로 **쓸** 수 있나" | `Accept` |

요청 본문은 이렇게 `Order`가 됩니다.

```
Content-Type 확인
   ↓
Order.newBuilder()  ← @RequestBody 타입(Order)의 정적 메서드를 리플렉션으로 찾아 호출
   ↓
┌─ application/x-protobuf → builder.mergeFrom(본문 스트림)          ← protoc가 생성한 Builder 코드
└─ application/json       → JsonFormat.parser().merge(본문, builder) ← protobuf-java-util
   ↓
builder.build() → 컨트롤러의 request 파라미터로 전달
```

- 응답을 쓸 때는 반대로 `Order`의 `writeTo()`가 본문에 바로 씁니다. HTTP로 보내도 본문은 `toByteArray()` 결과와 같은 바이트입니다.
- 바이너리를 실제로 읽는 것은 컨버터가 아니라 생성된 `Order.Builder`의 `mergeFrom()`입니다. `Order.parseFrom()`과 같은 파싱 로직으로, 태그 번호를 보고 필드에 값을 채웁니다. 컨버터는 Builder를 만들고 본문을 넘기는 역할만 합니다.
- JSON은 위에서 넘긴 JsonFormat parser가 같은 Builder에 값을 채웁니다.
- 읽을 때도 `Content-Type: application/json`이면 Jackson이 "읽을 수 있다"고 답하지만, 앞에 있는 Protobuf 컨버터가 먼저 뽑힙니다. `application/x-protobuf`는 Jackson이 지원하지 않는 형식이라 처음부터 Protobuf 컨버터만 후보입니다.

## Accept가 없으면 바이너리로 응답한다

`Accept` 헤더 없이 요청하면 JSON이 아니라 **바이너리**가 옵니다. 따로 설정한 동작이 아니라 위에서 [컨버터를 등록](#컨버터-등록)한 결과입니다.

- `Accept`가 없으면 "아무 형식이나 괜찮다"(`*/*`)로 봅니다.
- Spring은 컨버터보다 **응답 형식(media type)을 먼저** 고릅니다. `Order`를 쓸 수 있는 컨버터들이 만들 수 있는 형식을 모은 뒤, `Accept`와 맞는 것 중 첫 번째를 고릅니다.
- `Order`를 쓸 수 있다고 답하는 컨버터는 Protobuf 컨버터(1번째)와 Jackson 컨버터(맨 뒤쪽) 둘입니다. 형식은 컨버터 순서대로 모이므로 Protobuf 컨버터의 첫 형식인 `application/x-protobuf`가 맨 앞에 오고, 그래서 바이너리로 응답합니다.
- 그래서 JSON을 원하는 클라이언트(브라우저, 프론트엔드)는 `Accept: application/json`을 **반드시** 보내야 합니다.
- `Accept: application/json`이면 두 컨버터 모두 JSON을 쓸 수 있다고 답하지만, 앞에 있는 Protobuf 컨버터(`JsonFormat`)가 뽑힙니다.

**그래서 등록 순서가 중요합니다.** Jackson은 `Order`도 쓸 수 있다고 답하지만 실제로 변환하면 실패합니다. `Order`에 대해서는 Jackson이 Protobuf 컨버터 다음 순위인 셈입니다. Protobuf 컨버터를 등록하지 않거나 Jackson보다 뒤에 두면 Jackson이 뽑혀 `Type definition error`가 납니다. Boot가 등록한 Bean을 기본 컨버터들보다 앞에 넣어 주기 때문에 Protobuf 컨버터가 먼저 뽑힙니다.

## Protobuf 메시지 반환과 DTO 반환

**컨버터는 Spring이 반환 타입을 보고 정합니다.** 개발자가 컨버터를 지정하지 않습니다.

```
컨트롤러 반환값
   ├─ Order (Protobuf 메시지) → ProtobufHttpMessageConverter → JsonFormat
   └─ OrderResponse (DTO)     → JacksonJsonHttpMessageConverter → Jackson
```

- Spring은 등록된 컨버터마다 "이 타입을 이 형식(`Accept`)으로 쓸 수 있나"를 묻고, 가능하다고 답한 컨버터에 맡깁니다.
- Protobuf 컨버터는 `Message` 하위 클래스에만 가능하다고 답합니다. Jackson은 Protobuf 메시지에도 가능하다고 답하지만, 앞에 있는 Protobuf 컨버터가 먼저 뽑혀서 Jackson까지 가지 않습니다. (DTO는 Protobuf 컨버터가 거절하므로 Jackson이 맡습니다.)
- 그래서 JSON 모양을 바꾸려면 설정이 아니라 **컨트롤러의 반환 타입**을 바꿉니다.

**컨버터 순서는 "쓸 수 있다"고 답한 것끼리만 의미가 있습니다.** Spring은 1번부터 차례로 묻는데, 2~6번은 맡는 타입이 정해져 있어서 DTO는 모두 지나가고 Jackson까지 내려옵니다.

| 순서 | 컨버터 | DTO를 쓸 수 있나 | 이유 |
|---|---|---|---|
| 1 | Protobuf (등록한 Bean) | 아니요 | `Message` 하위 클래스가 아님 |
| 2 | ByteArray | 아니요 | `byte[]`만 |
| 3 | String | 아니요 | `String`만 |
| 4~5 | Resource, ResourceRegion | 아니요 | 파일·리소스만 |
| 6 | Form | 아니요 | 폼 데이터(`MultiValueMap`)만 |
| 7 | **Jackson** | **예** | 일반 객체는 모두 가능 → **여기서 처리** |
| 8 | Jaxb2 (XML) | 아니요 | `@XmlRootElement`가 붙은 클래스만 |

- `Order`는 1번 Protobuf와 7번 Jackson이 둘 다 "예"라고 답하고, 앞에 있는 1번이 뽑힙니다.
- 순서가 결과를 바꾸는 다른 예는 `String` 반환입니다. 3번 String 컨버터와 7번 Jackson 둘 다 가능하다고 답하지만 3번이 먼저라서 본문이 `hi` 그대로 나갑니다. Jackson이 먼저였다면 따옴표가 붙은 JSON 문자열 `"hi"`가 됩니다.

**같은 데이터라도 JSON 결과가 다릅니다.**

| 항목 | Protobuf 메시지 → JsonFormat | DTO → Jackson |
|---|---|---|
| int64 (`id`, `price`) | 문자열 `"1"` ([아래](#int64는-문자열로-나온다)) | 숫자 `1` |
| 기본값인 필드 (0, `""`, 빈 리스트) | **생략** | 그대로 출력 (`"id": 0`, `"items": []`) |
| 필드 이름 | `.proto`의 `customer_name` → `customerName` (규칙으로 자동 변환) | Java 필드 이름 그대로 |
| 형식 조정 | proto3 JSON 규칙에 묶임 | `@JsonProperty`, `@JsonIgnore` 등으로 자유롭게 조정 |

- 기본값 생략은 proto3가 기본값을 보내지 않는 규칙 때문입니다. 예를 들어 `id`가 0이고 상품이 없는 주문은 `{"customerName":"kim"}`만 나옵니다.
- 프론트엔드가 "키가 항상 있다"고 가정하면 이 차이도 문제가 될 수 있습니다.

## int64는 문자열로 나온다

JSON 응답의 `id`와 `price`는 숫자가 아니라 문자열입니다.

```json
{
  "id": "1",
  "customerName": "kim",
  "status": "ORDER_STATUS_PAID",
  "items": [
    { "productName": "product-1", "quantity": 1, "price": "1000" }
  ]
}
```

같은 `long` 값이라도 Jackson(DTO)은 숫자로, `JsonFormat`(Protobuf 메시지)은 문자열로 씁니다.

| 필드 (proto 타입) | Jackson (DTO의 `long`/`int`) | JsonFormat (Protobuf 메시지) |
|---|---|---|
| `id` (`int64`) | `1` | `"1"` |
| `price` (`int64`) | `1000` | `"1000"` |
| `quantity` (`int32`) | `1` | `1` (숫자 그대로) |

- `JsonFormat`이 proto3 JSON 규칙대로 64비트 정수(`int64`, `uint64`, `sint64`, `fixed64`, `sfixed64`)만 문자열로 씁니다. `int32`, `float`, `double`은 숫자 그대로입니다.
- JavaScript의 number는 2^53까지만 정확해서, 큰 `int64`를 숫자로 보내면 브라우저에서 값이 틀어질 수 있기 때문에 생긴 규칙입니다.
- 응답은 항상 문자열로 쓰지만, 요청을 읽을 때는 `"price": "1000"`(문자열)과 `"price": 1000`(숫자) 모두 같은 값 1000으로 파싱합니다. 그래서 클라이언트가 숫자로 보내도 됩니다.
- Jackson도 `@JsonFormat(shape = JsonFormat.Shape.STRING)` 등으로 문자열로 바꿀 수 있습니다. 큰 ID를 다루는 API는 DTO에서도 일부러 문자열로 내보내기도 합니다.

### 숫자로 받아야 한다면

컨트롤러가 Protobuf 메시지 대신 DTO를 반환하게 합니다. 반환 타입이 DTO면 Jackson 컨버터가 맡아 `long`을 숫자로 씁니다.

```
Order ──(Java 코드: getter로 옮겨 담기)──▶ OrderResponse (DTO) ──Jackson──▶ JSON (int64가 숫자)
```

- `Order` → DTO는 `order.getId()` 같은 getter로 값을 옮기는 평범한 Java 코드입니다. JsonFormat을 거치지 않고, JSON 변환은 Jackson이 DTO를 쓸 때 한 번만 일어납니다.
- DTO는 Protobuf 메시지가 아니라서 바이너리 응답은 지원되지 않습니다. 두 형식이 다 필요하면 같은 경로에 `produces`로 메서드를 나눕니다.

실무에서는 이렇게 고릅니다.

- **보통 쓰는 방식:** 프론트엔드용 API는 DTO로 응답합니다. 외부에 보여 줄 JSON 모양을 내부 스키마와 분리할 수 있다는 장점도 있습니다.
- **변환 코드를 줄이려면:** MapStruct 같은 매핑 라이브러리를 씁니다.
- **문자열로도 괜찮다면:** 프론트엔드가 문자열 ID를 받아들일 수 있으면 지금처럼 그대로 반환하는 게 가장 단순합니다. 큰 ID는 오히려 문자열이 안전합니다.
