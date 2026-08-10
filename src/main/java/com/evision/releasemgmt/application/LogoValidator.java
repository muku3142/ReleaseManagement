package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.LogoRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;

import static com.evision.releasemgmt.domain.template.LogoRejectionReason.DIMENSIONS_EXCEEDED;
import static com.evision.releasemgmt.domain.template.LogoRejectionReason.INVALID_TYPE;
import static com.evision.releasemgmt.domain.template.LogoRejectionReason.TOO_LARGE;

// TMPL-09 / REQUIREMENTS.md:234: an uploaded logo is the same class of untrusted input as a
// template body - validated by content, bounded in bytes and pixel dimensions, before any of it
// is trusted or stored.
@Service
public class LogoValidator {

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final long maxBytes;
    private final int maxWidth;
    private final int maxHeight;

    public LogoValidator(@Value("${app.template.logo.max-bytes}") long maxBytes,
                          @Value("${app.template.logo.max-width}") int maxWidth,
                          @Value("${app.template.logo.max-height}") int maxHeight) {
        this.maxBytes = maxBytes;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public LogoImage validate(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new LogoRejectedException(INVALID_TYPE);
        }
        // The size check comes before anything that touches the content, so an oversized file
        // costs nothing to reject.
        if (bytes.length > maxBytes) {
            throw new LogoRejectedException(TOO_LARGE);
        }

        // TMPL-09 / REQUIREMENTS.md:234: the type is decided by content, never by the filename or
        // the browser-supplied Content-Type. SVG is the case this exists for - it is XML, it can
        // carry script and external entity references, and openhtmltopdf would process it in
        // Phase 5. A file named logo.png whose first bytes are "<?xml" is refused here.
        String sniffedType;
        if (startsWith(bytes, PNG_SIGNATURE)) {
            sniffedType = "png";
        } else if (startsWith(bytes, JPEG_SIGNATURE)) {
            sniffedType = "jpeg";
        } else {
            throw new LogoRejectedException(INVALID_TYPE);
        }

        int width;
        int height;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new LogoRejectedException(INVALID_TYPE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                if (!sniffedType.equals(normalizeFormat(reader.getFormatName()))) {
                    throw new LogoRejectedException(INVALID_TYPE);
                }
                // getWidth/getHeight read the header only. ImageIO.read would allocate the full
                // raster, which is exactly the decompression-bomb the dimension cap exists to stop
                // - a small file declaring 30000x30000 pixels. Reject on the declared dimensions,
                // before any raster is allocated.
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new LogoRejectedException(INVALID_TYPE);
        }

        if (width > maxWidth || height > maxHeight) {
            throw new LogoRejectedException(DIMENSIONS_EXCEEDED);
        }

        String contentType = "png".equals(sniffedType) ? "image/png" : "image/jpeg";
        return new LogoImage(bytes, new LogoMetadata(contentType, safeFilename(filename),
                bytes.length, width, height, Instant.now()));
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeFormat(String formatName) {
        return formatName == null ? "" : formatName.toLowerCase(Locale.ROOT).replace("jpg", "jpeg");
    }

    // The filename is only ever echoed back to the SPA as display text, but a path fragment has
    // no business being stored (T-04-28). It is also later interpolated verbatim into a
    // Content-Disposition header (TemplateController.downloadLogo, DocumentController.logo), so
    // quotes and control characters must be stripped here - the single point where this
    // attacker-controlled string enters the system - matching ExportFilename's allowlist pattern.
    private static String safeFilename(String filename) {
        if (filename == null) {
            return "logo";
        }
        String base = filename;
        int lastSlash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        base = base.replaceAll("[\"\\\\\\r\\n\\p{Cntrl}]", "_").trim();
        if (base.isBlank()) {
            return "logo";
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }
}
