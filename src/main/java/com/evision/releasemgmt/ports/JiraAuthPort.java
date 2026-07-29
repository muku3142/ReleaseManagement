package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraIdentity;

public interface JiraAuthPort {

    JiraIdentity validate(String siteBaseUrl, String email, String apiToken);
}
