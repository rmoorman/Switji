package org.jinglenodes.session.persistence.redis;

import org.apache.log4j.Logger;
import org.jinglenodes.session.persistence.PersistenceWriter;
import redis.clients.jedis.Jedis;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: thiago
 * Date: 5/24/12
 * Time: 11:54 PM
 */
public class RedisWriter implements PersistenceWriter {

    final private static int DEFAULT_SESSION_TTL = 3600;
    final private static Logger log = Logger.getLogger(RedisWriter.class);
    final private String ENCODE = "UTF-8";
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private int timeToLive = DEFAULT_SESSION_TTL;

    @Override
    public void write(String id, byte[] data) {
        log.debug("Writing Data: " + id);
        try {
            JedisConnection connection = JedisConnection.getInstance(redisHost, redisPort);
            Jedis jedis = connection.getResource();

            if (jedis == null) return;

            try {
                log.debug("Persisting Data");
                final byte[] bkey = id.getBytes(ENCODE);
                jedis.setex(bkey, getTimeToLive(), data);
            } catch (UnsupportedEncodingException e) {
                log.error("Unsupported Encoding on CallSession ID", e);
            } finally {
                connection.returnResource(jedis);
            }
        } catch (Exception e) {
            log.error("Could not Write: " + id, e);
        }

    }

    @Override
    public byte[] read(String id) {
        log.debug("Reading Data: " + id);
        byte[] data = null;
        try {
            JedisConnection connection = JedisConnection.getInstance(redisHost, redisPort);
            Jedis jedis = connection.getResource();

            if (jedis == null) return null;

            try {
                log.debug("Reading Data");
                final byte[] bkey = id.getBytes(ENCODE);
                data = jedis.get(bkey);
            } catch (UnsupportedEncodingException e) {
                log.error("Unsupported Encoding on CallSession ID", e);
            } finally {
                connection.returnResource(jedis);
            }
        } catch (Exception e) {
            log.error("Could not Write: " + id, e);
        }
        return data;
    }

    @Override
    public void delete(String id) {
        log.debug("Deleting Data: " + id);
        try {
            JedisConnection connection = JedisConnection.getInstance(redisHost, redisPort);
            Jedis jedis = connection.getResource();

            if (jedis == null) return;

            try {
                log.debug("Deleting Persistent Data");
                jedis.del(id.getBytes(ENCODE));
            } catch (UnsupportedEncodingException e) {
                log.error("Unsupported Encoding on ID", e);
            } finally {
                connection.returnResource(jedis);
            }
        } catch (Exception e) {
            log.error("Could not Delete: " + id, e);
        }

    }

    @Override
    public List<byte[]> loadData() {

        final List<byte[]> data = new ArrayList<byte[]>();
        JedisConnection connection = null;
        Jedis jedis = null;

        try {
            connection = JedisConnection.getInstance(redisHost, redisPort);
            jedis = connection.getResource();

            if (jedis == null) return data;

            final Set<String> keys = jedis.keys("*");

            log.debug("Loading Persistent Data...");
            for (final String key : keys) {
                try {
                    log.debug("Loaded Key: " + key);
                    final byte[] b = jedis.get(key.getBytes(ENCODE));
                    data.add(b);
                } catch (UnsupportedEncodingException e) {
                    log.error("Unsupported Encoding on ID", e);
                }
            }

        } catch (Exception e) {
            log.error("Could not Load Data", e);
        } finally {
            if (connection != null && jedis != null) {
                connection.returnResource(jedis);
            }
        }

        return data;
    }

    @Override
    public Map<String, byte[]> loadDataWithKeys() {

        final Map<String, byte[]> data = new HashMap<String, byte[]>();
        JedisConnection connection = null;
        Jedis jedis = null;

        try {
            connection = JedisConnection.getInstance(redisHost, redisPort);
            jedis = connection.getResource();

            if (jedis == null) return data;

            final Set<String> keys = jedis.keys("*");

            log.debug("Loading Persistent Data...");
            for (final String key : keys) {
                try {
                    log.debug("Loaded Key: " + key);
                    final byte[] b = jedis.get(key.getBytes(ENCODE));
                    data.put(key, b);
                } catch (UnsupportedEncodingException e) {
                    log.error("Unsupported Encoding on ID", e);
                }
            }

        } catch (Exception e) {
            log.error("Could not Load Data", e);
        } finally {
            if (connection != null && jedis != null) {
                connection.returnResource(jedis);
            }
        }

        return data;
    }

    @Override
    public void reset() {
        try {
            JedisConnection connection = JedisConnection.getInstance(redisHost, redisPort);
            Jedis jedis = connection.getResource();

            if (jedis == null) return;

            try {
                log.debug("Deleting All Persistent Data");
                jedis.flushAll();
            } finally {
                connection.returnResource(jedis);
            }
        } catch (Exception e) {
            log.error("Could not Reset (FLUSHALL)", e);
        }
    }

    public String getRedisHost() {
        return redisHost;
    }

    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }
}
