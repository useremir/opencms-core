/*
 * File   : $Source: /alkacon/cvs/opencms/src/com/opencms/flex/cache/Attic/CmsFlexCache.java,v $
 * Date   : $Date: 2002/08/21 11:29:32 $
 * Version: $Revision: 1.2 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (C) 2002  The OpenCms Group
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about OpenCms, please see the
 * OpenCms Website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * First created on 9. April 2002, 12:40
 */


package com.opencms.flex.cache;

import java.util.HashMap;
import com.opencms.file.CmsObject;

/** 
 * This class implements the FlexCache.<p>
 *
 * The data structure used is a two-level hashtable.
 * This is optimized for the structure of the keys that are used to describe the
 * caching behaviour of the entries.
 * The first hash-level is calculated from the resource name, i.e. the
 * name of the resource as it is referred to in the VFS of OpenCms.
 * A suffix [online] or [offline] is appended to te resource name 
 * to distinguish between the online and offline projects of OpenCms.
 * The second hash-level is calculated from the cache-key of the resource,
 * which also is a String representing the specifc variation of the cached entry.<p>
 *
 * Entries in the first level of the cache are of type CmsFlexCacheVariation, 
 * which is a sub-class of CmsFlexCache. 
 * This class is a simple data type that contains of a Map of CmsFlexCacheEntries, 
 * with variations - Strings as keys.<p>
 *
 * Here's a short summary of used terms:
 * <ul>
 * <li><b>key:</b> 
 * A combination of a resource name and a variation.
 * The data structure used is CmsFlexCacheKey.
 * <li><b>resource:</b>
 * A String with the resource name and an appended [online] of [offline] suffix.
 * <li><b>variation:</b>
 * A String describing a variation of a cached entry in the CmsFlexCache language.
 * <li><b>entry:</b>
 * A CmsFlexCacheEntry data structure which is describes a cached OpenCms resource.
 * For every entry a key is saved which contains the resource name and the variation.
 * </ul>
 *
 * <b>TODO:</b> 
 * Implement cache aging, so that old entries are
 * removed if cache size grows to big. 
 * Alternative: Swap to disk if memory runs short, should be more efficient still 
 * then asking the database and re-calculating again.<br>
 * <b>TODO:</b> 
 * Currenty the whole cache is flushed if something is published.
 * Implement partial cache flushing, i.e. remove only changed elements at publish 
 * or change event (in case of offline resources).<br>
 *
 * @author Alexander Kandzior (a.kandzior@alkacon.com)
 * @version $Revision: 1.2 $
 * @see com.opencms.flex.cache.CmsFlexCacheKey
 * @see com.opencms.flex.cache.CmsFlexCacheEntry
 */
public class CmsFlexCache implements com.opencms.flex.I_CmsEventListener {
    
    /** Initial Cache size, this should be a prime number of best results in the hash algorithms */
    public static final int C_INITIAL_CAPACITY_CACHE = 509;
    // Alternatives: 127 257 509 1021 2039 4099 8191
    // TODO: Check is this should be a constructor variable, 
    // maybe related to the number of resources in the site.
    
    /** Initial size for variation lists */
    public static final int C_INITIAL_CAPACITY_VARIATIONS = 7;
    // Alternatives: 2 3 5 7 11 13 17 19 23 29 31 37 41 43 47 53 59 61 67 71
    
    /** Suffix to append to online cache entries */
    public static String C_CACHE_ONLINESUFFIX = " [online]";
    
    /** Suffix to append to online cache entries */
    public static String C_CACHE_OFFLINESUFFIX = " [offline]";
        
    /** Hashmap to store the Entries for fast lookup */
    private java.util.Map m_resourceMap;
    
    /** Counter for the size */
    private int m_size;
    
    /** Indicates if the cache is enabled or not */
    private boolean m_enabled;
    
    /** Indicates if offline resources should be cached or not */
    private boolean m_cacheOffline;
    
    /** Debug switch */
    private int DEBUG = 0;
  
