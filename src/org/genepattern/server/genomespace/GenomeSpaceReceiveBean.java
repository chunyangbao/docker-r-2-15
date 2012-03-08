package org.genepattern.server.genomespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.genepattern.server.dm.GpFilePath;
import org.genepattern.server.webapp.jsf.RunTaskBean;
import org.genepattern.server.webapp.jsf.UIBeanHelper;
import org.genepattern.server.webapp.uploads.UploadFilesBean;
import org.genepattern.server.webapp.uploads.UploadFilesBean.DirectoryInfoWrapper;
import org.genepattern.webservice.TaskInfo;

public class GenomeSpaceReceiveBean {
    private static Logger log = Logger.getLogger(GenomeSpaceReceiveBean.class);
    
    private UploadFilesBean uploadBean = null;
    private GenomeSpaceBean genomeSpaceBean = null;
    private List<GSReceivedFileWrapper> receivedFiles = null;
    private String oldRequestString = null;
    
    public String getRefreshPage() throws IOException {
        HttpServletRequest request = UIBeanHelper.getRequest();
        String queryString = request.getQueryString();
        if (queryString != null && queryString.length() > 0) {
            blankCurrentTaskInfo();
            cleanBean();
        }
        
        if (oldRequestString != null && !oldRequestString.equals(queryString)) {
            cleanBean();
        }
        
        List<GSReceivedFileWrapper> files = getReceivedFiles();
        if (files.size() < 1) {
            UIBeanHelper.getResponse().sendRedirect("/gp/");
        }

        oldRequestString = queryString;
        return null;
    }
    
    private void cleanBean() {
        receivedFiles = null;
    }
    
    private List<GSReceivedFileWrapper> parseParameters() {
        List<GSReceivedFileWrapper> received = new ArrayList<GSReceivedFileWrapper>();
        HttpServletRequest request = UIBeanHelper.getRequest();
        String filesString = request.getParameter("files");
        
        if (!initGenomeSpaceBean()) {
            log.error("Unable to acquire reference to GenomeSpaceBean in GenomeSpaceReceiveBean.parseParameters()");
            return received;
        }
        
        if (filesString != null) {
            String[] fileParams = filesString.split(",");
            for (String param : fileParams) {
                GpFilePath file = genomeSpaceBean.getFile(param);
                GSReceivedFileWrapper wrapped = new GSReceivedFileWrapper(file);
                if (file != null) received.add(wrapped);
            }
        }
        return received;
    }
    
    private void populateSelectItems() {
        for (GSReceivedFileWrapper i : receivedFiles) {
            for (String format : ((GenomeSpaceFile) i.getFile()).getConversions()) {
                SortedSet<TaskInfo> infos = getKindToModules().get(format);
                List<SelectItem> items = new ArrayList<SelectItem>();
                if (infos != null) {
                    for (TaskInfo j : infos) {
                        SelectItem item = new SelectItem();
                        item.setLabel(j.getName());
                        item.setValue(j.getLsid());
                        items.add(item);
                    }
                }
                i.setModuleSelects(format, items);
            }
        }
    }
    
    private void blankCurrentTaskInfo() {
        GenomeSpaceBean genomeSpaceBean = (GenomeSpaceBean) UIBeanHelper.getManagedBean("#{genomeSpaceBean}");
        genomeSpaceBean.setSelectedModule("");
    }
    
    public List<GSReceivedFileWrapper> getReceivedFiles() {
        if (receivedFiles == null) {
            receivedFiles = parseParameters();
            populateSelectItems();
        }
        return receivedFiles;
    }
    
    private boolean initGenomeSpaceBean() {
        if (genomeSpaceBean == null) {
            genomeSpaceBean = (GenomeSpaceBean) UIBeanHelper.getManagedBean("#{genomeSpaceBean}");
        }
        return genomeSpaceBean != null ? true : false;
    }
    
    private boolean initUploadBean() {
        if (uploadBean == null) {
            uploadBean = (UploadFilesBean) UIBeanHelper.getManagedBean("#{uploadFilesBean}");
        }
        return uploadBean != null ? true : false;
    }
    
