package de.metas.acct.api;

import de.metas.util.ISingletonService;

/**
 * This is the main entry point for all document posting(accounting) APIs
 *
 * @author tsa
 *
 */
public interface IPostingService extends ISingletonService
{
	IPostingRequestBuilder newPostingRequest();

	/** @return true if the accounting module is active; false if the accounting module is COMPLETELLY off */
	boolean isEnabled();
}
