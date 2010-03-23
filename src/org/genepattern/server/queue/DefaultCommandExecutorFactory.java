package org.genepattern.server.queue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Default implementation of the CommandExecutorFactory interface, it loads configuration settings 
 * from a single properties file, 'queue.properties', which must be in the same directory as the 'genepattern.properties' file.
 * 
 * @author pcarr
 */
public class DefaultCommandExecutorFactory implements CommandExecutorFactory {
    private static Logger log = Logger.getLogger(DefaultCommandExecutorFactory.class);
    
    private List<CommandExecutor> cmdExecutorList = new ArrayList<CommandExecutor>();
    private DefaultCommandExecutorMapper mapper = new DefaultCommandExecutorMapper();

    public DefaultCommandExecutorFactory() {
    }

    private boolean initialized = false;

    /**
     * Initialize the list of CommandExecutors.
     */
    private void init() {
        if (initialized) {
            return;
        }
        synchronized(this) {
            try {
                File queuePropertiesFile = new File(System.getProperty("genepattern.properties"), "queue.properties");
                if (!queuePropertiesFile.canRead()) {
                    if (!queuePropertiesFile.exists()) {
                        log.info("Command executor configuration file not found: "+queuePropertiesFile.getPath());
                    }
                    else {
                        log.error("Not able to read command executor configuration file: "+queuePropertiesFile.getPath());
                    }
                    defaultInit();
                    return;
                }
                Properties queueProperties = new Properties();
                try {
                    queueProperties.load(new FileInputStream(queuePropertiesFile));
                    initFromProperties(queueProperties);
                } 
                catch (IOException e) {
                    log.error("Failed to initialize command executor factory: "+e.getLocalizedMessage(), e);
                }
            }
            finally {
                initialized=true;
            }
        }
    }

    /**
     * Configuration when no properties file is found.
     */
    private void defaultInit() {
        log.info("Using default configuration for command execution");
        //use runtime exec for all jobs
        CommandExecutor cmdExec = new RuntimeCommandExecutor();
        cmdExecutorList.add(cmdExec);
        mapper.addCommandExecutor("default", cmdExec);
        mapper.setDefaultCmdExecId("default");
    }
    

    private void initFromProperties(Properties props) {
            Enumeration e = props.propertyNames();
            while(e.hasMoreElements()) {
                String propName = (String) e.nextElement();
                String propValue = props.getProperty(propName);
                if (isQueueId(propName)) {
                    loadQueue(propName, propValue);
                }
                else if ("default".equals(propName)) {
                    mapper.setDefaultCmdExecId(propValue);
                }
                else if(propName.startsWith("prop.")) {
                    //add to system properties
                    String sysProp=propName.substring("prop".length());
                    System.setProperty(sysProp, propValue);
                }
                else {
                    mapper.appendTask(propName, propValue);
                }
            }
    }
    
    private boolean isQueueId(String propName) {
        return propName.startsWith("queue.");
    }
    
    private void loadQueue(String queueId, String classname) {
        CommandExecutor svc = loadCommandExecutor(classname);
        if (svc != null) {
            cmdExecutorList.add(svc);
            mapper.addCommandExecutor(queueId, svc);
        }
        else {
            log.error("Failed to initialize queue, queue.id="+queueId+", classname="+classname);
        }
    }
    
    private CommandExecutor loadCommandExecutor(String svcClassname) {
        CommandExecutor svc = null;
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class svcClass = Class.forName(svcClassname, false, classLoader);
            if (!CommandExecutor.class.isAssignableFrom(svcClass)) {
                log.error(""+svcClass.getCanonicalName()+" does not implement "+CommandExecutor.class.getCanonicalName());
                return svc;
            }
            svc = (CommandExecutor) svcClass.newInstance();
            return svc;
        }
        catch (Throwable t) {
            log.error("Error loading CommandExecutor for classname: "+svcClassname+", "+t.getLocalizedMessage(), t);
        }
        return svc;
    }
    
    /**
     * call this at system startup to initialize the list of CommandExecutorService instances.
     */
    public void start() {
        init();
        
        for(CommandExecutor svc : cmdExecutorList) {
            try {
                svc.start();
            }
            catch (Throwable t) {
                log.error("Error starting CommandExecutorService, for class: "+svc.getClass().getCanonicalName()+": "+t.getLocalizedMessage(), t);
            }
        }
    }
    
    /**
     * call this at system shutdown to stop the list of running CommandExecutorService instances.
     */
    public void stop() {
        for(CommandExecutor svc : cmdExecutorList) {
            try {
                svc.stop();
            }
            catch (Throwable t) {
                log.error("Error stopping CommandExecutorService, for class: "+svc.getClass().getCanonicalName()+": "+t.getLocalizedMessage(), t);
            }
        }
    }

    public CommandExecutorMapper getCommandExecutorMapper() {
        return mapper;
    }

}
