package kr.antimony.accounts.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddUserRequest {
    private String uid;
    private String email;
    private String nickname;
    private String password;
}