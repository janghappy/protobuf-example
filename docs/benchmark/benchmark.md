# 성능 비교 (JMH): Jackson vs Protobuf vs JsonFormat

[← README](../../README.md)

step3에서는 크기만 비교했습니다. 여기서는 같은 주문을 직렬화/역직렬화하는 **속도(처리량)** 를 JMH로 측정합니다.

- 벤치마크: `src/jmh/java/com/example/protobuf/benchmark/SerializationBenchmark.java`
- 실행: `./gradlew jmh` (약 2분 30초). 결과는 `build/results/jmh/results.txt`에 저장됩니다.

## 무엇을 비교하나

| 방식 | 직렬화 | 역직렬화 |
|---|---|---|
| Jackson (JSON) | `objectMapper.writeValueAsBytes(dto)` | `objectMapper.readValue(bytes, OrderDto.class)` |
| Protobuf 바이너리 | `order.toByteArray()` | `Order.parseFrom(bytes)` |
| `JsonFormat` (Protobuf → JSON) | `printer.print(order).getBytes(UTF_8)` | `parser.merge(new String(bytes, UTF_8), builder)` |

- Jackson은 `Order`와 같은 필드를 가진 record(`OrderDto`)를 씁니다.
- 네트워크로 주고받는 상황을 가정해, 세 방식 모두 입출력을 `byte[]`로 맞췄습니다.
- `ObjectMapper`, `JsonFormat.Printer`/`Parser`는 미리 만들어 재사용합니다. 매번 만들면 생성 비용까지 측정됩니다.
- 설정: fork 1, warmup 1초 × 3회, 측정 1초 × 5회 (`build.gradle.kts`의 `jmh { }` 블록)

## 측정 데이터

`order.proto`의 `Order` 하나입니다. 주문 정보는 고정하고, 상품(`OrderItem`) 수만 `@Param`으로 **1개 / 10개 / 1,000개**로 바꿉니다. i번째 상품은 이름 `product-i`, 수량 `i`, 가격 `i × 1,000`입니다.

상품이 1개일 때를 Jackson JSON으로 보면 아래와 같습니다.

```json
{
  "id": 1,
  "customerName": "kim",
  "status": "ORDER_STATUS_PAID",
  "items": [
    { "productName": "product-1", "quantity": 1, "price": 1000 }
  ]
}
```

## 측정 흐름

`@Benchmark` 메서드 6개 × `@Param` 값 3개 = 18개 조합을 하나씩 잽니다. 조합마다 `setUp()`으로 데이터를 준비한 뒤, 1초 동안 메서드를 반복 호출한 횟수를 셉니다. 자세한 흐름은 `SerializationBenchmark`의 주석에 있습니다.

결과는 18개 조합이 모두 끝난 뒤 콘솔에 표로 출력되고 `build/results/jmh/results.txt`에도 저장됩니다.

- `build/`는 커밋되지 않으므로 아래 결과 표는 이 파일에서 옮겨 적었습니다.
- 코드와 설정이 그대로면 Gradle이 이전 결과를 재사용해 측정을 건너뜁니다(UP-TO-DATE). 다시 재려면 `./gradlew jmh --rerun`을 씁니다.
- `jmh { resultFormat = "JSON" }`으로 바꾸면 JMH Visualizer 같은 도구로 그래프를 볼 수 있습니다.

## 측정 환경

| 항목 | 값 |
|---|---|
| CPU | Intel Core Ultra 7 255U (14 스레드), 노트북 |
| OS | WSL2 (Linux 6.18) on Windows |
| 메모리 | 호스트 32GB 중 WSL2 할당 약 16GB (기본값: 호스트의 50%) |
| JDK | Temurin 21.0.11 |
| 라이브러리 | protobuf-java 4.31.1, jackson-databind 2.19.2, JMH 1.36 (`me.champeau.jmh` 0.7.3) |

## 결과 (ops/s, 클수록 빠름)

막대 길이는 상품 수별로 Protobuf를 100%로 놓은 상대값이고, `×`는 Jackson 대비 배수입니다. 상품 수가 다르면 처리량 단위가 수백 배 차이 나서, 상품 수끼리는 막대 길이를 비교하지 않습니다.

직렬화 (객체 → `byte[]`)

```
상품 1개
  Protobuf    ████████████████████████████████████████   31.49M  ×7.44
  Jackson     █████▍                                      4.23M  ×1.00
  JsonFormat  █▌                                          1.14M  ×0.27

상품 10개
  Protobuf    ████████████████████████████████████████    5.10M  ×4.75
  Jackson     ████████▍                                   1.07M  ×1.00
  JsonFormat  █▉                                         237.7K  ×0.22

상품 1,000개
  Protobuf    ████████████████████████████████████████    46.5K  ×3.89
  Jackson     ██████████▎                                 12.0K  ×1.00
  JsonFormat  ██                                           2.3K  ×0.20
```

