# step5. Well-Known Types: Timestamp, Duration

[← step5. 고급 타입](step5-advanced-types.md) · [← README](../../README.md)

스키마는 `order_detail.proto`의 `OrderDetail.ordered_at`(`Timestamp`, 6번), `delivery_time`(`Duration`, 7번)이고, `WellKnownTypesTest`에서 확인합니다.

Google이 미리 정의해 둔 공용 메시지(`google/protobuf/*.proto`)입니다. `protobuf-java`에 클래스가 포함되어 있어 `import`만 하면 됩니다.

| 타입 | 구조 | JSON | `java.time` 변환 |
|---|---|---|---|
| `Timestamp` | `seconds`(epoch 초) + `nanos` | `"2026-10-28T01:02:03Z"` (RFC 3339) | `Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())` |
| `Duration` | `seconds` + `nanos` | `"172800s"` | `Duration.ofSeconds(d.getSeconds(), d.getNanos())` |

- 간단히 만들 때는 `protobuf-java-util`의 `Timestamps`, `Durations`를 씁니다. (`Timestamps.parseUnchecked("2026-10-28T01:02:03.456Z")`, `Durations.parseUnchecked("172800.5s")`, `fromSeconds`, `fromMillis`, `fromNanos` 등) 다만 4.31.1에는 `Instant`/`java.time.Duration`을 직접 받는 메서드가 없어서, `java.time`과는 위처럼 초와 나노초로 변환합니다.
- 생성된 setter는 `setOrderedAt(Timestamp)`와 `setOrderedAt(Timestamp.Builder)` 두 가지로 오버로드되어 있어, `build()`한 값과 Builder 모두 넣을 수 있습니다.
- `Duration`은 `java.time.Duration`과 이름이 겹치므로 한쪽은 전체 패키지 이름으로 씁니다.
- 시각을 `int64` millis나 문자열로 정의하는 것보다 의미가 분명하고, 언어마다 표준 시간 타입으로 변환할 수 있습니다.

## Timestamp와 Duration의 차이

구조는 둘 다 `seconds` + `nanos`지만, `Timestamp`는 epoch(1970-01-01T00:00:00Z) 기준의 **시점**(`Instant`), `Duration`은 기준점 없는 **길이**(`java.time.Duration`)입니다.
연산도 `java.time`과 같습니다. 시점 + 길이 = 시점(`Timestamps.add`), 시점 - 시점 = 길이(`Timestamps.between`).

## LocalDateTime에 대응하는 타입

Well-Known Types에는 없습니다. 시간 관련 WKT는 `Timestamp`, `Duration` 두 가지뿐입니다. 대신 Google이 따로 배포하는 공용 타입(`google.type.*`)에 있습니다.

| Java | protobuf | 위치 |
|---|---|---|
| `Instant` | `google.protobuf.Timestamp` | WKT (`protobuf-java`에 포함) |
| `Duration` | `google.protobuf.Duration` | WKT |
| `LocalDateTime` | `google.type.DateTime` (`time_zone`, `utc_offset`을 비워 둠) | 공용 타입 |
| `ZonedDateTime` / `OffsetDateTime` | `google.type.DateTime` (`time_zone` 또는 `utc_offset` 지정) | 공용 타입 |
| `LocalDate` | `google.type.Date` | 공용 타입 |
| `LocalTime` | `google.type.TimeOfDay` | 공용 타입 |

- 공용 타입은 `protobuf-java`가 아니라 `com.google.api.grpc:proto-google-common-protos`에 있어 의존성을 추가해야 합니다.
- `Timestamp`와 달리 JSON에서 문자열로 바뀌지 않고 `{"year": 2026, "month": 10, ...}`처럼 일반 메시지 모양으로 나옵니다.

실무에서는 이렇게 고릅니다.

- 실제로 일어난 시각(주문, 로그)은 `LocalDateTime`이 아니라 **`Timestamp`(UTC)로 보내고**, 보여 줄 때 시간대를 적용합니다. 시간대 없는 시각은 서버마다 다르게 해석될 수 있습니다.
- 생일, 영업 시작 시간처럼 시간대와 무관한 "벽시계 시각"에만 `google.type.Date`, `TimeOfDay`, `DateTime`을 씁니다.
- 의존성을 늘리기 싫으면 `string`에 ISO-8601 문자열(`"2026-10-28T10:02:03"`)로 담기도 합니다. 다만 형식은 스키마가 아니라 약속으로만 지켜집니다.
