/*
 * Copyright (C) 2011 - Jingle Nodes - Yuilop - Neppo
 *
 *   This file is part of Switji (http://jinglenodes.org)
 *
 *   Switji is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   Switji is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with MjSip; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *   Author(s):
 *   Benhur Langoni (bhlangonijr@gmail.com)
 *   Thiago Camargo (barata7@gmail.com)
 */

package org.jinglenodes.relay;

import org.apache.log4j.Logger;
import org.jinglenodes.callkiller.CallKiller;
import org.jinglenodes.jingle.Reason;
import org.jinglenodes.jingle.processor.JingleProcessor;
import org.jinglenodes.jingle.transport.Candidate;
import org.jinglenodes.prepare.CallPreparation;
import org.jinglenodes.prepare.PrepareStatesManager;
import org.jinglenodes.prepare.SipPrepareStatesManager;
import org.jinglenodes.session.CallSession;
import org.xmpp.component.IqRequest;
import org.xmpp.component.ResultReceiver;
import org.xmpp.component.ServiceException;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.SipChannel;
import org.zoolu.sip.message.SipParsingException;
import org.zoolu.tools.ConcurrentTimelineHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 3/19/12
 * Time: 5:21 PM
 */
public class RelayCallPreparation extends CallPreparation implements ResultReceiver, RelayEventListener {

    final Logger log = Logger.getLogger(RelayCallPreparation.class);
    private RelayServiceProcessor relayServiceProcessor;
    private PrepareStatesManager prepareStatesManager;
    private SipPrepareStatesManager sipPrepareStatesManager;
    private ConcurrentTimelineHashMap<String, CallSession> sessions = new ConcurrentTimelineHashMap<String, CallSession>();
    private CallKiller callKiller;
    private long lastCheck = System.currentTimeMillis();
    public static long TIMEOUT = 60 * 1000 * 5;
    private boolean useOnForwardedCalls=true;

    @Override
    public void receivedResult(final IqRequest iqRequest) {
        if (iqRequest.getOriginalPacket() instanceof JingleIQ) {
            prepareStatesManager.prepareCall((JingleIQ) iqRequest.getOriginalPacket(), null);
        } else if (iqRequest.getOriginalPacket() instanceof Message) {
            sipPrepareStatesManager.prepareCall((Message) iqRequest.getOriginalPacket(), null, null);
        }
    }

    @Override
    public void receivedError(IqRequest iqRequest) {
        if (iqRequest.getOriginalPacket() instanceof JingleIQ) {
            callKiller.immediateKill((JingleIQ) iqRequest.getOriginalPacket(), new Reason("No Relay", Reason.Type.connectivity_error));
            prepareStatesManager.cancelCall((JingleIQ) iqRequest.getOriginalPacket(), null, new Reason("No Relay", Reason.Type.connectivity_error));
        } else if (iqRequest.getOriginalPacket() instanceof Message) {
            callKiller.immediateKill((Message) iqRequest.getOriginalPacket(), new Reason("No Relay", Reason.Type.connectivity_error));
            sipPrepareStatesManager.cancelCall((Message) iqRequest.getOriginalPacket(), null, null, new Reason("No Relay", Reason.Type.connectivity_error));
        }
    }

    @Override
    public void timeoutRequest(IqRequest iqRequest) {
        if (iqRequest.getOriginalPacket() instanceof JingleIQ) {
            callKiller.immediateKill((JingleIQ) iqRequest.getOriginalPacket(), new Reason("No Relay", Reason.Type.connectivity_error));
        } else if (iqRequest.getOriginalPacket() instanceof Message) {
            callKiller.immediateKill((Message) iqRequest.getOriginalPacket(), new Reason("No Relay", Reason.Type.connectivity_error));
        }
    }

    @Override
    public boolean prepareInitiate(JingleIQ iq, CallSession session) {
        JID initiator = JIDFactory.getInstance().getJID(iq.getJingle().getInitiator());
        cleanUp();
        if (session.getRelayIQ() == null) {
            try {
                relayServiceProcessor.queryService(iq, initiator.getNode(), initiator.getNode(), this);
            } catch (ServiceException e) {
                log.error("Failed Querying Relay Service.", e);
            }
            return false;
        }
        return true;
    }

    private void cleanUp() {
        if (System.currentTimeMillis() > lastCheck + TIMEOUT) {
            lastCheck = System.currentTimeMillis();
            sessions.cleanUpExpired(TIMEOUT);
        }
    }

    @Override
    public boolean proceedInitiate(JingleIQ iq, CallSession session) {
        if (session != null) {
            if (session.getRelayIQ() != null) {
                sessions.put(session.getRelayIQ().getChannelId(), session);
                JingleProcessor.updateJingleTransport(iq, session.getRelayIQ());
            }
        }
        return true;
    }

    @Override
    public boolean proceedTerminate(JingleIQ iq, CallSession session) {
        return true;
    }

    @Override
    public boolean proceedAccept(JingleIQ iq, CallSession session) {
        if (session != null) {
            if (session.getRelayIQ() != null &&
                    (isUseOnForwardedCalls() || session.getForwardInitIq() == null)) {
                JingleProcessor.updateJingleTransport(iq, session.getRelayIQ());
            }
        }
        return true;
    }

