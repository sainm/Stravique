package dev.stravique.api;

import dev.stravique.transformation.RuleDescriptor;
import dev.stravique.transformation.TransformationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transformation-rules")
public class TransformationRuleController {

    private final TransformationEngine engine;

    public TransformationRuleController(TransformationEngine engine) {
        this.engine = engine;
    }

    @GetMapping
    public List<RuleDescriptor> rules() {
        return engine.rules();
    }
}