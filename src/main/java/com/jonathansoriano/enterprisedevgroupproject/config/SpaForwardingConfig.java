package com.jonathansoriano.enterprisedevgroupproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend is a single-page React app: the browser router owns these
 * paths, and only {@code index.html} exists on disk. Without this, refreshing
 * on /marketplace (or following a deep link into the app) would 404.
 *
 * Routes are listed explicitly rather than matched with a catch-all so that a
 * genuinely unknown path — or a typo in an API URL — still returns 404
 * instead of silently serving the app shell.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    private static final String[] SPA_ROUTES = {
            "/marketplace", "/messages", "/community", "/support", "/directory", "/profile"
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
        // Clerk's hosted sign-in/sign-up flows route through nested paths
        // (e.g. /sign-in/factor-one), so those need the subtree too.
        registry.addViewController("/sign-in").setViewName("forward:/index.html");
        registry.addViewController("/sign-in/**").setViewName("forward:/index.html");
        registry.addViewController("/sign-up").setViewName("forward:/index.html");
        registry.addViewController("/sign-up/**").setViewName("forward:/index.html");
        // Clerk session tasks (setup-mfa today) are hosted by the SPA via ClerkProvider's
        // taskUrls, so Clerk navigates the browser straight to this path.
        registry.addViewController("/session-tasks/**").setViewName("forward:/index.html");
    }
}
