package com.playprobie.api.global.util;

/**
 * HashId 인코더
 * - ID를 예측 불가능한 코드로 변환
 * - salt를 사용하여 같은 ID도 다르게 보이도록
 */
//TODO: 검증
public class HashIdEncoder {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String SALT = "HelloProbieFrom2025hehe";
    private static final int MIN_LENGTH = 6;

    public static String encode(long id) {
        // salt와 ID를 조합하여 해시값 생성
        long hash = id * 31 + SALT.hashCode();
        hash = Math.abs(hash);

        StringBuilder sb = new StringBuilder();

        // ID 자체도 섞어서 인코딩
        long mixed = (id * 2654435761L) ^ (hash);
        mixed = Math.abs(mixed);

        while (mixed > 0 || sb.length() < MIN_LENGTH) {
            int index = (int) (mixed % ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
            mixed /= ALPHABET.length();
            if (mixed == 0 && sb.length() < MIN_LENGTH) {
                mixed = (hash + sb.length()) % 1000000;
            }
        }

        return sb.toString();
    }

    public static long decode(String encoded) {
        // 역방향 디코딩 (필요시 구현)
        throw new UnsupportedOperationException("decode는 현재 지원하지 않습니다.");
    }
}
