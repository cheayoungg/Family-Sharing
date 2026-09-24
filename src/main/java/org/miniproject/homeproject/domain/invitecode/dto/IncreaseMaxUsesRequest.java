package org.miniproject.homeproject.domain.invitecode.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IncreaseMaxUsesRequest(
		@NotNull @Min(1) Integer additionalSlots
) {
}