    /** Static ins to trigger clearcache events */
    public static final int C_CLEAR_ALL = 0;
    public static final int C_CLEAR_ENTRIES = 1;    
    public static final int C_CLEAR_ONLINE_ALL = 2;    
    public static final int C_CLEAR_ONLINE_ENTRIES = 3;  
    public static final int C_CLEAR_OFFLINE_ALL = 4;    
    public static final int C_CLEAR_OFFLINE_ENTRIES = 5;  


        
    /**
     * Constructor for class CmsFlexCache.<p>
     *
     * The parameter "enabled" is used to control if the cache is
     * actually on or off. Even if you don't need the cache, you still
     * have to create an instance of it with enabled=false.
     * This is because you need some of the FlexCache data structures
     * for JSP inclusion buffering.
     *
     * @param enabled Indicates if the cache is enabled or not
     * @param cacheOffline Indicates if offline resources should be cached or not
     */
    public CmsFlexCache(boolean enabled, boolean cacheOffline) {
        // TODO: Implement cache - aging & cleanup
        m_enabled = enabled;
        m_cacheOffline = cacheOffline;
        if (m_enabled) {
            clear();
            com.opencms.core.A_OpenCms.addCmsEventListener(this);
        }
        if (DEBUG > 0) System.err.println("FlexCache: Initializing with parameters enabled=" + m_enabled + " cacheOffline=" + m_cacheOffline);
    }
    
    /**
     * Indicates if the cache is enabled (i.e. actually
     * caching entries) or not.
     *
     * @return true if the cache is enabled, false if not
     */    
    public boolean isEnabled() {
        return m_enabled;
    }
    
    /**
     * Indicates if offline project resources are cached.
     *
     * @return true if offline projects are cached, false if not
     */    
    public boolean cacheOffline() {
        return m_cacheOffline;
    }
    
    /**
     * Clears all entries and all keys in the cache, online or offline.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */    
    private void clear(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing complete cache");        
        clear();
    }
    
    /**
     * Clears all entries in the cache, online or offline.
     * The keys are not cleared.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */    
    private synchronized void clearEntries(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing all entries");        
        java.util.Iterator i = m_resourceMap.keySet().iterator();
        while (i.hasNext()) {
             CmsFlexCacheVariation v = (CmsFlexCacheVariation)m_resourceMap.get(i.next());
             v.map = java.util.Collections.synchronizedMap(new HashMap(C_INITIAL_CAPACITY_VARIATIONS));             
        }
        m_size = 0;
    }
    
    /**
     * Clears all entries and all keys from offline projects in the cache.
     * Cached resources from the online project are not touched.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */        
    private void clearOffline(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing offline keys & entries");        
        clearOneHalf(C_CACHE_OFFLINESUFFIX, false);
    }    
    
    /**
     * Clears all entries from offline projects in the cache.
     * The keys from the offline projects are not cleared.
     * Cached resources from the online project are not touched.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */        
    private void clearOfflineEntries(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing offline entries");        
        clearOneHalf(C_CACHE_OFFLINESUFFIX, true);
    }    
        
    /**
     * Clears all entries and all keys from the online project in the cache.
     * Cached resources from the offline projects are not touched.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */     
    private void clearOnline(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing online keys & entries");        
        clearOneHalf(C_CACHE_ONLINESUFFIX, false);
    }
    
    /**
     * Clears all entries from the online project in the cache.
     * The keys from the online project are not cleared.
     * Cached resources from the offline projects are not touched.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     */
    private void clearOnlineEntries(CmsObject cms) {
        if (! isEnabled()) return;
        if (! isAdmin(cms)) return;
        if (DEBUG > 0) System.err.println("FlexCache: Clearing online entries");        
        clearOneHalf(C_CACHE_ONLINESUFFIX, true);
    }
    
