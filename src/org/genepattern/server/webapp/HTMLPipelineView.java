package org.genepattern.server.webapp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.genepattern.data.pipeline.JobSubmission;
import org.genepattern.data.pipeline.PipelineModel;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.util.GPConstants;
import org.genepattern.util.LSID;
import org.genepattern.webservice.OmnigeneException;
import org.genepattern.webservice.ParameterFormatConverter;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskInfoAttributes;
import org.genepattern.webservice.WebServiceException;
import org.genepattern.server.webservice.server.local.LocalTaskIntegratorClient;
		
import org.genepattern.server.webservice.server.local.LocalAdminClient;

public class HTMLPipelineView implements IPipelineView {

	/** maximum number of tasks definable in a pipeline (arbitrary) */
	int MAX_TASKS = 50;
	
	Writer writer = null;
	String submitURL = null;
	int taskNum = 0;
	TreeMap tmTaskTypes = null; // TreeMap of task type/TreeMap pairs.  TreeMap value is a TaskInfo object.
	Collection tmCatalog = null; // TaskInfo collection.
	String userAgent = null;
	String pipelineName = null;
	String userID = null;
	Map tmTasksByLSID = null;

	public HTMLPipelineView(Writer writer, String submitURL, String userAgent, String pipelineName) throws Exception {
		this.writer = writer;
		this.submitURL = submitURL;
		this.userAgent = userAgent;
		this.pipelineName = pipelineName;

		if (userAgent.indexOf("Mozilla/4") > -1 && userAgent.indexOf("MSIE") == -1) {
			System.err.println("userAgent=" + userAgent);
			throw new Exception("Cannot design pipelines using Netscape Navigator 4.x.  Try Netscape Navigator version 7 or Internet Explorer instead.");
		}

	}
	
	public void init(Collection tmCatalog, String userID) {
		this.userID = userID;
		this.tmCatalog = tmCatalog;
		tmTaskTypes = preprocessTaskInfo(tmCatalog);
		try {
			tmTasksByLSID = new org.genepattern.server.webservice.server.local.LocalAdminClient(userID).getTaskCatalogByLSID(tmCatalog);
		} catch (org.genepattern.webservice.WebServiceException re) {
			System.err.println(re.getMessage() + " in HTMLPipelineView.init");
			throw new RuntimeException(re);
		}
		//dumpCatalog();
		try {
			writer.write("");
		} catch (IOException ioe) {
		}
	}
	
