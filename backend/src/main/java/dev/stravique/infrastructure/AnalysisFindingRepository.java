package dev.stravique.infrastructure;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisFindingRepository extends JpaRepository<AnalysisFinding, UUID>,
        JpaSpecificationExecutor<AnalysisFinding> {

    List<AnalysisFinding> findByScanIdOrderByFilePathAscLineStartAsc(UUID scanId);

    List<AnalysisFinding> findByScanIdAndFileIdOrderByLineStartAsc(UUID scanId, UUID fileId);

    List<AnalysisFinding> findByScanIdAndObjectKeyOrderByFilePathAscLineStartAsc(UUID scanId, String objectKey);

    List<AnalysisFinding> findByScanIdAndAnalysisKindOrderByFilePathAscLineStartAsc(UUID scanId,
                                                                                    AnalysisKind analysisKind);

    long countByScanIdAndCertaintyIn(UUID scanId, Collection<Certainty> certainties);
}