    /**
     * This method purges the JSP repository dirs,
     * ie. it deletes all JSP files that OpenCms has written to the 
     * real FS.
     * Obviously this method must be used with caution.
     * Purpose of this method is to allow 
     * a complete purge of all JSP pages on a remote machine after 
     * a major update of JSP templates was made.
     *
     * @param cms The CmsObject used for user authorization
     */
    private synchronized void purgeJspRepository(CmsObject cms) {
        if (!isAdmin(cms) && !cms.getRequestContext().isEventControlled()) return;
        java.io.File d;
        if (DEBUG > 0) System.err.println("FlexCache.purgeJspRepository() purging JSP repositories!");
        d = new java.io.File(com.opencms.flex.CmsJspLoader.getJspRepository() + "online" + java.io.File.separator);
        if (DEBUG > 0) System.err.println("FlexCache.purgeJspRepository() trying to purge ONLINE repository: " + d);
        if (d.canRead() && d.isDirectory()) {
            java.io.File files[] = d.listFiles();
            if (DEBUG > 0) System.err.println("FlexCache.purgeJspRepository() Files in ONLINE repository = " + files.length);
            for (int i = 0; i<files.length; i++) {
                java.io.File f = files[i];
                if (f.canWrite()) {
                    f.delete(); 
                } else if (DEBUG > 0) {
                    System.err.println("FlexCache.purgeJspRepository() could not delete file = " + f);                    
                }
            }
        } else if (DEBUG > 0) {
            System.err.println("FlexCache.purgeJspRepository() could not access ONLINE repository: " + d);
            System.err.println("FlexCache.purgeJspRepository() d.isDirectory() = " + d.canWrite());
            System.err.println("FlexCache.purgeJspRepository() d.canRead() = " + d.canRead());
            
        }
        d = new java.io.File(com.opencms.flex.CmsJspLoader.getJspRepository() + "offline" + java.io.File.separator);
        if (DEBUG > 1) System.err.println("FlexCache.purgeJspRepository() trying to purge OFFLINE repository: " + d);
        if (d.canRead() && d.isDirectory()) {
            java.io.File files[] = d.listFiles();
            if (DEBUG > 0) System.err.println("FlexCache.purgeJspRepository() Files in OFFLINE repository = " + files.length);            
            for (int i = 0; i<files.length; i++) {
                java.io.File f = files[i];
                if (f.canWrite()) {
                    f.delete();
                } else if (DEBUG > 0) {
                    System.err.println("FlexCache.purgeJspRepository() could not delete file = " + f);                    
                }
            }
        } else if (DEBUG > 0) {
            System.err.println("FlexCache.purgeJspRepository() could not access OFFLINE repository: " + d);
            System.err.println("FlexCache.purgeJspRepository() d.isDirectory() = " + d.canWrite());
            System.err.println("FlexCache.purgeJspRepository() d.canRead() = " + d.canRead());
            
        }
        clear();
        log("JSP repository purged - purgeJspRepository() called");
    }
    
    /** 
     * Returns a set of all cached resource names.
     * Usefull if you want to show a list of all cached resources,
     * like on the FlexCache administration page.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param cms The CmsObject used for user authorization
     * @return The set of cached resource names (which are of type String)
     */    
    public java.util.Set getCachedResources(CmsObject cms) {
        if (! isEnabled()) return null;
        if (! isAdmin(cms)) return null;
        return m_resourceMap.keySet();
    }
    
    /**
     * Returns all variations in the cache for a given resource name.
     * The variations are of type String.
     * Usefull if you want to show a list of all cached entry - variations,
     * like on the FlexCache administration page.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param key The resource name for which to look up the variations for
     * @param cms The CmsObject used for user authorization
     * @return A set of cached variations (which are of type String)   
     */    
    public java.util.Set getCachedVariations(String key, CmsObject cms) {
        if (! isEnabled()) return null;        
        if (! isAdmin(cms)) return null;
        Object o = m_resourceMap.get(key);
        if (o != null) {
            CmsFlexCacheVariation v = (CmsFlexCacheVariation)o;
            return v.map.keySet();
        }
        return null;
    }
    
    /**
     * Returns the CmsFlexCacheKey data structre for a given
     * key (i.e. resource name).
     * Usefull if you want to show the cache key for a resources,
     * like on the FlexCache administration page.<p>
     *
     * Only users with administrator permissions are allowed
     * to perform this operation.
     *
     * @param key The resource name for which to look up the variation for
     * @param cms The CmsObject used for user authorization
     * @return The CmsFlexCacheKey data structure found for the resource
     */    
    public CmsFlexCacheKey getCachedKey(String key, CmsObject cms) {
        if (! isEnabled()) return null;        
        if (! isAdmin(cms)) return null;
        Object o = m_resourceMap.get(key);
        if (o != null) {
            CmsFlexCacheVariation v = (CmsFlexCacheVariation)o;
            return v.key;
        }
        return null;        
    }
    
