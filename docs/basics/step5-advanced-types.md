# step5. 고급 타입

[← README](../../README.md)

기본 스칼라 타입 외에 실무에서 자주 쓰는 타입입니다. 스키마는 `order_detail.proto`의 `OrderDetail`이고, 주제별 문서와 테스트로 나눠 다룹니다.

```protobuf
message OrderDetail {
  int64 order_id = 1;
  oneof payment_method {                           // oneof
    CardPayment card = 3;
    BankTransferPayment bank_transfer = 4;
  }
}
```

| 주제 | 문서 | 테스트 |
|---|---|---|
| oneof: 여러 필드 중 하나만 | [step5-oneof.md](step5-oneof.md) | `OneofTest` |