	/**
	 * Create TaskType, TaskInfo, ParameterInfo, and TaskTypes objects based on their internal GenePattern data,
	 * but limited to the data actually necessary for building a pipeline.  Populate TaskTypes and TaskInfo array.
	 * TaskTypes is an associative array of task names organized by task type.
	 *
	 * @author Jim Lerner
	 * @throws IOException
	 *
	 */
	protected void writeTaskData() throws IOException {
		String taskName = null;
		String lsid = null;
		TaskInfo taskInfo = null;
		TaskInfoAttributes tia = null;
		String taskType = null;
		Collection tmTasks = null;
		ParameterInfo[] parameterInfoArray = null;
		
		writer.write("<script language=\"Javascript\">\n");

		writer.write("var PIPELINE = \"" + GenePatternAnalysisTask.TASK_TYPE_PIPELINE + "\";\n");
		writer.write("var MAX_TASKS = " + MAX_TASKS + ";\n");
		writer.write("var divs = new Array(MAX_TASKS);\n");
		writer.write("for (tNum = 0; tNum < MAX_TASKS; tNum++) {\n");
		writer.write("	divs[tNum] = '';\n");
		writer.write("}\n");

		// build array of task types (eg. filters, visualizers)
		writer.write("var TaskTypesList = new Array(");
		for (Iterator itTaskTypes = tmTaskTypes.keySet().iterator(); itTaskTypes.hasNext(); ) {
			taskType = (String)itTaskTypes.next();
			writer.write("\"" + taskType + "\"");
			if (itTaskTypes.hasNext()) writer.write(", ");
		}
		writer.write(");\n");

		// build associative array of task names by task type (eg. filters, visualizers)
		writer.write("var TaskTypes = new Array();\n");
		for (Iterator itTaskTypes = tmTaskTypes.keySet().iterator(); itTaskTypes.hasNext(); ) {
			taskType = (String)itTaskTypes.next();
			tmTasks = (Collection)tmTaskTypes.get(taskType);
			writer.write("TaskTypes[\"" + taskType + "\"] = [");
			for (Iterator itTasks = tmTasks.iterator(); itTasks.hasNext(); ) {
				lsid = (String)((TaskInfo)itTasks.next()).getTaskInfoAttributes().get(GPConstants.LSID);
				writer.write("\"" + lsid + "\"");
				if (itTasks.hasNext()) writer.write(", ");
			}
			writer.write("];\n");
		}
		writer.write("\n");

		// build associative array of TaskInfos
		writer.write("var TaskInfos = new Object();\n");
		for (Iterator itTaskTypes = tmTaskTypes.keySet().iterator(); itTaskTypes.hasNext(); ) {
			taskType = (String)itTaskTypes.next();
			tmTasks = (Collection)tmTaskTypes.get(taskType);
			for (Iterator itTasks = tmTasks.iterator(); itTasks.hasNext(); ) {
				taskInfo = (TaskInfo)itTasks.next();
				tia = taskInfo.giveTaskInfoAttributes();
				lsid = (String)tia.get(GPConstants.LSID);

				writer.write("TaskInfos[\"" + lsid + "\"] = new TaskInfo(\"" + 
						taskInfo.getName() + "\", \"" + GenePatternAnalysisTask.htmlEncode(taskInfo.getDescription()) +
						"\", \"" + lsid + 
						"\", \"" + taskType + "\", new Array(");

				// build an array of Option(text, value, defaultSelected, selected)
				try {
			        	parameterInfoArray = new ParameterFormatConverter().getParameterInfoArray(taskInfo.getParameterInfo());
				} catch (OmnigeneException oe) {
				}
				for (int i = 0; parameterInfoArray != null && i < parameterInfoArray.length; i++) {
					if (i > 0) writer.write(", ");
					ParameterInfo pi = parameterInfoArray[i];
					HashMap pia = pi.getAttributes();
					writer.write("new ParameterInfo(\"" + pi.getName() + "\", \"" + 
									      GenePatternAnalysisTask.htmlEncode(pi.getDescription())  + "\", \"" + 
									      GenePatternAnalysisTask.htmlEncode((String)pi.getValue()) + "\", " +
									      (pi.isInputFile() ? "true" : "false") + ", " +
									      (pi.isOutputFile() ? "true" : "false") + ", \"" +
									      ((pia != null && pia.get(GenePatternAnalysisTask.PARAM_INFO_DEFAULT_VALUE[0]) != null) ? GenePatternAnalysisTask.htmlEncode(((String)pia.get(GenePatternAnalysisTask.PARAM_INFO_DEFAULT_VALUE[0])).trim()) : "") + "\", " +
									      ((pia != null && pia.get(GenePatternAnalysisTask.PARAM_INFO_OPTIONAL[0]) != null) ? "true" : "false") + 
									      ")");
				}
				writer.write(")"); // close Array
				writer.write(", new Array("); // create array of doc filenames

				try {
					
					File[] docFiles = new LocalTaskIntegratorClient(userID).getDocFiles(taskInfo);
					for (int i = 0; i < docFiles.length; i++) {
						if (i > 0) writer.write(",");
						writer.write("\"" + docFiles[i].getName() + "\"");
					}
				} catch (Exception e) {}
				writer.write(")"); // close array of docs
				writer.write(");\n"); // close TaskInfo
			}
		}

		writer.write("\n");
	}

