# step5. 고급 타입

[← README](../../README.md)

기본 스칼라 타입 외에 실무에서 자주 쓰는 타입입니다. 스키마는 `order_detail.proto`의 `OrderDetail`이고, 주제별 문서와 테스트로 나눠 다룹니다.

```protobuf
message OrderDetail {
  int64 order_id = 1;
  optional int64 coupon_amount = 2;                // optional
  oneof payment_method {                           // oneof
    CardPayment card = 3;
    BankTransferPayment bank_transfer = 4;
  }
  map<string, string> attributes = 5;              // map
  google.protobuf.Timestamp ordered_at = 6;        // Well-Known Types
  google.protobuf.Duration delivery_time = 7;
  google.protobuf.Any extra = 8;                   // Any
}
```

| 주제 | 문서 | 테스트 |
|---|---|---|
| optional: 0과 "값을 안 넣음" 구분 | [step5-optional.md](step5-optional.md) | `OptionalTest` |
| oneof: 여러 필드 중 하나만 | [step5-oneof.md](step5-oneof.md) | `OneofTest` |
| map: 바이너리에서는 repeated 메시지 | [step5-map.md](step5-map.md) | `MapTest` |
| Well-Known Types: Timestamp, Duration | [step5-well-known-types.md](step5-well-known-types.md) | `WellKnownTypesTest` |
| Any: 어떤 메시지든 담기 | [step5-any.md](step5-any.md) | `AnyTest` |
