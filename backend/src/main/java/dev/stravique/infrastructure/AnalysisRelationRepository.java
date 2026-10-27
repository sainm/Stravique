package dev.stravique.infrastructure;

import dev.stravique.domain.AnalysisRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnalysisRelationRepository extends JpaRepository<AnalysisRelation, UUID> {

    @Query("""
            select relation
            from AnalysisRelation relation
            where relation.scanId = :scanId
              and (relation.sourceFileId = :fileId or relation.targetFileId = :fileId)
            order by relation.relationType, relation.evidencePath, relation.lineStart
            """)
    List<AnalysisRelation> findForFile(@Param("scanId") UUID scanId,
                                       @Param("fileId") UUID fileId);
}
