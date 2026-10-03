package kr.antimony.accounts.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
@RestController
public class AuthApiController {
    // 화면에 버튼이 있는 소셜 로그인 제공자 (프로토타입 순서)
    private static final List<String> SOCIAL_PROVIDERS = List.of("google", "kakao", "naver");

    private final InMemoryClientRegistrationRepository clientRegistrations;

    // CSRF 토큰을 발급한다. 응답과 함께 XSRF-TOKEN 쿠키가 설정된다.
    // 화면(Next.js)은 POST 요청 전에 이 값을 폼의 _csrf 또는 X-XSRF-TOKEN 헤더로 보낸다.
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName());
    }

    // 소셜 로그인 제공자별 사용 가능 여부. application.yml에 등록한 제공자만 true
    @GetMapping("/api/auth/providers")
    public Map<String, Boolean> providers() {
        List<String> registered = StreamSupport.stream(clientRegistrations.spliterator(), false)
                .map(ClientRegistration::getRegistrationId)
                .toList();
        Map<String, Boolean> result = new LinkedHashMap<>();
        SOCIAL_PROVIDERS.forEach(provider -> result.put(provider, registered.contains(provider)));
        return result;
    }
}
