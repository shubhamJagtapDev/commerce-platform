package com.commerce.identityaccess.edgegateway;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GatewayRouteValidator {

    private GatewayRouteValidator() {
    }

    public static void validate(List<GatewayRouteSpec> routes) {
        Set<String> ids = new HashSet<>();
        for (var route : routes) {
            requireComplete(route);
            if (!ids.add(route.id())) {
                throw new IllegalStateException("Duplicate gateway route id: " + route.id());
            }
            if (isCatchAll(route.path())) {
                throw new IllegalStateException("Catch-all gateway routes are forbidden: " + route.id());
            }
        }

        for (int left = 0; left < routes.size(); left++) {
            for (int right = left + 1; right < routes.size(); right++) {
                var first = routes.get(left);
                var second = routes.get(right);
                if (first.method().equalsIgnoreCase(second.method()) && pathsOverlap(first.path(), second.path())) {
                    throw new IllegalStateException(
                            "Ambiguous gateway routes: " + first.id() + " and " + second.id());
                }
            }
        }
    }

    private static void requireComplete(GatewayRouteSpec route) {
        if (route == null
                || isBlank(route.id())
                || isBlank(route.method())
                || isBlank(route.path())
                || route.target() == null
                || route.access() == null
                || route.deadline() == null
                || route.deadline().isNegative()
                || route.deadline().isZero()
                || route.maxRequestBytes() < 1) {
            throw new IllegalStateException("Every gateway route requires id, method, path, target, access, deadline, and size limit");
        }
        if (!route.path().startsWith("/") || route.target().getScheme() == null) {
            throw new IllegalStateException("Gateway route path and target URI are invalid: " + route.id());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isCatchAll(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.equals("/**")
                || normalized.endsWith("/**")
                || normalized.contains("{*")
                || normalized.contains("**");
    }

    private static boolean pathsOverlap(String first, String second) {
        String[] left = first.split("/");
        String[] right = second.split("/");
        if (left.length != right.length) {
            return false;
        }
        for (int index = 0; index < left.length; index++) {
            boolean variable = left[index].startsWith("{") || right[index].startsWith("{");
            if (!variable && !left[index].equals(right[index])) {
                return false;
            }
        }
        return true;
    }
}
