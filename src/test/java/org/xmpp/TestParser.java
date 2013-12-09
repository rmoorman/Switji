package org.xmpp;

import junit.framework.TestCase;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.jinglenodes.jingle.Info;
import org.jinglenodes.jingle.Jingle;
import org.jinglenodes.jingle.Reason;
import org.jinglenodes.jingle.content.Content;
import org.jinglenodes.jingle.description.Description;
import org.jinglenodes.jingle.description.Payload;
import org.jinglenodes.jingle.processor.JingleException;
import org.jinglenodes.jingle.processor.JingleProcessor;
import org.jinglenodes.jingle.processor.JingleSipException;
import org.jinglenodes.jingle.transport.Candidate;
import org.jinglenodes.jingle.transport.RawUdpTransport;
import org.jinglenodes.prepare.CallPreparation;
import org.jinglenodes.session.CallSession;
import org.jinglenodes.sip.processor.SipProcessor;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.tinder.JingleIQ;
import org.zoolu.sip.message.JIDFactory;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.Participants;
import org.zoolu.sip.message.SipParsingException;

public class TestParser extends TestCase {

    final private String source = "<jingle xmlns=\"urn:xmpp:jingle:1\" action=\"session-initiate\" sid=\"abc\" initiator=\"a@a.com\" responder=\"b@b.com\">\n" +
            "  <content creator=\"initiator\" name=\"audio\" senders=\"both\">\n" +
            "    <description xmlns=\"urn:xmpp:jingle:apps:rtp:1\" media=\"audio\"></description>\n" +
            "    <transport xmlns=\"urn:xmpp:jingle:transports:raw-udp:1\">\n" +
            "      <candidate ip=\"10.166.108.22\" port=\"10000\" generation=\"0\" type=\"host\"/>\n" +
            "    </transport>\n" +
            "  </content>\n" +
            "</jingle>";
    final private String altSource = "<jingle xmlns=\"urn:xmpp:jingle:1\" action=\"session-initiate\" sid=\"abc\" initiator=\"a@a.com\" responder=\"b@b.com\">  <content creator=\"initiator\" name=\"audio\" senders=\"both\"><description xmlns=\"urn:xmpp:jingle:apps:rtp:1\" media=\"audio\">      <payload-type id=\"18\" name=\"G729\" clockrate=\"0\" channels=\"1\"/></description><transport xmlns=\"urn:xmpp:jingle:transports:raw-udp:1\">      <candidate ip=\"10.166.108.22\" port=\"10000\" generation=\"0\" type=\"host\"/></transport></content></jingle>";
    final String initiator = "a@a.com";
    final String responder = "b@b.com";

    public void testGenParser() {
        final Jingle jingle = new Jingle("abc", initiator, responder, Jingle.SESSION_INITIATE);
        jingle.setContent(new Content("initiator", "audio", "both", new Description("audio"), new RawUdpTransport(new Candidate("10.166.108.22", "10000", "0"))));
        jingle.getContent().getDescription().addPayload(Payload.G729);
        final JingleIQ jingleIQ = new JingleIQ(jingle);
        //assertEquals(jingleIQ.getChildElement().element("jingle").asXML(), source);
        System.out.println(jingleIQ.toXML());
        final JingleIQ jingleIQParsed = JingleIQ.fromXml(jingleIQ);
        System.out.println(jingleIQParsed.getChildElement().element("jingle").asXML());
        //assertEquals(source, jingleIQParsed.getChildElement().element("jingle").asXML());
        //assertEquals(jingleIQParsed.getJingle().getInitiator(), initiator);
        JingleIQ.getStream().fromXML(altSource);
        System.out.println(source);
    }

    final private String sourceTerminate = "<jingle xmlns=\"urn:xmpp:jingle:1\" action=\"session-terminate\" sid=\"abc\" initiator=\"a@a.com\" responder=\"b@b.com\">\n" +
            "  <reason><success/></reason>\n" +
            "</jingle>";


    public void testDoubleParse() throws DocumentException {

        final String initiator = "romeo@localhost";
        final String responder = "juliet@localhost";
        final String packet = "<jingle xmlns=\"urn:xmpp:jingle:1\" action=\"session-initiate\" initiator=\"" + initiator + "\" responder=\"" + responder + "\" sid=\"37665\"><content xmlns=\"\" creator=\"initiator\" name=\"audio\" senders=\"both\"><description xmlns=\"urn:xmpp:jingle:apps:rtp:1\"><payload-type xmlns=\"\" id=\"0\" name=\"PCMU\"/></description><transport xmlns=\"urn:xmpp:jingle:transports:raw-udp:1\"><candidate xmlns=\"\" ip=\"192.168.20.172\" port=\"22000\" generation=\"0\"/></transport></content></jingle>";

        Document doc = DocumentHelper.parseText(packet);

        final IQ iq = new IQ(doc.getRootElement());
        final JingleIQ jingleIQ = JingleIQ.fromXml(iq);
        jingleIQ.setFrom(initiator);
        jingleIQ.setTo("sip.localhost");

        final JingleIQ newJingle = JingleIQ.fromXml(jingleIQ);
        assertTrue(newJingle.getJingle().getContent().getDescription() != null);
    }

