package com.evision.releasemgmt.domain.assistant;

import java.util.List;

public record AnsweredTurn(SignedTurn turn, List<ToolResult> results) {
}
