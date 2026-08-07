package com.example.protobuf.basics.step5_advancedtypes;

import com.example.protobuf.order.OrderDetail;
import com.example.protobuf.support.Hex;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * 5. 고급 타입 - map: 바이너리에서는 key/value를 가진 repeated 엔트리 메시지로 기록된다.
 * 스키마는 OrderDetail.attributes(map<string, string>, 5번). 설명은 docs/basics/step5-map.md
 */
class MapTest {

    @Test
    void map은_바이너리에서_key_value를_가진_repeated_메시지로_기록된다() {
        OrderDetail detail = OrderDetail.newBuilder().putAttributes("a", "b").build();

        // attributes(5번, LEN) -> 태그 2a, 길이 06, 안쪽은 엔트리 메시지 { key(1번) = "a", value(2번) = "b" }
        //   2a 06 | 0a 01 61 | 12 01 62
        //         |  key "a" | value "b"
        assertThat(Hex.of(detail)).isEqualTo("2a 06 0a 01 61 12 01 62");

        // 스키마 정보로도 확인: map 필드는 repeated이고, 타입은 protoc가 만든 AttributesEntry 메시지
        FieldDescriptor field = OrderDetail.getDescriptor().findFieldByName("attributes");
        assertThat(field.isRepeated()).isTrue();
        assertThat(field.isMapField()).isTrue();
        assertThat(field.getMessageType().getName()).isEqualTo("AttributesEntry");
        assertThat(field.getMessageType().getFields()).extracting(FieldDescriptor::getName).containsExactly("key", "value");
    }

    @Test
    void map은_같은_키를_다시_넣으면_덮어쓰고_없는_키는_기본값으로_조회한다() {
        OrderDetail detail = OrderDetail.newBuilder()
                .putAttributes("gift_wrap", "false")
                .putAttributes("gift_wrap", "true")
                .putAttributes("channel", "app")
                .build();

        assertThat(detail.getAttributesMap()).containsOnly(
                entry("gift_wrap", "true"),
                entry("channel", "app"));
        assertThat(detail.getAttributesOrDefault("coupon", "none")).isEqualTo("none");
        assertThat(detail.containsAttributes("coupon")).isFalse();
    }
}