	/**
	 * generate the body of the page
	 *
	 * @author Jim Lerner
	 * @throws IOException
	 */
	protected void writeEndHead() throws IOException {
		TaskInfo taskInfo = (pipelineName != null ? (TaskInfo)tmTasksByLSID.get(pipelineName) : null);
		LSID taskLSID = null;
		if (taskInfo != null) {
  			try {
  				taskLSID = new LSID((String)taskInfo.giveTaskInfoAttributes().get(GPConstants.LSID));
	  		} catch (MalformedURLException mue) {
  				// ignore
  			}
		}

		writer.write("var thisTaskName = window.location.search.split('=')[1];\n");
		writer.write("if (thisTaskName == null) thisTaskName = '';\n");
		writer.write("thisTaskName = thisTaskName.split('#')[0];\n"); // strip the hash from the URL
		writer.write("if (thisTaskName == null) thisTaskName = '';\n");

		writer.write("var versionlessLSIDs = new Array();\n");
		writer.write("TaskTypes[PIPELINE].sort(sortTaskTypesByName);\n");
		writer.write("var lsid = new LSID(thisTaskName);\n");
		writer.write("var thisLSIDNoVersion = lsid.authority + '" + LSID.DELIMITER + "' + lsid.identifier + '" + LSID.DELIMITER + "';\n");

		writer.write("</script>\n");
		writer.write("<title>" + (taskInfo != null ? taskInfo.getName() : "new pipeline") + "</title>\n");
		writer.write("</head>\n");
		writer.write("<body>\n");

		// simulate <jsp:include page="navbar.jsp">
		String navbarURL = submitURL + "/../navbar.jsp?" + GenePatternAnalysisTask.USERID + "=" + userID;
		URL url = new URL(navbarURL);
		InputStream is = url.openStream();
		if (is == null) {
			System.err.println("null connection to navbar.jsp");
		} else {
			byte[] buf = new byte[2000];
			int numRead;
			while ((numRead = is.read(buf)) != -1) {
				writer.write(new String(buf, 0, numRead));
			}
		}

		writer.write("<form name=\"pipeline\" action=\"" + submitURL + "\" target=\"pipeline_code\" method=\"post\" ENCTYPE=\"multipart/form-data\">\n");

		writer.write("<h2>GenePattern Pipeline Designer");
		if (taskInfo != null) {
			writer.write(" - " + taskInfo.getName() + " version ");
			writer.write("<select name=\"notused\" onchange=\"javascript:window.location='pipelineDesigner.jsp?" + GPConstants.NAME + "=' + this.options[this.selectedIndex].value\" style=\"font-weight: bold; font-size: medium; outline-style: none;\">\n");
			writer.write(versionSelector(taskInfo));
	   		writer.write("</select>\n");
		}
		writer.write("</h2>\n");
		writer.write("<table cols=\"2\">\n");
		
		writer.write("<tr><td align=\"right\" width=\"1\" valign=\"top\"><a name=\"0\"></a>Pipeline&nbsp;name:</td><td width=\"*\"><input name=\"pipeline_name\" value=\"\" size=\"" + (pipelineName != null ? pipelineName.length() : 20) + "\" onchange=\"javascript:if (document.forms['pipeline'].pipeline_name.value != '' && !isRSafe(document.forms['pipeline'].pipeline_name.value)) alert(pipelineInstruction);\"> (* required)\n");
		writer.write("<input type=\"hidden\" name=\"cloneName\">\n");
		writer.write("<input type=\"hidden\" name=\"autoSave\">\n");

		writer.write("&nbsp;&nbsp;&nbsp; <select name=\"changePipeline\" onChange=\"window.location='pipelineDesigner.jsp?name=' + this.options[this.selectedIndex].value\">\n");

		writer.write("<script language=\"Javascript\">\n");
		writer.write("document.writeln('<option value=\"\">new pipeline</option' + (thisTaskName == \"\" ? ' selected' : '') + '>');\n");

		writer.write("for (i in TaskTypes[PIPELINE]) {\n");
		writer.write("	var pipelineLSID = TaskTypes[PIPELINE][i];\n");
		writer.write("	var pipelineName = TaskInfos[pipelineLSID].name;\n");
		writer.write("	lsid = new LSID(pipelineLSID);\n");
		writer.write("	var key = lsid.authority + '" + LSID.DELIMITER + "' + lsid.identifier + '" + LSID.DELIMITER + "';\n");
		writer.write("	if (versionlessLSIDs[key] == null) {\n");
		writer.write("		document.writeln('<option value=\"' + pipelineLSID + '\"' + ((pipelineName == thisTaskName || pipelineLSID == thisTaskName || key == thisLSIDNoVersion) ? ' selected' : '') + ' class=\"tasks-' + lsid.authorityType + '\">' + pipelineName.substring(0, pipelineName.lastIndexOf('.')) + '</option>');\n");
		writer.write("		versionlessLSIDs[key] = pipelineLSID;\n");
		writer.write("	}\n");
		writer.write("}\n");
		writer.write("</script>\n");

		writer.write("</select>\n");

		// build version selector		
		if (taskInfo != null) {
			writer.write("<select name=\"notused\" onchange=\"javascript:window.location='pipelineDesigner.jsp?" + GPConstants.NAME + "=' + this.options[this.selectedIndex].value\">\n");
			writer.write(versionSelector(taskInfo));
	   		writer.write("</select>\n");
       		}


		if (pipelineName != null && pipelineName.length() > 0) {
			writer.write("<input type=\"submit\" value=\"delete...\" name=\"delete\" onclick=\"return deletePipeline()\" class=\"little\">\n");
			writer.write("<input type=\"submit\" value=\"clone...\" name=\"clone\" onclick=\"return clonePipeline()\" class=\"little\">\n");
		}
		writer.write("</td></tr>\n");

		
		writer.write("<tr><td align=\"right\" width=\"1\">Description:</td><td width=\"*\"><input name=\"pipeline_description\" value=\"\" size=\"80\"></td></tr>\n");
		writer.write("<tr><td align=\"right\" width=\"1\">Author:</td><td width=\"*\"><input name=\"pipeline_author\" value=\"\" size=\"40\"> (name)</td></tr>\n");
		writer.write("<tr><td align=\"right\" width=\"1\">Owner:</td><td width=\"*\"><input name=\"" + GenePatternAnalysisTask.USERID + "\" value=\"" + userID + "\" size=\"40\"> (email address)</td></tr>\n");

		writer.write("<tr><td align=\"right\" width=\"1\">Privacy:</td><td width=\"*\"><select name=\"" + GenePatternAnalysisTask.PRIVACY + "\">");
		String[] privacies = GenePatternAnalysisTask.PRIVACY_LEVELS;
		for (int i = 0; i < privacies.length; i++) {
			writer.write("<option value=\"" + privacies[i] + "\">" + privacies[i] + "</option>");
		}
		writer.write("</select></td></tr>\n");

		writer.write("<tr><td align=\"right\" width=\"1\" valign=\"top\">Version comment:</td><td width=\"*\"><textarea name=\"" + GenePatternAnalysisTask.VERSION + "\" cols=\"80\" rows=\"1\"></textarea></td></tr>\n");

		// output language is no longer used, but the radio button to support it is required to avoid getting an error on existing pipelines
		writer.write("<input type=\"radio\" name=\"" + GenePatternAnalysisTask.LANGUAGE + "\" style=\"visibility: hidden\">\n");

		// JTL 3/2/05 Adding spot to add doc to pipelines
		addPipelineDoc();


		writer.write("<tr><td align=\"right\" width=\"1\">LSID:</td><td width=\"*\"><input type=\"text\" name=\"" + GPConstants.LSID + "\" value=\"\" size=\"80\" readonly style=\"border-style: none\"></td></tr>\n");

		// TODO: great place for a summary of the tasks!  Eg. Threshold -> Slice -> GetRows, next line: Slice -> NearestNeighbors.
		// This would be an inverted tree based on input file inheritance

		writer.write("</table>\n");
		
		writer.write("<script language=\"Javascript\">\n");
		writer.write("for (tNum = 0; tNum < MAX_TASKS; tNum++) {\n");
		writer.write("	if (ie4 || ns4) {\n");
		writer.write("		document.writeln('<div id=\"id' + tNum + '\"></div>');\n");
		writer.write("	} else if (ns6) {\n");
		writer.write("		document.writeln('<layer id=\"' + tNum + '\" visibility=\"show\"></layer>');\n");
		writer.write("	}\n");
		writer.write("}\n");
		writer.write("</script>\n");

	}
	
