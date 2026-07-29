package com.evision.releasemgmt.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardingController {
    @RequestMapping(value = {"/{path:^(?!api|actuator)[^.]*}", "/{path:^(?!api|actuator)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
