/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.opencmis;

import java.util.List;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.activities.ActivityType;
import org.alfresco.repo.model.filefolder.HiddenAspect;
import org.alfresco.repo.model.filefolder.HiddenAspect.Visibility;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.activities.ActivityService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.util.FileFilterMode.Client;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;

/**
 * OpenCMIS methods may use an instance of this class to post activity data.
 * 
 * @see ActivityPoster
 * @author sglover
 */
public class ActivityPosterImpl implements ActivityPoster, InitializingBean
{
    private static final String APP_TOOL = "CMIS";
    public static final char PathSeperatorChar = '/';

    // Logging
    private static Log logger = LogFactory.getLog(ActivityPoster.class);

    private ActivityService activityService;
    private SiteService siteService;
    private TenantService tenantService;
    private NodeService nodeService;
    private FileFolderService fileFolderService;
    private HiddenAspect hiddenAspect;

    private boolean activitiesEnabled = true;

	/**
     * Constructor
     */
    public ActivityPosterImpl()
    {
    }

	public void setHiddenAspect(HiddenAspect hiddenAspect)
	{
		this.hiddenAspect = hiddenAspect;
	}

	public void setFileFolderService(FileFolderService fileFolderService)
    {
		this.fileFolderService = fileFolderService;
	}

	public void setTenantService(TenantService tenantService)
	{
		this.tenantService = tenantService;
	}

	public void setSiteService(SiteService siteService)
    {
		this.siteService = siteService;
	}

	public void setNodeService(NodeService nodeService)
	{
		this.nodeService = nodeService;
	}

	public void setActivityService(ActivityService activityService)
    {
		this.activityService = activityService;
	}

	public void setActivitiesEnabled(boolean activitiesEnabled)
	{
		this.activitiesEnabled = activitiesEnabled;
	}
	
    private boolean isHidden(NodeRef nodeRef)
    {
        return nodeService.hasAspect(nodeRef, ContentModel.ASPECT_HIDDEN);
    }

    private final String getPathFromNode(NodeRef rootNodeRef, NodeRef nodeRef) throws FileNotFoundException
    {
        // Check if the nodes are valid, or equal
        if (rootNodeRef == null || nodeRef == null)
            throw new IllegalArgumentException("Invalid node(s) in getPathFromNode call");
        
        // short cut if the path node is the root node
        if (rootNodeRef.equals(nodeRef))
            return "";
        
        // get the path elements
        List<FileInfo> pathInfos = fileFolderService.getNamePath(rootNodeRef, nodeRef);
        
        // build the path string
        StringBuilder sb = new StringBuilder(pathInfos.size() * 20);
        for (FileInfo fileInfo : pathInfos)
        {
            sb.append(PathSeperatorChar);
            sb.append(fileInfo.getName());
        }
        // done
        if (logger.isDebugEnabled())
        {
            logger.debug("Build name path for node: \n" +
                    "   root: " + rootNodeRef + "\n" +
                    "   target: " + nodeRef + "\n" +
                    "   path: " + sb);
        }
        return sb.toString();
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "activityService", activityService);
        PropertyCheck.mandatory(this, "siteService", siteService);
        PropertyCheck.mandatory(this, "tenantService", tenantService);
        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "fileFolderService", fileFolderService);
	}

    private String getCurrentTenantDomain()
    {
        String tenantDomain = tenantService.getCurrentUserDomain();
        if (tenantDomain == null)
        {
            return TenantService.DEFAULT_DOMAIN;
        }
        return tenantDomain;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void postFileAdded(FileInfo fileInfo)
    {
    	if(activitiesEnabled && !fileInfo.isHidden())
    	{
    		NodeRef nodeRef = fileInfo.getNodeRef();
        	SiteInfo siteInfo = siteService.getSite(nodeRef);
        	String siteId = (siteInfo != null ? siteInfo.getShortName() : null);
        	NodeRef parentNodeRef = nodeService.getPrimaryParent(nodeRef).getParentRef();
        	
    		postFileActivity(ActivityType.FILE_ADDED, null, parentNodeRef, nodeRef, siteId, fileInfo.getName());
    	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postFileUpdated(NodeRef nodeRef)
    {
    	if(activitiesEnabled && hiddenAspect.getVisibility(Client.cmis, nodeRef) == Visibility.Visible)
    	{
        	SiteInfo siteInfo = siteService.getSite(nodeRef);
        	String siteId = (siteInfo != null ? siteInfo.getShortName() : null);
        	String fileName = (String)nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

    		postFileActivity(ActivityType.FILE_UPDATED, null, null, nodeRef, siteId, fileName);
    	}
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void postFileDeleted(
            String parentPath,
            NodeRef parentNodeRef,
            NodeRef nodeRef,
            String siteId,
            String fileName)
    {
    	if(activitiesEnabled)
    	{
    		postFileActivity(ActivityType.FILE_DELETED, parentPath, parentNodeRef, nodeRef, siteId, fileName);
    	}
    }

    public String getParentPath(NodeRef nodeRef)
    {
    	SiteInfo siteInfo = siteService.getSite(nodeRef);
    	String siteId = (siteInfo != null ? siteInfo.getShortName() : null);
    	NodeRef parentNodeRef = nodeService.getPrimaryParent(nodeRef).getParentRef();
        NodeRef documentLibrary = siteService.getContainer(siteId, SiteService.DOCUMENT_LIBRARY);
        String parentPath = "/";
        try
        {
        	parentPath = getPathFromNode(documentLibrary, parentNodeRef);
	    }
	    catch (FileNotFoundException error)
	    {
	        if (logger.isDebugEnabled())
	        {
	            logger.debug("No " + SiteService.DOCUMENT_LIBRARY + " container found.");
	        }
	    }

	    return parentPath;
    }

    private void postFileActivity(
            String activityType,
            String parentPath,
            NodeRef parentNodeRef,
            NodeRef nodeRef,
            String siteId,
            String fileName)
    {
    	JSONObject json = createActivityJSON(getCurrentTenantDomain(), parentPath, parentNodeRef, nodeRef, fileName);

    	activityService.postActivity(
    			activityType,
    			siteId,
    			APP_TOOL,
    			json.toString());
    }
    
    /**
     * Create JSON suitable for create, modify or delete activity posts. Returns a new JSONObject
     * containing appropriate key/value pairs.
     * 
     * @param tenantDomain
     * @param nodeRef
     * @param fileName
     * @throws WebDAVServerException
     * @return JSONObject
     */
    private JSONObject createActivityJSON(
                String tenantDomain,
                String parentPath,
                NodeRef parentNodeRef,
                NodeRef nodeRef,
                String fileName)
    {
        JSONObject json = new JSONObject();
        try
        {
            json.put("nodeRef", nodeRef);
            
            if (parentNodeRef != null)
            {
                // Used for deleted files.
                json.put("parentNodeRef", parentNodeRef);
            }
            
            if (parentPath != null)
            {
                // Used for deleted files.
                json.put("page", "documentlibrary?path=" + parentPath);
            }
            else
            {
                // Used for added or modified files.
                json.put("page", "document-details?nodeRef=" + nodeRef);
            }
            json.put("title", fileName);
            
            if (!tenantDomain.equals(TenantService.DEFAULT_DOMAIN))
            {
                // Only used in multi-tenant setups.
                json.put("tenantDomain", tenantDomain);
            }
        }
        catch (JSONException error)
        {
            throw new AlfrescoRuntimeException("", error);
        }
        
        return json;
    }
}