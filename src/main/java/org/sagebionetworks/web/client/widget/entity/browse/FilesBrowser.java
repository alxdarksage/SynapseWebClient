package org.sagebionetworks.web.client.widget.entity.browse;

import org.sagebionetworks.repo.model.AutoGenFactory;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.schema.adapter.AdapterFactory;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.DisplayConstants;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.SynapseClientAsync;
import org.sagebionetworks.web.client.events.EntityUpdatedEvent;
import org.sagebionetworks.web.client.events.EntityUpdatedHandler;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.transform.NodeModelCreator;
import org.sagebionetworks.web.client.widget.SynapseWidgetPresenter;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class FilesBrowser implements FilesBrowserView.Presenter, SynapseWidgetPresenter {
	
	private FilesBrowserView view;
	private String configuredEntityId;
	private SynapseClientAsync synapseClient;
	private NodeModelCreator nodeModelCreator;
	private AdapterFactory adapterFactory;
	private AutoGenFactory autogenFactory;
	private EntityUpdatedHandler entityUpdatedHandler;
	GlobalApplicationState globalApplicationState;
	AuthenticationController authenticationController;
	boolean canEdit = false;
	
	@Inject
	public FilesBrowser(FilesBrowserView view,
			SynapseClientAsync synapseClient,
			NodeModelCreator nodeModelCreator, AdapterFactory adapterFactory,
			AutoGenFactory autogenFactory,
			GlobalApplicationState globalApplicationState,
			AuthenticationController authenticationController) {
		this.view = view;		
		this.synapseClient = synapseClient;
		this.nodeModelCreator = nodeModelCreator;
		this.adapterFactory = adapterFactory;
		this.autogenFactory = autogenFactory;
		this.globalApplicationState = globalApplicationState;
		this.authenticationController = authenticationController;
		view.setPresenter(this);
	}	
	
	/**
	 * Configure tree view with given entityId's children as start set
	 * @param entityId
	 */
	public void configure(String entityId) {
		this.configuredEntityId = entityId;		
		view.configure(entityId, canEdit);
	}
	
	public void configure(String entityId, String title) {
		this.configuredEntityId = entityId;
		view.configure(entityId, canEdit, title);
	}
	
	public void setCanEdit(boolean canEdit) {
		this.canEdit = canEdit;
	}
	
	public void clearState() {
		view.clear();
		// remove handlers
		this.entityUpdatedHandler = null;		
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
	public Widget asWidget() {
		view.setPresenter(this);		
		return view.asWidget();
	}

	@Override
	public void createFolder(final String name) {
		Entity folder = createNewEntity(Folder.class.getName(), configuredEntityId);
		folder.setName(name);
		String entityJson;
		try {
			entityJson = folder.writeToJSONObject(adapterFactory.createNew()).toJSONString();
			synapseClient.createOrUpdateEntity(entityJson, null, true, new AsyncCallback<String>() {
				@Override
				public void onSuccess(String newId) {
					view.showInfo("Folder '" + name + "' Added", "");
					view.refreshTreeView(configuredEntityId);
				}
				
				@Override
				public void onFailure(Throwable caught) {
					if(!DisplayUtils.handleServiceException(caught, globalApplicationState.getPlaceChanger(), authenticationController.isLoggedIn(), view))
						view.showErrorMessage(DisplayConstants.ERROR_FOLDER_CREATION_FAILED);
				}			
			});
		} catch (JSONObjectAdapterException e) {			
			view.showErrorMessage(DisplayConstants.ERROR_INCOMPATIBLE_CLIENT_VERSION);		
		}
	}

	
	/*
	 * Private Methods
	 */
	private Entity createNewEntity(String className, String parentId) {
		Entity entity = (Entity) autogenFactory.newInstance(className);
		entity.setParentId(parentId);
		entity.setEntityType(className);		
		return entity;
	}

}
