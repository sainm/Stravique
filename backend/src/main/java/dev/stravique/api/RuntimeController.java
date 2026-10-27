package dev.stravique.api;

import dev.stravique.api.dto.RuntimeDto;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.inventory.ScanPathPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {

    private final ScanPathPolicy pathPolicy;
    private final StraviqueProperties properties;

    public RuntimeController(ScanPathPolicy pathPolicy, StraviqueProperties properties) {
        this.pathPolicy = pathPolicy;
        this.properties = properties;
    }

    @GetMapping
    public RuntimeDto runtime() {
        return new RuntimeDto(
                pathPolicy.allowedRoots(),
                properties.getScan().getMaxFileBytes(),
                properties.getScan().getMaxFiles(),
                properties.getAnalyzerVersion(),
                properties.getDefaultLocale(),
                true
        );
    }
}