	protected void addPipelineDoc()throws IOException {
	try {
		writer.write("<tr><td valign='top'>Documentation:</td><td>");
		TaskInfo task = new LocalAdminClient(userID).getTask(pipelineName);
		LocalTaskIntegratorClient taskIntegratorClient = new LocalTaskIntegratorClient(userID);
	 	File[] docFiles = taskIntegratorClient.getDocFiles(task);
		
		if (docFiles != null){

			if (docFiles.length > 0){
				for (int i = 0; i < docFiles.length; i++) {
					if (i > 0) writer.write("  ,");
					writer.write("<a href='getTaskDoc.jsp?name="+pipelineName+"'&file="+docFiles[i].getName()+" target='_new'>"+docFiles[i].getName()+"</a>");
				}
				
				if (docFiles.length > 1){
					  			
					writer.write("<nobr><select name='deleteFiles' width='30'>");
			   		writer.write("<option value=''>delete doc files...</option>");	
			   		for (int i = 0; i < docFiles.length; i++) { 
						writer.write("<option value='"+ GenePatternAnalysisTask.htmlEncode(docFiles[i].getName())+"'>"+ docFiles[i].getName()+"</option>"); 
			   		}  
			   		writer.write("</select>");
				
			   		writer.write("<input type='button' value='delete doc...' class='little' onclick='deleteDocFiles()'></nobr>");
			   	} else {
					writer.write("<input type='hidden' name='deleteFiles' value='"+docFiles[0].getName()+"'>");
					writer.write("<input type='button' value='delete "+docFiles[0].getName()+"'  onclick='deleteDocFiles()'>");

				}
				writer.write("</td></tr><tr><td></td><td>Add doc file");
			}

			writer.write("<input type='file' name='doc' size='30' ></td></tr>");

								
		}
			  
	} catch (WebServiceException e){
		// no doc displayed
		writer.write("<input type='file' name='doc' size='60' ></td></tr>");

	} catch (IOException ioe){
		writer.write("<input type='file' name='doc' size='60' ></td></tr>");

		throw ioe;
	} catch (Exception e){
		// no doc displayed			 	
		writer.write("<input type='file' name='doc' size='60' ></td></tr>");
	
	} 
    }