    public List<SelectItem> getUploadDirectories() {
        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        boolean addedUploadRoot = false;
        if (!initUploadBean()) {
            log.error("Unable to acquire reference to UploadFilesBean in GenomeSpaceReceiveBean.getUploadDirectories()");
            return selectItems;
        }
        for (DirectoryInfoWrapper dir : uploadBean.getDirectories()) {
            SelectItem item = new SelectItem();
            if (dir.getPath().equals("./")) {
                if (!addedUploadRoot) item.setLabel("Upload Directory");
                else continue;
                addedUploadRoot = true;
            }
            else {
                item.setLabel(dir.getPath());
            }
            item.setValue(dir.getPath());
            selectItems.add(item);
        }
        return selectItems;
    }
    
    public String getRootUploadDirectory() {
        return "Upload Directory";
    }
    
    public Map<String, SortedSet<TaskInfo>> getKindToModules() {
        if (!initUploadBean()) {
            log.error("Unable to acquire reference to UploadFilesBean in GenomeSpaceReceiveBean.getKindToModules()");
            return null;
        }
        return uploadBean.getKindToTaskInfo();
    }

    public String loadTask() {
        HttpServletRequest request = UIBeanHelper.getRequest();
        String lsid = request.getParameter("module");
        lsid = UIBeanHelper.decode(lsid);
        request.setAttribute("lsid", lsid);
        
        for (Object i : request.getParameterMap().keySet()) {
            String parameter = (String) i;
            if (parameter.endsWith(":source")) {
                String attribute = UIBeanHelper.decode(request.getParameter(parameter));
                request.setAttribute("outputFileSource", attribute);
            }
            if (parameter.endsWith(":name")) {
                String attribute = UIBeanHelper.decode(request.getParameter(parameter));
                request.setAttribute("outputFileName", attribute);
            }
            if (parameter.endsWith(":path")) {
                String attribute = UIBeanHelper.decode(request.getParameter(parameter));
                request.setAttribute("downloadPath", attribute);
            }
        }
        
        //cleanBean();
        RunTaskBean runTaskBean = (RunTaskBean) UIBeanHelper.getManagedBean("#{runTaskBean}");
        assert runTaskBean != null;
        runTaskBean.setTask(lsid);
        return "run task";
    }
    
    public String prepareSaveFile() {
        HttpServletRequest request = UIBeanHelper.getRequest();
        String directoryPath = null;
        String fileUrl = null;
        
        for (Object i : request.getParameterMap().keySet()) {
            String parameter = (String) i;
            if (parameter.endsWith(":uploadDirectory")) {
                directoryPath = UIBeanHelper.decode(request.getParameter(parameter));
            }
            if (parameter.endsWith(":path")) {
                fileUrl = UIBeanHelper.decode(request.getParameter(parameter));
            }
        }
        
        if (directoryPath == null || fileUrl == null) {
            log.error("directoryPath was null in prepareSaveFile(): " + fileUrl);
            UIBeanHelper.setErrorMessage("Unable to get the selected directory to save file");
            return null;
        }
        
        GenomeSpaceBean genomeSpaceBean = (GenomeSpaceBean) UIBeanHelper.getManagedBean("#{genomeSpaceBean}");
        genomeSpaceBean.saveFileToUploads(fileUrl, directoryPath);

        //cleanBean();
        return "home";
    }
    
    public class GSReceivedFileWrapper {
        private GpFilePath file = null;
        public Map<String, List<SelectItem>> moduleSelects = new HashMap<String, List<SelectItem>>();
        
        public GSReceivedFileWrapper(GpFilePath file) {
            this.file = file;
        }
        
        public GpFilePath getFile() {
            return file;
        }
        
        public Map<String, List<SelectItem>> getModuleSelects() {
            return moduleSelects;
        }
        
        public List<SelectItem> getModuleSelects(String extension) {
            return moduleSelects.get(extension);
        }
        
        public void setModuleSelects(String extension, List<SelectItem> moduleSelects) {
            this.moduleSelects.put(extension, moduleSelects);
        }
    }
}
