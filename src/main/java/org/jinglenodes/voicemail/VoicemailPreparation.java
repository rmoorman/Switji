package org.jinglenodes.voicemail;

import org.apache.log4j.Logger;
import org.jinglenodes.jingle.processor.JingleProcessor;
import org.jinglenodes.prepare.CallPreparation;
import org.jinglenodes.session.CallSession;
import org.xmpp.component.ExternalComponent;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.SipChannel;
import org.zoolu.tools.ConcurrentTimelineHashMap;
import org.zoolu.tools.NamingThreadFactory;

import java.util.concurrent.*;

/**
 * Voicemail preparation - handle voicemail forward/redirect
 *
 * @author bhlangonijr
 *         Date: 5/20/13
 *         Time: 3:20 PM
 */
public class VoicemailPreparation extends CallPreparation {
    final Logger log = Logger.getLogger(VoicemailPreparation.class);
    private static final int DEFAULT_MAX_ENTRIES = 20000;
    private static final long DEFAULT_CALL_TIMEOUT = 45 * 1000;

    private final ConcurrentTimelineHashMap<String, Future> pendingCalls;
    private final long callTimeout;
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(
            5, new NamingThreadFactory("voicemail-preparation-thread"));

    private final JingleProcessor jingleProcessor;
    private ExternalComponent component;
    private String voicemailService;

    public VoicemailPreparation(JingleProcessor jingleProcessor) {
        this(DEFAULT_CALL_TIMEOUT, jingleProcessor);
    }

    public VoicemailPreparation(long callTimeout, JingleProcessor jingleProcessor) {
        this.pendingCalls = new ConcurrentTimelineHashMap<String, Future>(DEFAULT_MAX_ENTRIES, callTimeout * 2);
        this.callTimeout = callTimeout;
        this.jingleProcessor = jingleProcessor;
    }

    @Override
    public boolean prepareInitiate(JingleIQ iq, CallSession session) {
        return true;
    }

    @Override
    public boolean proceedInitiate(JingleIQ iq, final CallSession session) {

        log.debug("Jingle initiate " + iq);

        try {

            scheduleVoicemailTask(iq, session);

        } catch (Exception e) {
            log.error("Error initiate jingle proceed",e);
        }

        return true;

    }

    @Override
    public boolean proceedTerminate(JingleIQ iq, CallSession session) {

        log.debug("Jingle terminate " + iq);

        final String sid = iq.getJingle().getSid();

        if (session.getForwardInitIq() == null) {
            // if calee hangs up before accepting the call, caller will be forwarded to voicemail
            if (pendingCalls.containsKey(sid) && !session.isCallKilled()
                    && !session.isJingleInitiator()) {
                handleForwardCall(session);
            }

            try {
                cancelTask(sid);
            } catch (Exception e) {
                log.error("Error canceling task",e);
            }
        }

        return true;
    }

