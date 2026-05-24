package com.agentscope.demo.auth;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SaTokenJwtConfiguration {

    @Bean
    StpLogic stpLogicJwt() {
        return new StpLogicJwtForSimple();
    }
}
