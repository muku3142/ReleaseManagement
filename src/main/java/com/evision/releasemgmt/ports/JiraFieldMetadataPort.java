package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.JiraField;

import java.util.List;

public interface JiraFieldMetadataPort {

    List<JiraField> fetchAllFields(JiraAccessContext access);
}
