package com.testproject.calendar.booking;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class PublicApplicationOrigin {
    private final String value;

    public PublicApplicationOrigin(@Value("${calendar.public-origin}") String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            // Do not echo a possibly secret-bearing or newline-injected configuration value.
            throw invalid();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean loopback = host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("[::1]"));
        if ((!scheme.equals("https") && !(scheme.equals("http") && loopback))
                || host == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (!uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))
                || uri.getRawAuthority().endsWith(":")
                || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) {
            throw invalid();
        }
        this.value = scheme + "://" + uri.getRawAuthority();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("calendar.public-origin: expected an HTTPS origin with a valid host/port"
                + " and no credentials, path, query or fragment;"
                + " HTTP is allowed only for localhost, 127.0.0.1 or [::1]");
    }

    public String resultUrl(String token) {
        return value + "/bookings/" + token;
    }
}
