package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * RiskAssessment.java
 *
 * <p>Output of the intelligence layer for one verification event: the
 * unsupervised anomaly score, the composite 0-100 risk score, the human-readable
 * flags that explain the score, and the advisory recommendation.</p>
 *
 * <p>A provider accumulates one assessment per verification run, which gives the
 * time-based history described in the Milestone 2 data model.</p>
 */
@Entity
@Table(name = "risk_assessments", indexes = {
        @Index(name = "idx_risk_provider", columnList = "provider_id")
})
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id")
    private VerificationRecord verificationRecord;

    /** Composite risk score on a 0 (lowest risk) to 100 (highest risk) scale. */
    private double score;

    /** Isolation Forest anomaly score in the range 0.0-1.0. */
    private double anomalyScore;

    /** Model confidence, derived from how far the score sits from the threshold. */
    private double confidence;

    /** True when the anomaly score exceeded the configured threshold. */
    private boolean anomalous;

    /** Pipe-separated explanation strings shown in the Risk Analysis screen. */
    @Column(length = 1000)
    private String flags;

    /** The feature vector that was scored, retained for explainability. */
    @Lob
    private String featureSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RiskRecommendation recommendation = RiskRecommendation.REVIEW;

    /** Identifies which trained model produced the score. */
    @Column(length = 60)
    private String modelVersion;

    /** Decision recorded by a human reviewer, if one has been made. */
    @Column(length = 40)
    private String reviewerDecision;

    @Column(length = 80)
    private String reviewedBy;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant assessedAt = Instant.now();

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public VerificationRecord getVerificationRecord() { return verificationRecord; }
    public void setVerificationRecord(VerificationRecord verificationRecord) { this.verificationRecord = verificationRecord; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isAnomalous() { return anomalous; }
    public void setAnomalous(boolean anomalous) { this.anomalous = anomalous; }
    public String getFlags() { return flags; }
    public void setFlags(String flags) { this.flags = flags; }
    public String getFeatureSnapshot() { return featureSnapshot; }
    public void setFeatureSnapshot(String featureSnapshot) { this.featureSnapshot = featureSnapshot; }
    public RiskRecommendation getRecommendation() { return recommendation; }
    public void setRecommendation(RiskRecommendation recommendation) { this.recommendation = recommendation; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getReviewerDecision() { return reviewerDecision; }
    public void setReviewerDecision(String reviewerDecision) { this.reviewerDecision = reviewerDecision; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }
}
