package org.pyt.traveladvisor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdvisoryWithAuditDto {

    private AdvisoryResponseDto advisory;
    private AuditType audit;

}

