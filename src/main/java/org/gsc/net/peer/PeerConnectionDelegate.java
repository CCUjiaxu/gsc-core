package org.gsc.net.peer;

import org.gsc.common.overlay.message.Message;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.net.message.GSCMessage;

public abstract class PeerConnectionDelegate {

  public abstract void onMessage(PeerConnection peer, GSCMessage msg);

  public abstract Message getMessage(Sha256Hash msgId);

  public abstract void onConnectPeer(PeerConnection peer);

  public abstract void onDisconnectPeer(PeerConnection peer);

}
