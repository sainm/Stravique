package dev.stravique.infrastructure;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisFindingRepository extends JpaRepository<AnalysisFinding, UUID>,
        JpaSpecificationExecutor<AnalysisFinding> {

    List<AnalysisFinding> findByScanIdOrderByFilePathAscLineStartAsc(UUID scanId);

    List<AnalysisFinding> findByScanIdAndRuleIdInOrderByFilePathAscLineStartAsc(
            UUID scanId, Collection<String> ruleIds);

    List<AnalysisFinding> findByScanIdAndFileIdOrderByLineStartAsc(UUID scanId, UUID fileId);

    List<AnalysisFinding> findByScanIdAndObjectKeyOrderByFilePathAscLineStartAsc(UUID scanId, String objectKey);

    List<AnalysisFinding> findByScanIdAndAnalysisKindOrderByFilePathAscLineStartAsc(UUID scanId,
                                                                                    AnalysisKind analysisKind);

    long countByScanIdAndCertaintyIn(UUID scanId, Collection<Certainty> certainties);

    List<AnalysisFinding> findByScanIdAndCertaintyInOrderByFilePathAscLineStartAsc(
            UUID scanId, Collection<Certainty> certainties);

    long countByScanIdAndAnalysisKind(UUID scanId, AnalysisKind analysisKind);

    long countByScanIdAndAnalysisKindInAndObjectKeyIn(UUID scanId,
                                                      Collection<AnalysisKind> kinds,
                                                      Collection<String> objectKeys);

    @Query("select count(finding) from AnalysisFinding finding where finding.scanId = :scanId "
            + "and (finding.certainty = :failed or finding.analysisKind = :parseError)")
    long countByCertaintyOrKind(@Param("scanId") UUID scanId,
                                @Param("failed") Certainty failed,
                                @Param("parseError") AnalysisKind parseError);

    @Query("select finding from AnalysisFinding finding where finding.scanId = :scanId "
            + "and (finding.certainty = :failed or finding.analysisKind = :parseError) "
            + "order by finding.filePath asc, finding.lineStart asc")
    List<AnalysisFinding> findByIdentityOrKindOrdered(@Param("scanId") UUID scanId,
                                                      @Param("failed") Certainty failed,
                                                      @Param("parseError") AnalysisKind parseError);
}
