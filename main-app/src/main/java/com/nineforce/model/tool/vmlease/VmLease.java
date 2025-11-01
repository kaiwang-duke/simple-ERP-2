package com.nineforce.model.tool.vmlease;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vm_lease")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VmLease {

    /** The GCE / Cloud Run instance name – primary key. */
    @Id
    @Column(name = "vm_name", length = 100, nullable = false)
    private String vmName;

    @Column(name = "zone", length = 50, nullable = false)
    private String zone;

    /** E‑mail of the user who most recently (re)started / extended the lease. */
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /** When the VM was powered on for *this* session. */
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    /** When the lease expires (rounded to the next-quarter hour). */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}

