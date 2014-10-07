package org.xmpp;

import junit.framework.TestCase;
import org.jinglenodes.session.persistence.PersistenceWriterQueue;
import org.jinglenodes.session.persistence.redis.RedisWriter;
import org.jinglenodes.sip.account.SipAccount;
import org.jinglenodes.util.DistributedMap;

/**
 * @author bhlangonijr
 *         Date: 10/3/14
 *         Time: 4:00 PM
 */
public class TestDistributedMap extends TestCase {

    static RedisWriter writer = null;
    static PersistenceWriterQueue persistenceWriter = null;

    static {

        writer = new RedisWriter();
        writer.setRedisHost("localhost");
        writer.setRedisPort(6379);
        persistenceWriter = new PersistenceWriterQueue(writer);

        writer.reset();

    }

    public void testMapCreation() throws InterruptedException {

        SipAccount sipAccount = new SipAccount("test", "test", "test", "test", "test", "test");
        DistributedMap<String, SipAccount> map = new DistributedMap<String, SipAccount>();

        map.setWriter(writer);
        map.setPersistenceWriterQueue(persistenceWriter);

        map.put("test", sipAccount);
        Thread.sleep(50);

        SipAccount sipAccount2 = map.get("test");

        assertNotNull(sipAccount2);


    }

    public void testValueRetrieval() throws InterruptedException {
        writer.reset();
        SipAccount sipAccount = new SipAccount("test", "test", "test", "test", "test", "test");
        DistributedMap<String, SipAccount> map = new DistributedMap<String, SipAccount>();

        map.setWriter(writer);
        map.setPersistenceWriterQueue(persistenceWriter);
        map.put("test4", sipAccount);
        Thread.sleep(50);

        DistributedMap<String, SipAccount> map2 = new DistributedMap<String, SipAccount>();
        map2.setWriter(writer);
        map2.setPersistenceWriterQueue(persistenceWriter);

        SipAccount sipAccount2 = map2.get("test4");

        assertNotNull(sipAccount2);

    }


    public void testSynchronization() throws Exception {
        writer.reset();
        SipAccount sipAccount1 = new SipAccount("test1", "test", "test", "test", "test", "test");
        SipAccount sipAccount2 = new SipAccount("test2", "test", "test", "test", "test", "test");
        SipAccount sipAccount3 = new SipAccount("test3", "test", "test", "test", "test", "test");

        DistributedMap<String, SipAccount> map = new DistributedMap<String, SipAccount>();

        map.setWriter(writer);
        map.setPersistenceWriterQueue(persistenceWriter);
        map.put("account1", sipAccount1);
        map.put("account2", sipAccount2);
        map.put("account3", sipAccount3);
        Thread.sleep(50);

        DistributedMap<String, SipAccount> map2 = new DistributedMap<String, SipAccount>();
        map2.setWriter(writer);
        map2.setPersistenceWriterQueue(persistenceWriter);

        map2.synchronize();
        assertEquals(map2.size(), 3);

    }

    public void testRemoval() throws Exception {
        writer.reset();
        SipAccount sipAccount1 = new SipAccount("test1", "test", "test", "test", "test", "test");
        SipAccount sipAccount2 = new SipAccount("test2", "test", "test", "test", "test", "test");
        SipAccount sipAccount3 = new SipAccount("test3", "test", "test", "test", "test", "test");

        DistributedMap<String, SipAccount> map = new DistributedMap<String, SipAccount>();

        map.setWriter(writer);
        map.setPersistenceWriterQueue(persistenceWriter);
        map.put("account1", sipAccount1);
        map.put("account2", sipAccount2);
        map.put("account3", sipAccount3);
        map.remove("account1");
        Thread.sleep(50);

        DistributedMap<String, SipAccount> map2 = new DistributedMap<String, SipAccount>();
        map2.setWriter(writer);
        map2.setPersistenceWriterQueue(persistenceWriter);

        map2.synchronize();
        assertEquals(map2.size(), 2);

    }



}
