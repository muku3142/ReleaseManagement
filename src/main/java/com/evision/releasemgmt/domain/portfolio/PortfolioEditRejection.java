package com.evision.releasemgmt.domain.portfolio;

public enum PortfolioEditRejection {
    PRODUCT_NAME_DUPLICATE,
    CLIENT_NAME_DUPLICATE,
    PRODUCT_ARCHIVED,
    CLIENT_ARCHIVED,
    CLIENT_LINK_HAS_INSTALLS,
    PRODUCT_HAS_INSTALLS_ON_RELEASE,
    // D-15-18: create-release with no product
    RELEASE_PRODUCT_REQUIRED,
    // D-15-18: a product change would leave the release with none
    RELEASE_LAST_PRODUCT,
    // D-17.1-20: a product Jira project key that isn't letters/digits/underscore starting with a letter
    PRODUCT_JIRA_KEY_INVALID
}