    @Override
    public boolean proceedAccept(JingleIQ iq, CallSession session) {

        log.debug("Jingle accept " + iq);

        try {

            if (session.getForwardInitIq() == null) {
                cancelTask(iq.getJingle().getSid());
                if (log.isDebugEnabled()) {
                    log.debug("Jingle Call accepted. Cancelling scheduled voicemail forward.. " + iq);
                }
            } else {
                final JingleIQ initiateIQ = session.getInitiateIQ();

                if (initiateIQ != null) {
                    iq.setTo(initiateIQ.getFrom().toBareJID());
                } else {
                    log.error("Initiate IQ not found in call session: "+session.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error canceling task",e);
        }
        return true;
    }

    @Override
    public void proceedInfo(JingleIQ iq, CallSession session) {

    }

    @Override
    public boolean prepareInitiate(Message msg, CallSession session, SipChannel channel) {
        return true;
    }

    @Override
    public JingleIQ proceedSIPInitiate(JingleIQ iq, CallSession session, SipChannel channel) {

        log.debug("SIP INITIATE " + iq);

        try {

            scheduleVoicemailTask(iq, session);

        } catch (Exception e) {
            log.error("Error initiate sip proceed",e);
        }

        return iq;
    }

    @Override
    public void proceedSIPInfo(JingleIQ iq, CallSession session, SipChannel channel) {
        log.debug("SIP INFO " + iq);
    }

    @Override
    public JingleIQ proceedSIPEarlyMedia(JingleIQ iq, CallSession session, SipChannel channel) {
        return iq;
    }

    @Override
    public JingleIQ proceedSIPTerminate(JingleIQ iq, CallSession session, SipChannel channel) {

        log.debug("SIP Terminate " + iq);

        final String sid = iq.getJingle().getSid();

        if (session.getForwardInitIq() == null) {
            // if calee hangs up before accepting the call, caller will be forwarded to voicemail
            if (pendingCalls.containsKey(sid) && !session.isCallKilled()
                    && session.isJingleInitiator()) {
                handleForwardCall(session);
            }

            try {
                cancelTask(sid);
            } catch (Exception e) {
                log.error("Error canceling task",e);
            }
        } else {
            final JingleIQ initiateIQ = session.getInitiateIQ();

            if (initiateIQ != null) {

                iq = JingleProcessor.createJingleTermination(
                        JIDFactory.getInstance().getJID(initiateIQ.getJingle().getInitiator()),
                        JIDFactory.getInstance().getJID(initiateIQ.getJingle().getResponder()),
                        initiateIQ.getFrom().toBareJID(),
                        iq.getJingle().getReason(), initiateIQ.getJingle().getSid());
            } else {
                log.error("Initiate IQ not found in call session: "+session.getId());
            }
        }

        return iq;
    }

    @Override
    public JingleIQ proceedSIPAccept(JingleIQ iq, CallSession session, SipChannel channel) {
        log.debug("SIP accept " + iq);
        try {

            if (session.getForwardInitIq() == null) {
                cancelTask(iq.getJingle().getSid());
                if (log.isDebugEnabled()) {
                    log.debug("Jingle Call accepted. Cancelling scheduled voicemail forward.. " + iq);
                }
            } else {
                final JingleIQ initiateIQ = session.getInitiateIQ();

                if (initiateIQ != null) {

                    iq = JingleProcessor.createJingleAccept(
                            JIDFactory.getInstance().getJID(initiateIQ.getJingle().getInitiator()),
                            JIDFactory.getInstance().getJID(initiateIQ.getJingle().getResponder()),
                            initiateIQ.getFrom().toBareJID(),
                            initiateIQ.getJingle().getContent(), initiateIQ.getJingle().getSid());
                } else {
                    log.error("Initiate IQ not found in call session: "+session.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error canceling task",e);
        }
        return iq;
    }


    public void scheduleVoicemailTask(JingleIQ iq, CallSession session) {

        final Future future = service.schedule(new VoicemailForwardTask(session,this),
                getCallTimeout(), TimeUnit.MILLISECONDS);

        pendingCalls.put(iq.getJingle().getSid(), future);

    }


    public long getCallTimeout() {
        return callTimeout;
    }

    public boolean cancelTask(final String sid) {

        boolean result = false;

        Future f = pendingCalls.remove(sid);

        if (f != null && !f.isDone() && !f.isCancelled()) {
            result = f.cancel(true);
        }

        return result;
    }

    /*
     * forward the call to the voicemail service
     */
    public void handleForwardCall(CallSession callSession) {

        log.debug("Handling forward call: "+callSession.getId());

        final JingleIQ initiateIQ = callSession.getInitiateIQ();

        if (initiateIQ != null) {

            final String to = getVoicemailService();
            final String from = component.getJID().toBareJID();

            if (callSession.isJingleInitiator()) {
                jingleProcessor.sendSipTermination(initiateIQ, callSession);
            } else {
                jingleProcessor.sendJingleTermination(initiateIQ, callSession);
            }

            final JingleIQ iniIQ = JingleProcessor.createJingleInitialization(
                    JIDFactory.getInstance().getJID(initiateIQ.getJingle().getInitiator()),
                    JIDFactory.getInstance().getJID(initiateIQ.getJingle().getResponder()),
                    to,initiateIQ.getJingle().getContent(), initiateIQ.getJingle().getSid());

            iniIQ.setFrom(from);

            jingleProcessor.send(iniIQ);

            callSession.setForwardInitIq(iniIQ);

            log.debug("Forward call to Voicemail component: "+iniIQ);


        } else {
            log.error("Initiate IQ not found in call session: "+callSession.getId());
        }

    }

    public ConcurrentTimelineHashMap<String, Future> getPendingCalls() {
        return pendingCalls;
    }

    public JingleProcessor getJingleProcessor() {
        return jingleProcessor;
    }

    public ExternalComponent getComponent() {
        return component;
    }

    public void setComponent(ExternalComponent component) {
        this.component = component;
    }

    public String getVoicemailService() {
        return voicemailService;
    }

    public void setVoicemailService(String voicemailService) {
        this.voicemailService = voicemailService;
    }
}