	protected String versionSelector(TaskInfo taskInfo) {
		// build version selector		
		
		StringBuffer sb = new StringBuffer();
		if (taskInfo != null) {
			LSID l;
			LSID taskLSID = null;
  			try {
  				taskLSID = new LSID((String)taskInfo.giveTaskInfoAttributes().get(GPConstants.LSID));
	  		} catch (MalformedURLException mue) {
  				// ignore
				return "";
  			}
                        String thisLSIDNoVersion = taskLSID.toStringNoVersion();
                        for (Iterator itTasks = tmCatalog.iterator(); itTasks.hasNext(); ) {
        			TaskInfo ti = (TaskInfo)itTasks.next();
        			TaskInfoAttributes tia2 = ti.giveTaskInfoAttributes();
                                String lsid = tia2.get(GPConstants.LSID);
        			try {
        				l = new LSID(lsid);
        				String versionlessLSID = l.toStringNoVersion();
					if (versionlessLSID.equals(thisLSIDNoVersion)) {
		                                sb.append("<option value=\"" + l.toString() + "\"" + (lsid.equals(taskLSID.toString()) ? " selected" : "") + ">" + l.getVersion() + "</option>\n");
        				}
        	 		} catch (MalformedURLException mue) {
        				// ignore
        	 		}
                        }
       		}
		return sb.toString();
	}
		
	public void begin() {
		taskNum = 0;
		try {
			writeTaskData();
			writeEndHead();
			writer.flush();
		} catch (IOException ioe) {
		}
	}

	public void generateTask(TaskInfo taskInfo) {
//		System.out.println("HTMLPipelineView.generateTask\n");
	}
	
	public void onSubmit() {
		System.out.println("HTMLPipelineView.onSubmit\n");
	}
	
	public void onCancel() {
		System.out.println("HTMLPipelineView.onCancel\n");
	}

