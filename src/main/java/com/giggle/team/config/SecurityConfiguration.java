package com.giggle.team.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    /*
     * more about spring security
     * https://mainul35.medium.com/spring-mvc-spring-security-in-memory-user-details-configuration-90d106b53d23
     */

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/","/auth").permitAll()
                .anyRequest().authenticated()
                .and().httpBasic()
                .and().csrf().disable()
                .formLogin().disable();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        // about password encoders
        // https://reflectoring.io/spring-security-password-handling/
        return new BCryptPasswordEncoder(10);
    }


    @Override
    public void configure(AuthenticationManagerBuilder builder) throws Exception {
        builder.inMemoryAuthentication()
                .withUser("admin")
                .password("$2y$10$LKR2johV8BDiMeA/cga2Yut3VU7ZmsPr3cOgBsJEnbFfKZTHeMUYq")
                .roles("ADMIN");
        builder.inMemoryAuthentication()
                .withUser("spring")
                .password("$2y$10$LKR2johV8BDiMeA/cga2Yut3VU7ZmsPr3cOgBsJEnbFfKZTHeMUYq")
                .roles("USER");
    }
}