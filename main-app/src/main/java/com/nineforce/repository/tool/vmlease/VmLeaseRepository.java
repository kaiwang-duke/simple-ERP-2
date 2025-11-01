package com.nineforce.repository.tool.vmlease;

import com.nineforce.model.tool.vmlease.VmLease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface VmLeaseRepository extends JpaRepository<VmLease, String> {

    /** Leases that have expired at or before *now*. */
    @Query("select l from VmLease l where l.expiresAt <= :now")
    List<VmLease> findExpired(OffsetDateTime now);
}