	/**
	 * handle visualization step at end of processing pipeline
	 *
	 * @author Jim Lerner
	 *
	 */
	public void end() {
	    try {
			
			// remove this line when visualization is enabled again
			writer.write("<br><hr><input type=\"hidden\" name=\"display\" value=\"\"><br>\n");

			writer.write("<center>");
			writer.write("<input type=\"hidden\" name=\"cmd\" value=\"save\">\n");
			writer.write("<input type=\"button\" value=\"save\" name=\"save\" onclick=\"savePipeline(true, 'save')\" class=\"little\">&nbsp;&nbsp");
			writer.write("<input type=\"button\" value=\"run\" name=\"run\" onclick=\"savePipeline(false, 'run')\" class=\"little\">");
			writer.write("</center>\n");
			writer.write("</form>");
			writer.write("<script language=\"Javascript\">\n");
			//writer.write("function afterLoading() {\n");
			writer.write("	window.onerror = scriptError;\n");
			writer.write("	addAnother(0);\n");
			boolean askName = true;
			if (pipelineName != null) {
				Collection tmTask = (Collection)tmTaskTypes.get(GenePatternAnalysisTask.TASK_TYPE_PIPELINE);
				if (tmTask != null) {
					// find this pipeline in the Collection

					TaskInfo task = null;
					TaskInfoAttributes tia = null;
		                        for (Iterator itTasks = tmTask.iterator(); itTasks.hasNext(); ) {
		        			TaskInfo ti = (TaskInfo)itTasks.next();
		        			tia = ti.giveTaskInfoAttributes();
		                                String lsid = tia.get(GPConstants.LSID);
						if (ti.getName().equals(pipelineName) || lsid.equals(pipelineName)) {
							task = ti;
							break;
						}
		                        }

//System.out.println("HTMLPipelineView.end: task for " + pipelineName + "=" + task);
					if (task != null) {
//System.out.println("HTMLPipelineView.end: tia for " + pipelineName + "=" + tia);
						if (tia != null) {
						    // generate the Javascript that recapitulates the pipeline design
						    String recreateScript = null;
						    String serializedModel = (String)tia.get(GenePatternAnalysisTask.SERIALIZED_MODEL);
//System.out.println("HTMLPipelineView.end: serialized model for " + pipelineName + "=" + serializedModel);
						    if (serializedModel != null && serializedModel.length() > 0) {
							    try {
								PipelineModel model = PipelineModel.toPipelineModel(serializedModel);

								model.setLsid((String)tia.get(GPConstants.LSID));

								// Javascript code
								recreateScript = getJavascript(model);
								//System.out.println("regenerated Javascript length=" + recreateScript.length());
							    } catch (Exception e) {
									System.err.println(e.getMessage() + " while deserializing pipeline model");
									e.printStackTrace();
							    }		
						    }
						    // load legacy Javascript code
						    if (recreateScript == null) {
							// no serialized model available, check for legacy code
							recreateScript = tia.get(GPConstants.PIPELINE_SCRIPT);
							//if (recreateScript != null) System.out.println("legacy Javascript length=" + recreateScript.length());
						    }
						    if (recreateScript != null) {
							writer.write(recreateScript);
							askName = false;
						    }
						}
					}
				}
			} 
			if (askName) {
				writer.write("var n = window.prompt('Please enter a name for this pipeline.  ' + pipelineInstruction + '\\n\\nPipeline name:', '');\n");
				writer.write("if (n != null) document.pipeline['pipeline_name'].value = n;\n");
				writer.write("if (n != null && n.length > 0 && !isRSafe(n)) alert(pipelineInstruction);\n");
			}
			
			//writer.write("}\n");
			writer.write("</script>\n");
			writer.flush();
	    } catch (IOException ioe) {
	    	System.err.println(ioe + " while outputting end");
	    }
	}
	
