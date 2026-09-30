package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {

    Optional<SourceDocument> findByChecksum(String checksum);

    @Query("""
            select d from SourceDocument d
            where (:topic is null or d.topic = :topic)
              and (:status is null or d.status = :status)
            order by d.createdAt desc
            """)
    List<SourceDocument> search(@Param("topic") Topic topic, @Param("status") DocumentStatus status);
}
