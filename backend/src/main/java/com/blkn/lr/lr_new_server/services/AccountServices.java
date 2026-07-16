package com.blkn.lr.lr_new_server.services;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.blkn.lr.lr_new_server.dao.impl.UserDaoImpl;
import com.blkn.lr.lr_new_server.dto.common.UserDto;
import com.blkn.lr.lr_new_server.dto.request.LoginRequest;
import com.blkn.lr.lr_new_server.exception.AuthException;
import com.blkn.lr.lr_new_server.exception.BusinessErrorException;
import com.blkn.lr.lr_new_server.models.common.User;
import com.blkn.lr.lr_new_server.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountServices {
    private static final int PATIENT_ROLE = 1;
    private static final String INVALID_CREDENTIALS_MESSAGE = "用户名或密码错误";

    private final UserDaoImpl userDao;
    private final TokenUtil tokenUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash = passwordEncoder.encode("invalid-login-password");

    public UserDto loginWithPassword(LoginRequest request) {
        if (request == null) {
            throw new BusinessErrorException("错误的登录请求");
        }

        User user = userDao.findByIdentity(request.getIdentity());
        if (user == null) {
            // 未知账号也执行一次 BCrypt 校验，降低通过响应耗时枚举账号的风险。
            passwordEncoder.matches(normalizePassword(request.getPassword()), dummyPasswordHash);
            throw new AuthException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!isPasswordValid(user, request.getPassword())) {
            throw new AuthException(INVALID_CREDENTIALS_MESSAGE);
        }

        return issueToken(user);
    }

    public UserDto loginWithToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("缺少Token");
        }

        DecodedJWT decodedJWT = tokenUtil.verifyToken(token);
        if (decodedJWT == null) {
            throw new AuthException("Token无效或已过期");
        }
        String uid = decodedJWT.getClaim("uid").asString();
        if (uid == null || uid.isBlank()) {
            throw new AuthException("Token无效或已过期");
        }
        User user = userDao.findById(uid);
        if (user == null) {
            throw new AuthException("Token无效或已过期");
        }

        return issueToken(user);
    }

    private UserDto issueToken(User user) {
        UserDto dto = new UserDto(user);
        dto.setToken(tokenUtil.getToken(user.getId(), user.getRole()));
        return dto;
    }

    public UserDto register(UserDto dto) {
        String password = dto.getPassword();

        User user = new User();
        user.setIdentity(dto.getIdentity());
        // 不信任客户端提交的角色。公开注册永远创建患者账号；
        // 医生账号由受控的管理员/数据初始化流程创建。
        user.setRole(PATIENT_ROLE);
        user.setPassword(passwordEncoder.encode(password));

        User created = userDao.register(user);
        UserDto dtoToReturn = new UserDto(created);
        dtoToReturn.setToken(tokenUtil.getToken(created.getId(), PATIENT_ROLE));
        return dtoToReturn;
    }

    private boolean isPasswordValid(User user, String rawPassword) {
        String encodedPassword = user.getPassword();
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private String normalizePassword(String rawPassword) {
        return rawPassword == null ? "" : rawPassword;
    }
}