	/**
	 * creates the script that pipelineDesigner.jsp uses to reload the pipeline design for post-creation editing
	 *
	 * @return Javascript code (almost language-agnostic)
	 * @author Jim Lerner
	 */	
	public String getJavascript(PipelineModel model) {
	    try {
		StringBuffer s = new StringBuffer();
		s.append("	setField(\"pipeline_name\", \"" + javascriptEncode(model.getName()) + "\");\n");
		s.append("	setField(\"pipeline_description\", \"" + javascriptEncode(model.getDescription()) + "\");\n");
		s.append("	setField(\"pipeline_author\", \"" + javascriptEncode(model.getAuthor()) + "\");\n");
		s.append("	setField(\"" + GenePatternAnalysisTask.USERID + "\", \"" + javascriptEncode(model.getUserID()) + "\");\n");
		s.append("	setField(\"" + GenePatternAnalysisTask.VERSION + "\", \"" + javascriptEncode(model.getVersion()) + "\");\n");
		s.append("	setSelector(\"" + GenePatternAnalysisTask.PRIVACY + "\", \"" + (model.isPrivate() ? GenePatternAnalysisTask.PRIVATE : GenePatternAnalysisTask.PUBLIC) + "\");\n");
//		s.append("	setOption(\"" + GenePatternAnalysisTask.LANGUAGE + "\", \"R\");\n");
		s.append("	setField(\"" + GPConstants.LSID + "\", \"" + javascriptEncode(model.getLsid()) + "\");\n");

		int taskNum = 0;
		for (Enumeration eTasks = model.getTasks().elements(); eTasks.hasMoreElements(); taskNum++) {
			s.append("</script><script language=\"Javascript\">\n");

			JobSubmission jobSubmission = (JobSubmission)eTasks.nextElement();
		        ParameterInfo[] parameterInfoArray = jobSubmission.giveParameterInfoArray();
			TaskInfo taskInfo = null;
			String lsid = jobSubmission.getLSID();
			if (lsid != null) {
				taskInfo = (TaskInfo)tmTasksByLSID.get(lsid);
			}
			if (taskInfo == null) {
//				System.err.println("HTMLPipelineView.getJavascript: legacy pipeline " + model.getName() + " uses task " + jobSubmission.getName() + " but lacks its LSID");
				for (Iterator itTasks = tmCatalog.iterator(); itTasks.hasNext(); ) {
					taskInfo = (TaskInfo)itTasks.next();
					if (taskInfo.getName().equals(jobSubmission.getName())) {
						break;
					} else {
						taskInfo = null;
					}
				}
			}
			if (taskInfo == null) {
				System.err.println("HTMLPipelineView.getJavascript: Unable to load " + jobSubmission.getName() + " (" + jobSubmission.getLSID() + ")");
				continue;
			}
			ParameterInfo[] formals = new ParameterFormatConverter().getParameterInfoArray(taskInfo.getParameterInfo());
			String taskName = jobSubmission.getName().replace('-', '.').replace('_','.').replace(' ','.');
			s.append("	setTaskName(" + taskNum + ", \"" + javascriptEncode(taskName) + "\", \"" + javascriptEncode(jobSubmission.getLSID()) + "\");\n");

			// set value, set checkboxes, set selectedIndex for each parameter.
			// Note that input files cannot be set and must be re-prompted instead
			for (int i = 0; parameterInfoArray != null && i < parameterInfoArray.length; i++) {
			        ParameterInfo pi = parameterInfoArray[i];
				HashMap pia = pi.getAttributes();
				if (pia == null) pia = new HashMap();
				ParameterInfo piFormal = null;
				int formalP = 0;
				for (formalP = 0; formalP < formals.length; formalP++) {
					if (pi.getName().equals(formals[formalP].getName())) {
						piFormal = formals[formalP];
						break;
					}
				}
				if (piFormal == null) {
					s.append("	window.alert(\"Unable to find parameter " + pi.getName() + " in task " + taskInfo.getName() + " (step " + (taskNum+1) + ").");
					if (pi.getValue().length() > 0) {
						s.append("  It was previously stored as " + pi.getValue() + ".");
					}
					s.append("\");\n");
					continue;
				}
				
				//System.out.println("getPersistentDesign: " + pi.getName() + ", " + pi.getDescription() + ", " + pi.getValue() + ", formal: " + piFormal.getValue());

				if (pi.isOutputFile()) {
					// do nothing
				} else if (piFormal.isInputFile()) {
					// set shadow
					if (pi.getValue() != null && pi.getValue().length() > 0) {
						s.append("	setParameter(" + taskNum + ", \"shadow" + formalP + "\", \"" + javascriptEncode(pi.getValue()) + "\");\n");
					}
					
					if (pia.get(AbstractPipelineCodeGenerator.INHERIT_TASKNAME) != null) {

						// set inherited properties
						s.append("	setFileInheritance(" + taskNum + ", " + formalP + ", " +
								pia.get(AbstractPipelineCodeGenerator.INHERIT_TASKNAME) + ", \"" +
								pia.get(AbstractPipelineCodeGenerator.INHERIT_FILENAME) + "\");\n");
					}
				} else if (pi.getValue() != null && pi.getValue().length() > 0) {
					// this is either a dropdown list or a text box.  Decide by checking getValue in the original task's parameterinfo
					if (piFormal.getValue().length() > 0) {
						// set selectedIndex
						s.append("	setSelector(\"t" + taskNum + "_" + pi.getName() + "\", \"" + pi.getValue() + "\");\n");
					} else {
						s.append("	setParameter(" + taskNum + ", \"" + pi.getName() + "\", \"" + pi.getValue() + "\");\n");
					}
				}
				if (jobSubmission.getRuntimePrompt()[i] && !pi.isOutputFile()) {
					// set checkbox
					s.append("	setCheckbox(\"t" + taskNum + "_prompt_" + formalP + "\", true);\n");
				}
			}
		}
		return s.toString();

	    } catch (Exception e) {
	    	System.err.println(e + " while recreating script");
		e.printStackTrace();
		return "";
	    }
	}

