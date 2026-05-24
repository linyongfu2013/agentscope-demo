package com.agentscope.demo.auth;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.reactor.context.SaReactorHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Mono<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return SaReactorHolder.sync(() -> {
            UserAccount user = userService.register(request.username(), request.password());
            StpUtil.login(user.id());
            return new AuthResponse(user.id(), StpUtil.getTokenValue());
        });
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return SaReactorHolder.sync(() -> {
            UserAccount user = userService.login(request.username(), request.password());
            StpUtil.login(user.id());
            return new AuthResponse(user.id(), StpUtil.getTokenValue());
        });
    }
}
