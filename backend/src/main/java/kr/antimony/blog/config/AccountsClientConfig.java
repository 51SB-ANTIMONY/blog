package kr.antimony.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

import java.util.Map;

// blog를 accounts의 OIDC 클라이언트로 등록한다.
// application.yml의 issuer-uri를 쓰면 서버를 켤 때 accounts에서 설정 문서를 받아 와야 해서, accounts가 꺼져 있으면 blog도 켜지지 않는다.
// 그래서 accounts(Spring Authorization Server)의 기본 주소로 직접 등록하고, issuer는 ID 토큰의 iss 검사에 쓴다.
//
// 주소는 두 종류다.
// - issuer: 브라우저가 가는 주소(로그인, 로그아웃). 로컬에서는 accounts 화면(Next.js, 3001)
// - backchannel-uri: blog 서버가 직접 부르는 주소(토큰 교환, 서명 공개 키). 로컬에서는 accounts 서버(Spring, 9000)
//   Next.js를 거치면 Java HTTP 클라이언트가 보내는 HTTP/2 업그레이드 요청에 응답하지 않아 연결이 끊긴다.
@Configuration
public class AccountsClientConfig {

    public static final String REGISTRATION_ID = "accounts";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${accounts.issuer}") String issuer,
            @Value("${accounts.backchannel-uri:${accounts.issuer}}") String backchannelUri,
            @Value("${accounts.client-id}") String clientId,
            @Value("${accounts.client-secret}") String clientSecret) {
        ClientRegistration accounts = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("ANTIMONY")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // http://localhost:8080/login/oauth2/code/accounts (accounts에 등록한 주소와 같아야 한다)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
                .issuerUri(issuer)
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(backchannelUri + "/oauth2/token")
                .jwkSetUri(backchannelUri + "/oauth2/jwks")
                // 로그인한 사용자의 이름(Authentication.getName())은 ID 토큰의 sub(accounts 회원 id)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                // 로그아웃할 때 accounts 로그인도 끝내기 위한 주소
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/connect/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(accounts);
    }
}
