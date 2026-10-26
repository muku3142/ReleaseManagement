package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.BriefingFallbackRequest;
import com.evision.releasemgmt.adapters.web.dto.BriefingSettingsRequest;
import com.evision.releasemgmt.application.briefing.BriefingRunNowService;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.TaskUpdate;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.Update;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.View;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

// Any signed-in user may change these (D-17.1-18); /api/** is authenticated by SecurityConfig.
@RestController
@RequestMapping("/api/settings/briefings")
public class BriefingSettingsController {

    private final BriefingSettingsService settings;
    private final BriefingRunNowService runNow;

    public BriefingSettingsController(BriefingSettingsService settings, BriefingRunNowService runNow) {
        this.settings = settings;
        this.runNow = runNow;
    }

    @GetMapping
    public View get() {
        return settings.view();
    }

    @PutMapping
    public View save(@Valid @RequestBody BriefingSettingsRequest request) {
        return settings.save(new Update(request.graceMinutes(), request.workDays(), request.keepRuns(),
                request.tasks().stream().map(t -> new TaskUpdate(t.id(), t.enabled(), t.days(), t.time(),
                        t.staleAfterWorkingDays(), t.dueSoonDays(), t.backlogItems())).toList()));
    }

    // No body: a user can only designate themselves (D-17.1-19).
    @PutMapping("/scheduler-user")
    public View designateMe() {
        return settings.designateMe();
    }

    // Off by default (D-17.1-24): the app runs nothing on its own schedule until someone turns this on.
    @PutMapping("/fallback")
    public View setFallback(@Valid @RequestBody BriefingFallbackRequest request, Principal principal) {
        settings.setFallbackEnabled(request.enabled(), principal.getName());
        return settings.view();
    }

    @PostMapping("/tasks/{taskId}/run")
    public BriefingRunNowService.Result run(@PathVariable String taskId) {
        return runNow.runNow(taskId);
    }
}
