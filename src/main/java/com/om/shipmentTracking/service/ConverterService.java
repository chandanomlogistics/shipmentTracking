package com.om.shipmentTracking.service;

import com.om.shipmentTracking.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConverterService {

    private final RestTemplate restTemplate;

    public String convertToPdfBase64(String url) {
        if (url == null || url.isBlank()) {
            log.warn("convertToPdfBase64: URL is null or blank");
            return null;
        }

        try {
            String cleanUrl = url.replace(" ", "%20");

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    cleanUrl, HttpMethod.GET, null, byte[].class
            );

            byte[] body = response.getBody();
            MediaType contentType = response.getHeaders().getContentType();

            if (body == null) {
                log.warn("Empty response body | URL={}", url);
                return null;
            }

            // Case 1: Direct PDF — encode as-is
            if (contentType != null && contentType.includes(MediaType.APPLICATION_PDF)) {
                log.info("Direct PDF | ContentType={} | URL={}", contentType, url);
                return Base64.getEncoder().encodeToString(body);
            }

            // Case 2: Direct image — wrap in PDF
            if (contentType != null && contentType.getType().equalsIgnoreCase("image")) {
                log.info("Direct image, wrapping in PDF | ContentType={} | URL={}", contentType, url);
                byte[] pdfBytes = imagesToPdf(List.of(body));
                return pdfBytes != null ? Base64.getEncoder().encodeToString(pdfBytes) : null;
            }

            // Case 3: HTML — extract all image URLs, fetch all, merge into PDF
            if (contentType != null && contentType.includes(MediaType.TEXT_HTML)) {
                String html = new String(body, StandardCharsets.UTF_8);
                log.info("HTML Response:\n{}", html);

                // Check if HTML contains a PDF link
                String pdfUrl = extractPdfHrefFromHtml(html);
                if (pdfUrl != null) {
                    log.info("Found PDF link in HTML | pdfUrl={}", pdfUrl);
                    ResponseEntity<byte[]> pdfResponse = restTemplate.exchange(
                            pdfUrl.replace(" ", "%20"), HttpMethod.GET, null, byte[].class
                    );
                    byte[] pdfBytes = pdfResponse.getBody();
                    return pdfBytes != null ? Base64.getEncoder().encodeToString(pdfBytes) : null;
                }

                // Extract all <img src> URLs
                List<String> imgUrls = extractAllImgSrcs(html);
                log.info("Found {} image(s) in HTML | URLs={}", imgUrls.size(), imgUrls);

                if (imgUrls.isEmpty()) {
                    log.warn("No images or PDF found in HTML | URL={}", url);
                    return null;
                }

                // Fetch all images
                List<byte[]> imageBytesList = new ArrayList<>();
                for (String imgUrl : imgUrls) {
                    try {
                        ResponseEntity<byte[]> imgResponse = restTemplate.exchange(
                                imgUrl.replace(" ", "%20"), HttpMethod.GET, null, byte[].class
                        );
                        byte[] imgBytes = imgResponse.getBody();
                        if (imgBytes != null) {
                            imageBytesList.add(imgBytes);
                            log.info("Fetched image | URL={} | size={}KB", imgUrl, imgBytes.length / 1024);
                        } else {
                            log.warn("Null image body | URL={}", imgUrl);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch image | URL={} | error={}", imgUrl, e.getMessage());
                    }
                }

                if (imageBytesList.isEmpty()) {
                    log.warn("All image fetches failed | URL={}", url);
                    return null;
                }

                // Merge all images into one PDF
                byte[] pdfBytes = imagesToPdf(imageBytesList);
                return pdfBytes != null ? Base64.getEncoder().encodeToString(pdfBytes) : null;
            }

            log.warn("Unexpected content type | ContentType={} | URL={}", contentType, url);
            return null;

        } catch (Exception e) {
            log.warn("convertToPdfBase64 failed | URL={} | error={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Merges multiple image byte arrays into a single PDF.
     * Each image gets its own page, sized to fit the image dimensions.
     */
    public byte[] imagesToPdf(List<byte[]> imageBytesList) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            for (byte[] imageBytes : imageBytesList) {
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, imageBytes, "image");

                float width = pdImage.getWidth();
                float height = pdImage.getHeight();

                PDPage page = new PDPage(new PDRectangle(width, height));
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 0, 0, width, height);
                }
            }

            document.save(outputStream);
            log.info("PDF created | pages={} | size={}KB",
                    document.getNumberOfPages(), outputStream.size() / 1024);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("imagesToPdf failed | error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts all <img src="..."> URLs from HTML.
     */
    private List<String> extractAllImgSrcs(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null || html.isBlank()) return urls;

        Pattern pattern = Pattern.compile("<img[^>]+src=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            urls.add(matcher.group(1).replace("&amp;", "&"));
        }
        return urls;
    }

    /**
     * Extracts <a href="...pdf..."> from HTML (for PDF download links).
     */
    private String extractPdfHrefFromHtml(String html) {
        if (html == null || html.isBlank()) return null;

        Pattern pattern = Pattern.compile("<a[^>]+href=['\"]([^'\"]+\\.pdf[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).replace("&amp;", "&") : null;
    }
}
