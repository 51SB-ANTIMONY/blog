package kr.antimony.accounts.controller;

import jakarta.validation.Valid;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.dto.SignupResponse;
import kr.antimony.accounts.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class SignupApiController {
    private final UserService userService;

    @PostMapping("/api/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SignupResponse.from(userService.signup(request)));
    }
}
