package dev.stravique.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

@Component
public class RequestLocaleSupport {

    private static final List<Locale> SUPPORTED = List.of(
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("zh-CN"),
            Locale.forLanguageTag("ja-JP"));

    private static final Locale FALLBACK = SUPPORTED.getFirst();

    private final Locale defaultLocale;

    public RequestLocaleSupport(StraviqueProperties properties) {
        Locale configured = narrow(Locale.forLanguageTag(properties.getDefaultLocale()));
        this.defaultLocale = configured == null ? FALLBACK : configured;
    }

    public Locale resolve(HttpServletRequest request) {
        return resolve(request.getParameter("lang"), request);
    }

    public Locale resolve(String requestedTag, HttpServletRequest request) {
        Locale explicit = fromTag(requestedTag);
        if (explicit != null) {
            return explicit;
        }
        Locale accepted = fromHeader(request);
        return accepted == null ? defaultLocale : accepted;
    }

    public Locale defaultLocale() {
        return defaultLocale;
    }

    public List<Locale> supported() {
        return SUPPORTED;
    }

    private Locale fromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return narrow(Locale.forLanguageTag(tag.trim().replace('_', '-')));
    }

    private Locale fromHeader(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return null;
        }
        Enumeration<Locale> candidates = request.getLocales();
        while (candidates.hasMoreElements()) {
            Locale narrowed = narrow(candidates.nextElement());
            if (narrowed != null) {
                return narrowed;
            }
        }
        return null;
    }

    private static Locale narrow(Locale candidate) {
        if (candidate == null || candidate.getLanguage().isBlank()) {
            return null;
        }
        for (Locale supported : SUPPORTED) {
            if (supported.getLanguage().equalsIgnoreCase(candidate.getLanguage())) {
                return supported;
            }
        }
        return null;
    }
}
