package kr.antimony.blog.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CsrfApiController {

    // CSRF 토큰을 발급한다. 응답과 함께 XSRF-TOKEN 쿠키가 설정된다.
    // 화면의 JavaScript는 POST, PUT, DELETE 요청에 이 쿠키 값을 X-XSRF-TOKEN 헤더로 담아 보낸다.
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName());
    }
}
