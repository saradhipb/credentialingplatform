package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.Provider;
import edu.gcu.capstone.credentialing.domain.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ProviderRepository.java
 *
 * <p>Data-access interface for {@link Provider}. Spring Data JPA generates the
 * implementation from the method names, so the persistence layer stays
 * declarative and no hand-written SQL is required for ordinary lookups.</p>
 */
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    /** Finds a provider by exact NPI, used to avoid creating duplicate records. */
    Optional<Provider> findByNpi(String npi);

    /** Counts providers in a given workflow state for the dashboard tiles. */
    long countByStatus(ProviderStatus status);

    /** Returns providers in a given state, newest first. */
    List<Provider> findByStatusOrderByCreatedAtDesc(ProviderStatus status);

    /** Returns every provider ordered by most recently created. */
    List<Provider> findAllByOrderByCreatedAtDesc();

    /**
     * Case-insensitive search across NPI, surname and organization name, used by
     * the dashboard search box.
     *
     * @param term search text already lower-cased by the caller
     * @return matching providers, newest first
     */
    @Query("select p from Provider p where "
            + "lower(coalesce(p.npi, '')) like concat('%', :term, '%') "
            + "or lower(coalesce(p.lastName, '')) like concat('%', :term, '%') "
            + "or lower(coalesce(p.firstName, '')) like concat('%', :term, '%') "
            + "or lower(coalesce(p.organizationName, '')) like concat('%', :term, '%') "
            + "order by p.createdAt desc")
    List<Provider> search(@Param("term") String term);
}