    @Override
    public void proceedInfo(JingleIQ iq, CallSession session) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean prepareInitiate(Message msg, CallSession session, final SipChannel sipChannel) {
        JID initiator = null;
        try {
            initiator = msg.getParticipants().getInitiator();
            if (session.getRelayIQ() == null) {
                try {
                    relayServiceProcessor.queryService(msg, initiator.getNode(), initiator.getNode(), this);
                } catch (ServiceException e) {
                    log.error("Failed Querying Relay Service.", e);
                }
                return false;
            }
        } catch (SipParsingException e) {
            log.error("Parsing Exception on Prepare Initiate: " + msg.toString(), e);
            return false;
        }
        return true;
    }

    @Override
    public JingleIQ proceedSIPInitiate(JingleIQ iq, CallSession session, SipChannel channel) {
        cleanUp();
        if (session != null) {
            log.debug("SIP Initiate Trying to Update Transport SIP...");
            if (session.getRelayIQ() != null) {
                sessions.put(session.getRelayIQ().getChannelId(), session);
                return JingleProcessor.updateJingleTransport(iq, session.getRelayIQ());
            } else {
                log.debug("Trying to Update Transport SIP... Failed. No RelayIQ");
            }
        } else {
            log.debug("Trying to Update Transport SIP... Failed. No Session Found!");
        }
        return iq;
    }

    @Override
    public void proceedSIPInfo(JingleIQ iq, CallSession session, SipChannel channel) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public JingleIQ proceedSIPEarlyMedia(JingleIQ iq, CallSession session, SipChannel channel) {
        if (session != null) {
            log.debug("SIP Early Media Trying to Update Transport SIP...");
            if (session.getRelayIQ() != null) {
                sendRelayRedirect(iq, session.getRelayIQ());
                return JingleProcessor.updateJingleTransport(iq, session.getRelayIQ());
            } else {
                log.debug("Trying to Update Transport SIP... Failed. No RelayIQ");
            }
        } else {
            log.debug("Trying to Update Transport SIP... Failed. No Session Found!");
        }
        return iq;
    }

    private void sendRelayRedirect(final JingleIQ iq, final RelayIQ relayIQ) {
        final RelayRedirectIQ redirectIQ = new RelayRedirectIQ(true);
        redirectIQ.setChannelId(relayIQ.getChannelId());
        final Candidate candidate = iq.getJingle().getContent().getTransport().getCandidates().get(0);
        redirectIQ.setHost(candidate.getIp());
        redirectIQ.setPort(candidate.getPort());
        redirectIQ.setFrom(relayIQ.getTo());
        redirectIQ.setTo(relayIQ.getFrom());

        log.debug("Sending Redirect IQ: " + redirectIQ.toXML());
        relayServiceProcessor.getComponent().send(redirectIQ);
    }

    @Override
    public JingleIQ proceedSIPTerminate(JingleIQ iq, CallSession session, SipChannel channel) {
        return iq;
    }

    @Override
    public JingleIQ proceedSIPAccept(JingleIQ iq, CallSession session, SipChannel channel) {
        JingleIQ jiq = null;
        if (session != null) {
            log.debug("SIP Accept Trying to Update Transport SIP...");
            if (session.getRelayIQ() != null) {
                jiq = JingleProcessor.updateJingleTransport(iq, session.getRelayIQ());
            } else {
                log.debug("Trying to Update Transport SIP... Failed. No RelayIQ");
            }
        } else {
            log.debug("Trying to Update Transport SIP... Failed. No Session Found!");
        }
        return jiq;
    }

    public RelayServiceProcessor getRelayServiceProcessor() {
        return relayServiceProcessor;
    }

    public void setRelayServiceProcessor(RelayServiceProcessor relayServiceProcessor) {
        this.relayServiceProcessor = relayServiceProcessor;
    }

    public PrepareStatesManager getPrepareStatesManager() {
        return prepareStatesManager;
    }

    public void setPrepareStatesManager(PrepareStatesManager prepareStatesManager) {
        this.prepareStatesManager = prepareStatesManager;
    }

    public SipPrepareStatesManager getSipPrepareStatesManager() {
        return sipPrepareStatesManager;
    }

    public void setSipPrepareStatesManager(SipPrepareStatesManager sipPrepareStatesManager) {
        this.sipPrepareStatesManager = sipPrepareStatesManager;
    }

    @Override
    public void relayEventReceived(final RelayEventIQ iq) {
        log.debug("Relay Event Received: " + iq.toXML());
        if (RelayEventIQ.KILLED.equals(iq.getEvent())) {
            notifyCallKiller(iq);
        }
    }

    private void notifyCallKiller(RelayEventIQ iq) {
        log.debug("Notify Call Killer: " + iq.toXML());
        if (iq.getChannelId() != null) {
            final CallSession session = sessions.remove(iq.getChannelId());
            if (session != null && callKiller != null) {
                callKiller.immediateKill(session, new Reason(Reason.Type.connectivity_error));
            }
        }
    }

    public CallKiller getCallKiller() {
        return callKiller;
    }

    public void setCallKiller(CallKiller callKiller) {
        this.callKiller = callKiller;
    }

    public boolean isUseOnForwardedCalls() {
        return useOnForwardedCalls;
    }

    public void setUseOnForwardedCalls(boolean useOnForwardedCalls) {
        this.useOnForwardedCalls = useOnForwardedCalls;
    }

    public ConcurrentTimelineHashMap<String, CallSession> getSessions() {
        return sessions;
    }

    public void setSessions(ConcurrentTimelineHashMap<String, CallSession> sessions) {
        this.sessions = sessions;
    }
}
