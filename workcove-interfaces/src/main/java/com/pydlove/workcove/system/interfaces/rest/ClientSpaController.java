package com.pydlove.workcove.system.interfaces.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("client")
public class ClientSpaController {

    @GetMapping({"/", "/login", "/dashboard", "/app"})
    public String index() {
        return "forward:/index.html";
    }
}
