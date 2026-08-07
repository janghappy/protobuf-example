# step5. oneof: 여러 필드 중 하나만

[← step5. 고급 타입](step5-advanced-types.md) · [← README](../../README.md)

스키마는 `order_detail.proto`의 `OrderDetail.payment_method`(`card` 3번, `bank_transfer` 4번)이고, `OneofTest`에서 확인합니다.

결제 수단처럼 **동시에 하나만 의미 있는 필드**를 묶습니다.

```java
OrderDetail detail = OrderDetail.newBuilder()
        .setCard(CardPayment.newBuilder().setCardNumber("1234-5678"))
        .setBankTransfer(BankTransferPayment.newBuilder().setBankName("kb"))  // ← card는 지워짐
        .build();

detail.getPaymentMethodCase();  // BANK_TRANSFER  (아무것도 없으면 PAYMENTMETHOD_NOT_SET)
detail.hasCard();               // false
```

- oneof마다 **`getXxxCase()` 메서드와, 그 반환 타입인 `XxxCase` enum**이 생성됩니다. 이름은 oneof 이름과 필드 이름에서 나옵니다.

  | `.proto` | 생성 결과 |
  |---|---|
  | oneof `payment_method` | `getPaymentMethodCase()`, `clearPaymentMethod()`(Builder), enum `OrderDetail.PaymentMethodCase` |
  | 필드 `card = 3` | 상수 `PaymentMethodCase.CARD` (값 3 = 필드 번호) |
  | 필드 `bank_transfer = 4` | 상수 `PaymentMethodCase.BANK_TRANSFER` (값 4) |
  | 아무것도 설정되지 않음 | 상수 `PaymentMethodCase.PAYMENTMETHOD_NOT_SET` (값 0, oneof 이름의 밑줄이 빠짐) |

  ```java
  switch (detail.getPaymentMethodCase()) {
      case CARD -> ...;
      case BANK_TRANSFER -> ...;
      case PAYMENTMETHOD_NOT_SET -> ...;
  }
  ```

- 모든 필드에 공통으로 생기는 `int` 상수(`CARD_FIELD_NUMBER = 3` 등)와는 다릅니다. `XxxCase` enum은 oneof에만 생깁니다.
- 특히 **`XXX_NOT_SET`은 oneof에서만 제공되는 상수**로, "선택지 중 아무것도 설정되지 않음"을 뜻합니다.
  - 일반 필드에는 "설정 안 됨"을 나타내는 상수가 없습니다. 기본값을 돌려줄 뿐입니다.
  - `optional`도 내부적으로는 숨은 oneof지만, Java 코드에는 Case enum이나 `NOT_SET`이 생성되지 않고 `hasXxx()`만 생깁니다.
  - enum 타입의 0번 값(`ORDER_STATUS_UNSPECIFIED`)도 "지정 안 됨"이라는 비슷한 역할을 하지만, 생성된 것이 아니라 `.proto`에 직접 정의한 값입니다.
- 내부적으로는 필드마다 변수를 두지 않고, "지금 설정된 필드 번호"(`paymentMethodCase_`)와 값(`paymentMethod_`) 두 변수를 함께 씁니다. 그래서 다른 필드를 set하면 이전 값이 덮어쓰여 지워집니다.
- 바이너리에는 특별한 구조가 없고, 설정된 필드 하나만 평범하게 기록됩니다.
- `oneof` 안에는 `repeated`, `map`을 넣을 수 없습니다.
