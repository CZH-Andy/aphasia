package com.blkn.lr.lr_new_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码登录请求体。密码只允许出现在 HTTPS JSON 请求体中，不通过 URL 或请求头传递。
 */
@Data
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "identity不能为空")
    @Size(max = 254, message = "identity长度不能超过254")
    private String identity;

    @NotBlank(message = "password不能为空")
    @Size(max = 128, message = "password长度不能超过128")
    private String password;
}
