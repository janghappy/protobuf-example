package com.example.protobuf.basics.step1_serialization;

import com.example.protobuf.order.Order;
import com.example.protobuf.order.OrderItem;
import com.example.protobuf.order.OrderStatus;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1. 기본 사용법: 생성된 클래스로 메시지를 만들고, 바이트로 직렬화/역직렬화한다.
 */
class SerializationTest {

    @Test
    void 메시지를_바이트로_직렬화하고_다시_읽는다() throws InvalidProtocolBufferException {
        // 생성된 클래스는 불변(immutable)이고 Builder로 만든다
        Order order = Order.newBuilder()
                .setId(1L)
                .setCustomerName("kim")
                .setStatus(OrderStatus.ORDER_STATUS_PAID)
                .addItems(OrderItem.newBuilder().setProductName("keyboard").setQuantity(1).setPrice(50_000))
                .addItems(OrderItem.newBuilder().setProductName("mouse").setQuantity(2).setPrice(20_000))
                .build();

        byte[] bytes = order.toByteArray();     // 직렬화
        Order parsed = Order.parseFrom(bytes);  // 역직렬화

        System.out.println(parsed);
        assertThat(parsed).isEqualTo(order);
        assertThat(parsed.getItemsList()).extracting(OrderItem::getProductName).containsExactly("keyboard", "mouse");
    }

    @Test
    void 값을_넣지_않은_필드는_기본값을_돌려준다() throws InvalidProtocolBufferException {
        Order order = Order.newBuilder().setId(1L).build();

        // toString()은 기본값인 필드를 생략한다 -> "id: 1"만 출력
        System.out.println(order);
        System.out.println(JsonFormat.printer().print(order));
        // JSON은 옵션을 켜면 기본값인 필드도 출력한다
        System.out.println(JsonFormat.printer().alwaysPrintFieldsWithNoPresence().print(order));

        // proto3에는 null이 없다: 문자열은 "", 숫자는 0, enum은 0번, repeated는 빈 리스트
        assertThat(order.getCustomerName()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDER_STATUS_UNSPECIFIED);
        assertThat(order.getItemsList()).isEmpty();
    }

    @Test
    void 기존_메시지를_수정하려면_toBuilder로_복사한다() {
        Order created = Order.newBuilder().setId(1L).setStatus(OrderStatus.ORDER_STATUS_CREATED).build();

        Order paid = created.toBuilder().setStatus(OrderStatus.ORDER_STATUS_PAID).build();

        assertThat(created.getStatus()).isEqualTo(OrderStatus.ORDER_STATUS_CREATED);  // 원본은 그대로
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.ORDER_STATUS_PAID);
    }

    @Test
    void 메시지를_JSON으로_변환한다() throws InvalidProtocolBufferException {
        Order order = Order.newBuilder().setId(1L).setCustomerName("kim").setStatus(OrderStatus.ORDER_STATUS_PAID).build();

        // 필드 이름은 lowerCamelCase로, int64는 JSON 숫자 정밀도 문제 때문에 문자열로 나온다
        String json = JsonFormat.printer().print(order);

        System.out.println(json);
        assertThat(json).contains("\"customerName\": \"kim\"", "\"id\": \"1\"", "\"status\": \"ORDER_STATUS_PAID\"");
    }

    @Test
    void JSON을_메시지로_변환한다() throws InvalidProtocolBufferException {
        String json = """
                { "id": "1", "customerName": "kim", "status": "ORDER_STATUS_PAID" }
                """;

        // 메시지는 불변이라 Builder에 값을 채운 뒤 build한다
        Order.Builder builder = Order.newBuilder();   // 빈 Builder
        JsonFormat.parser().merge(json, builder);     // JSON의 값을 builder에 채움
        Order parsed = builder.build();               // 완성된 메시지

        assertThat(parsed).isEqualTo(
                Order.newBuilder().setId(1L).setCustomerName("kim").setStatus(OrderStatus.ORDER_STATUS_PAID).build());
    }
}
