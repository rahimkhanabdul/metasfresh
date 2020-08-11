package de.metas.acct.posting.server;

import org.adempiere.ad.trx.api.ITrxManager;
import org.slf4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.metas.Profiles;
import de.metas.acct.api.IAcctSchemaDAO;
import de.metas.acct.doc.AcctDocRegistry;
import de.metas.acct.posting.DocumentPostRequest;
import de.metas.acct.posting.DocumentPostRequestHandler;
import de.metas.logging.LogManager;
import de.metas.notification.INotificationBL;
import de.metas.util.Services;
import lombok.NonNull;

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

@Component
@Profile(Profiles.PROFILE_AccountingService)
public class AccountingServer implements DocumentPostRequestHandler
{
	private static final Logger logger = LogManager.getLogger(AccountingServer.class);
	private final IAcctSchemaDAO acctSchemaDAO = Services.get(IAcctSchemaDAO.class);
	private final INotificationBL userNotifications = Services.get(INotificationBL.class);
	private final ITrxManager trxManager = Services.get(ITrxManager.class);
	private final AcctDocRegistry acctDocFactory;

	public AccountingServer(@NonNull final AcctDocRegistry acctDocFactory)
	{
		this.acctDocFactory = acctDocFactory;
	}

	@Override
	public void handleRequest(final DocumentPostRequest request)
	{
		logger.debug("Posting: {}", request);

		DocumentPostRequestProcessCommand.builder()
				.acctSchemaDAO(acctSchemaDAO)
				.userNotifications(userNotifications)
				.trxManager(trxManager)
				.acctDocFactory(acctDocFactory)
				//
				.request(request)
				//
				.execute();
	}
}
