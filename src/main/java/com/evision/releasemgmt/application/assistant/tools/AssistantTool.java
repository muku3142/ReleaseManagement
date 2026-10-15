package com.evision.releasemgmt.application.assistant.tools;

import java.util.Map;

public interface AssistantTool {

    /** lower_snake name, e.g. "releases". */
    String name();

    /** At most 300 characters; states what is returned and that the values are authoritative. */
    String description();

    /** Flat JSON schema: {"type":"object","properties":{...},"additionalProperties":false}. */
    Map<String, Object> parameters();

    /** @throws ToolArgumentException for arguments the model got wrong */
    ToolOutput call(Map<String, Object> arguments);
}
