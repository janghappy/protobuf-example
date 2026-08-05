package com.example.protobuf.basics.step4_evolution;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4. 스키마 진화: v1(id, name, email) -> v2(id, display_name, phone, 3번 reserved)
 * 서버와 클라이언트가 서로 다른 버전의 스키마를 써도 데이터를 주고받을 수 있는지 확인한다.
 */
class SchemaEvolutionTest {

    @Test
    void 새_버전은_이전_버전_데이터를_읽을_수_있다() throws InvalidProtocolBufferException {
        byte[] v1Bytes = com.example.protobuf.user.v1.User.newBuilder()
                .setId(1L).setName("kim").setEmail("kim@example.com")
                .build().toByteArray();

        com.example.protobuf.user.v2.User v2 = com.example.protobuf.user.v2.User.parseFrom(v1Bytes);

        System.out.println("v2 = " + v2);

        assertThat(v2.getId()).isEqualTo(1L);
        assertThat(v2.getDisplayName()).isEqualTo("kim");  // 이름이 바뀌어도 번호(2)가 같아서 읽힌다
        assertThat(v2.getPhone()).isEmpty();               // v1에 없던 필드는 기본값
    }

    @Test
    void 이전_버전은_새_버전_데이터를_읽을_수_있다() throws InvalidProtocolBufferException {
        byte[] v2Bytes = com.example.protobuf.user.v2.User.newBuilder()
                .setId(1L).setDisplayName("kim").setPhone("010-1234-5678")
                .build().toByteArray();

        com.example.protobuf.user.v1.User v1 = com.example.protobuf.user.v1.User.parseFrom(v2Bytes);

        System.out.println("v1 = " + v1);

        assertThat(v1.getId()).isEqualTo(1L);
        assertThat(v1.getName()).isEqualTo("kim");
        assertThat(v1.getEmail()).isEmpty();               // v2가 보내지 않는 필드는 기본값

        // v1이 모르는 4번(phone)은 버리지 않고 unknown field로 보관한다 (타입 정보가 없어 바이트 그대로)
        String unknownPhone = v1.getUnknownFields().getField(4).getLengthDelimitedList().get(0).toStringUtf8();
        assertThat(unknownPhone).isEqualTo("010-1234-5678");
    }

    @Test
    void 이전_버전을_거쳐도_모르는_필드가_유실되지_않는다() throws InvalidProtocolBufferException {
        com.example.protobuf.user.v2.User original = com.example.protobuf.user.v2.User.newBuilder()
                .setId(1L).setDisplayName("kim").setPhone("010-1234-5678")
                .build();

        // v2 -> v1 서비스가 받아서 이름만 수정 후 다시 전달 -> v2
        com.example.protobuf.user.v1.User relayed = com.example.protobuf.user.v1.User.parseFrom(original.toByteArray())
                .toBuilder().setName("lee").build();
        com.example.protobuf.user.v2.User received = com.example.protobuf.user.v2.User.parseFrom(relayed.toByteArray());

        System.out.println("received = " + received);

        assertThat(received.getDisplayName()).isEqualTo("lee");
        assertThat(received.getPhone()).isEqualTo("010-1234-5678");  // v1이 몰라도 그대로 전달됨
    }
}
