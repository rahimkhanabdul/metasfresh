package de.metas.event.log;

import javax.annotation.Nullable;

import org.compiere.util.Env;

import de.metas.error.AdIssueId;
import de.metas.organization.ClientAndOrgId;
import de.metas.util.StringUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2020 metas GmbH
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
class EventLogEntryRequest
{
	boolean processed;
	boolean error;
	AdIssueId adIssueId;
	String message;
	Class<?> eventHandlerClass;

	ClientAndOrgId clientAndOrgId;

	@Builder(buildMethodName = "createAndStore")
	public EventLogEntryRequest(
			final boolean processed,
			final boolean error,
			final AdIssueId adIssueId,
			@Nullable final String message, Class<?> eventHandlerClass)
	{
		this.processed = processed;
		this.error = error;
		this.adIssueId = adIssueId;
		this.message = message;
		this.eventHandlerClass = eventHandlerClass;
		this.clientAndOrgId = Env.getClientAndOrgId();

		final EventLogEntryCollector eventLogCollector = EventLogEntryCollector.getThreadLocal();
		eventLogCollector.addEventLog(this);
	}

	public static class EventLogEntryRequestBuilder
	{
		public EventLogEntryRequest.EventLogEntryRequestBuilder formattedMessage(
				@NonNull final String message,
				@Nullable final Object... params)
		{
			message(StringUtils.formatMessage(message, params));
			return this;
		}
	}
}
