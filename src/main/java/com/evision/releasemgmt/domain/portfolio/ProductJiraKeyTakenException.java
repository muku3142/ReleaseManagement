package com.evision.releasemgmt.domain.portfolio;

/** key and productName are null when the clash was only detected by the unique index (a race). */
public class ProductJiraKeyTakenException extends RuntimeException {

    private final String key;
    private final String productName;

    public ProductJiraKeyTakenException(String key, String productName) {
        this.key = key;
        this.productName = productName;
    }

    public String key() {
        return key;
    }

    public String productName() {
        return productName;
    }
}
