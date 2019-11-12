package de.metas.rest_api.invoicecandidates.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.metas.rest_api.common.MetasfreshId;
import de.metas.util.lang.ExternalId;
import lombok.Builder;
import lombok.Value;

/*
 * #%L
 * de.metas.business.rest-api
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@Value
@Builder
public class JsonCheckInvoiceCandidatesStatusResponseItem
{
	ExternalId externalHeaderId;
	ExternalId externalLineId;

	MetasfreshId metasfreshId;

	@Nullable
	BigDecimal qtyEntered;

	@Nullable
	BigDecimal qtyToInvoice;

	@Nullable
	BigDecimal qtyInvoiced;

	@Nullable
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	LocalDate dateInvoiced;

	@Nullable
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	LocalDate dateToInvoice;

	@Nullable
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	List<JsonInvoiceStatus> invoices;

	@Nullable
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	List<JsonWorkPackageStatus> workPackages;

}