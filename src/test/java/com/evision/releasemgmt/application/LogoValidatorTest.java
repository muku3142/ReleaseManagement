package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoRejectedException;
import com.evision.releasemgmt.domain.template.LogoRejectionReason;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// TMPL-09 / REQUIREMENTS.md:234: proves the logo validator rejects by content, never by filename
// or declared Content-Type, and bounds both bytes and pixel dimensions before decoding.
class LogoValidatorTest {

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final LogoValidator validator = new LogoValidator(1024, 100, 100);

    @Test
    void validPngUnderCapsValidatesWithSniffedTypeAndDimensions() throws IOException {
        byte[] png = png(10, 10);

        LogoImage image = validator.validate(png, "logo.png");

        assertThat(image.metadata().contentType()).isEqualTo("image/png");
        assertThat(image.metadata().width()).isEqualTo(10);
        assertThat(image.metadata().height()).isEqualTo(10);
        assertThat(image.metadata().byteSize()).isEqualTo(png.length);
    }

    @Test
    void validJpegUnderCapsValidatesWithSniffedType() throws IOException {
        byte[] jpeg = jpeg(10, 10);

        LogoImage image = validator.validate(jpeg, "logo.jpg");

        assertThat(image.metadata().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void svgNamedPngIsRejectedAsInvalidTypeBecauseTypeIsDecidedByContent() {
        byte[] svg = "<?xml version=\"1.0\"?><svg><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(svg, "logo.png"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    @Test
    void gifIsRejectedAsInvalidType() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(gif, "logo.gif"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    @Test
    void pdfIsRejectedAsInvalidType() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(pdf, "logo.pdf"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    @Test
    void zipIsRejectedAsInvalidType() {
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};

        assertThatThrownBy(() -> validator.validate(zip, "logo.zip"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    @Test
    void emptyByteArrayIsRejectedAsInvalidType() {
        assertThatThrownBy(() -> validator.validate(new byte[0], "logo.png"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    @Test
    void oversizedFileIsRejectedAsTooLargeBeforeAnyDecodingIsAttempted() {
        // Not a real image at all - proves the byte-size check runs before content is inspected.
        byte[] oversized = new byte[2000];

        assertThatThrownBy(() -> validator.validate(oversized, "logo.png"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.TOO_LARGE));
    }

    @Test
    void declaredDimensionsAboveTheCapAreRejectedWithoutDecompressingTheImage() throws IOException {
        byte[] png = png(200, 10);

        assertThatThrownBy(() -> validator.validate(png, "logo.png"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.DIMENSIONS_EXCEEDED));
    }

    @Test
    void filenameQuotesAndControlCharactersAreStrippedBecauseTheyAreLaterInterpolatedIntoAContentDispositionHeader()
            throws IOException {
        byte[] png = png(10, 10);

        LogoImage image = validator.validate(png, "logo\".png\"; filename=\"evil\r\n.png");

        assertThat(image.metadata().filename()).doesNotContain("\"", "\r", "\n");
    }

    @Test
    void pngMagicBytesWithAnUnparseableStreamAreRejectedAsInvalidTypeNotARawIOException() {
        byte[] corrupted = new byte[PNG_SIGNATURE.length + 20];
        System.arraycopy(PNG_SIGNATURE, 0, corrupted, 0, PNG_SIGNATURE.length);
        // The remaining bytes are zero-filled garbage - no reader can parse an IHDR chunk from this.

        assertThatThrownBy(() -> validator.validate(corrupted, "logo.png"))
                .isInstanceOf(LogoRejectedException.class)
                .satisfies(ex -> assertThat(((LogoRejectedException) ex).reason())
                        .isEqualTo(LogoRejectionReason.INVALID_TYPE));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
