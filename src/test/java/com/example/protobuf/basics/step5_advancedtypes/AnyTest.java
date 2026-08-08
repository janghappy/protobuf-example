package com.example.protobuf.basics.step5_advancedtypes;

import com.example.protobuf.order.GiftOption;
import com.example.protobuf.order.OrderDetail;
import com.example.protobuf.order.OrderItem;
import com.example.protobuf.support.Hex;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 5. 고급 타입 - Any: 타입 URL + 직렬화된 바이트로 어떤 메시지든 담는다.
 * 스키마는 OrderDetail.extra(Any, 8번), GiftOption. 설명은 docs/basics/step5-any.md
 */
class AnyTest {

    @Test
    void Any는_타입_URL과_직렬화된_바이트를_담고_unpack으로_꺼낸다() throws InvalidProtocolBufferException {
        GiftOption gift = GiftOption.newBuilder().setMessage("happy birthday").build();

        Any any = Any.pack(gift);

        // 타입 URL = "type.googleapis.com/" + 패키지.메시지 이름 (.proto의 package 기준, Java 패키지 아님)
        assertThat(any.getTypeUrl()).isEqualTo("type.googleapis.com/example.order.GiftOption");
        assertThat(any.getValue().toByteArray()).isEqualTo(gift.toByteArray());

        // 1) 보내는 쪽: Any를 OrderDetail의 extra(8번) 필드에 넣는다
        OrderDetail sent = OrderDetail.newBuilder().setExtra(any).build();
        System.out.println("[1. 보내는 OrderDetail]\n" + sent);

        // 2) 전송: OrderDetail 전체를 바이트로 (GiftOption 바이트는 Any.value 안에 중첩되어 들어간다)
        byte[] bytes = sent.toByteArray();
        System.out.println("[2. 전송 바이트] " + Hex.of(bytes));

        // 3) 받는 쪽: 보낸 것이 OrderDetail이므로 OrderDetail로 파싱한다
        //    이 시점의 extra는 Any(type_url + 바이트)일 뿐, GiftOption으로는 아직 파싱되지 않았다
        OrderDetail received = OrderDetail.parseFrom(bytes);
        Any receivedExtra = received.getExtra();
        System.out.println("[3. 받은 extra] type_url = " + receivedExtra.getTypeUrl()
                + ", value = " + Hex.of(receivedExtra.getValue().toByteArray()));

        // 4) 타입을 확인하고 GiftOption으로 꺼낸다 (여기서 value 바이트가 GiftOption으로 파싱된다)
        assertThat(receivedExtra.is(GiftOption.class)).isTrue();
        assertThat(receivedExtra.is(OrderItem.class)).isFalse();
        GiftOption unpacked = receivedExtra.unpack(GiftOption.class);
        System.out.println("[4. unpack 결과] " + unpacked.toString().trim());

        assertThat(unpacked.getMessage()).isEqualTo("happy birthday");
    }

    @Test
    void Any를_다른_타입으로_unpack하면_예외가_발생한다() {
        Any any = Any.pack(GiftOption.newBuilder().setMessage("happy birthday").build());

        assertThatThrownBy(() -> any.unpack(OrderItem.class))
                .isInstanceOf(InvalidProtocolBufferException.class)
                .hasMessageContaining("Type of the Any message does not match");
    }

    @Test
    void Any를_JSON으로_바꾸려면_TypeRegistry에_타입을_등록해야_한다() throws InvalidProtocolBufferException {
        OrderDetail detail = OrderDetail.newBuilder()
                .setExtra(Any.pack(GiftOption.newBuilder().setMessage("happy birthday").build()))
                .build();

        // 바이트만으로는 필드 이름을 알 수 없어서, 타입을 모르면 JSON으로 바꿀 수 없다
        assertThatThrownBy(() -> JsonFormat.printer().print(detail))
                .isInstanceOf(InvalidProtocolBufferException.class);

        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder().add(GiftOption.getDescriptor()).build();
        String json = JsonFormat.printer().usingTypeRegistry(registry).omittingInsignificantWhitespace().print(detail);
        System.out.println(json);

        assertThat(json).isEqualTo(
                "{\"extra\":{\"@type\":\"type.googleapis.com/example.order.GiftOption\",\"message\":\"happy birthday\"}}");
    }
}
