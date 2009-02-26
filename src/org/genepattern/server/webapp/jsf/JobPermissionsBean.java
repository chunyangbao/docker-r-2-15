package org.genepattern.server.webapp.jsf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.faces.context.FacesContext;

import org.apache.log4j.Logger;
import org.genepattern.server.PermissionsHelper;
import org.genepattern.server.auth.GroupPermission;
import org.genepattern.server.auth.GroupPermission.Permission;

/**
 * Backing bean for viewing and editing access permissions for a job result.
 * 
 * Should be request scope.
 * @author pcarr
 */
public class JobPermissionsBean {
    private static Logger log = Logger.getLogger(JobPermissionsBean.class);

    private int jobId = -1;
    private Permission publicAccessPermission;
    private List<GroupPermission> groupAccessPermissions;
    
    private boolean isPublic = false;
    private boolean isShared = false;
    
    //is the current user allowed to delete the job
    private boolean isDeleteAllowed = false;
    
    //toggle-state in Job Result page
    private boolean showPermissionsDiv = false;

    //for displaying read-only summary information (e.g. in Job Results Page)
    private List<String> groupIdsWithFullAccess;
    private List<String> groupIdsWithReadOnlyAccess;

    public JobPermissionsBean() {
    }
    
    /**
     * Load (or reload) the values from the database. Requires a valid jobId.
     */
    private void reset() { 
        String currentUserId = UIBeanHelper.getUserId();
        PermissionsHelper permissionsHelper = new PermissionsHelper(currentUserId, jobId);
        this.isPublic = permissionsHelper.isPublic();
        this.isShared = permissionsHelper.isShared();
        this.publicAccessPermission = permissionsHelper.getPublicAccessPermission();
        this.isDeleteAllowed = permissionsHelper.canWriteJob();
        
        List<GroupPermission> currentPermissions = permissionsHelper.getNonPublicPermissions();
        //copy
        groupAccessPermissions = new ArrayList<GroupPermission>();
        for (GroupPermission gp : currentPermissions) {
            groupAccessPermissions.add(new GroupPermission(gp.getGroupId(), gp.getPermission()));
        }
        
        SortedSet<String> g_full_access = new TreeSet<String>();
        SortedSet<String> g_read_only_access = new TreeSet<String>();
        for(GroupPermission gp : permissionsHelper.getJobResultPermissions(false)) {
            if (gp.getPermission() == Permission.READ_WRITE) {
                g_full_access.add(gp.getGroupId());
            }
            else if (gp.getPermission() == Permission.READ) {
                g_read_only_access.add(gp.getGroupId());
            }
        }
        groupIdsWithFullAccess = new ArrayList<String>(g_full_access);
        groupIdsWithReadOnlyAccess = new ArrayList<String>(g_read_only_access);
    }
    
    public void setJobId(int jobId) {
        this.jobId = jobId;
        reset();
    }
    
    public int getJobId() {
    	return this.jobId;
    }
    
    public Permission getPublicAccessPermission() {
        return publicAccessPermission;
    }
    
    public void setPublicAccessPermission(Permission p) {
        this.publicAccessPermission = p;
    }
    
    public int getNumGroupAccessPermissions() {
        return groupAccessPermissions == null ? 0 : groupAccessPermissions.size();
    }

    public List<GroupPermission> getGroupAccessPermissions() {
        return groupAccessPermissions;
    }
    
    public boolean isDeleteAllowed() {
        return isDeleteAllowed;
    }
    
    public boolean isPublic() {
        return isPublic;
    }

    public boolean isShared() {
        return isShared;
    }

    public boolean isShowPermissionsDiv() {
        return showPermissionsDiv;
    }
    
    public void setShowPermissionsDiv(boolean b) {
        this.showPermissionsDiv = b;
    }
    
    //helpers for read only view on 'Job Results' page
    public String getPermissionsLabel() {
        if (isPublic()) {
            return "Public";
        }
        else if (isShared()) {
            return "Shared";
        }
        return "Private";
    }

    public int getNumGroupsWithFullAccess() {
        return groupIdsWithFullAccess.size();
    }
    
    public List<String> getGroupsWithFullAcess() {
        return Collections.unmodifiableList(groupIdsWithFullAccess);
    }

    public int getNumGroupsWithReadOnlyAccess() {
        return groupIdsWithReadOnlyAccess.size();
    }

    public List<String> getGroupsWithReadOnlyAccess() {
        return Collections.unmodifiableList(groupIdsWithReadOnlyAccess);
    }
    
    
    //helpers for updating permissions
    /**
     * Process request parameters (from form submission) and update the access permissions for the current job.
     * Only the owner of a job is allowed to change its permissions.
     */
    public String savePermissions() { 
        // parse request parameters from jobResultsPermissionsForm (see jobSharing.xhtml)
        // JSF for public permission
        // generated html for the groups
        // name="jobAccessPerm:#{groupPermission.groupId}" value="#{groupPermission.permission.flag}"
        
        //NOTE: don't edit the jobSharing.xhtml without also editing this page 
        //    in other words, DON'T REUSE THIS CODE in another page unless you know what you are doing
        Set<GroupPermission> updatedPermissions = new HashSet<GroupPermission>();
        if (publicAccessPermission != Permission.NONE) {
            updatedPermissions.add(new GroupPermission(GroupPermission.PUBLIC, publicAccessPermission));
        }
        Map<String,String[]> requestParameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterValuesMap();
        for(String name : requestParameters.keySet()) {
            int idx = name.indexOf("jobAccessPerm:");
            if (idx >= 0) {
                idx += "jobAccessPerm:".length();
                String groupId = name.substring(idx);
                Permission groupAccessPermission = Permission.NONE;
                
                String permFlagStr = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get(name);
                try {
                    groupAccessPermission = Permission.valueOf(permFlagStr);
                }
                catch (IllegalArgumentException e) {
                    handleException("Ignoring permissions flag: "+permFlagStr, e);
                    return "error";
                    
                }
                catch (NullPointerException e) {
                    handleException("Ignoring permissions flag: "+permFlagStr, e);
                    return "error";
                    
                }
                if (groupAccessPermission != Permission.NONE) {
                    //ignore NONE
                    updatedPermissions.add(new GroupPermission(groupId, groupAccessPermission));
                }
            }
        }
        
        try {
            String currentUserId = UIBeanHelper.getUserId();
            PermissionsHelper permissionsHelper = new PermissionsHelper(currentUserId, jobId);
            permissionsHelper.setPermissions(updatedPermissions);
            setShowPermissionsDiv(true);
            reset();
            return "success";
        }
        catch (Exception e) {
            reset();
            handleException("You are not authorized to change the permissions for this job", e);
            return "error";
        }
    }

    private void handleException(String message, Exception e) {
        log.error(message, e);
        UIBeanHelper.setErrorMessage(message);
    }
}
