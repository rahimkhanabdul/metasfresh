package de.metas.acct.posting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.ad.trx.api.OnTrxMissingPolicy;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.service.ISysConfigBL;
import org.adempiere.util.lang.IAutoCloseable;
import org.compiere.util.Env;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import de.metas.event.Event;
import de.metas.event.IEventBus;
import de.metas.event.IEventBusFactory;
import de.metas.event.IEventListener;
import de.metas.event.Topic;
import de.metas.event.log.EventLogUserService;
import de.metas.event.log.EventLogUserService.InvokeHandlerAndLogRequest;
import de.metas.event.remote.RabbitMQEventBusConfiguration;
import de.metas.logging.LogManager;
import de.metas.logging.TableRecordMDC;
import de.metas.util.JSONObjectMapper;
import de.metas.util.Services;
import lombok.NonNull;
import lombok.ToString;

/*
 * #%L
 * de.metas.acct.base
 * %%
 * Copyright (C) 2018 metas GmbH
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

@Service
public class DocumentPostingBusService implements IDocumentPostingBusService
{
	// services
	private static final Logger logger = LogManager.getLogger(DocumentPostingBusService.class);
	private static final JSONObjectMapper<DocumentPostRequest> jsonObjectMapper = JSONObjectMapper.forClass(DocumentPostRequest.class);
	private final ITrxManager trxManager = Services.get(ITrxManager.class);
	private final ISysConfigBL sysConfigBL = Services.get(ISysConfigBL.class);
	private final IEventBusFactory eventBusFactory;
	private final EventLogUserService eventLogUserService;
	private final ImmutableList<DocumentPostRequestHandler> handlers;

	private static final Topic TOPIC = RabbitMQEventBusConfiguration.AccountingQueueConfiguration.EVENTBUS_TOPIC;
	private static final String EVENT_PROPERTY_DocumentPostRequest = "DocumentPostRequest";
	/** Flag indicating that the whole accounting module is enabled or disabled */
	@VisibleForTesting
	public static final String SYSCONFIG_Enabled = "org.adempiere.acct.Enabled";

	public DocumentPostingBusService(
			@NonNull final IEventBusFactory eventBusFactory,
			@NonNull final EventLogUserService eventLogUserService,
			@NonNull final Optional<List<DocumentPostRequestHandler>> handlers)
	{
		this.eventBusFactory = eventBusFactory;
		this.eventLogUserService = eventLogUserService;
		this.handlers = handlers.map(ImmutableList::copyOf)
				.orElseGet(ImmutableList::of);
	}

	@PostConstruct
	private void postConstruct()
	{
		handlers.forEach(this::registerHandler);
	}

	private void registerHandler(@NonNull final DocumentPostRequestHandler handler)
	{
		final IEventBus eventBus = getEventBus();
		eventBus.subscribe(DocumentPostRequestHandlerAsEventListener.builder()
				.handler(handler)
				.eventLogUserService(eventLogUserService)
				.build());

		logger.info("Registered `{}` to `{}`", handler, eventBus);
	}

	/** @return true if the accounting module is active; false if the accounting module is COMPLETELLY off */
	public boolean isEnabled()
	{
		final boolean defaultValue = true; // enabled by default
		return sysConfigBL.getBooleanValue(SYSCONFIG_Enabled, defaultValue);
	}

	@Override
	public void postRequestAfterCommit(@NonNull final DocumentPostRequest request)
	{
		if (!isEnabled())
		{
			logger.debug("Skip posting {} because accounting is not active", request);
			return;
		}

		final Event event = toEvent(request);

		final ITrx currentTrx = trxManager.getThreadInheritedTrx(OnTrxMissingPolicy.ReturnTrxNone);
		if (trxManager.isActive(currentTrx))
		{
			final EventsCollector collector = currentTrx.getPropertyAndProcessAfterCommit(
					EventsCollector.class.getName(),
					() -> new EventsCollector(getEventBus()),
					EventsCollector::postAllCollected);

			collector.collect(event);
		}
		else
		{
			getEventBus().postEvent(event);
		}
	}

	private IEventBus getEventBus()
	{
		return eventBusFactory.getEventBus(TOPIC);
	}

	private static Event toEvent(@NonNull final DocumentPostRequest request)
	{
		final String requestStr = jsonObjectMapper.writeValueAsString(request);

		return Event.builder()
				.putProperty(EVENT_PROPERTY_DocumentPostRequest, requestStr)
				.shallBeLogged()
				.build();
	}

	private static DocumentPostRequest extractDocumentPostRequest(@NonNull final Event event)
	{
		final String json = event.getProperty(EVENT_PROPERTY_DocumentPostRequest);
		return jsonObjectMapper.readValue(json);
	}

	//
	//
	//
	//
	//

	@lombok.ToString
	private static final class DocumentPostRequestHandlerAsEventListener implements IEventListener
	{
		private final EventLogUserService eventLogUserService;
		private final DocumentPostRequestHandler handler;

		@lombok.Builder
		private DocumentPostRequestHandlerAsEventListener(
				@NonNull final DocumentPostRequestHandler handler,
				@NonNull final EventLogUserService eventLogUserService)
		{
			this.handler = handler;
			this.eventLogUserService = eventLogUserService;
		}

		@Override
		public void onEvent(@NonNull final IEventBus eventBus, @NonNull final Event event)
		{
			final DocumentPostRequest request = extractDocumentPostRequest(event);

			try (final IAutoCloseable ctxCloseable = switchCtx(request);
					final MDCCloseable requestRecordMDC = TableRecordMDC.putTableRecordReference(request.getRecord());
					final MDCCloseable eventHandlerMDC = MDC.putCloseable("eventHandler.className", handler.getClass().getName());)
			{
				eventLogUserService.invokeHandlerAndLog(InvokeHandlerAndLogRequest.builder()
						.handlerClass(handler.getClass())
						.invocation(() -> handler.handleRequest(request))
						.build());
			}
		}

		private static IAutoCloseable switchCtx(@NonNull final DocumentPostRequest request)
		{
			final Properties ctx = Env.newTemporaryCtx();
			Env.setClientId(ctx, request.getClientId());
			return Env.switchContext(ctx);
		}
	}

	//
	//
	//
	//
	//

	@ToString
	private static class EventsCollector
	{
		private final IEventBus eventBus;
		private ArrayList<Event> events = new ArrayList<>();

		public EventsCollector(@NonNull final IEventBus eventBus)
		{
			this.eventBus = eventBus;
		}

		public void collect(@NonNull final Event event)
		{
			if (events == null)
			{
				throw new AdempiereException("Events already sent");
			}
			events.add(event);
		}

		public void postAllCollected()
		{
			final ArrayList<Event> events = this.events;
			this.events = null;

			events.forEach(eventBus::postEvent);
		}
	}
}
