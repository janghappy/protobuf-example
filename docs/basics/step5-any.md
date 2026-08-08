# step5. Any: 어떤 메시지든 담기

[← step5. 고급 타입](step5-advanced-types.md) · [← README](../../README.md)

스키마는 `order_detail.proto`의 `OrderDetail.extra`(`Any`, 8번)와 `GiftOption`이고, `AnyTest`에서 확인합니다.

`Any`는 **타입 URL**과 **직렬화된 바이트**를 가진 메시지입니다.

```protobuf
message Any {
  string type_url = 1;   // "type.googleapis.com/example.order.GiftOption"
  bytes value = 2;       // GiftOption.toByteArray()
}
```

## Any가 제공하는 메서드

`is()`, `unpack()`은 `com.google.protobuf.Any` 클래스에만 있는 메서드입니다. 일반 메시지와 필드에는 없습니다. 일반 필드는 스키마에 타입이 정해져 있어 확인할 필요가 없고, oneof에서 비슷한 역할을 하는 것은 `getXxxCase()`입니다.

| 메서드 | 하는 일 |
|---|---|
| static Any `pack`(T message) | 메시지를 Any로 감쌈 (type_url + 직렬화된 바이트) |
| boolean `is`(Class&lt;T&gt; clazz) | type_url이 그 타입인지 확인 |
| T `unpack`(Class&lt;T&gt; clazz) | value 바이트를 그 타입으로 파싱. 타입이 다르면 `InvalidProtocolBufferException` |
| T `unpackSameTypeAs`(T defaultInstance) | `unpack`과 같음. Class 대신 인스턴스로 타입 지정 |
| `getTypeUrl`(), `getValue`() | 원본 type_url 문자열, value 바이트 그대로 |

**안의 값을 쓰려면 `unpack`이 필요합니다.** Any에 담긴 것은 바이트일 뿐이라, `getMessage()` 같은 필드 접근은 해당 타입으로 파싱한 뒤에 할 수 있습니다. 보통은 `is()`로 확인하고 꺼냅니다.

```java
Any extra = received.getExtra();
if (extra.is(GiftOption.class)) {
    GiftOption gift = extra.unpack(GiftOption.class);
    gift.getMessage();
} else if (extra.is(OrderItem.class)) {
    OrderItem item = extra.unpack(OrderItem.class);
}
```

- `unpack`하려면 받는 쪽에 그 타입의 생성 클래스(`GiftOption`)가 있어야 합니다.
- 생성 클래스 없이 다뤄야 하면 `JsonFormat` + `TypeRegistry`로 JSON으로 보거나, descriptor로 `DynamicMessage`를 만들어 필드 이름으로 꺼냅니다.

## 주의할 점

- 타입 URL은 Java 패키지가 아니라 `.proto`의 `package` 기준입니다. (`example.order.GiftOption`)
- **JSON 변환에는 `TypeRegistry`가 필요합니다.** 바이트만으로는 필드 이름을 알 수 없어서, 등록하지 않으면 `JsonFormat.printer().print()`가 예외를 던집니다. 등록하면 `{"@type": "...", "message": "..."}` 형태로 나옵니다.
- 받는 쪽이 어떤 타입이 올지 모두 알아야 하므로, 가능한 타입이 정해져 있다면 `oneof`가 더 안전합니다.

| | `oneof` | `Any` |
|---|---|---|
| 담을 수 있는 타입 | 스키마에 나열한 것만 | 아무 메시지 |
| 타입 확인 | 컴파일 시점 (`getXxxCase()`) | 실행 시점 (`is()`, `unpack()`) |
| 바이트 | 필드 하나 | 타입 URL 문자열이 추가로 붙음 |
| 쓰는 곳 | 결제 수단처럼 선택지가 정해진 경우 | 에러 상세(`google.rpc.Status.details`), 플러그인 확장 데이터 |
