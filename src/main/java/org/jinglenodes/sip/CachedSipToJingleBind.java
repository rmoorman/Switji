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

package org.jinglenodes.sip;

import org.apache.log4j.Logger;
import org.jinglenodes.prepare.NodeFormat;
import org.jinglenodes.prepare.PrefixNodeFormat;
import org.jinglenodes.sip.account.SipAccount;
import org.jinglenodes.sip.account.SipAccountProvider;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;
import org.zoolu.tools.ConcurrentTimelineHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 2/22/12
 * Time: 12:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class CachedSipToJingleBind implements SipToJingleBind {

    private static final Logger log = Logger.getLogger(CachedSipToJingleBind.class);
    private final ConcurrentTimelineHashMap<String, JID> sipToXmpp;
    private final ConcurrentTimelineHashMap<String, JID> xmppToSip;

    private JID defaultJID;
    private String defaultResource;
    private SipAccountProvider accountProvider;
    private NodeFormat format;


    public CachedSipToJingleBind() {
        sipToXmpp = new ConcurrentTimelineHashMap<String, JID>();
        xmppToSip = new ConcurrentTimelineHashMap<String, JID>();
        format = new PrefixNodeFormat(); //default
    }


    public CachedSipToJingleBind(int maxEntries, long timeToLive, long purgeDelay) {
        sipToXmpp = new ConcurrentTimelineHashMap<String, JID>(maxEntries, timeToLive, purgeDelay);
        xmppToSip = new ConcurrentTimelineHashMap<String, JID>(maxEntries, timeToLive, purgeDelay);
        format = new PrefixNodeFormat(); //default
        sipToXmpp.enableScheduledPurge();
        xmppToSip.enableScheduledPurge();
    }


    public void addXmppToBind(final JID sipTo, final JID xmppTo) {
        log.debug("add XMPP Bind: " + sipTo.toString() + ":" + xmppTo.toString());

        String toNode = format.formatNode(sipTo.getNode(),null);

        sipToXmpp.put(toNode, xmppTo);
    }

    public JID getXmppTo(JID sipTo, final JingleIQ lastReceivedJingle) {
        log.debug("Get XMPP Bind: " + sipTo.toString());
        if (lastReceivedJingle != null) {
            log.debug("Get XMPP Bind 1: " + sipTo.toString() + " returned: " + lastReceivedJingle);
            return lastReceivedJingle.getFrom();
        }

        String toNode = format.formatNode(sipTo.getNode(), null);

        final JID jid = sipToXmpp.get(toNode);
        if (jid != null) {
            log.debug("Get XMPP Bind(2): " + sipTo.toString() + " returned: " + lastReceivedJingle);
            return jid;
        }

        return null;
    }

    public JID getSipFrom(JID xmppInitiator) {

        String fromBare = format.formatNode(xmppInitiator.toBareJID(), null);

        final JID jid = xmppToSip.get(fromBare);
        if (jid != null) return jid;

        if (accountProvider != null) {
            final SipAccount account = accountProvider.getSipAccount(JIDFactory.getInstance().getJID(fromBare));
            if (account != null) {
                final JID accountJid = JIDFactory.getInstance().getJID(account.getSipUsername() + "@" + account.getServer() + "/" + defaultResource);
                xmppToSip.put(xmppInitiator.toBareJID(), accountJid);
                return accountJid;
            }
        }

        return null;
    }

    public JID getDefaultJID() {
        return defaultJID;
    }

    public void setDefaultJID(JID defaultJID) {
        this.defaultJID = defaultJID;
    }

    public String getDefaultResource() {
        return defaultResource;
    }

    public void setDefaultResource(String defaultResource) {
        this.defaultResource = defaultResource;
    }

    public SipAccountProvider getAccountProvider() {
        return accountProvider;
    }

    public void setAccountProvider(SipAccountProvider accountProvider) {
        this.accountProvider = accountProvider;
    }

    public NodeFormat getFormat() {
        return format;
    }

    public void setFormat(NodeFormat format) {
        this.format = format;
    }
}
