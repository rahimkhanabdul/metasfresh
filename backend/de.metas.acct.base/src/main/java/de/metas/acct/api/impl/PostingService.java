package de.metas.acct.api.impl;

import org.adempiere.service.ISysConfigBL;

import com.google.common.annotations.VisibleForTesting;

import de.metas.acct.api.IPostingRequestBuilder;
import de.metas.acct.api.IPostingService;
import de.metas.util.Services;

public class PostingService implements IPostingService
{
	/** Flag indicating that the whole accounting module is enabled or disabled */
	@VisibleForTesting
	public static final String SYSCONFIG_Enabled = "org.adempiere.acct.Enabled";

	@Override
	public boolean isEnabled()
	{
		final ISysConfigBL sysConfigBL = Services.get(ISysConfigBL.class);
		final boolean defaultValue = true; // enabled by default
		return sysConfigBL.getBooleanValue(SYSCONFIG_Enabled, defaultValue);
	}

	@Override
	public IPostingRequestBuilder newPostingRequest()
	{
		return new PostingRequestBuilder();
	}
}
