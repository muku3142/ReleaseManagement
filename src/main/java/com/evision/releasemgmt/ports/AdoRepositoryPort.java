package com.evision.releasemgmt.ports;

public interface AdoRepositoryPort {

    /** One cheap vso.code call proving token validity, Code (Read) scope and the organization name together. */
    void validate(String organization, String token);
}
