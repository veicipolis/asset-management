package com.veici.asset.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class AccountRefreshFilter extends OncePerRequestFilter {

    private final DatabaseUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(
                    authentication.getName());
        } catch (UsernameNotFoundException exception) {
            logout(request, response);
            return;
        }

        if (!user.isEnabled()
                || !user.isAccountNonLocked()
                || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            logout(request, response);
            return;
        }

        var refreshed = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        refreshed.setDetails(authentication.getDetails());
        refreshed.eraseCredentials();

        // 为本次请求创建独立上下文，避免修改其他并发请求的上下文。
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private void logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);

        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}