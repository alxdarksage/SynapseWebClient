package org.sagebionetworks.web.client.security;

import java.util.Date;

import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.schema.adapter.AdapterFactory;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.UserAccountServiceAsync;
import org.sagebionetworks.web.client.cookie.CookieKeys;
import org.sagebionetworks.web.client.cookie.CookieProvider;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.datepicker.client.CalendarUtil;
import com.google.inject.Inject;

/**
 * A util class for authentication
 * 
 * CODE SPLITTING NOTE: this class should be kept small
 * 
 * @author dburdick
 *
 */
public class AuthenticationControllerImpl implements AuthenticationController {
	
	private static final String AUTHENTICATION_MESSAGE = "Invalid usename or password.";
	private static UserSessionData currentUser;
	
	private CookieProvider cookies;
	private UserAccountServiceAsync userAccountService;	
	private AdapterFactory adapterFactory;
	
	@Inject
	public AuthenticationControllerImpl(CookieProvider cookies, UserAccountServiceAsync userAccountService, AdapterFactory adapterFactory){
		this.cookies = cookies;
		this.userAccountService = userAccountService;
		this.adapterFactory = adapterFactory;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void loginUser(final String username, String password, boolean explicitlyAcceptsTermsOfUse, final AsyncCallback<String> callback) {
		if(username == null || password == null) callback.onFailure(new AuthenticationException(AUTHENTICATION_MESSAGE));		
		userAccountService.initiateSession(username, password, explicitlyAcceptsTermsOfUse, new AsyncCallback<String>() {		
			@Override
			public void onSuccess(String userSessionJson) {
				UserSessionData userSessionData = null;
				try {
					//automatically expire after a day
					Date tomorrow = getDayFromNow();
					setUserSessionDataCookie(userSessionJson, tomorrow);
					userSessionData = new UserSessionData(adapterFactory.createNew(userSessionJson)); 
					cookies.setCookie(CookieKeys.USER_LOGIN_TOKEN, userSessionData.getSessionToken(), tomorrow);
				} catch (JSONObjectAdapterException e) {
					//can't save the cookie
					e.printStackTrace();
				}
				
				currentUser = userSessionData;
				callback.onSuccess(userSessionJson);
			}

			
			@Override
			public void onFailure(Throwable caught) {
				callback.onFailure(caught);
			}
		});
	}
		
	@Override
	public void loginUser(final String token, final AsyncCallback<String> callback) {
		setUser(token, callback, false);
	}
	
	@Override
	public void loginUserSSO(final String token, final AsyncCallback<String> callback) {
		setUser(token, callback, true);
	}

	@Override
	public void logoutUser() {
		String loginCookieString = cookies.getCookie(CookieKeys.USER_LOGIN_DATA);
		if(loginCookieString != null) {
			// don't actually terminate session, just remove the cookies			
			cookies.removeCookie(CookieKeys.USER_LOGIN_DATA);
			cookies.removeCookie(CookieKeys.USER_LOGIN_TOKEN);
			currentUser = null;
		}
	}	

	/*
	 * Private Methods
	 */
	@SuppressWarnings("deprecation")
	private void setUser(String token, final AsyncCallback<String> callback, final boolean isSSO) {
		if(token == null) callback.onFailure(new AuthenticationException(AUTHENTICATION_MESSAGE));
		userAccountService.getUser(token, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String userSessionJson) {
				if (userSessionJson != null) {					
					UserSessionData userSessionData = null;
					try {
						JSONObjectAdapter usdAdapter = adapterFactory.createNew(userSessionJson);
						userSessionData = new UserSessionData(usdAdapter);
						userSessionData.setIsSSO(isSSO);
						Date tomorrow = getDayFromNow();
						cookies.setCookie(CookieKeys.USER_LOGIN_DATA, usdAdapter.toJSONString(), tomorrow);
						cookies.setCookie(CookieKeys.USER_LOGIN_TOKEN, userSessionData.getSessionToken(), tomorrow);
						currentUser = userSessionData;
						callback.onSuccess(usdAdapter.toJSONString());
					} catch (JSONObjectAdapterException e){
						callback.onFailure(e);
					}
				} else {
					callback.onFailure(new AuthenticationException(AUTHENTICATION_MESSAGE));
				}
			}
			
			@Override
			public void onFailure(Throwable caught) {
				callback.onFailure(new AuthenticationException(AUTHENTICATION_MESSAGE));
			}
		});		
	}

	@Override
	public void getTermsOfUse(AsyncCallback<String> callback) {
		userAccountService.getTermsOfUse(callback);
	}

	@Override
	public boolean isLoggedIn() {
		String loginCookieString = cookies.getCookie(CookieKeys.USER_LOGIN_DATA);
		if(loginCookieString != null) {
			try {
				currentUser = new UserSessionData(adapterFactory.createNew(loginCookieString));
				if(currentUser != null) return true;				
			} catch (JSONObjectAdapterException e) {				
			}			
		} 
		return false;
	}

	@Override
	public String getCurrentUserPrincipalId() {
		if(currentUser != null) {		
			UserProfile profileObj = currentUser.getProfile();
			if(profileObj != null && profileObj.getOwnerId() != null) {							
				return profileObj.getOwnerId();						
			}
		} 
		return null;
	}
	
	@Override
	public void reloadUserSessionData() {
		String sessionToken = cookies.getCookie(CookieKeys.USER_LOGIN_TOKEN);
		setUser(sessionToken, new AsyncCallback<String>() {
			@Override
			public void onFailure(Throwable caught) {				
			}

			@Override
			public void onSuccess(String result) {
			}
		}, getCurrentUserIsSSO());
		
	}

	@Override
	public UserSessionData getCurrentUserSessionData() {
		if (isLoggedIn()) {
			return currentUser;
		} else
			return null;
	}

	@Override
	public String getCurrentUserSessionToken() {
		if(currentUser != null) return currentUser.getSessionToken();
		else return null;
	}
	
	@Override
	public boolean getCurrentUserIsSSO() {
		if(currentUser != null) return currentUser.getIsSSO();
		else return false;
	}

	
	/*
	 * Private Methods
	 */
	private void setUserSessionDataCookie(String userSessionJson,
			Date tomorrow) {
		cookies.setCookie(CookieKeys.USER_LOGIN_DATA, userSessionJson, tomorrow);
	}

	private Date getDayFromNow() {
		Date date = new Date();
		CalendarUtil.addDaysToDate(date, 1);
		return date;  
	}

	
}
