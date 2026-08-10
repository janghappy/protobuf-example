# 서비스 간 통신과 JSON 변환기 (Jackson, JsonFormat)

[← README](../../README.md) · [성능 비교](../benchmark/benchmark.md)

Protobuf를 Spring Boot에서 쓸 때 "누가 무엇을 변환하는지"를 정리합니다.

## 클라이언트-서버와 서버-서버

```
[브라우저/앱] ──(1) JSON──▶ [주문 서비스] ──(2)──▶ [결제 서비스]
                                  │
                                  └──(2)──▶ [재고 서비스]
```

- **(1) 클라이언트와 서버:** 브라우저나 앱이 호출합니다. 사람이 읽기 쉽고 JavaScript가 바로 다룰 수 있는 JSON을 주로 씁니다.
- **(2) 서비스 간 통신:** 서버끼리 주고받습니다. 서비스를 여러 개로 나눈 구조(MSA)에서 많이 생깁니다. 사람이 볼 일이 없고 호출이 많아서 작고 빠른 형식이 유리합니다.

서버 간 통신에도 여러 방식이 있고, 서버끼리라고 꼭 Protobuf를 쓰는 것은 아닙니다.

| 서버 간 통신 방식 | 형식 | 예 |
|---|---|---|
| REST + JSON | JSON | `RestClient`, `WebClient`, OpenFeign으로 다른 서비스 호출. 가장 흔함 |
| REST + Protobuf | 바이너리 | REST 호출에 `Content-Type: application/x-protobuf`를 붙여 바이너리로 주고받음 |
| gRPC | Protobuf 바이너리 | HTTP/2 기반. 다른 서버의 메서드를 호출하듯 요청 |
| Kafka 같은 메시지 큐 | JSON, Protobuf, Avro 등 | 비동기 이벤트 발행과 구독 |

- 호출량이 많지 않으면 익숙하고 디버깅이 쉬운 REST + JSON으로 충분합니다.
- 호출이 아주 많거나 지연에 민감할 때, 또는 `.proto`로 서비스 간 계약을 엄격하게 관리하고 싶을 때 gRPC나 Protobuf를 씁니다.

## Jackson과 JsonFormat

둘 다 JSON 변환기지만 다루는 객체가 다릅니다.

| 변환하려는 객체 | 쓰는 것 | 이유 |
|---|---|---|
| 일반 Java 객체 (DTO, record, Entity) | **Jackson** | getter와 필드를 보고 변환. Protobuf와 상관없는 Spring Boot의 기본 동작 |
| Protobuf가 생성한 클래스 (`Order`, `OrderItem`) | **JsonFormat** | `.proto` 스키마(descriptor)를 보고 변환 |

- Jackson으로 Protobuf 메시지를 바로 변환하면 제대로 되지 않습니다. 생성 클래스에는 `getDefaultInstanceForType()`, `getUnknownFields()` 같은 getter가 많아서 Jackson이 이것까지 직렬화하려다 예외가 나거나 엉뚱한 JSON을 만듭니다.
- `JsonFormat`은 `protobuf-java-util`에 있고, 내부에서 Gson을 씁니다. 직렬화할 때는 문자열 이스케이프 정도에 쓰고, 역직렬화할 때는 Gson이 JSON을 트리(`JsonElement`)로 파싱한 뒤 JsonFormat이 그 트리를 메시지에 채웁니다.
- Protobuf 메시지가 JsonFormat을 거쳐 다시 Jackson을 거치는 **이중 변환은 없습니다.** 객체 하나에는 둘 중 하나만 쓰입니다.

### Spring은 반환 타입을 보고 변환기를 하나 고른다

Spring은 등록된 `HttpMessageConverter`를 차례로 돌며 "이 타입을 이 Content-Type으로 쓸 수 있나"(`canWrite`)를 묻고, 처음 가능하다고 답한 하나만 씁니다.

| 컨트롤러 반환 타입 | 선택되는 변환기 | JSON을 만드는 것 |
|---|---|---|
| `OrderDto` (record) | Jackson 변환기 | Jackson |
| `Order` (Protobuf 메시지) | `ProtobufHttpMessageConverter` | JsonFormat (`application/json` 요청일 때) |

`ProtobufHttpMessageConverter`를 Bean으로 등록해야 `Message` 타입을 이 변환기가 맡습니다. 등록하지 않으면 Jackson이 처리하려다 위의 문제가 생깁니다. 실제 등록과 동작은 [Spring REST + Protobuf](../rest/rest.md)에서 확인합니다.

## 바이너리는 JSON 변환기 없이 객체가 된다

**Protobuf 바이너리는 JSON을 거치지 않고 `parseFrom()`으로 바로 자바 객체가 됩니다.**

```
바이너리 ──parseFrom()──▶ Order 인스턴스        (JSON 변환기 없음)
JSON     ──JsonFormat──▶ Order 인스턴스        (JSON으로 받았을 때만)
```

```java
byte[] bytes = order.toByteArray();      // 보내는 쪽: 메시지 → 바이너리
Order received = Order.parseFrom(bytes); // 받는 쪽: 바이너리 → 메시지
received.getCustomerName();              // 바로 사용
```

- `parseFrom()`은 바이트를 읽으면서 필드 번호를 보고(1번이면 `id`, 2번이면 `customer_name`) 해당 필드에 값을 채웁니다. 매핑 정보가 `.proto`에서 생성된 코드 안에 있어서 별도 변환기가 필요 없습니다.
- 받는 쪽에 필요한 것은 JsonFormat이 아니라 **같은 `.proto`로 생성한 클래스**입니다. 바이너리에는 필드 이름 없이 번호만 있기 때문입니다(step2).
- JSON 문자열을 해석하는 단계가 없어서 Protobuf 역직렬화가 가장 빠릅니다([성능 비교](../benchmark/benchmark.md)).

Spring 서비스에서 받는 경우도 같습니다.

- **Kafka:** consumer가 받은 `byte[]`를 `Order.parseFrom(record.value())`로 파싱합니다. 보통은 Protobuf deserializer를 설정해 자동으로 합니다.
- **gRPC:** 생성된 코드가 파싱해서 메서드 파라미터로 `Order` 객체가 바로 들어옵니다.
- **REST + Protobuf:** `ProtobufHttpMessageConverter`가 `application/x-protobuf` 요청을 `parseFrom()`으로 파싱해 `@RequestBody Order`에 넣어 줍니다.

## 정리

| 통신 | 형식 | 변환 |
|---|---|---|
| 클라이언트와 서버 (일반 REST API) | JSON | DTO를 Jackson으로 |
| 클라이언트와 서버 (Protobuf 메시지를 REST로 반환) | JSON | Protobuf 메시지를 JsonFormat으로 |
| 서비스 간 통신 (gRPC, Kafka, REST + Protobuf) | 바이너리 | Protobuf 메시지를 `toByteArray()` / `parseFrom()`으로. JSON 변환기를 쓰지 않음 |

- JSON이 필요하면 DTO는 Jackson, Protobuf 메시지는 JsonFormat으로 변환합니다.
- JSON이 필요 없으면 바이너리를 그대로 주고받고, Jackson과 JsonFormat 둘 다 쓰지 않습니다.
