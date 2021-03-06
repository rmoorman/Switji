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
package org.jinglenodes.callkiller;

import org.apache.log4j.Logger;
import org.jinglenodes.jingle.Reason;
import org.jinglenodes.jingle.processor.JingleException;
import org.jinglenodes.jingle.processor.JingleProcessor;
import org.jinglenodes.session.CallSession;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 5/23/12
 * Time: 11:07 AM
 */
public class CallKillerTask implements Runnable {

    private final Logger log = Logger.getLogger(CallKillerTask.class);
    private final CallSession session;
    private final JingleProcessor jingleProcessor;
    private final Reason reason;

    public CallKillerTask(CallSession session, JingleProcessor jingleProcessor, final Reason reason) {
        this.session = session;
        this.jingleProcessor = jingleProcessor;
        this.reason = reason;
    }

    @Override
    public void run() {
        if (session != null) {
            if (session.isActive() && session.isConnected()) {
                log.warn("Killing Call: " + session.getId() + " Proceeds: " + session.getProceeds().size());
                try {
                    final JID to = getDestination();

                    final JingleIQ jingleIq = session.getAcceptIQ() == null ?
                            session.getInitiateIQ() : session.getAcceptIQ();

                    JingleIQ terminationIQ = createTerminate(jingleIq, to);

                    session.setCallKilled(true);

                    if (log.isDebugEnabled() ) {
                        if (session.getAcceptIQ() == null) {
                            log.debug("Using initiateIQ to create Terminate: "+jingleIq.toString());
                        } else {
                            log.debug("Using acceptIQ to create Terminate: "+jingleIq.toString());
                        }
                    }

                    try {
                        jingleProcessor.processJingle(terminationIQ);
                    } catch (JingleException e) {
                        log.error("Failed to Force Termination Process", e);
                    }

                    //HACK: in case a preparation has overwritten terminateIQ info
                    terminationIQ = createTerminate(jingleIq, to);

                    final JingleIQ backTerminationIQ = JingleProcessor.
                            createJingleTermination(session.getInitiateIQ(), reason);

                    backTerminationIQ.setTo(to);

                    backTerminationIQ.setFrom((JID) null);

                    if (log.isDebugEnabled() ) {
                        log.debug("Call Killer Back Terminate: " + backTerminationIQ.toString());
                        log.debug("Call Killer Terminate: " + terminationIQ.toString());
                    }

                    jingleProcessor.send(backTerminationIQ);
                    jingleProcessor.send(terminationIQ);

                } catch (Exception e) {
                    log.error("Could not Kill Properly Call: " + session.getId(), e);
                }
            }
        } else {
            log.warn("Unable to kill null Session");
        }
    }

    private JingleIQ createTerminate(final JingleIQ jingleIq, JID to) {

        final JID initiator = JIDFactory.getInstance().getJID(jingleIq.getJingle().getInitiator());
        final JID responder = JIDFactory.getInstance().getJID(jingleIq.getJingle().getResponder());

        final JingleIQ terminationIQ = JingleProcessor.createJingleTermination(
                initiator, responder, to.toString(),
                reason, jingleIq.getJingle().getSid());

        terminationIQ.setFrom(session.isJingleInitiator() ?
                initiator : responder);

        return terminationIQ;
    }

    private JID getDestination() {
        JID to;

        if (session.isJingleInitiator()) {
            to = session.getInitiateIQ().getFrom();
        } else {
            if (session.getAcceptIQ() != null) {
                to = session.getAcceptIQ().getFrom();
            } else {
                to = session.getInitiateIQ().getTo();
            }
        }
        return to;
    }


}