    /** 
     * Returns the total number of entries in the cache.
     * 
     * @return The number of entries in the cache
     */    
    public int size() {
        return m_size;
    }
    
    /**
     * Returns the total number of cached resource keys.
     *
     * @return The number of resource keys in the cache 
     */
    public int keySize() {
        if (! isEnabled()) return 0;        
        return m_resourceMap.size();
    }    
    
    /** 
     * This method checks if a given key
     * is already contained in the cache.
     *
     * @return true if key is in the cache, false otherwise
     * @param key The key to look for 
     */    
    boolean containsKey(CmsFlexCacheKey key) {
        if (! isEnabled()) return false;        
        return (get(key) != null);       
    }
    
    /** 
     * Implements the CmsEvent interface.
     * The FlexCache uses the events to clear itself in case a project is published.<p>
     *
     * <b>TODO:</b>
     * Implement "partial" cache clearing, clear only items that have changed etc.
     *
     * @param event CmsEvent that has occurred
     */
    public void cmsEvent(com.opencms.flex.CmsEvent event) {
        if (! isEnabled()) return;

        switch (event.getType()) {
            case com.opencms.flex.I_CmsEventListener.EVENT_PUBLISH_PROJECT:
            case com.opencms.flex.I_CmsEventListener.EVENT_CLEAR_CACHES:
                if (DEBUG > 0) System.err.println("FlexCache: Recieved event, clearing cache!");
                clear();
                break;
            case com.opencms.flex.I_CmsEventListener.EVENT_FLEX_PURGE_JSP_REPOSITORY:
                if (DEBUG > 0) System.err.println("FlexCache: Recieved event, purging JSP repository!");
                purgeJspRepository(event.getCmsObject());
                break;
            case com.opencms.flex.I_CmsEventListener.EVENT_FLEX_CACHE_CLEAR:
                if (DEBUG > 0) System.err.println("FlexCache: Recieved event, clearing part of cache!");
                java.util.Map m = event.getData();
                if (m == null) break;
                Integer it = null;
                try {
                    it = (Integer)m.get("action");
                } catch (Exception e) {}
                if (it == null) break; 
                int i = it.intValue();
                switch (i) {
                    case C_CLEAR_ALL:
                        clear(event.getCmsObject());
                        break;
                    case C_CLEAR_ENTRIES:
                        clearEntries(event.getCmsObject());
                        break;                                              
                    case C_CLEAR_ONLINE_ALL: 
                        clearOnline(event.getCmsObject());
                        break;                          
                    case C_CLEAR_ONLINE_ENTRIES:
                        clearOnlineEntries(event.getCmsObject());
                        break;
                    case C_CLEAR_OFFLINE_ALL:  
                        clearOffline(event.getCmsObject());
                        break;                          
                    case C_CLEAR_OFFLINE_ENTRIES:  
                        clearOfflineEntries(event.getCmsObject());
                        break;
                }
        }
    }
    
    /** 
     * Lookup a specific entry in the cache.
     * In case a found entry has a timeout set, it will be checked upon lookup.
     * In case the timeout of the entry has been reached, it will be removed from 
     * the cache (and null will be returend in this case).
     *
     * @param key The key to look for in the cache
     * @return The entry found for the key, or null if key is not in the cache
     */    
    CmsFlexCacheEntry get(CmsFlexCacheKey key) {
        if (! isEnabled()) return null;        
        if (DEBUG > 0) System.err.println("FlexCache: Trying to get entry for resource " + key.Resource);
        Object o = m_resourceMap.get(key.Resource);
        if (o != null) {
            CmsFlexCacheVariation v = (CmsFlexCacheVariation)o;
            String variation = v.key.matchRequestKey(key);
            if (DEBUG > 0) {
                if (variation != null) {
                    CmsFlexCacheEntry e = (CmsFlexCacheEntry)v.map.get(variation);
                    if (e != null) {
                        System.err.println("FlexCache: Found entry for variation " + variation);
                    } else {
                        System.err.println("FlexCache: Did not find entry for variation " + variation);
                    }
                } else {
                    System.err.println("FlexCache: Found nothing because resource is not cachable for this request!");
                }
            }
            if (variation == null) return null;
            if (v.key.m_timeout < 0) {
                // No timeout for this resource is specified
                return (CmsFlexCacheEntry)v.map.get(variation);
            } else {
                // Check for possible timeout of entry
                CmsFlexCacheEntry e = (CmsFlexCacheEntry)v.map.get(variation);
                if (e == null) return null;
                if (DEBUG > 1) System.err.println("FlexCache: Checking timeout for resource " + key.Resource);
                if (e.getTimeout() < key.m_timeout) {
                    if (DEBUG > 1) System.err.println("FlexCache: Resource has reached timeout, removing from cache!");
                    v.map.remove(variation);
                    this.decSize();
                    return null;
                }
                if (DEBUG > 1) System.err.println("FlexCache: Resource timeout not reached!");
                return e;
            }
        } else if (DEBUG > 0) {
            System.err.println("FlexCache: Did not find any entry for resource" );
            return null;
        } else return null;
    }
    
