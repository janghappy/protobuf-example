package com.example.protobuf.rest;

import com.example.protobuf.order.Order;
import com.example.protobuf.order.OrderItem;
import com.example.protobuf.order.OrderStatus;
import com.example.protobuf.support.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 API(/orders)가 Accept, Content-Type 헤더에 따라 JSON과 Protobuf 바이너리를 둘 다 처리하는지 확인.
 * 컨트롤러는 Order만 다루고, 변환은 ProtobufHttpMessageConverter가 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

    @Autowired
    MockMvcTester mvc;

    @Test
    void Accept가_application_json이면_JsonFormat으로_만든_JSON으로_응답한다() throws Exception {
        long id = create(order(1));

        MvcTestResult result = mvc.get().uri("/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON) // JSON으로 달라고 요청
                .exchange();

        print(result);
        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.customerName").isEqualTo("kim");
        // JsonFormat은 proto3 JSON 규칙대로 int64를 문자열로 쓴다 (Jackson이면 숫자)
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(String.valueOf(id));
    }

    @Test
    void Accept가_application_x_protobuf면_바이너리로_응답하고_parseFrom으로_읽는다() throws Exception {
        long id = create(order(1));

        MvcTestResult result = mvc.get().uri("/orders/{id}", id)
                .accept(PROTOBUF) // 바이너리로 달라고 요청
                .exchange();

        print(result);
        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(PROTOBUF);
        Order received = Order.parseFrom(result.getResponse().getContentAsByteArray()); // JSON 변환기 없이 바로 객체
        assertThat(received.getCustomerName()).isEqualTo("kim");
        assertThat(received.getItemsCount()).isEqualTo(1);
    }

    @Test
    void Accept가_없으면_먼저_등록된_Protobuf_컨버터의_첫_타입인_바이너리로_응답한다() {
        long id = create(order(1));

        MvcTestResult result = mvc.get().uri("/orders/{id}", id)
                .exchange(); // Accept 헤더 없음 (*/*)

        print(result);
        // JSON을 원하는 클라이언트는 Accept: application/json을 반드시 보내야 한다
        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(PROTOBUF); // JSON이 아니라 바이너리
    }

    @Test
    void Content_Type이_application_x_protobuf인_요청_본문을_Order로_받는다() throws Exception {
        MvcTestResult result = mvc.post().uri("/orders")
                .contentType(PROTOBUF) // 보내는 본문은 바이너리 -> 서버에서 생성된 Order.Builder의 mergeFrom()이 파싱
                .content(order(2).toByteArray())
                .accept(PROTOBUF)
                .exchange();

        print(result);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Order created = Order.parseFrom(result.getResponse().getContentAsByteArray());
        assertThat(created.getId()).isPositive(); // 서버가 id를 채워서 돌려준다
        assertThat(created.getItemsCount()).isEqualTo(2);
    }

    @Test
    void Content_Type이_application_json인_요청_본문도_같은_API로_받는다() {
        String json = """
                {"customerName": "kim", "status": "ORDER_STATUS_PAID", "items": [{"productName": "product-1", "quantity": 1, "price": "1000"}]}
                """;

        MvcTestResult result = mvc.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON) // 보내는 본문은 JSON (같은 API) -> 서버에서 JsonFormat parser가 파싱
                .content(json)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();

        print(result);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.items[0].productName").isEqualTo("product-1");
    }

    // 상태, Content-Type, 본문 출력. 바이너리는 hex로, JSON은 문자열로
    private static void print(MvcTestResult result) {
        MockHttpServletResponse response = result.getResponse();
        String contentType = response.getContentType();
        String body = MediaType.parseMediaType(contentType).isCompatibleWith(PROTOBUF)
                ? Hex.of(response.getContentAsByteArray())
                : new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        System.out.printf("%d %s%n%s%n", response.getStatus(), contentType, body);
    }

    // 바이너리로 주문을 만들고 서버가 붙인 id를 돌려준다
    private long create(Order order) {
        MvcTestResult result = mvc.post().uri("/orders")
                .contentType(PROTOBUF).content(order.toByteArray())
                .accept(PROTOBUF).exchange();
        try {
            return Order.parseFrom(result.getResponse().getContentAsByteArray()).getId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // 주문 하나. i번째 상품은 이름 product-i, 수량 i, 가격 i * 1,000
    private static Order order(int itemCount) {
        Order.Builder builder = Order.newBuilder().setCustomerName("kim").setStatus(OrderStatus.ORDER_STATUS_PAID);
        for (int i = 1; i <= itemCount; i++) {
            builder.addItems(OrderItem.newBuilder().setProductName("product-" + i).setQuantity(i).setPrice(i * 1_000L));
        }
        return builder.build();
    }
}
