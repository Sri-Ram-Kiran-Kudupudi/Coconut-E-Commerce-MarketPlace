package com.coconut.coconut_marketplace.enums;

public enum ProductStatus {
    ACTIVE("Active"),
    OUT_OF_STOCK("Out of Stock"),
    DISABLED("Disabled");

    private final String displayName;

    ProductStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
