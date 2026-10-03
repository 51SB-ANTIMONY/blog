package kr.antimony.accounts.dto;

import java.time.LocalDateTime;
import java.util.List;

// 내 계정 정보
public record MeResponse(
        Long id,
        String uid,
        String email,
        String nickname,
        boolean hasPassword,
        List<LinkedIdentity> identities
) {
    public record LinkedIdentity(String provider, LocalDateTime linkedAt) {
    }
}
