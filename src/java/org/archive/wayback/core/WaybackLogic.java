/* WaybackLogic
 *
 * Created on 2005/10/18 14:00:00
 *
 * Copyright (C) 2005 Internet Archive.
 *
 * This file is part of the Wayback Machine (crawler.archive.org).
 *
 * Wayback Machine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Wayback Machine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Wayback Machine; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.archive.wayback.core;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import org.archive.wayback.PropertyConfigurable;
import org.archive.wayback.ReplayRenderer;
import org.archive.wayback.QueryRenderer;
import org.archive.wayback.ResultURIConverter;
import org.archive.wayback.ResourceIndex;
import org.archive.wayback.ResourceStore;
import org.archive.wayback.exception.ConfigurationException;

/**
 * Constructor and go-between for the major components in the Wayback Machine.
 * 
 * @author Brad Tofel
 * @version $Date$, $Revision$
 */
public class WaybackLogic implements PropertyConfigurable {
	private static final Logger LOGGER = Logger.getLogger(WaybackLogic.class
			.getName());

	private static final String REPLAY_URI_CONVERTER_PROPERTY =
		"replayuriconverter";

	private static final String REPLAY_RENDERER_PROPERTY = "replayrenderer";

	private static final String QUERY_RENDERER_PROPERTY = "queryrenderer";

	private static final String RESOURCE_STORE_PROPERTY = "resourcestore";

	private static final String RESOURCE_INDEX_PROPERTY = "resourceindex";

	private Properties configuration = null;
	
	Hashtable objectCache = new Hashtable();
	
	/**
	 * Constructor
	 */
	public WaybackLogic() {
		super();
	}

	/**
	 * Initialize this WaybackLogic. Pass in the specific configurations via
	 * Properties. Will ferret away the configuration properties, to be
	 * used later when constructing and initializing implementations of
	 * ResourceIndex, ResourceStore, QueryUI, ReplayUI, and 
	 * ReplayResultURIConverter.
	 * 
	 * @param configuration
	 *            Generic properties bag for configurations
	 */
	public void init(Properties configuration) {
		
		this.configuration = configuration;
		
	}

	protected PropertyConfigurable getInstance(final Properties p,
			final String classPrefix) throws ConfigurationException {

		LOGGER.info("WaybackLogic constructing " + classPrefix + "...");
		
		PropertyConfigurable result = null;

		String classNameKey = classPrefix + ".classname";
		String propertyPrefix = classPrefix + ".";
		String className = null;

		// build new class-specific Properties for class initialization:
		Properties classProperties = new Properties();
		for (Enumeration e = p.keys(); e.hasMoreElements();) {
			String key = (String) e.nextElement();

			if (key.equals(classNameKey)) {

				// special .classname value:
				className = (String) p.get(key);

			} else if (key.startsWith(propertyPrefix)) {

				String finalKey = key.substring(propertyPrefix.length());
				String value = (String) p.get(key);
				classProperties.put(finalKey, value);

			}
		}

		// did we find the implementation class?
		if (className == null) {
			throw new ConfigurationException("No configuration for ("
					+ classNameKey + ")");
		}

		try {
			result = (PropertyConfigurable) Class.forName(className)
					.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new ConfigurationException(e.getMessage());
		}
		LOGGER.info("new " + className + " created.");
		result.init(p);
		LOGGER.info("initialized " + className);

		return result;
	}

	/**
	 * possibly initializes and returns an instance of class className
	 * 
	 * @param className
	 * @return object of PropertyConfigurable class that has been configured
	 *     with a call to init() 
	 * @throws ConfigurationException
	 */
	public PropertyConfigurable getCachedInstance(final String className) 
	throws ConfigurationException {
		if(!objectCache.containsKey(className)) {
			objectCache.put(className,getInstance(configuration,className));
		}
		return (PropertyConfigurable) objectCache.get(className);
	}
	
	/**
	 *  possibly initializes and returns the resourceIndex
	 * 
	 * @return Returns the resourceIndex.
	 * @throws ConfigurationException 
	 */
	public ResourceIndex getResourceIndex() throws ConfigurationException {
		return (ResourceIndex) getCachedInstance(RESOURCE_INDEX_PROPERTY);

	}

	/**
	 *  possibly initializes and returns the resourceStore
	 * 
	 * @return Returns the resourceStore.
	 * @throws ConfigurationException 
	 */
	public ResourceStore getResourceStore() throws ConfigurationException {
		return (ResourceStore) getCachedInstance(RESOURCE_STORE_PROPERTY);

	}

	/**
	 *  possibly initializes and returns the uriConverter
	 *  
	 * @return Returns the uriConverter.
	 * @throws ConfigurationException 
	 */
	public ResultURIConverter getURIConverter()
	throws ConfigurationException {
		return (ResultURIConverter) getCachedInstance(
				REPLAY_URI_CONVERTER_PROPERTY);
	}

	/**
	 *  possibly initializes and returns the replayRenderer
	 * 
	 * @return Returns the replayRenderer.
	 * @throws ConfigurationException 
	 */
	public ReplayRenderer getReplayRenderer() throws ConfigurationException {
		return (ReplayRenderer) getCachedInstance(REPLAY_RENDERER_PROPERTY);
	}

	/**
	 *  possibly initializes and returns the queryRenderer
	 * 
	 * @return Returns the queryRenderer.
	 * @throws ConfigurationException 
	 */
	public QueryRenderer getQueryRenderer() throws ConfigurationException {
		return (QueryRenderer) getCachedInstance(QUERY_RENDERER_PROPERTY);
	}
}
