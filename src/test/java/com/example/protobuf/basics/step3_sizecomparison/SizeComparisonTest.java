package com.example.protobuf.basics.step3_sizecomparison;

import com.example.protobuf.order.Order;
import com.example.protobuf.order.OrderItem;
import com.example.protobuf.order.OrderStatus;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3. 같은 데이터를 JSON과 Protobuf로 직렬화했을 때 크기 비교.
 */
class SizeComparisonTest {

    @Test
    void 같은_주문이라도_Protobuf가_JSON보다_작다() throws InvalidProtocolBufferException {
        Order.Builder builder = Order.newBuilder().setId(1L).setCustomerName("kim").setStatus(OrderStatus.ORDER_STATUS_PAID);
        for (int i = 1; i <= 10; i++) {
            builder.addItems(OrderItem.newBuilder().setProductName("product-" + i).setQuantity(i).setPrice(i * 1_000L));
        }
        Order order = builder.build();

        int jsonSize = JsonFormat.printer().omittingInsignificantWhitespace().print(order)
                .getBytes(StandardCharsets.UTF_8).length;
        int protobufSize = order.toByteArray().length;

        System.out.printf("JSON: %d bytes, Protobuf: %d bytes (%.0f%%)%n",
                jsonSize, protobufSize, protobufSize * 100.0 / jsonSize);
        // JSON은 매 항목마다 "productName", "quantity", "price" 키 문자열을 반복하지만
        // Protobuf는 1바이트 태그(필드 번호)만 쓴다
        assertThat(protobufSize).isLessThan(jsonSize / 2);
    }
}
