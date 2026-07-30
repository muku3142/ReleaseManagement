package com.evision.releasemgmt.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardingController {
    // `assets` must be excluded as well as api/actuator: the [^.]* guard only constrains the
    // FIRST path segment, so "/assets/index-abc123.js" matched the second pattern (path =
    // "assets", /** = the filename) and every Vite bundle was served as index.html with
    // Content-Type text/html. Browsers then refuse the module script under strict MIME
    // checking and the SPA renders a blank page.
    @RequestMapping(value = {
            "/{path:^(?!api|actuator|assets)[^.]*}",
            "/{path:^(?!api|actuator|assets)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
