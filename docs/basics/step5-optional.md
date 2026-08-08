# step5. optional: 0과 "값을 안 넣음" 구분

[← step5. 고급 타입](step5-advanced-types.md) · [← README](../../README.md)

스키마는 `order_detail.proto`의 `OrderDetail.coupon_amount`(`optional int64`, 2번)이고, `OptionalTest`에서 확인합니다.

**요약:** proto3 일반 필드는 0을 넣은 것과 안 넣은 것이 바이트까지 똑같습니다. `optional`을 붙이면 `hasXxx()`가 생기고, 0을 넣어도 바이너리에 기록됩니다(`10 00`).

proto3 일반 필드는 기본값을 기록하지 않으므로([step2](../../README.md#step2-바이너리wire-format-구조)) **0을 넣은 것과 안 넣은 것이 바이트까지 똑같습니다.** "할인 0원"과 "할인 정보 없음"을 구분해야 하면 `optional`을 붙입니다.

| | 일반 `int64` | `optional int64` |
|---|---|---|
| 생성 메서드 | `getXxx()` | `getXxx()`, **`hasXxx()`**, `clearXxx()` |
| 안 넣음 | 0바이트, `get` → 0 | 0바이트, `has` → false |
| 0을 넣음 | 0바이트 (안 넣음과 구분 불가) | `10 00` (기록됨), `has` → true |

`optional`은 내부적으로 필드 하나짜리 숨은 `oneof`로 구현되어, "설정됨" 비트를 따로 가집니다.

> `optional`이 없던 시절(proto3 3.15 이전)에는 wrapper 타입(`google.protobuf.Int64Value` 등)으로 값을 메시지로 감싸 `hasXxx()`를 얻었습니다.
> 바이트가 더 들고(값 5가 optional은 `10 05` 2바이트, wrapper는 `[태그][길이] 08 05` 4바이트) `.getValue()`를 거쳐야 하므로, 새로 작성할 때는 `optional`을 씁니다.

## has 정보는 어디에 저장되나

**Java 객체에는 별도 비트로 있고, 바이너리에는 따로 없습니다.** 바이너리에서는 "그 필드의 태그가 있느냐"가 곧 has 정보입니다.

생성된 `OrderDetail.java`를 보면 값과 has 비트가 따로 있습니다.

```java
private int bitField0_;            // has 비트 모음 (필드마다 1비트)
private long couponAmount_ = 0L;   // 실제 값

public boolean hasCouponAmount() { return ((bitField0_ & 0x00000001) != 0); }
public long getCouponAmount()    { return couponAmount_; }
```

직렬화할 때는 has 비트가 켜져 있을 때만 필드를 씁니다. 일반 필드는 "값이 기본값이 아닐 때" 쓰고, optional은 "has 비트가 켜져 있을 때" 쓰는 것이 차이입니다.

```java
// writeTo
if (((bitField0_ & 0x00000001) != 0)) {
    output.writeInt64(2, couponAmount_);   // 값이 0이어도 씀
}

// 파싱 (Builder)
case 16: {                                 // 태그 0x10 = 16 → 2번 필드, VARINT
    couponAmount_ = input.readInt64();
    bitField0_ |= 0x00000002;              // 태그를 읽었으니 "넣었음"으로 표시 (Builder 쪽 비트 위치)
    break;
}
```

| | 바이너리 | 받은 쪽 `couponAmount_` (`get`) | 받은 쪽 has 비트 (`has`) |
|---|---|---|---|
| 안 넣음 | 2번 필드 없음 (0바이트) | 0: 아무도 건드리지 않은 초기값 | false: 태그를 못 봤으니 꺼진 그대로 |
| 0을 넣음 | `10 00` | 0: 바이트에서 읽은 값 | true: 태그를 읽을 때 켬 |

- `get`의 값은 둘 다 0이지만 들어온 경로가 다릅니다. 그래서 `get`만으로는 구분할 수 없고 `has`를 봐야 합니다.
- "안 넣었다"는 정보를 따로 보내는 것이 아니라, **아무것도 보내지 않는 것이 곧 "안 넣었다"**입니다.
- Builder로 직접 `setCouponAmount(0)`을 호출해도 값 0을 넣고 has 비트를 켭니다. 파싱의 `case 16`과 같은 일입니다.

## 일반 필드는 has 처리만 빠진 것인가

아닙니다. **직렬화 규칙이 달라서 값이 0일 때 바이너리부터 다릅니다.** 같은 2번 필드를 일반 `int64`와 `optional int64`로 선언했을 때를 비교하면 다음과 같습니다.

| 넣은 값 | 일반 `int64` | `optional int64` |
|---|---|---|
| 안 넣음 | 0바이트 | 0바이트 |
| 0 | **0바이트** | **`10 00`** |
| 5 | `10 05` | `10 05` (같음) |

쓰는 조건이 다릅니다. (`order_id`는 일반 필드)

```java
if (orderId_ != 0L) { output.writeInt64(1, orderId_); }                         // 일반: 값이 기본값이 아니면 씀
if (((bitField0_ & 0x00000001) != 0)) { output.writeInt64(2, couponAmount_); }   // optional: has 비트가 켜져 있으면 씀
```

- 일반 필드는 "넣었는가"가 아니라 "0인가"만 봅니다. 0을 넣어도 쓰지 않으므로 애초에 바이너리에 흔적이 남지 않습니다.
- 0이 아닐 때는 태그와 값이 같아 바이트가 똑같습니다. 그래서 일반 필드 ↔ optional 변경은 바이너리 호환됩니다.

Java 객체 쪽도 Builder와 완성된 메시지가 다릅니다.

```java
// Builder: 일반 필드도 set/파싱할 때 비트를 켠다 ("이 필드를 건드렸음", build() 때 복사 대상 표시용)
case 8: {
    orderId_ = input.readInt64();
    bitField0_ |= 0x00000001;
    break;
}

// build() → buildPartial0: 일반 필드는 값만 복사, optional만 메시지의 has 비트로 옮긴다
if ((from_bitField0_ & 0x00000001) != 0) { result.orderId_ = orderId_; }
if ((from_bitField0_ & 0x00000002) != 0) { result.couponAmount_ = couponAmount_; to_bitField0_ |= 0x00000001; }
```

| | 일반 필드 | optional |
|---|---|---|
| 값 0 직렬화 | 쓰지 않음 | 씀 (`10 00`) |
| 값 0이 아닐 때 직렬화 | 씀 | 씀 (같은 바이트) |
| Builder 비트 | 있음 (복사 최적화용) | 있음 |
| 완성된 메시지의 has 비트 | 없음 → `hasXxx()` 없음 | 있음 → `hasXxx()` |

## 넣지 않은 optional 필드를 get하면

**null이 아니라 기본값이 나옵니다.** Protobuf Java에는 "null인 필드"가 없고, `optional`은 값이 있는지를 `hasXxx()`로 따로 알려 줄 뿐입니다.

```java
OrderDetail detail = OrderDetail.newBuilder().build();   // coupon_amount를 넣지 않음

detail.hasCouponAmount();   // false
detail.getCouponAmount();   // 0  (예외도 null도 아님)
```

`get`만 보면 일반 필드와 똑같이 0으로 보이므로, 0과 미설정을 구분하려면 반드시 `has`를 먼저 확인합니다. Java 쪽에서 null이나 `OptionalLong`으로 다루고 싶다면 직접 변환합니다.

```java
OptionalLong coupon = detail.hasCouponAmount() ? OptionalLong.of(detail.getCouponAmount()) : OptionalLong.empty();
```

| 필드 | 넣지 않았을 때 `get` | 구분 방법 |
|---|---|---|
| `optional int64` | `0` (반환 타입이 `long`이라 null이 될 수 없음) | `hasXxx()` |
| `optional string` | `""` | `hasXxx()` |
| 메시지 (`Timestamp` 등) | 빈 기본 인스턴스 (`Timestamp.getDefaultInstance()`) | `hasXxx()` |
| `oneof`에서 선택되지 않은 필드 | 기본값 / 기본 인스턴스 | `getXxxCase()` |

**반대로 null을 넣으면 `NullPointerException`이 납니다.**

- `setOrderedAt(null)`처럼 객체를 받는 setter는 null을 허용하지 않습니다.
- `setCouponAmount`는 `long`을 받으므로, null인 `Long`을 넘기면 언박싱하면서 NPE가 납니다.
- 값을 지우려면 null 대신 `clearXxx()`를 씁니다.