    /**
     * Returns the CmsFlexCacheKey data structre for a given esource name.
     *
     * @param resource The resource name for which to look up the key for
     * @return The CmsFlexCacheKey data structure found for the resource
     */ 
    CmsFlexCacheKey getKey(String resource) {
        if (! isEnabled()) return null;        
        Object o = m_resourceMap.get(resource);
        if (o != null) {
            if (DEBUG > 1) System.err.println("FlexCache: Found pre-calculated key for resource " + resource);
            return ((CmsFlexCacheVariation)o).key;
        } else {
            if (DEBUG > 1) System.err.println("FlexCache: Did not find pre-calculated key for resource " + resource);
            return null;
        }
    }        
    
    /**
     * Adds a key with a new, empty variation map to the cache.
     *
     * @param key The key to add to the cache.
     */
    void putKey(CmsFlexCacheKey key) {
        if (! isEnabled()) return;        
        Object o = m_resourceMap.get(key.Resource);
        if (o == null) {
            // No variation map for this resource yet, so create one            
            m_resourceMap.put(key.Resource, new CmsFlexCacheVariation(key));
            if (DEBUG > 1) System.err.println("FlexCache: Added pre-calculated key for resource " + key.Resource);
        } 
        // If != null the key is already in the cache, so we just do nothing
    }
    
    /** 
     * This method adds new entries to the cache.<p>
     *
     * The key describes the conditions under which the value can be cached.
     * Usually the key belongs to the response.
     * The variation describes the conditions under which the
     * etry was created. This is usually calculated from the request.
     * If the variation is != null, the entry is cachable.
     *
     * @param key The key for the new value entry. Usually calculated from the response.
     * @param entry The CmsFlexCacheEntry to store in the cache.
     * @param variation The pre-calculated variation for the entry.
     * @return true if the value was added to the cache, false otherwise.
     */    
    boolean put(CmsFlexCacheKey key, CmsFlexCacheEntry entry, String variation) {
        if (! isEnabled()) return false;        
        if (DEBUG > 1) System.err.println("FlexCache: Trying to add entry for resource " + key.Resource);
        if (variation != null) {
            // This is a cachable result
            key.Variation = variation;
            if (DEBUG > 1) System.err.println("FlexCache: Adding entry for resource " + key.Resource + " with variation:" + key.Variation);
            put(key, entry);
            // Note that duplicates are NOT checked, it it assumed that this is done beforehand,
            // while checking if the entry is already in the cache or not.
            return true;
        } else {
            // Result is not cachable
            if (DEBUG > 1) System.err.println("FlexCache: Nothing added because resource is not cachable for this request!");
            return false;
        }        
    }        
    
    /** 
     * Removes an entry from the cache.
     *
     * @param key The key which describes the entry to remove from the cache
     */    
    void remove(CmsFlexCacheKey key) {
        if (! isEnabled()) return;        
        Object o = m_resourceMap.get(key.Resource);
        if (o != null) {
            Object old = ((HashMap)o).remove(key.Variation);
            if (old != null) decSize();
        };      
    }
    
    /** 
     * Checks if the cache is empty or if at last one element is contained.
     *
     * @return true if the cache is empty, false otherwise 
     */    
    boolean isEmpty() {
        if (! isEnabled()) return true;        
        return m_resourceMap.isEmpty();
    }
    
