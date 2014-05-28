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

package org.jinglenodes.charge;

import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jinglenodes.prepare.NodeFormat;
import org.jinglenodes.session.CallSession;
import org.jinglenodes.session.CallSessionMapper;
import org.xmpp.component.AbstractServiceProcessor;
import org.xmpp.component.IqRequest;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;

/**
 * Online charge service processor<br>
 *
 * Creates and handle responses for #consume messages to energy system
 *
 *  @see <a href="Hot Billing">https://docs.google.com/a/upptalk.com/document/d/1WGib4ectK2mwGwAz6rzBRYoLd5kcFt58nfIsYZO6WDM</a>
 *
 * @author bhlangonijr
 *
 */
public class OnlineChargeServiceProcessor extends AbstractServiceProcessor {
    private final Logger log = Logger.getLogger(OnlineChargeServiceProcessor.class);
    private final Element requestElement;
    private final String xmlns;
    private CallSessionMapper sessionMapper;
    private String onlineChargeService;
    private NodeFormat nodeFormat;

    public OnlineChargeServiceProcessor(final String elementName, final String xmlns) {
        this.xmlns = xmlns;
        this.requestElement = DocumentHelper.createElement(new QName(elementName, new Namespace("", xmlns)));
    }

    @Override
    public IQ createServiceRequest(Object object, String fromNode, String toNode) {
        if (object instanceof JingleIQ) {
            final JingleIQ jingleIQ = (JingleIQ) object;
            final CallSession session = sessionMapper.getSession(jingleIQ);
            if (session != null) {
                final OnlineChargeSession chargeSession = session.getOnlineChargeSession();
                if (chargeSession != null) {
                    toNode = nodeFormat.formatNode(toNode, fromNode);
                    fromNode = nodeFormat.formatNode(fromNode, null);
                    final JID to = JIDFactory.getInstance().getJID(null, onlineChargeService, null);
                    final JID from = JIDFactory.getInstance().getJID(fromNode, this.getComponentJID().getDomain(), null);
                    final IQ request = new IQ(IQ.Type.set);
                    request.setTo(to);
                    request.setFrom(from);
                    log.debug("Creating hot billing request SID: " + session.getId() + " Start: " +
                            session.getStartTime() + "ms Finish: " + session.getFinishTime() + "ms" +
                    " - time slice: " + chargeSession.getChargeSeconds() );
                    final int chargedTime = chargeSession.getChargeSeconds();
                    final String toBareJid = JIDFactory.getInstance().getJID(toNode, onlineChargeService, null).toBareJID();

                    final Element e = requestElement.createCopy();
                    e.addAttribute("initiator", from.toBareJID());
                    e.addAttribute("responder", toBareJid);
                    e.addAttribute("seconds", String.valueOf(chargedTime));
                    e.addAttribute("sid", jingleIQ.getJingle().getSid());

                    if (chargeSession.getSeqNumber() != null) {
                        Element esPrivate = e.addElement("es-private");
                        esPrivate.addAttribute("seqnr", chargeSession.getSeqNumber());
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("There is no es-private element to send: " + request.toXML());
                        }
                    }

                    request.setChildElement(e);

                    chargeSession.incChargeCount();

                    log.debug("Issuing hot billing request["+chargeSession.getChargeCount()+"]: " + request.toXML());
                    return request;
                } else {
                    log.error("No charge session found");
                }
            } else {
                log.error("No session found for creating Online Charge");
            }
        }
        return null;
    }

    @Override
    protected String getRequestId(Object obj) {
        if (obj instanceof JingleIQ) {
            final JingleIQ iq = (JingleIQ) obj;
            return iq.getJingle().getSid();
        }
        return null;
    }

    @Override
    protected void handleResult(IqRequest iq) {
        if (iq.getOriginalPacket() instanceof JingleIQ) {
            final CallSession session = sessionMapper.getSession((JingleIQ) iq.getOriginalPacket());
            if (session != null) {
                final OnlineChargeSession chargeSession = session.getOnlineChargeSession();
                if (chargeSession != null) {
                    chargeSession.incChargeCount();
                    log.debug("Incrementing credit session: " + chargeSession.getChargeCount());
                }
            }
        }
    }

    @Override
    protected void handleError(IqRequest iq) {
        log.error("Failed to Charge Account: " + iq.getResult().toXML());
    }

    @Override
    protected void handleTimeout(IqRequest request) {
        log.warn("Request timed out: " + request.getResult().toXML());
    }

    @Override
    public String getNamespace() {
        return xmlns;
    }

    public String getOnlineChargeService() {
        return onlineChargeService;
    }

    public void setOnlineChargeService(String chargeService) {
        this.onlineChargeService = chargeService;
    }

    public CallSessionMapper getSessionMapper() {
        return sessionMapper;
    }

    public void setSessionMapper(CallSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    public NodeFormat getNodeFormat() {
        return nodeFormat;
    }

    public void setNodeFormat(NodeFormat nodeFormat) {
        this.nodeFormat = nodeFormat;
    }

}
