package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.RiskAssessment;
import edu.gcu.capstone.credentialing.domain.RiskRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * RiskAssessmentRepository.java
 *
 * <p>Data-access interface for machine-learning risk output, including the
 * queries that drive the risk distribution chart and the review queue.</p>
 */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    Optional<RiskAssessment> findFirstByProviderIdOrderByAssessedAtDesc(Long providerId);

    List<RiskAssessment> findByProviderIdOrderByAssessedAtDesc(Long providerId);

    long countByRecommendation(RiskRecommendation recommendation);

    long countByAnomalousTrue();

    /** Assessments awaiting a reviewer decision, highest risk first. */
    List<RiskAssessment> findByReviewerDecisionIsNullOrderByScoreDesc();

    @Query("select avg(r.score) from RiskAssessment r")
    Double averageScore();
}
