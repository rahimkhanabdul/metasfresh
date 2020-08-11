package de.metas.acct.posting.server;

import java.util.List;
import java.util.Properties;

import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.service.ClientId;
import org.adempiere.util.lang.IAutoCloseable;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.acct.Doc;
import org.compiere.util.Env;

import de.metas.acct.api.AcctSchema;
import de.metas.acct.api.IAcctSchemaDAO;
import de.metas.acct.doc.AcctDocRegistry;
import de.metas.acct.posting.DocumentPostRequest;
import de.metas.event.Topic;
import de.metas.notification.INotificationBL;
import de.metas.notification.UserNotificationRequest;
import de.metas.notification.UserNotificationRequest.TargetRecordAction;
import de.metas.user.UserId;
import lombok.Builder;
import lombok.NonNull;

/*
 * #%L
 * de.metas.acct.base
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

final class DocumentPostRequestProcessCommand
{
	// Services
	private final IAcctSchemaDAO acctSchemaDAO;
	private final INotificationBL userNotifications;
	private final ITrxManager trxManager;
	private final AcctDocRegistry acctDocFactory;

	// Parameters
	private static final Topic NOTIFICATIONS_TOPIC = Topic.remote("de.metas.acct.UserNotifications");
	private final ClientId clientId;
	private final TableRecordReference documentRef;
	private final boolean force;
	private final UserId onErrorNotifyUserId;

	@Builder
	private DocumentPostRequestProcessCommand(
			@NonNull final IAcctSchemaDAO acctSchemaDAO,
			@NonNull final INotificationBL userNotifications,
			@NonNull final ITrxManager trxManager,
			@NonNull final AcctDocRegistry acctDocFactory,
			//
			@NonNull final DocumentPostRequest request)
	{
		this.acctSchemaDAO = acctSchemaDAO;
		this.userNotifications = userNotifications;
		this.trxManager = trxManager;
		this.acctDocFactory = acctDocFactory;

		this.clientId = request.getClientId();
		this.documentRef = request.getRecord();
		this.force = request.isForce();
		this.onErrorNotifyUserId = request.getOnErrorNotifyUserId();
	}

	public static class DocumentPostRequestProcessCommandBuilder
	{
		public void execute()
		{
			build().execute();
		}
	}

	public void execute()
	{
		trxManager.runAfterCommit(this::execute0);
	}

	private void execute0()
	{
		try (final IAutoCloseable c = switchContext())
		{
			final List<AcctSchema> ass = acctSchemaDAO.getAllByClient(clientId);

			final Doc<?> doc = acctDocFactory.get(ass, documentRef);
			final boolean repost = true;
			doc.post(force, repost);
		}
		catch (final Exception ex)
		{
			final AdempiereException postedException = AdempiereException.wrapIfNeeded(ex);
			notifyUserIfPossbile(postedException);
			throw postedException;
		}
	}

	private IAutoCloseable switchContext()
	{
		final Properties ctx = Env.newTemporaryCtx();
		Env.setClientId(ctx, clientId);
		return Env.switchContext(ctx);
	}

	private void notifyUserIfPossbile(@NonNull final AdempiereException postedException)
	{
		if (onErrorNotifyUserId == null)
		{
			return;
		}

		userNotifications.sendAfterCommit(UserNotificationRequest.builder()
				.topic(NOTIFICATIONS_TOPIC)
				.recipientUserId(onErrorNotifyUserId)
				.contentPlain(postedException.getLocalizedMessage())
				.targetAction(TargetRecordAction.of(documentRef))
				.build());
	}
}
