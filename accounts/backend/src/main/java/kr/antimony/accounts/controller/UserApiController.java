package kr.antimony.accounts.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.exception.DuplicateUserException;
import kr.antimony.accounts.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@RequiredArgsConstructor
@Controller
public class UserApiController {
    private final UserService userService;

    // 임시 Thymeleaf 회원가입 폼. 화면을 Next.js로 바꾸면(1-7) JSON API(POST /api/signup)만 남긴다.
    @PostMapping("/user")
    public String signup(@Valid SignupRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "redirect:/signup?error";
        }
        try {
            userService.signup(request);
        } catch (DuplicateUserException e) {
            return "redirect:/signup?duplicate";
        }
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login";
    }
}