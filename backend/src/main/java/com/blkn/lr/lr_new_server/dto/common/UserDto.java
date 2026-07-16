package com.blkn.lr.lr_new_server.dto.common;

import com.blkn.lr.lr_new_server.models.common.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {
    @NotBlank(message = "identity不能为空")
    String identity;

    @NotBlank(message = "password不能为空")
    String password;

    String uid;
    String token;

    /**
     * 公开注册只允许创建患者账号。医生账号必须通过受控的后台流程或种子数据创建。
     * 登录响应仍可正常返回 role=2；校验只发生在 @Valid 注册请求上。
     */
    @Min(value = 1, message = "公开注册仅支持患者角色1")
    @Max(value = 1, message = "公开注册仅支持患者角色1")
    int role;

    public UserDto(User user) {
        identity = user.getIdentity();
        uid = user.getId();
        role = user.getRole();
    }
}
