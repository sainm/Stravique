package dev.stravique.infrastructure;

import dev.stravique.domain.AnalysisRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnalysisRelationRepository extends JpaRepository<AnalysisRelation, UUID>,
        JpaSpecificationExecutor<AnalysisRelation> {

    List<AnalysisRelation> findByScanIdOrderByEvidencePathAscLineStartAsc(UUID scanId);

    @Query("""
            select relation
            from AnalysisRelation relation
            where relation.scanId = :scanId
              and (relation.sourceFileId = :fileId or relation.targetFileId = :fileId)
            order by relation.relationType, relation.evidencePath, relation.lineStart
            """)
    List<AnalysisRelation> findForFile(@Param("scanId") UUID scanId,
                                       @Param("fileId") UUID fileId);

    @Query("""
            select relation.relationType, count(relation)
            from AnalysisRelation relation
            where relation.scanId = :scanId
            group by relation.relationType
            """)
    List<Object[]> countByType(@Param("scanId") UUID scanId);

    @Query("""
            select relation.certainty, count(relation)
            from AnalysisRelation relation
            where relation.scanId = :scanId
            group by relation.certainty
            """)
    List<Object[]> countByCertainty(@Param("scanId") UUID scanId);

    @Query("""
            select relation.sourceFileId, relation.targetFileId
            from AnalysisRelation relation
            where relation.scanId = :scanId
            """)
    List<Object[]> findFileEndpoints(@Param("scanId") UUID scanId);
}