| 상품 수 | Protobuf | Jackson | JsonFormat | Protobuf / Jackson |
|---:|---:|---:|---:|---:|
| 1 | 31,493,922 | 4,234,575 | 1,137,078 | 7.4배 |
| 10 | 5,100,955 | 1,073,020 | 237,705 | 4.8배 |
| 1,000 | 46,483 | 11,961 | 2,334 | 3.9배 |

역직렬화 (`byte[]` → 객체)

```
상품 1개
  Protobuf    ████████████████████████████████████████    8.54M  ×3.77
  Jackson     ██████████▋                                 2.26M  ×1.00
  JsonFormat  █▉                                         401.8K  ×0.18

상품 10개
  Protobuf    ████████████████████████████████████████    1.66M  ×3.07
  Jackson     █████████████                              540.1K  ×1.00
  JsonFormat  ██▌                                        102.0K  ×0.19

상품 1,000개
  Protobuf    ████████████████████████████████████████    21.3K  ×5.10
  Jackson     ███████▉                                     4.2K  ×1.00
  JsonFormat  ███▉                                         2.1K  ×0.49
```

| 상품 수 | Protobuf | Jackson | JsonFormat | Protobuf / Jackson |
|---:|---:|---:|---:|---:|
| 1 | 8,541,853 | 2,264,234 | 401,761 | 3.8배 |
| 10 | 1,659,434 | 540,088 | 101,972 | 3.1배 |
| 1,000 | 21,275 | 4,172 | 2,061 | 5.1배 |

## 결과 해석

> **측정값은 환경마다 다릅니다.**
>
> - CPU, JDK, JVM 옵션, 다른 프로세스 부하에 따라 수치가 크게 바뀝니다.
> - 이 측정은 노트북의 WSL2에서 해서 오차(`±`)가 큰 항목이 있습니다. (예: Protobuf 역직렬화 1개 `±6,047,480`)
> - 절대값이 아니라 **같은 환경에서의 상대 비교**로 보고, 자기 환경에서 `./gradlew jmh`로 직접 재 보는 것이 정확합니다.

**Protobuf 바이너리가 가장 빠릅니다.**

- Jackson보다 직렬화는 약 4~7배, 역직렬화는 약 3~5배 빨랐습니다.
- 필드 이름 문자열을 쓰거나 찾지 않고, 숫자를 텍스트로 바꾸지 않고 varint로 바로 쓰기 때문입니다. (step2, step3)

**직렬화가 역직렬화보다 빠릅니다.**

- Protobuf 역직렬화는 객체와 `String`을 새로 만들어야 해서 직렬화보다 2~4배 느립니다.
- Protobuf 객체가 직렬화 크기를 캐시해 두는 것도 직렬화 쪽에 유리합니다.

**`JsonFormat`은 가장 느립니다.**

- Jackson보다도 직렬화는 약 4~5배, 역직렬화는 약 2~6배 느렸습니다.
- descriptor(리플렉션 성격)로 필드를 하나씩 처리하고, 내부적으로 Gson을 거치기 때문입니다.
- 디버깅, 로그, REST 호환용으로 쓰고 성능이 중요한 경로에는 쓰지 않는 것이 좋습니다.
- 언제 Jackson이나 JsonFormat이 쓰이는지는 [서비스 간 통신과 JSON 변환기](../communication/service-communication.md)에 정리했습니다.

**상품 수가 늘면 처리량은 거의 비례해서 줄어듭니다.**

- 1개 → 1,000개에서 처리량이 수백 분의 1이 됩니다.
- 1개일 때는 고정 비용(객체 생성, 버퍼 할당)의 비중이 커서 정확히 비례하지는 않습니다.

**JSON 두 방식은 출력도 조금 다릅니다.**

- `JsonFormat`은 proto3 JSON 규칙대로 `int64`(`id`, `price`)를 `"1000"`처럼 **문자열**로 쓰고, Jackson은 `1000` 숫자로 씁니다.
- JavaScript의 number가 2^53까지만 정확하기 때문에 생긴 규칙입니다.

**정리하면**

- 서비스 간 통신처럼 처리량이 중요한 곳에는 Protobuf 바이너리가 크기(step3)와 속도 모두 유리합니다.
- JSON이 필요하면 Protobuf 메시지를 `JsonFormat`으로 바꾸는 것보다 Jackson이 훨씬 빠릅니다.
