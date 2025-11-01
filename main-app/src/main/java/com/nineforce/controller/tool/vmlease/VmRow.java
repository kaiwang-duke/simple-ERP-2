// src/main/java/com/nineforce/controller/tool/vmlease/VmRow.java
package com.nineforce.controller.tool.vmlease;

import java.time.OffsetDateTime;

/** What the Thymeleaf table consumes – one row per VM. */
public record VmRow(
        String           name,
        String           zone,
        String           status,
        String           publicIP,
        String           leaseUser,   // last person who (re)started
        OffsetDateTime   startedAt,   // this run’s very first start time
        OffsetDateTime   expiresAt,
        long             remainingMin) { }
