package dev.stravique.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLocaleSupportTest {

    private final RequestLocaleSupport support = new RequestLocaleSupport(new StraviqueProperties());

    @Test
    void explicitLanguageParameterWinsOverHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ja-JP");

        assertThat(support.resolve("zh-CN", request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void headerIsUsedWhenNoParameterIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ja-JP, en;q=0.8");

        assertThat(support.resolve(request)).isEqualTo(Locale.JAPAN);
    }

    @Test
    void configuredDefaultIsUsedWhenNothingMatches() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE");

        assertThat(support.resolve(request)).isEqualTo(Locale.US);
    }

    @Test
    void supportedLocaleIsSelectedFromHeaderQualityOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE, zh-CN;q=0.7, ja-JP;q=0.5");

        assertThat(support.resolve(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void regionalVariantNarrowsToSupportedLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(support.resolve("zh-TW", request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(support.resolve("ja", request)).isEqualTo(Locale.JAPAN);
        assertThat(support.resolve("en-GB", request)).isEqualTo(Locale.US);
    }

    @Test
    void underscoreTagIsAccepted() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(support.resolve("zh_CN", request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void unsupportedExplicitTagFallsBackToHeaderThenDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ja-JP");

        assertThat(support.resolve("de-DE", request)).isEqualTo(Locale.JAPAN);

        MockHttpServletRequest bare = new MockHttpServletRequest();
        assertThat(support.resolve("de-DE", bare)).isEqualTo(Locale.US);
    }

    @Test
    void blankParameterIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN");

        assertThat(support.resolve("   ", request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(support.resolve((String) null, request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void configuredDefaultLocaleDrivesFallback() {
        StraviqueProperties properties = new StraviqueProperties();
        properties.setDefaultLocale("ja-JP");
        RequestLocaleSupport japaneseDefault = new RequestLocaleSupport(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(japaneseDefault.resolve(request)).isEqualTo(Locale.JAPAN);
        assertThat(japaneseDefault.defaultLocale()).isEqualTo(Locale.JAPAN);
    }

    @Test
    void unsupportedConfiguredDefaultNarrowsToEnglish() {
        StraviqueProperties properties = new StraviqueProperties();
        properties.setDefaultLocale("de-DE");
        RequestLocaleSupport invalidDefault = new RequestLocaleSupport(properties);

        assertThat(invalidDefault.defaultLocale()).isEqualTo(Locale.US);
    }
}
