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

package org.jinglenodes.credit;

import org.apache.log4j.Logger;
import org.jinglenodes.callkiller.CallKiller;
import org.jinglenodes.jingle.Reason;
import org.jinglenodes.prepare.CallPreparation;
import org.jinglenodes.prepare.PrepareStatesManager;
import org.jinglenodes.session.CallSession;
import org.xmpp.component.IqRequest;
import org.xmpp.component.ResultReceiver;
import org.xmpp.component.ServiceException;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.SipChannel;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 3/26/12
 * Time: 1:52 PM
 */

public class CreditPreparation extends CallPreparation implements ResultReceiver {

    final Logger log = Logger.getLogger(CreditPreparation.class);
    private CreditServiceProcessor creditServiceProcessor;
    private PrepareStatesManager prepareStatesManager;
    private CallKiller callKiller;

    @Override
    public void receivedResult(final IqRequest iqRequest) {
        if (iqRequest.getOriginalPacket() instanceof JingleIQ) {
            prepareStatesManager.prepareCall((JingleIQ) iqRequest.getOriginalPacket(), null);
        }
    }

    @Override
    public void receivedError(IqRequest iqRequest) {
        if (iqRequest.getOriginalPacket() instanceof JingleIQ) {
            final JingleIQ iq = (JingleIQ) iqRequest.getOriginalPacket();
            prepareStatesManager.cancelCall(iq, null, new Reason(Reason.Type.forbidden));
        }
    }

    @Override
    public void timeoutRequest(IqRequest iqRequest) {
        //TODO Implement Retry
    }

    @Override
    public boolean prepareInitiate(JingleIQ iq, CallSession session) {
        JID responder = JIDFactory.getInstance().getJID(iq.getJingle().getResponder());
        log.debug("Preparing Initiate from: " + iq.getFrom() + " iq:" + iq.toXML());
        if (session.getRelayIQ() == null) {
            try {
                creditServiceProcessor.queryService(iq, iq.getFrom().getNode(), responder.getNode(), this);
            } catch (ServiceException e) {
                log.error("Failed Querying Credit Service.", e);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean proceedInitiate(JingleIQ iq, CallSession session) {
        return verifyCredits(session);
    }

    public CreditServiceProcessor getCreditServiceProcessor() {
        return creditServiceProcessor;
    }

    public void setCreditServiceProcessor(CreditServiceProcessor creditServiceProcessor) {
        this.creditServiceProcessor = creditServiceProcessor;
    }

    public PrepareStatesManager getPrepareStatesManager() {
        return prepareStatesManager;
    }

    public void setPrepareStatesManager(PrepareStatesManager prepareStatesManager) {
        this.prepareStatesManager = prepareStatesManager;
    }

    @Override
    public boolean proceedTerminate(JingleIQ iq, CallSession session) {
        log.debug("Credit Proceed Terminate: " + iq.toXML() + " - " + session.getId());
        setSessionFinishTime(session, System.currentTimeMillis());
        if (callKiller != null) {
            callKiller.cancelKill(session);
        }
        return true;
    }

    @Override
    public boolean proceedAccept(JingleIQ iq, CallSession session) {
        startCharging(session);
        return true;
    }

    private void startCharging(CallSession session) {
        setSessionStartTime(session, System.currentTimeMillis());
        if (callKiller != null) {
            if (session.getSessionCredit() != null) {
                final int seconds = session.getSessionCredit().getMaxDurationInSeconds();
                callKiller.scheduleKill(session, seconds);
            } else {
                log.warn("SEVERE - Call Processed without Credits(sid): " + session.getId());
                callKiller.immediateKill(session, new Reason("No Credits", Reason.Type.payment));
            }
        }
    }

    @Override
    public void proceedInfo(JingleIQ iq, CallSession session) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private void setSessionStartTime(final CallSession session, final long time) {
        if (session != null) {
            session.setStartTime(time);
        }
    }

    private void setSessionFinishTime(final CallSession session, final long time) {
        if (session != null) {
            session.setFinishTime(time);
        }
    }

    @Override
    public boolean prepareInitiate(Message msg, CallSession session, final SipChannel sipChannel) {
        return true;
    }

    @Override
    public JingleIQ proceedSIPInitiate(JingleIQ iq, CallSession session, SipChannel channel) {
        verifyCredits(session);
        return iq;
    }

    @Override
    public void proceedSIPInfo(JingleIQ iq, CallSession session, SipChannel channel) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private boolean verifyCredits(CallSession session) {
        if (session != null) {
            final SessionCredit sessionCredit = session.getSessionCredit();
            if (sessionCredit == null || sessionCredit.getMaxDurationInSeconds() < 1 || SessionCredit.RouteType.ip.equals(sessionCredit.getRouteType())) {
                callKiller.immediateKill(session, new Reason("No Credits", Reason.Type.payment));
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public JingleIQ proceedSIPEarlyMedia(JingleIQ iq, CallSession session, SipChannel channel) {
        return iq;
    }

    @Override
    public JingleIQ proceedSIPTerminate(JingleIQ iq, CallSession session, SipChannel channel) {
        setSessionFinishTime(session, System.currentTimeMillis());
        if (callKiller != null) {
            callKiller.cancelKill(session);
        }
        return iq;
    }

    @Override
    public JingleIQ proceedSIPAccept(JingleIQ iq, CallSession session, SipChannel channel) {
        startCharging(session);
        return iq;
    }

    public CallKiller getCallKiller() {
        return callKiller;
    }

    public void setCallKiller(CallKiller callKiller) {
        this.callKiller = callKiller;
    }
}
