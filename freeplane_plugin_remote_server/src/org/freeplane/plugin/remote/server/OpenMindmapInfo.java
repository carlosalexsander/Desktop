package org.freeplane.plugin.remote.server;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.docear.messages.Messages.ListenToUpdateOccurrenceResponse;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.remote.v10.model.updates.AddNodeUpdate;
import org.freeplane.plugin.remote.v10.model.updates.ChangeNodeAttributeUpdate;
import org.freeplane.plugin.remote.v10.model.updates.DeleteNodeUpdate;
import org.freeplane.plugin.remote.v10.model.updates.MapUpdate;
import org.freeplane.plugin.remote.v10.model.updates.MoveNodeUpdate;

import akka.actor.ActorRef;

public class OpenMindmapInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	private final URL mapUrl;
	private final Set<NodeModel> lockedNodes;
	private long lastAccessTime;
	private long lastUpdateTime;
	private final ActorRef sender;
	private final List<MapUpdate> updateList;
	private final List<ActorRef> listeningActors;

	public OpenMindmapInfo(URL mapUrl, ActorRef sender) {
		this.mapUrl = mapUrl;
		this.lockedNodes = new HashSet<NodeModel>();
		this.updateList = new ArrayList<MapUpdate>();
		this.listeningActors = new ArrayList<ActorRef>();
		this.sender = sender;
		updateAccessTime();
		
	}

	public URL getMapUrl() {
		updateAccessTime();
		return mapUrl;
	}

	public ActorRef getSender() {
		return sender;
	}

	public Set<NodeModel> getLockedNodes() {
		updateUpdateTime();
		return lockedNodes;
	}
	
	public void addLockedNode(NodeModel freeplaneNode) {
		updateAccessTime();
		updateUpdateTime();
		lockedNodes.add(freeplaneNode);
	}
	
	public void removeLockedNode(NodeModel freeplaneNode) {
		updateAccessTime();
		updateUpdateTime();
		lockedNodes.remove(freeplaneNode);
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}
	
	public long getLastUpdateTime() {
		return lastUpdateTime;
	}
	
	private void updateAccessTime() {
		lastAccessTime = System.currentTimeMillis();
	}
	
	//update and revision related
	private void updateUpdateTime() {
		lastUpdateTime = System.currentTimeMillis();
	}
	
	public int getCurrentRevision() {
		updateAccessTime();
		return updateList.size();
	}
	
	public void addUpdate(MapUpdate updateStatement) {
		RemoteController.getLogger().debug("OpenMindmapInfo.addUpdate => update added: "+updateStatement.getClass().getSimpleName());
		updateList.add(updateStatement);
		updateUpdateTime();
		//tell listeners that change has happened
		for(ActorRef ref : listeningActors) {
			ref.tell(new ListenToUpdateOccurrenceResponse(true), null);
		}
		//empty list, because they have to register again
		listeningActors.clear();
	}
	
	public void registerUpdateListener(ActorRef actor) {
		updateAccessTime();
		listeningActors.add(actor);
	}
	
	public List<String> getUpdateListAsJson(long sinceRevisionNumber) {
		updateAccessTime();
		return getShortUpdateListAsJson((int)sinceRevisionNumber);
	}
	
	public List<String> getShortUpdateListAsJson(int sinceRevision) {
		updateAccessTime();
		
		final List<String> shortList = new ArrayList<String>();
		for(int i = sinceRevision; i < updateList.size(); i++) {
			final MapUpdate update = updateList.get(i);
			boolean hasMatch = false;
			if(update instanceof AddNodeUpdate) {
				hasMatch = hasMatch((AddNodeUpdate)update, i+1);
			} else if(update instanceof ChangeNodeAttributeUpdate) {
				hasMatch = hasMatch((ChangeNodeAttributeUpdate) update, i+1);
			} else if(update instanceof MoveNodeUpdate) {
				hasMatch = hasMatch((MoveNodeUpdate)update, i+1);
			}

			if(!hasMatch)
				shortList.add(update.toJson());
		}
		return shortList;
	}
	
	private boolean hasMatch(AddNodeUpdate update, int sinceRevision) {
		//has to check, if node has been deleted in future
		
		for(int j = sinceRevision; j < updateList.size(); j++) { //check against all coming events
			final MapUpdate curUpdate = updateList.get(j);
			if(curUpdate instanceof DeleteNodeUpdate) {
				final DeleteNodeUpdate delUpdate = (DeleteNodeUpdate)curUpdate;
				if (delUpdate.getNodeId().equals(update.getNewNodeId())) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean hasMatch(ChangeNodeAttributeUpdate update, int sinceRevision) {
		//has to check, if node has been deleted in future
		//has to check, if attribute has been changed in future
		
		for(int j = sinceRevision; j < updateList.size(); j++) { //check against all coming events
			final MapUpdate curUpdate = updateList.get(j);
			if(curUpdate instanceof DeleteNodeUpdate) {
				final DeleteNodeUpdate delUpdate = (DeleteNodeUpdate)curUpdate;
				if (delUpdate.getNodeId().equals(update.getNodeId())) {
					return true;
				}
			} else if(curUpdate instanceof ChangeNodeAttributeUpdate) {
				final ChangeNodeAttributeUpdate changeUpdate =(ChangeNodeAttributeUpdate) curUpdate; 
				if(changeUpdate.getNodeId().equals(update.getNodeId())
						&& changeUpdate.getAttribute().equals(update.getAttribute())) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean hasMatch(MoveNodeUpdate update, int sinceRevision) {
		//has to check, if node has been deleted in future
		
		for(int j = sinceRevision; j < updateList.size(); j++) { //check against all coming events
			final MapUpdate curUpdate = updateList.get(j);
			if(curUpdate instanceof DeleteNodeUpdate) {
				final DeleteNodeUpdate delUpdate = (DeleteNodeUpdate)curUpdate;
				if (delUpdate.getNodeId().equals(update.getNodetoMoveId())) {
					return true;
				}
			}
		}
		return false;
	}
}