    public void testGetNode(){
        final String n = new JID("a@b.com").getNode();
        assertEquals("a", n);
    }

    public void testGenParserTerminate() {
        final Jingle jingle = new Jingle("abc", initiator, responder, Jingle.SESSION_TERMINATE);
        jingle.setReason(new Reason(Reason.Type.success));
        final JingleIQ jingleIQ = new JingleIQ(jingle);
        //assertEquals(jingleIQ.getChildElement().element("jingle").asXML(), sourceTerminate);
        System.out.println(jingleIQ.toXML());
        final JingleIQ jingleIQParsed = JingleIQ.fromXml(jingleIQ);
        System.out.println(jingleIQParsed.getChildElement().element("jingle").asXML());
        assertEquals(sourceTerminate, jingleIQParsed.getChildElement().element("jingle").asXML());
        assertEquals(jingleIQParsed.getJingle().getInitiator(), initiator);
    }

    public void testRingingPacket() {

        final String initiator = "romeo@localhost";
        final String responder = "juliet@localhost";

        final Jingle jingle = new Jingle("12121", initiator, responder, Jingle.SESSION_INFO);
        jingle.setInfo(new Info());
        final JingleIQ iq = new JingleIQ(jingle);
        iq.setTo(initiator);
        iq.setFrom(responder);

        System.out.println(jingle.toString());
        System.out.println(iq.toXML());

    }

    public void testSIPParsing() throws JingleSipException {
        final String sipString = "SIP/2.0 200 OK\n" +
                "Via: SIP/2.0/UDP 194.183.72.28:5060;branch=z9hG4bK7942901908306987;received=178.33.112.237;rport=5062\n" +
                "From: \"+31651827042@yuilop.tv\" <sip:+31651827042@yuilop.tv>;tag=A(1.6)(6295399E195B795057FD01CF6D65301CB2E41499)\n" +
                "To: \"0031611537782\" <sip:0031611537782@sip.yuilop.tv>;tag=as3efe9f43\n" +
                "Call-ID: 7942901908306987\n" +
                "CSeq: 1 INVITE\n" +
                "User-Agent: Asterisk PBX 1.6.0.26-FONCORE-r78\n" +
                "Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REFER, SUBSCRIBE, NOTIFY, INFO\n" +
                "Supported: replaces, timer\n" +
                "Contact: <sip:0031611537782@194.183.72.28>\n" +
                "Content-Type: application/sdp\n" +
                "Content-Length: 124\n" +
                "\n" +
                "v=0\n" +
                "o=root 2139395421 2139395422 IN IP4 194.183.72.28\n" +
                "s=Asterisk PBX 1.6.0.26-FONCORE-r78\n" +
                "c=IN IP4 194.183.72.28\n" +
                "t=0 0\n" +
                "m=audio 0 RTP/AVP 18 112 3 0";

        final Message m = new Message(sipString);

        final Content c = SipProcessor.getContent("v=0\n" +
                "o=root 2139395421 2139395422 IN IP4 194.183.72.28\n" +
                "s=Asterisk PBX 1.6.0.26-FONCORE-r78\n" +
                "c=IN IP4 194.183.72.28\n" +
                "t=0 0\n" +
                "m=audio 0 RTP/AVP 18 112 3 0");



        System.out.println(m.toString());
    }

