package dev.stravique.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/projects", "/projects/**"})
    public String projectsRoute() {
        return "forward:/index.html";
    }
}
