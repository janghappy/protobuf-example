# step5. map: 바이너리에서는 repeated 메시지

[← step5. 고급 타입](step5-advanced-types.md) · [← README](../../README.md)

스키마는 `order_detail.proto`의 `OrderDetail.attributes`(`map<string, string>`, 5번)이고, `MapTest`에서 확인합니다.

`map<string, string> attributes = 5;`은 protoc가 아래처럼 바꿔서 처리합니다.

```protobuf
message AttributesEntry {   // protoc가 자동 생성
  string key = 1;
  string value = 2;
}
repeated AttributesEntry attributes = 5;
```

`{"a": "b"}`의 바이트입니다.

```
2a 06 | 0a 01 61 | 12 01 62
│  │  │  key "a" │ value "b"
│  └ 길이 6
└ 태그 2a = 42 → 42 / 8 = 5 나머지 2 → 5번 필드, LEN
```

- 엔트리 하나가 중첩 메시지 하나입니다. 그래서 `repeated AttributesEntry`로 선언한 스키마와 바이트가 호환됩니다.
- 같은 키를 다시 `put`하면 덮어씁니다. 바이트에 같은 키가 여러 번 있으면 마지막 값이 이깁니다.
- 순서는 보장되지 않습니다. key는 정수·문자열·bool만 가능합니다 (`float`, `bytes`, 메시지 불가).

## key에 optional을 붙일 수 있나

**없습니다.** map 필드 자체, key, value 어디에도 `optional`을 붙일 수 없습니다. 모두 protoc 컴파일 오류입니다.

| 선언 | protoc 결과 |
|---|---|
| `optional map<string, string> m = 1;` | `Field labels (required/optional/repeated) are not allowed on map fields.` |
| `map<optional string, string> m = 1;` | `Expected ","` |
| `map<string, optional string> m = 1;` | `Expected ">"` |

대신 map은 **key 단위로 이미 "있음/없음"을 구분합니다.** key가 있느냐가 곧 has 정보입니다.

```java
detail.containsAttributes("coupon");               // key가 있는지 (optional의 hasXxx 역할)
detail.getAttributesOrDefault("coupon", "none");   // 없으면 지정한 기본값
detail.getAttributesOrThrow("coupon");             // 없으면 IllegalArgumentException
```

기본값을 넣어도 구분됩니다. Java 구현은 map 엔트리의 key와 value를 **기본값이어도 생략하지 않고 기록**합니다. 일반 필드가 기본값을 생략하는 것과 다릅니다.

| `put` | 바이트 | `containsAttributes` |
|---|---|---|
| `put("a", "")` | `2a 05 0a 01 61 12 00` (value `""`도 `12 00`으로 기록) | `"a"` → true (파싱 후에도 true) |
| `put("", "b")` | `2a 05 0a 00 12 01 62` (key `""`도 `0a 00`으로 기록) | `""` → true |

| | 일반 필드 | optional | map의 key |
|---|---|---|---|
| "없음" 판단 | 불가 (기본값과 같음) | `hasXxx()` | `containsXxx(key)` |
| 기본값을 넣었을 때 | 기록 안 됨 | 기록됨 | 엔트리 기록됨 |

value 쪽은 다릅니다. 어떤 key의 value가 "빈 문자열"인지 "값 없음"인지는 구분할 수 없습니다. 꼭 구분해야 하면 value를 메시지로 감싸는 방법(`map<string, SomeMessage>`)이 있지만, 보통은 key를 넣지 않는 것으로 "없음"을 표현합니다.

## set 타입은 없다

Protobuf의 컬렉션은 `repeated`와 `map`뿐입니다. set은 둘 중 하나로 표현합니다.

| | `repeated string tags` | `map<string, bool> tags` (key만 사용) |
|---|---|---|
| 중복 방지 | 애플리케이션 책임 (`new LinkedHashSet<>(getTagsList())`) | 스키마로 보장 (같은 key는 덮어씀) |
| 순서 | 유지 | 보장 안 됨 |
| 원소 타입 | 모든 타입 | 정수, 문자열, bool |
| JSON | `["a", "b"]` | `{"a": true, "b": true}` |

보통은 `repeated`를 쓰고, 중복이 없어야 한다는 것이 계약상 중요할 때만 `map`을 씁니다.
