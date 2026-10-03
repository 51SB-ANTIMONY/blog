package kr.antimony.accounts.security;

// 로그인한 사용자를 나타내는 객체(Authentication의 principal)가 공통으로 가지는 정보.
// 아이디·비밀번호 로그인(AccountUserDetails)과 소셜 로그인(AccountOAuth2User) 모두 우리 회원 id를 알려 준다.
// 인가 서버는 이 id를 토큰의 sub로 쓴다.
public interface AccountPrincipal {
    Long getUserId();
}