	protected static String javascriptEncode(String s) {
		s = s.replaceAll("\\\\", "\\\\\\\\");
		s = s.replaceAll("\"", "\\\\\"");
		s = s.replaceAll("'", "\\\\'");
		return s;
	}

	/** 
	 * return a TreeMap of task types, each of whose values is a TreeMap of TaskInfo of that type of task, each of whose values is a TaskInfo
	 * 
	 * @return TreeMap of task types
	 * @param tmCatalog TreeMap of TaskInfo of all tasks
	 * @author Jim Lerner
	 * 
	 */
	protected TreeMap preprocessTaskInfo(Collection tmCatalog) {
		TreeMap tmTaskTypes = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		TaskInfo ti = null;
		String name = null;
		String taskType = null;
		String lsid = null;
		Collection tmTasks = null;
		for (Iterator itTasks = tmCatalog.iterator(); itTasks.hasNext(); ) {
			ti = (TaskInfo)itTasks.next();
			name = ti.getName();
			taskType = ti.giveTaskInfoAttributes().get(GenePatternAnalysisTask.TASK_TYPE);
			lsid = ti.giveTaskInfoAttributes().get(GenePatternAnalysisTask.LSID);
			if (taskType.length() == 0) {
				taskType = "[unclassified]";
			}
			tmTasks = (Collection)tmTaskTypes.get(taskType);
			if (tmTasks == null) {
				//System.out.println("adding " + taskType);
				tmTasks = new Vector();
				tmTaskTypes.put(taskType, tmTasks);
			}
			tmTasks.add(ti);
		}
		return tmTaskTypes;
	}

	/**
	 * debug routine
	 *
	 * @author Jim Lerner
	 *
	 */
	protected void dumpCatalog() {
		TreeMap tmTasks = null;
		String taskType = null;
		String taskName = null;
		TaskInfo taskInfo = null;
		ParameterInfo[] parameterInfoArray = null;
		
		for (Iterator itTaskTypes = tmTaskTypes.keySet().iterator(); itTaskTypes.hasNext(); ) {
			taskType = (String)itTaskTypes.next();
			tmTasks = (TreeMap)tmTaskTypes.get(taskType);
			System.out.println("taskType: " + taskType);
			for (Iterator itTasks = tmTasks.keySet().iterator(); itTasks.hasNext(); ) {
				taskName = (String)itTasks.next();
				taskInfo = (TaskInfo)tmTasks.get(taskName);
				System.out.println("taskName: " + taskName + ", lsid: " + taskInfo.giveTaskInfoAttributes().get(GPConstants.LSID));
				try {
			        	parameterInfoArray = new ParameterFormatConverter().getParameterInfoArray(taskInfo.getParameterInfo());
				} catch (OmnigeneException oe) {
				}
				for (int i = 0; parameterInfoArray != null && i < parameterInfoArray.length; i++) {
					System.out.println(parameterInfoArray[i].getName() + ": " + 
							   (parameterInfoArray[i].getDescription() != null ? parameterInfoArray[i].getDescription() : "") +
							   (parameterInfoArray[i].getValue() != null ? (" (" + parameterInfoArray[i].getValue() + ")") : ""));
				}
				System.out.println("");
			}
		}
	}
}
