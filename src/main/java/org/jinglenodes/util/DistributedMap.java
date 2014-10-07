package org.jinglenodes.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.apache.log4j.Logger;
import org.jinglenodes.session.persistence.PersistenceWriter;
import org.jinglenodes.session.persistence.PersistenceWriterQueue;
import org.zoolu.tools.ConcurrentTimelineHashMap;

import java.util.Map;

/**
 * Distributed HashMap based on Redis
 *
 * @author bhlangonijr
 *         Date: 02/6/14
 *         Time: 10:55 AM
 */
public class DistributedMap<K, V> extends ConcurrentTimelineHashMap<K, V> {

    private final static Logger log = Logger.getLogger(DistributedMap.class);
    private PersistenceWriterQueue persistenceWriterQueue;
    private PersistenceWriter writer;
    final private XStream xStream;
    private boolean compressed = true;

    public DistributedMap() {
        xStream = new XStream(new DomDriver());
        xStream.autodetectAnnotations(true);
    }

    public DistributedMap(int maxEntries, long timeToLive) {
        super(maxEntries, timeToLive);
        xStream = new XStream(new DomDriver());
        xStream.autodetectAnnotations(true);
    }

    public DistributedMap(int maxEntries, long timeToLive, long purgeDelay) {
        super(maxEntries, timeToLive, purgeDelay);
        xStream = new XStream(new DomDriver());
        xStream.autodetectAnnotations(true);
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (writer != null && value == null) {
            try {
                final byte[] bytes = writer.read(getKey(key));
                value =    fromXml(isCompressed() ? ZipUtil.unzip(bytes): new String(bytes));
                super.put((K) key, (V) value); // update cache
            } catch (Exception e) {
                log.error("Could not retrieve item", e);
            }
        }
        return value;
    }

    @Override
    public V put(K key, V value) {
        if (persistenceWriterQueue != null) {
            try {
                byte[] bytes = toXml(value).getBytes();
                persistenceWriterQueue.persist(getKey(key), isCompressed() ? ZipUtil.zip(bytes) : bytes);
            } catch (Exception e) {
                log.error("Could not store item", e);
            }
        }
        return super.put(key, value);
    }

    @Override
    public V remove(Object key) {
        if (persistenceWriterQueue != null) {
            try {
                persistenceWriterQueue.delete(getKey(key));
            } catch (Exception e) {
                log.error("Could not remove item", e);
            }
        }
        return super.remove(key);
    }

    /**
     * Synchronize the local cache with remote Redis data
     *
      */
    public void synchronize() throws Exception {
        if (writer != null) {
            final Map<String, byte[]> data = writer.loadDataWithKeys();
            for (Entry entry : data.entrySet()) {
                final byte[] bytes = (byte[]) entry.getValue();
                final V value = fromXml(isCompressed() ? ZipUtil.unzip(bytes) : new String(bytes));
                super.put((K) entry.getKey(), value);
            }
        }
    }

    /*
    make the key based on object
     */
    private final static String getKey(Object key) {
        if (key instanceof String) {
            return (String)key;
        }
        return String.valueOf(key.hashCode());
    }


    private V fromXml(final String xml) throws Exception {
        return (V) xStream.fromXML(xml);
    }

    private String toXml(final V value) throws Exception {
        return xStream.toXML(value);
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public PersistenceWriterQueue getPersistenceWriterQueue() {
        return persistenceWriterQueue;
    }

    public void setPersistenceWriterQueue(PersistenceWriterQueue persistenceWriterQueue) {
        this.persistenceWriterQueue = persistenceWriterQueue;
    }

    public PersistenceWriter getWriter() {
        return writer;
    }

    public void setWriter(PersistenceWriter writer) {
        this.writer = writer;
    }
}
