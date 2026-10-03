package kr.antimony.accounts.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginPageTest {

    @Autowired
    MockMvc mockMvc;

    @DisplayName("로그인 화면: 소셜 로그인 오류 코드에 맞는 메시지를 보여 준다.")
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "email_already_registered | 이미 같은 이메일로 가입한 계정이 있습니다.",
            "linked_to_another_user   | 이 소셜 계정은 이미 다른 회원에 연결되어 있습니다.",
            "invalid_state_parameter  | 소셜 로그인에 실패했습니다."
    })
    void socialErrorMessage(String code, String message) throws Exception {
        mockMvc.perform(get("/login").param("social_error", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(message)));
    }
}
