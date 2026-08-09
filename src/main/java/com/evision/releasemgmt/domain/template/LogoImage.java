package com.evision.releasemgmt.domain.template;

// The bytes plus their metadata, used only on the upload path and the image-download endpoint.
public record LogoImage(byte[] bytes, LogoMetadata metadata) {
}
