package io.reflectoring.coderadar.graph.query.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GetMetricsForAllFilesInCommitRepository extends Neo4jRepository {
}
