package com.agentscope.demo.auth;

import com.agentscope.demo.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserAccount register(String username, String password) {
        String tenantId = TenantContext.currentTenantId();
        if (userRepository.findByTenantIdAndUsername(tenantId, username).isPresent()) {
            throw new IllegalArgumentException("username already exists in tenant");
        }
        return userRepository.insert(tenantId, username, hash(password));
    }

    public UserAccount login(String username, String password) {
        UserAccount user = userRepository.findByTenantIdAndUsername(TenantContext.currentTenantId(), username)
                .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
        if (user == null || !user.passwordHash().equals(hash(password))) {
            throw new IllegalArgumentException("invalid username or password");
        }
        return user;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
