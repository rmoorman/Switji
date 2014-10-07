package org.jinglenodes.session.persistence;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 5/24/12
 * Time: 11:24 PM
 */
public interface PersistenceWriter {

    public void write(final String id, final byte[] data);

    public void delete(final String id);

    public byte[] read(final String id);

    public List<byte[]> loadData();

    public Map<String, byte[]> loadDataWithKeys();

    public void reset();

    public int getTimeToLive();

    public void setTimeToLive(int timeToLive);
}
