package dev.stravique.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TextDecoderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void decodesUtf8AndJapaneseLegacyText() throws Exception {
        TextDecoder decoder = new TextDecoder();
        Path utf8 = temporaryDirectory.resolve("utf8.properties");
        Files.writeString(utf8, "title=Orders\n");
        assertThat(decoder.decode(utf8).text()).contains("Orders");

        if (Charset.isSupported("Shift_JIS")) {
            Path japanese = temporaryDirectory.resolve("japanese.properties");
            Files.write(japanese, "title=注文\n".getBytes(Charset.forName("Shift_JIS")));
            TextDecoder.DecodedSource decoded = decoder.decode(japanese);
            assertThat(decoded.text()).contains("注文");
            assertThat(decoded.encoding()).isNotBlank();
        }
    }
}