    /** 
     * Emptys the cache completly.
     */    
    private synchronized void clear() {
        if (! isEnabled()) return;
        m_resourceMap = java.util.Collections.synchronizedMap(new HashMap(C_INITIAL_CAPACITY_CACHE));
        m_size = 0;
        log("Complete cache cleared - clear() called" );        
    }
    
    /** 
     * Save a value to the cache.
     * 
     * @param key The key under shich the value is saved.
     * @param value The value to save in the cache.
     */    
    private void put(CmsFlexCacheKey key, CmsFlexCacheEntry value) {
        Object o = m_resourceMap.get(key.Resource);
        if (key.m_timeout > 0) value.setTimeout(key.m_timeout * 60000);
        if (o != null) {
            // We already have a variation map for this resource 
            java.util.Map m = ((CmsFlexCacheVariation)o).map;
            if (! m.containsKey(key.Variation)) incSize();
            m.put(key.Variation, value);
        } else {
            // No variation map for this resource yet, so create one
            CmsFlexCacheVariation list = new CmsFlexCacheVariation(key);
            list.map.put(key.Variation, value);
            m_resourceMap.put(key.Resource, list);
            incSize();
        }
        if (DEBUG > 0) System.err.println("FlexCache: Entry "  + m_size + " added for resource " + key.Resource + " with variation " + key.Variation);
        if (DEBUG > 2) System.err.println("FlexCache: Entry added was:\n" + value);
    }
    
    /** 
     * This assures increasing the size is done synchronized.
     */
    private synchronized void incSize() {
        m_size++;
    }
    
    /** 
     * This assures decreasing the size is done synchronized 
     */
    private synchronized void decSize() {
        m_size--;
    }
    
    /**
     * Internal method to determine if a user has Administration permissions.
     */
    private boolean isAdmin(CmsObject cms) {
        boolean result;
        try {
            result = cms.getRequestContext().isAdmin();
        } catch (Exception e) {
            result = false;
        }
        return result;
    }
    
    /**
     * Internal method to perform cache clearance. 
     * It clears "one half" of the cache, i.e. either 
     * the online or the offline parts. 
     * Another parameter is used to indicate if only 
     * the entries or keys and entries are to be cleared.
     */
    private synchronized void clearOneHalf(String suffix, boolean entriesOnly) {
        java.util.Set keys = new java.util.HashSet(m_resourceMap.keySet());
        java.util.Iterator i = keys.iterator();
        while (i.hasNext()) {
            String s = (String)i.next();
            if (s.endsWith(suffix)) {                
                CmsFlexCacheVariation v = (CmsFlexCacheVariation)m_resourceMap.get(s);
                if (entriesOnly) {
                    // Clear only entry
                    m_size -= v.map.size();
                    v.map = java.util.Collections.synchronizedMap(new HashMap(C_INITIAL_CAPACITY_VARIATIONS));
                } else {
                    // Clear key and entry
                    m_size -= v.map.size();
                    v.map = null;
                    v.key = null;
                    m_resourceMap.remove(s);  
                }
            }
        }    
        log("Part of the FlexCache cleared - clearOneHalf(" + suffix + ", " + entriesOnly + ") called" );
    }
    
    /**
     * Internal "quick and dirty" data structure to poupate the cache with.
     */
    private class CmsFlexCacheVariation {
        
        /** The key belonging to the resource */
        public CmsFlexCacheKey key;
        /** Maps variations to CmsFlexCacheEntries */
        public java.util.Map map;
        
        /**
         * Generates a new instance of CmsFlexCacheVariation
         *
         * @param k The (resource) key to contruct this variation list for
         */        
        public CmsFlexCacheVariation(CmsFlexCacheKey k) {
            key = k;
            map = java.util.Collections.synchronizedMap(new HashMap(C_INITIAL_CAPACITY_VARIATIONS));
        }
    }
            
    /**     
     * Logs a message to the OpenCms log in the channel "flex_cache".
     *
     * @param message The string to write in the log file
     */    
    private void log(String message) {
        if (com.opencms.boot.I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING) {
            com.opencms.boot.CmsBase.log(com.opencms.boot.CmsBase.C_FLEX_CACHE, "[CmsFlexCache] " + message);
        }
    }    
}
