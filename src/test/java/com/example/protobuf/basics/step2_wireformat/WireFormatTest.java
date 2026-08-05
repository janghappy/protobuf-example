package com.example.protobuf.basics.step2_wireformat;

import com.example.protobuf.order.Order;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2. 바이너리(wire format) 들여다보기.
 * 각 필드는 [태그 = (필드 번호 << 3) | wire type][값] 으로 기록된다.
 * wire type: 0 = varint(정수, enum), 2 = length-delimited(문자열, 메시지, repeated)
 */
class WireFormatTest {

    private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

    @Test
    void 정수는_varint로_작은_값일수록_적은_바이트를_쓴다() {
        // id = 1  -> 태그 08 (1 << 3 | 0), 값 01
        // 태그 08은 16진수(0x08)이고, "필드 1번, wire type 0(varint)"라는 뜻
        // 10진수로 바꾸고 8로 나눈다: 0x08 = 8 -> 8 / 8 = 1 나머지 0 -> 몫(1)이 필드 번호, 나머지(0)가 wire type
        assertThat(hex(Order.newBuilder().setId(1).build())).isEqualTo("08 01");

        // id = 150 -> 값이 7비트를 넘으므로 2바이트 varint: 96 01
        assertThat(hex(Order.newBuilder().setId(150).build())).isEqualTo("08 96 01");
    }

    @Test
    void 문자열은_태그_길이_내용_순으로_기록된다() {
        // customer_name(2번) = "kim" -> 태그 12 (2 << 3 | 2), 길이 03, "kim" = 6b 69 6d
        // 태그 12는 16진수(0x12)이고, "필드 2번, wire type 2(length-delimited)"라는 뜻
        // 10진수로 바꾸고 8로 나눈다: 0x12 = 18 -> 18 / 8 = 2 나머지 2 -> 몫(2)이 필드 번호, 나머지(2)가 wire type
        assertThat(hex(Order.newBuilder().setCustomerName("kim").build())).isEqualTo("12 03 6b 69 6d");
    }

    @Test
    void 필드_이름은_바이너리에_들어가지_않는다() {
        byte[] bytes = Order.newBuilder().setCustomerName("kim").build().toByteArray();

        assertThat(new String(bytes)).doesNotContain("customer");
    }

    @Test
    void 기본값인_필드는_아예_기록되지_않는다() {
        // id = 0, customer_name = "" 는 기본값이므로 0바이트
        Order order = Order.newBuilder().setId(0).setCustomerName("").build();

        assertThat(order.toByteArray()).isEmpty();
    }

    private static String hex(Order order) {
        String hex = HEX.formatHex(order.toByteArray());
        System.out.println(order.toString().trim() + " -> " + hex);
        return hex;
    }
}
