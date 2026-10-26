package com.evision.releasemgmt.adapters.render;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Opens every produced PDF with PDFBox and asserts on its extracted text/page count/image
// XObjects, never merely on "bytes came back non-empty" - a blank document would also pass that.
@WireMockTest(httpsEnabled = true)
class PdfExportAdapterTest {

    private final PdfExportAdapter adapter = new PdfExportAdapter(new MustacheFlexmarkRenderAdapter());

    @Test
    void producesAValidPdf() throws IOException {
        byte[] pdf = adapter.toPdf("Hello world", ChromeFields.empty(), null);
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void coverPageIsItsOwnPageAndBodyFollows() throws IOException {
        ChromeFields chrome = chrome(true, "Release 4.2.0", "Release Notes", "Orchstro Header", "", false);
        String body = "## First\n\n## Second\n\n## Third\n";
        byte[] pdf = adapter.toPdf(body, chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            String page1 = textOfPage(document, 1);
            assertThat(page1).contains("Release 4.2.0");
            assertThat(page1).doesNotContain("Orchstro Header");
            String page2 = textOfPage(document, 2);
            assertThat(page2).contains("Orchstro Header");
        }
    }

    @Test
    void pageNumberingRendersRealNumbers() throws IOException {
        ChromeFields chrome = chrome(true, "Release 4.2.0", "", "", "", true);
        byte[] pdf = adapter.toPdf("Short body.", chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = textOf(document);
            assertThat(text).contains("Page 2 of ");
            // openhtmltopdf must resolve the CSS counters - a literal, unresolved token is a failure.
            assertThat(text).doesNotContain("counter(page)");
        }
    }

    @Test
    void pageNumberingAbsentWhenDisabled() throws IOException {
        ChromeFields chrome = chrome(false, "", "", "", "", false);
        byte[] pdf = adapter.toPdf("Short body.", chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = textOf(document);
            assertThat(text).doesNotContainPattern("Page \\d+ of \\d+");
        }
    }

    @Test
    void noCoverMeansPageOneKeepsItsChrome() throws IOException {
        ChromeFields chrome = chrome(false, "", "", "Orchstro Header", "", false);
        byte[] pdf = adapter.toPdf("Short body.", chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String page1 = textOfPage(document, 1);
            assertThat(page1).contains("Orchstro Header");
        }
    }

    @Test
    void markdownTableRendersAsATable() throws IOException {
        String body = """
                | Service | Owner |
                |---|---|
                | Archival Management API | Platform Team |
                """;
        byte[] pdf = adapter.toPdf(body, ChromeFields.empty(), null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = textOf(document);
            assertThat(text).contains("Service");
            assertThat(text).contains("Owner");
            assertThat(text).contains("Archival Management API");
            assertThat(text).contains("Platform Team");
        }
    }

    @Test
    void headingsAndListsSurvive() throws IOException {
        String body = """
                # Release Notes

                - First item
                - Second item
                - Third item
                """;
        byte[] pdf = adapter.toPdf(body, ChromeFields.empty(), null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = textOf(document);
            assertThat(text).contains("Release Notes");
            assertThat(text).contains("First item");
            assertThat(text).contains("Second item");
            assertThat(text).contains("Third item");
        }
    }

    @Test
    void htmlInChromeTextIsEscapedNotRendered() throws IOException {
        ChromeFields chrome = chrome(true, "<b>x</b>", "", "", "", false);
        byte[] pdf = adapter.toPdf("Body.", chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String page1 = textOfPage(document, 1);
            assertThat(page1).contains("<b>x</b>");
        }
    }

    @Test
    void quoteInFooterTextDoesNotBreakTheStylesheet() throws IOException {
        ChromeFields chrome = chrome(false, "", "", "", "He said \"go\"", true);
        byte[] pdf = adapter.toPdf("Short body.", chrome, null);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = textOf(document);
            assertThat(text).contains("He said \"go\"");
            assertThat(text).contains("Page 1 of ");
        }
    }

    @Test
    void logoIsEmbeddedWhenPresentAndAbsentWhenNull() throws IOException {
        ChromeFields chrome = chrome(true, "Release 4.2.0", "", "", "", false);
        LogoImage logo = new LogoImage(onePixelPng(), new LogoMetadata("image/png", "logo.png", 100, 1, 1,
                Instant.now()));

        byte[] withLogo = adapter.toPdf("Body.", chrome, logo);
        try (PDDocument document = PDDocument.load(withLogo)) {
            assertThat(countImageXObjects(document)).isGreaterThanOrEqualTo(1);
        }

        byte[] withoutLogo = adapter.toPdf("Body.", chrome, null);
        try (PDDocument document = PDDocument.load(withoutLogo)) {
            assertThat(countImageXObjects(document)).isZero();
        }
    }

    @Test
    void emptyMarkdownStillProducesAOnePagePdf() {
        assertThatCode(() -> {
            byte[] pdf = adapter.toPdf("", ChromeFields.empty(), null);
            try (PDDocument document = PDDocument.load(pdf)) {
                assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void remoteImageOverHttpIsNeverFetched(WireMockRuntimeInfo wm) throws IOException {
        assertRemoteImageNotFetched("http://localhost:" + wm.getHttpPort() + "/img.png");
    }

    @Test
    void remoteImageOverHttpsIsNeverFetched(WireMockRuntimeInfo wm) throws IOException {
        assertRemoteImageNotFetched("https://localhost:" + wm.getHttpsPort() + "/img.png");
    }

    @Test
    void remoteImageIsRefusedButCoverLogoDataUriStillRenders() throws IOException {
        ChromeFields chrome = chrome(true, "Release 4.2.0", "", "", "", false);
        LogoImage logo = new LogoImage(onePixelPng(), new LogoMetadata("image/png", "logo.png", 100, 1, 1,
                Instant.now()));
        byte[] pdf = adapter.toPdf("![x](http://localhost:1/img.png)\n", chrome, logo);
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(countImageXObjects(document)).isEqualTo(1);
        }
    }

    private void assertRemoteImageNotFetched(String imageUrl) throws IOException {
        stubFor(get(urlEqualTo("/img.png")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "image/png").withBody(onePixelPng())));
        byte[] pdf = adapter.toPdf("# T\n\n![x](" + imageUrl + ")\n", ChromeFields.empty(), null);
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
        verify(0, getRequestedFor(urlEqualTo("/img.png")));
    }

    private static ChromeFields chrome(boolean coverEnabled, String coverTitle, String coverSubtitle,
                                        String headerText, String footerText, boolean pageNumbersEnabled) {
        return new ChromeFields(coverEnabled, coverTitle, coverSubtitle, headerText, footerText,
                pageNumbersEnabled, ChromeFields.DEFAULT_LOGO_RENDER_HEIGHT_MM);
    }

    private static byte[] onePixelPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String textOf(PDDocument document) throws IOException {
        return new PDFTextStripper().getText(document);
    }

    private static String textOfPage(PDDocument document, int pageNumberOneBased) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumberOneBased);
        stripper.setEndPage(pageNumberOneBased);
        return stripper.getText(document);
    }

    private static int countImageXObjects(PDDocument document) throws IOException {
        int count = 0;
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    count++;
                }
            }
        }
        return count;
    }
}