    public void testSIPInfoParsing() throws JingleSipException, SipParsingException {
        final String sipString = "\n" +
                "SIP/2.0 183 Session Progress\n" +
                "Via: SIP/2.0/UDP 213.232.148.92:5060;branch=z9hG4bKA28u005511997158437x131125;received=178.33.112.237;rport=5062\n" +
                "From: \"+5511997158437@ym.ms\" <sip:+5511997158437@ym.ms>;tag=Ax1.9.3.3200xx43532CCE7F832F320B6632A1D64E9F64ED36F0C8x\n" +
                "To: \"005511950505668\" <sip:005511950505668@213.232.148.92>;tag=as12426bbb\n" +
                "Call-ID: A28u005511997158437x131125\n" +
                "CSeq: 1 INVITE\n" +
                "Server: Asterisk PBX 1.8.13.0\n" +
                "Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REFER, SUBSCRIBE, NOTIFY, INFO, PUBLISH\n" +
                "Supported: replaces, timer\n" +
                "Contact: <sip:005511950505668@213.232.148.92:5060>\n" +
                "Content-Type: application/sdp\n" +
                "Content-Length: 410\n" +
                "\n" +
                "v=0\n" +
                "o=root 1026971017 1026984194 IN IP4 213.232.148.92\n" +
                "s=Asterisk PBX 1.8.13.0\n" +
                "c=IN IP4 213.232.148.92\n" +
                "t=0 0\n" +
                "m=audio 17196 RTP/AVP 18 3 112 8 0 101\n" +
                "a=rtpmap:18 G729/8000\n" +
                "a=fmtp:18 annexb=no\n" +
                "a=rtpmap:3 GSM/8000\n" +
                "a=rtpmap:112 iLBC/8000\n" +
                "a=fmtp:112 mode=30\n" +
                "a=rtpmap:8 PCMA/8000\n" +
                "a=rtpmap:0 PCMU/8000\n" +
                "a=rtpmap:101 telephone-event/8000\n" +
                "a=fmtp:101 0-16\n" +
                "a=silenceSupp:off - - - -\n" +
                "a=ptime:20\n" +
                "a=sendrecv";

        final Message m = new Message(sipString);

        sendJingleRinging(m);


        System.out.println(m.toString());
    }

    public final void sendJingleRinging(final Message msg) throws JingleSipException, SipParsingException {


        final Participants participants = msg.getParticipants();

        final JID initiator = participants.getInitiator();
        final JID responder = participants.getResponder();
        JID to = initiator;

        final JingleIQ iq = JingleProcessor.createJingleSessionInfo(initiator, responder, to.toString(), msg.getCallIdHeader().getCallId(), Info.Type.ringing);

        System.out.println(iq);

    }


    public void testJID(){

        Jingle jingle = new Jingle("asdasd12e21d",
                "+4915738512828@test.ym.ms/Ax1.9.3180xxD0622D16ABBF6C26EAED9D96C981DE791A6F503Dx",
                "+4915750599999@178.33.162.38/Ax1.9.3180xxD0622D16ABBF6C26asdasd9D96C981DE791A6F503Dx",
                "session-terminate");

        JingleIQ iq = new JingleIQ(jingle);


        System.out.println(iq.toXML());


        final JID j = JIDFactory.getInstance().getJID(iq.getJingle().getResponder());
        final JID z = JIDFactory.getInstance().getJID(iq.getJingle().getInitiator());

        iq.setTo(j.toFullJID());
        iq.setFrom(z.toFullJID());


        final JingleIQ terminationIQ = JingleProcessor.createJingleTermination(
                j, z,
                j.toFullJID(),
                iq.getJingle().getReason(), "qweqeqweqweqweqwe");

        terminationIQ.setFrom(z.toFullJID());

        System.out.println(terminationIQ);



    }

    public void testCallKiller() {


        Jingle jingle = new Jingle("asdasd12e21d",
                "+4915738512828@test.ym.ms/Ax1.9.3180xxD0622D16ABBF6C26EAED9D96C981DE791A6F503Dx",
                "+4915750599998@178.33.162.38/Ax1.9.3180xxD0622D16ABBF6C26EAED9D96C981DE791A6F503Dx",
                "session-accept");

        final JingleIQ jingleIq = new JingleIQ(jingle);

        final JID initiator = JIDFactory.getInstance().getJID(jingleIq.getJingle().getInitiator());
        final JID responder = JIDFactory.getInstance().getJID(jingleIq.getJingle().getResponder());

        jingleIq.setTo(initiator);
        jingleIq.setFrom(responder);

        final JingleIQ terminationIQ = JingleProcessor.createJingleTermination(
                initiator, responder,
                responder.toFullJID(),
                jingleIq.getJingle().getReason(), jingleIq.getJingle().getSid());

        terminationIQ.setFrom(initiator.toFullJID());

        final JingleIQ backTerminationIQ = JingleProcessor.createJingleTermination(jingleIq,
                jingleIq.getJingle().getReason());
        backTerminationIQ.setTo(jingleIq.getFrom());
        backTerminationIQ.setFrom((JID) null);
        System.out.println("Call Killer Back Terminate: " + backTerminationIQ.toString());
        System.out.println("Call Killer Terminate: " + terminationIQ.toString());


    }

}
