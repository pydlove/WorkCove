package com.pydlove.workcove.system.interfaces.rest;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("client")
public class ClientSpaController {

    @GetMapping({"/", "/login", "/dashboard", "/app"})
    public String index(HttpServletResponse response) {
        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        return "forward:/index.html";
    }
}
