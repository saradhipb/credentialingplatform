package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.ExclusionStatus;
import edu.gcu.capstone.credentialing.domain.NpiVerificationStatus;
import edu.gcu.capstone.credentialing.domain.VerificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * VerificationRecordRepository.java
 *
 * <p>Data-access interface for verification evidence, including the aggregate
 * queries that populate the dashboard KPI tiles.</p>
 */
public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {

    List<VerificationRecord> findByProviderIdOrderByVerifiedAtDesc(Long providerId);

    Optional<VerificationRecord> findFirstByProviderIdOrderByVerifiedAtDesc(Long providerId);

    long countByNpiStatus(NpiVerificationStatus status);

    long countByExclusionStatus(ExclusionStatus status);

    /** Mean external-verification round trip in milliseconds, or null if none yet. */
    @Query("select avg(v.durationMs) from VerificationRecord v")
    Double averageDurationMs();

    /** Most recent verifications across all providers, for the dashboard table. */
    List<VerificationRecord> findTop25ByOrderByVerifiedAtDesc();
}
