package org.sagebionetworks.web.client.widget.entity;

import org.sagebionetworks.repo.model.AccessRequirement;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Locationable;
import org.sagebionetworks.repo.model.Study;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.web.client.ClientProperties;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.EntityTypeProvider;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.SynapseClientAsync;
import org.sagebionetworks.web.client.events.EntityUpdatedEvent;
import org.sagebionetworks.web.client.events.EntityUpdatedHandler;
import org.sagebionetworks.web.client.model.EntityBundle;
import org.sagebionetworks.web.client.place.LoginPlace;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.transform.NodeModelCreator;
import org.sagebionetworks.web.client.utils.APPROVAL_TYPE;
import org.sagebionetworks.web.client.utils.Callback;
import org.sagebionetworks.web.client.utils.CallbackP;
import org.sagebionetworks.web.client.utils.GovernanceServiceHelper;
import org.sagebionetworks.web.client.utils.RESTRICTION_LEVEL;
import org.sagebionetworks.web.client.widget.entity.EntityMetadataView.Presenter;
import org.sagebionetworks.web.client.widget.entity.file.LocationableTitleBar;
import org.sagebionetworks.web.shared.EntityWrapper;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class EntityMetadata implements Presenter {

	private EntityMetadataView view;
	private SynapseClientAsync synapseClient;
	private NodeModelCreator nodeModelCreator;
	private AuthenticationController authenticationController;
	private JSONObjectAdapter jsonObjectAdapter;
	private EntityTypeProvider entityTypeProvider;
	private JiraURLHelper jiraURLHelper;
	private GlobalApplicationState globalApplicationState;
	private EntityBundle bundle;	
	private EntityUpdatedHandler entityUpdatedHandler;
	
	
	@Inject
	public EntityMetadata(EntityMetadataView view,
			SynapseClientAsync synapseClient,
			NodeModelCreator nodeModelCreator,
			AuthenticationController authenticationController,
			JSONObjectAdapter jsonObjectAdapter,
			GlobalApplicationState globalApplicationState,
			EntityTypeProvider entityTypeProvider, JiraURLHelper jiraURLHelper) {
		this.view = view;
		this.view.setPresenter(this);
		this.synapseClient = synapseClient;
		this.nodeModelCreator = nodeModelCreator;
		this.authenticationController = authenticationController;
		this.jsonObjectAdapter = jsonObjectAdapter;
		this.globalApplicationState = globalApplicationState;
		this.entityTypeProvider = entityTypeProvider;
		this.jiraURLHelper = jiraURLHelper;
	}


	public Widget asWidget() {
		return view.asWidget();
	}

	public void setEntityBundle(EntityBundle bundle, Long versionNumber) {
		this.bundle = bundle;
		view.setEntityBundle(bundle, bundle.getPermissions().getCanChangePermissions(), bundle.getPermissions().getCanEdit(), versionNumber != null);
		boolean showDetailedMetadata = false;
		boolean showEntityName = false;
		if (bundle.getEntity() instanceof FileEntity) {
			//it has data if there is a file handle associated with it
			showDetailedMetadata = ((FileEntity)bundle.getEntity()).getDataFileHandleId() != null;
			showEntityName = !showDetailedMetadata;
		}
		else {
			//TODO: delete this after migration to FileHandle system.  This corresponds to the old logic
			boolean isLocationable = bundle.getEntity() instanceof Locationable;
			boolean isStudy = bundle.getEntity() instanceof Study; //if study, always show metadata and entity name
			showDetailedMetadata = !isLocationable || isStudy || LocationableTitleBar.isDataPossiblyWithinLocationable(bundle, !isAnonymous());
			showEntityName = !isLocationable || isStudy || !LocationableTitleBar.isDataPossiblyWithinLocationable(bundle, !isAnonymous());
		}
		view.setDetailedMetadataVisible(showDetailedMetadata);
		view.setEntityNameVisible(showEntityName);
	}

	private UserProfile getUserProfile() {
		UserSessionData sessionData = authenticationController.getCurrentUserSessionData();
		return (sessionData==null ? null : sessionData.getProfile());				
	}

	@Override
	public boolean isAnonymous() {
		return getUserProfile()==null;
	}

	@Override
	public String getJiraFlagUrl() {
		UserProfile userProfile = getUserProfile();
		if (userProfile==null) throw new IllegalStateException("UserProfile is null");
		return jiraURLHelper.createFlagIssue(
				userProfile.getUserName(), 
				userProfile.getDisplayName(), 
				bundle.getEntity().getId());
	}

	public String getJiraRestrictionUrl() {
		UserProfile userProfile = getUserProfile();
		if (userProfile==null) throw new IllegalStateException("UserProfile is null");
		return jiraURLHelper.createAccessRestrictionIssue(
				userProfile.getUserName(), 
				userProfile.getDisplayName(), 
				bundle.getEntity().getId());
	}

	@Override
	public String getJiraRequestAccessUrl() {
		UserProfile userProfile = getUserProfile();
		if (userProfile==null) throw new IllegalStateException("UserProfile is null");
		return jiraURLHelper.createRequestAccessIssue(
				userProfile.getOwnerId(), 
				userProfile.getDisplayName(), 
				userProfile.getUserName(), 
				bundle.getEntity().getId(), 
				getAccessRequirement().getId().toString());
	}

	@Override
	public boolean hasAdministrativeAccess() {
		return bundle.getPermissions().getCanChangePermissions();
	}

	@Override
	public RESTRICTION_LEVEL getRestrictionLevel() {
		return GovernanceServiceHelper.entityRestrictionLevel(bundle.getAccessRequirements());
	}

	@Override
	public APPROVAL_TYPE getApprovalType() {
		return GovernanceServiceHelper.accessRequirementApprovalType(getAccessRequirement());
	}

	@Override
	public boolean hasFulfilledAccessRequirements() {
		return bundle.getUnmetAccessRequirements().size()==0L;
	}

	@Override
	public boolean includeRestrictionWidget() {
		return (bundle.getEntity() instanceof FileEntity) || (bundle.getEntity() instanceof Locationable);
	}

	@Override
	public String accessRequirementText() {
		return GovernanceServiceHelper.getAccessRequirementText(getAccessRequirement());
	}
	
	private AccessRequirement getAccessRequirement() {
		return GovernanceServiceHelper.selectAccessRequirement(bundle.getAccessRequirements(), bundle.getUnmetAccessRequirements());
	}

	@Override
	public Callback accessRequirementCallback() {
		if (APPROVAL_TYPE.USER_AGREEMENT!=GovernanceServiceHelper.accessRequirementApprovalType(getAccessRequirement())) 
			throw new IllegalStateException("not a 'User Agreement' requirement type");
		return new Callback() {
			@Override
			public void invoke() {
				// create the self-signed access approval, then update this object
				String principalId = getUserProfile().getOwnerId();
				AccessRequirement ar = getAccessRequirement();
				Callback onSuccess = new Callback() {
					@Override
					public void invoke() {
						fireEntityUpdatedEvent();
					}
				};
				CallbackP<Throwable> onFailure = new CallbackP<Throwable>() {
					@Override
					public void invoke(Throwable t) {
						view.showInfo("Error", t.getMessage());
					}
				};
				GovernanceServiceHelper.signTermsOfUse(
						principalId, 
						ar.getId(), 
						onSuccess, 
						onFailure, 
						synapseClient, 
						jsonObjectAdapter);
			}
		};
	}

	
	@Override
	public void fireEntityUpdatedEvent() {
		if (entityUpdatedHandler != null)
			entityUpdatedHandler.onPersistSuccess(new EntityUpdatedEvent());
	}
	
	public void setEntityUpdatedHandler(EntityUpdatedHandler handler) {
		this.entityUpdatedHandler = handler;
	}
	
	@Override
	public Callback getImposeRestrictionsCallback() {
		return new Callback() {
			@Override
			public void invoke() {
				synapseClient.createLockAccessRequirement(bundle.getEntity().getId(), new AsyncCallback<EntityWrapper>(){
					@Override
					public void onSuccess(EntityWrapper result) {
						fireEntityUpdatedEvent();
					}
					@Override
					public void onFailure(Throwable caught) {
						if(!DisplayUtils.handleServiceException(caught, globalApplicationState.getPlaceChanger(), authenticationController.isLoggedIn(), view))
							view.showInfo("Error", caught.getMessage());
					}
				});
			}
		};
	}
	
	@Override
	public Callback getLoginCallback() {
		return new Callback() {
			public void invoke() {		
				globalApplicationState.getPlaceChanger().goTo(new LoginPlace(ClientProperties.DEFAULT_PLACE_TOKEN));
			}
		};
	}


}
