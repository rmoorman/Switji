package org.xmpp.component;

import org.dom4j.Element;
import org.jinglenodes.prepare.IQNormalizer;
import org.jinglenodes.relay.RelayIQ;
import org.jivesoftware.whack.ExternalComponentManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.tinder.JingleIQ;

public class MockExternalComponentManager extends ExternalComponentManager {

    private String accountServiceNamespace;
    private String creditServiceNamespace;
    private IQNormalizer iqNormalizer;


    public MockExternalComponentManager(String host, int port) {
        super(host, port);
    }

    @Override
    public void addComponent(String subdomain, Component component) throws ComponentException {
        //Do nothing
    }

    @Override
    public void sendPacket(Component component, Packet packet) {
        //Do Nothing
        System.out.println("Fake Sending: " + packet.toXML());
        if (component instanceof ExternalComponent) {
            final ExternalComponent externalComponent = ((ExternalComponent) component);
            packet.setFrom(externalComponent.getJID());
            if (getIqNormalizer() != null && (IQ)packet instanceof JingleIQ ) {
                System.out.println("** Packet" + packet.toString());
                packet = iqNormalizer.deNormalize(JingleIQ.fromXml((IQ)packet));
            }
            //Fake Responses
            if (packet instanceof IQ) {
                if (((IQ) packet).getChildElement() != null) {
                    if (((IQ) packet).getChildElement().getNamespace().getStringValue().equals(accountServiceNamespace)) {
                        final IQ reply = IQ.createResultIQ((IQ) packet);
                        reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                        final Element e = reply.getChildElement().addElement("phone");
                        e.addAttribute("type", "private");
                        e.addAttribute("number", "0033557565");
                        final Element ee = reply.getChildElement().addElement("phone");
                        ee.addAttribute("type", "jinglee");
                        ee.addAttribute("number", "0033557564");
                        externalComponent.handleIQResult(reply);
                    } else if (((IQ) packet).getChildElement().getNamespace().getStringValue().equals(RelayIQ.NAMESPACE)) {
                        final RelayIQ reply = new RelayIQ(false);
                        reply.setFrom(packet.getTo());
                        reply.setTo(packet.getFrom());
                        reply.setID(packet.getID());
                        reply.setChannelId("TestChannel");
                        reply.setHost("80.80.80.80");
                        reply.setLocalport("10000");
                        reply.setRemoteport("10002");
                        externalComponent.handleIQResult(reply);
                    } else if (((IQ) packet).getChildElement().getNamespace().getStringValue().equals(creditServiceNamespace)) {
                        final IQ reply = IQ.createResultIQ((IQ) packet);
                        reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                        final Element e = reply.getChildElement();//.addElement("energy");
                        e.addAttribute("type", "pstn");
                        e.addAttribute("maxseconds", "300");
                        externalComponent.handleIQResult(reply);
                    }
                }
            }
        }
    }

    public String getAccountServiceNamespace() {
        return accountServiceNamespace;
    }

    public void setAccountServiceNamespace(String accountServiceNamespace) {
        this.accountServiceNamespace = accountServiceNamespace;
    }

    public String getCreditServiceNamespace() {
        return creditServiceNamespace;
    }

    public void setCreditServiceNamespace(String creditServiceNamespace) {
        this.creditServiceNamespace = creditServiceNamespace;
    }

    public IQNormalizer getIqNormalizer() {
        return iqNormalizer;
    }

    public void setIqNormalizer(IQNormalizer iqNormalizer) {
        this.iqNormalizer = iqNormalizer;
    }
}
