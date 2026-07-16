package com.blkn.lr.lr_new_server.services;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.blkn.lr.lr_new_server.dao.impl.UserDaoImpl;
import com.blkn.lr.lr_new_server.dto.common.UserDto;
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

    private final UserDaoImpl userDao;
    private final TokenUtil tokenUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserDto login(String token, String identity, String password) {
        User user = null;
        if (token != null) {
            DecodedJWT decodedJWT = tokenUtil.verifyToken(token);
            if (decodedJWT == null) {
                throw new BusinessErrorException("token过期");
            }
            String uid =  decodedJWT.getClaim("uid").asString();
            user = userDao.findById(uid);
            if (user == null) {
                throw new BusinessErrorException("无效的token");
            }
        }

        if (user == null) {
            if (identity != null) {
                user = userDao.findByIdentity(identity);
                if (user == null) {
                    throw new BusinessErrorException("用户不存在");
                }

                if (!isPasswordValid(user, password)) {
                    throw new BusinessErrorException("用户密码错误");
                }

            } else {
                throw new BusinessErrorException("错误的登录请求");
            }
        }

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
}
