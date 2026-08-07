package com.example.protobuf.support;

import com.google.protobuf.Message;

import java.util.HexFormat;

/**
 * 테스트용 hex 출력 유틸. 바이트를 "08 96 01"처럼 공백으로 구분한 16진수 문자열로 바꾼다.
 */
public final class Hex {

    private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

    private Hex() {
    }

    /** 메시지를 직렬화한 바이트의 hex. 메시지 내용과 hex를 한 줄로 출력한다. */
    public static String of(Message message) {
        String hex = of(message.toByteArray());
        System.out.println(message.toString().trim().replaceAll("\\s+", " ") + " -> " + hex);
        return hex;
    }

    public static String of(byte[] bytes) {
        return HEX.formatHex(bytes);
    }
}
