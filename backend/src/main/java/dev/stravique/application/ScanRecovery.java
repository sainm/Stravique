package dev.stravique.application;

import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.ScanRunRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScanRecovery implements ApplicationRunner {

    private final ScanRunRepository scanRepository;

    public ScanRecovery(ScanRunRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (var scan : scanRepository.findByStatusIn(
                List.of(ScanStatus.QUEUED, ScanStatus.RUNNING, ScanStatus.CANCEL_REQUESTED))) {
            scan.fail("SCAN_INTERRUPTED", "The previous process stopped before this scan completed.");
            scanRepository.save(scan);
        }
    }
}
