package com.playprobie.api.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AWS GameLift Streams의 Stream Class 정의.
 * 
 * <p>
 * OS 호환성 규칙:
 * <ul>
 * <li>{@code _win2022} 접미사가 있는 ID는 WINDOWS 빌드 전용</li>
 * <li>접미사가 없는 ID는 LINUX 빌드 전용</li>
 * </ul>
 * 
 * @see <a href=
 *      "https://docs.aws.amazon.com/gamelift/latest/developerguide/stream-classes.html">AWS
 *      GameLift Stream Classes</a>
 */
@Getter
@RequiredArgsConstructor
public enum StreamClass {

    // ========== Gen6 - Windows ==========
    GEN6N_PRO_WIN2022("gen6n_pro_win2022", "WINDOWS", 16, 64, 24, "NVIDIA L4"),
    GEN6N_ULTRA_WIN2022("gen6n_ultra_win2022", "WINDOWS", 8, 32, 24, "NVIDIA L4"),

    // ========== Gen6 - Linux ==========
    GEN6N_PRO("gen6n_pro", "LINUX", 16, 64, 24, "NVIDIA L4"),
    GEN6N_ULTRA("gen6n_ultra", "LINUX", 8, 32, 24, "NVIDIA L4"),
    GEN6N_HIGH("gen6n_high", "LINUX", 4, 16, 12, "NVIDIA L4"),
    GEN6N_MEDIUM("gen6n_medium", "LINUX", 2, 8, 6, "NVIDIA L4"),
    GEN6N_SMALL("gen6n_small", "LINUX", 1, 4, 2, "NVIDIA L4"),

    // ========== Gen5 - Windows ==========
    GEN5N_WIN2022("gen5n_win2022", "WINDOWS", 8, 32, 24, "NVIDIA A10G"),

    // ========== Gen5 - Linux ==========
    GEN5N_ULTRA("gen5n_ultra", "LINUX", 8, 32, 24, "NVIDIA A10G"),
    GEN5N_HIGH("gen5n_high", "LINUX", 4, 16, 12, "NVIDIA A10G"),

    // ========== Gen4 - Windows ==========
    GEN4N_WIN2022("gen4n_win2022", "WINDOWS", 8, 32, 16, "NVIDIA T4"),

    // ========== Gen4 - Linux ==========
    GEN4N_ULTRA("gen4n_ultra", "LINUX", 8, 32, 16, "NVIDIA T4"),
    GEN4N_HIGH("gen4n_high", "LINUX", 4, 16, 8, "NVIDIA T4");

    private final String value;
    private final String osType;
    private final int vCpu;
    private final int ramGb;
    private final int vramGb;
    private final String gpu;

    /**
     * Stream Class ID 문자열로부터 Enum을 찾습니다.
     * 
     * @param value Steam Class ID (예: "gen4n_win2022")
     * @return 해당 StreamClass enum
     * @throws IllegalArgumentException 유효하지 않은 ID인 경우
     */
    public static StreamClass fromValue(String value) {
        for (StreamClass streamClass : values()) {
            if (streamClass.value.equals(value)) {
                return streamClass;
            }
        }
        throw new IllegalArgumentException("Unknown StreamClass: " + value);
    }

    /**
     * 해당 Stream Class가 특정 OS 타입과 호환되는지 검증합니다.
     * 
     * @param streamClassValue Stream Class ID
     * @param osType           OS 타입 (WINDOWS / LINUX)
     * @return 호환 여부
     */
    public static boolean isCompatible(String streamClassValue, String osType) {
        StreamClass streamClass = fromValue(streamClassValue);
        return streamClass.osType.equals(osType);
    }

    /**
     * Windows 전용 Stream Class인지 확인합니다.
     */
    public boolean isWindowsOnly() {
        return this.osType.equals("WINDOWS");
    }

    /**
     * Linux 전용 Stream Class인지 확인합니다.
     */
    public boolean isLinuxOnly() {
        return this.osType.equals("LINUX");
    }
}
