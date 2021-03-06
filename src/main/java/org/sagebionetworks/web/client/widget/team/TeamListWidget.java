package org.sagebionetworks.web.client.widget.team;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.schema.adapter.AdapterFactory;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.SynapseClientAsync;
import org.sagebionetworks.web.client.security.AuthenticationController;

import com.google.gwt.place.shared.Place;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class TeamListWidget implements TeamListWidgetView.Presenter{

	private TeamListWidgetView view;
	private GlobalApplicationState globalApplicationState;
	private SynapseClientAsync synapseClient;
	private AuthenticationController authenticationController;
	private AdapterFactory adapterFactory;
	@Inject
	public TeamListWidget(TeamListWidgetView view, 
			SynapseClientAsync synapseClient, 
			GlobalApplicationState globalApplicationState, 
			AuthenticationController authenticationController,
			AdapterFactory adapterFactory) {
		this.view = view;
		view.setPresenter(this);
		this.globalApplicationState = globalApplicationState;
		this.synapseClient = synapseClient;
		this.authenticationController = authenticationController;
		this.adapterFactory = adapterFactory;
	}
	
	@Override
	public void goTo(Place place) {
		globalApplicationState.getPlaceChanger().goTo(place);
	}
	
	public void configure(List<Team> teams, boolean isBig) {
		view.configure(teams, isBig);
	}
	
	public void configure(String userId, final boolean isBig) {
		//get the teams associated to the given user
		getTeams(userId, new AsyncCallback<List<Team>>() {
			@Override
			public void onSuccess(List<Team> teams) {
				configure(teams, isBig);
			}
			@Override
			public void onFailure(Throwable caught) {
				if(!DisplayUtils.handleServiceException(caught, globalApplicationState.getPlaceChanger(), authenticationController.isLoggedIn(), view)) {					
					view.showErrorMessage(caught.getMessage());
				} 
			}
		});
	}
	
	private void getTeams(String userId, final AsyncCallback<List<Team>> callback) {
		synapseClient.getTeamsForUser(userId, new AsyncCallback<ArrayList<String>>() {
			@Override
			public void onSuccess(ArrayList<String> results) {
				try {
					List<Team> teams = new ArrayList<Team>();
					for (String teamString : results) {
						teams.add(new Team(adapterFactory.createNew(teamString)));
					}
					callback.onSuccess(teams);
				} catch (JSONObjectAdapterException e) {
					onFailure(e);
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				callback.onFailure(caught);
			}
		});
	}
	public Widget asWidget() {
		view.setPresenter(this);
		return view.asWidget();
	}
	

}
