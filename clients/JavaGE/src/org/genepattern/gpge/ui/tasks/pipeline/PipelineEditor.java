package org.genepattern.gpge.ui.tasks.pipeline;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

import org.genepattern.data.pipeline.PipelineModel;
import org.genepattern.gpge.GenePattern;
import org.genepattern.gpge.message.ChangeViewMessageRequest;
import org.genepattern.gpge.message.MessageManager;
import org.genepattern.gpge.message.TaskInstallMessage;
import org.genepattern.gpge.ui.graphics.draggable.ObjectTextField;
import org.genepattern.gpge.ui.maindisplay.GroupPanel;
import org.genepattern.gpge.ui.maindisplay.TogglePanel;
import org.genepattern.gpge.ui.tasks.AnalysisServiceDisplay;
import org.genepattern.gpge.ui.tasks.AnalysisServiceManager;
import org.genepattern.gpge.ui.tasks.ParameterChoice;
import org.genepattern.gpge.ui.tasks.Sendable;
import org.genepattern.gpge.ui.tasks.TaskDisplay;
import org.genepattern.gpge.ui.tasks.TaskHelpActionListener;
import org.genepattern.gpge.ui.tasks.VersionComboBox;
import org.genepattern.gpge.ui.util.GUIUtil;
import org.genepattern.util.GPConstants;
import org.genepattern.util.LSID;
import org.genepattern.webservice.AnalysisService;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskIntegratorProxy;
import org.genepattern.webservice.WebServiceException;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

// inherited task numbers start at 0, output files start at one, names for params start at one
public class PipelineEditor extends JPanel implements TaskDisplay,
		PipelineListener {
	private static final String CHOOSE_TASK = "Choose Task";

	private static final boolean DEBUG = false;

	private static final int INPUT_FIELD_COLUMN = 5;

	private static final int INPUT_LABEL_COLUMN = 3;

	private static final int PROMPT_WHEN_RUN_COLUMN = 1;

	private PipelineEditorModel model;

	private JPanel tasksPanel;

	private FormLayout tasksLayout;

	/** list of TaskPanel objects */
	private ArrayList taskDisplayList = new ArrayList();

	private JPanel buttonPanel;

	private HeaderPanel headerPanel;

	private JComboBox tasksInPipelineComboBox;

	private JScrollPane scrollPane;

	private TaskHelpActionListener taskHelpActionListener;

	private JButton addAfterButton;

	private JButton addBeforeButton;

	private JButton deleteButton;

	private JButton moveUpButton;

	private JButton moveDownButton;

	private AnalysisService analysisService;

	private JButton runButton;

	private JButton viewButton;

	private JButton helpButton;

	private boolean pipelineChanged = false;

	private void enableButtons() {
		deleteButton.setEnabled(model.getTaskCount() > 0);
		if (model.getTaskCount() == 0) {
			addAfterButton.setText("Add Task");
		} else {
			addAfterButton.setText("Add Task After");
		}
		int index = tasksInPipelineComboBox.getSelectedIndex();
		addBeforeButton.setEnabled(model.getTaskCount() != 0);
		moveDownButton.setEnabled((index + 1) != model.getTaskCount());
		moveUpButton.setEnabled(index > 0);

		runButton.setEnabled(analysisService != null);
		viewButton.setEnabled(analysisService != null);
		helpButton.setEnabled(analysisService != null);
	}

	/**
	 * Only one instance should be created by the ViewManager
	 * 
	 */
	public PipelineEditor() {
		setBackground(Color.white);
		setMinimumSize(new java.awt.Dimension(100, 100));
		setLayout(new BorderLayout());

		scrollPane = new JScrollPane();
		Border b = scrollPane.getBorder();
		scrollPane.setBorder(GUIUtil.createBorder(b, 0, -1, -1, -1));
		add(scrollPane, BorderLayout.CENTER);

		tasksInPipelineComboBox = new JComboBox();

		addAfterButton = new JButton("Add Task After");

		addBeforeButton = new JButton("Add Task Before");

		deleteButton = new JButton("Delete");

		moveUpButton = new JButton("Move Up");

		moveDownButton = new JButton("Move Down");

		tasksInPipelineComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				enableButtons();
			}
		});

		ActionListener taskBtnListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int index = tasksInPipelineComboBox.getSelectedIndex();
				updateInputFileValues();
				if (e.getSource() == addAfterButton) {
					showAddTask(index, true);
				} else if (e.getSource() == addBeforeButton) {
					showAddTask(index, false);
				} else if (e.getSource() == deleteButton) {
					model.remove(index);
				} else if (e.getSource() == moveUpButton) {
					model.move(index, index - 1);
				} else if (e.getSource() == moveDownButton) {
					model.move(index, index + 1);
				}
				enableButtons();
			}
		};

		addAfterButton.addActionListener(taskBtnListener);
		addBeforeButton.addActionListener(taskBtnListener);
		deleteButton.addActionListener(taskBtnListener);
		moveUpButton.addActionListener(taskBtnListener);
		moveDownButton.addActionListener(taskBtnListener);

		buttonPanel = new JPanel(new BorderLayout());
		JPanel topPanel = new JPanel();
		topPanel.add(tasksInPipelineComboBox);
		topPanel.add(addAfterButton);
		topPanel.add(addBeforeButton);
		topPanel.add(deleteButton);
		topPanel.add(moveUpButton);
		topPanel.add(moveDownButton);

		JPanel bottomPanel = new JPanel();
		final JButton expandAllButton = new JButton("Expand All");
		final JButton collapseAllButton = new JButton("Collapse All");
		bottomPanel.add(collapseAllButton);
		bottomPanel.add(expandAllButton);
		buttonPanel.add(topPanel, BorderLayout.CENTER);
		buttonPanel.add(bottomPanel, BorderLayout.SOUTH);
		buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
				Color.DARK_GRAY));

		ActionListener expandListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == expandAllButton) {
					for (int i = 0; i < taskDisplayList.size(); i++) {
						TaskPanel p = (TaskPanel) taskDisplayList.get(i);
						p.setExpanded(true);
					}
				} else if (e.getSource() == collapseAllButton) {
					for (int i = 0; i < taskDisplayList.size(); i++) {
						TaskPanel p = (TaskPanel) taskDisplayList.get(i);
						p.setExpanded(false);
					}
				}
			}

		};

		expandAllButton.addActionListener(expandListener);
		collapseAllButton.addActionListener(expandListener);

		JPanel bottomBtnPanel = new JPanel();
		final JButton saveButton = new JButton("Save");
		runButton = new JButton("Run");
		viewButton = new JButton("View");
		helpButton = new JButton("Help");
		taskHelpActionListener = new TaskHelpActionListener();
		helpButton.addActionListener(taskHelpActionListener);

		ActionListener btnListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == saveButton) {
					save();
				} else if (e.getSource() == runButton) {
					if (pipelineChanged) {
						int result = GUIUtil
								.showYesNoCancelDialog(
										GenePattern.getDialogParent(),
										"GenePattern",
										"Do you want to save the changes to the pipeline?",
										new String[] { "Save", "Don't Save",
												"Cancel" });

						if (result == JOptionPane.YES_OPTION) {
							save();
						} else if (result == JOptionPane.NO_OPTION) {
							MessageManager
									.notifyListeners(new ChangeViewMessageRequest(
											this,
											ChangeViewMessageRequest.SHOW_RUN_TASK_REQUEST,
											analysisService));
						}
					} else {
						MessageManager
						.notifyListeners(new ChangeViewMessageRequest(
								this,
								ChangeViewMessageRequest.SHOW_RUN_TASK_REQUEST,
								analysisService));
					}

				} else if (e.getSource() == viewButton) {
					if (pipelineChanged) {
						int result = GUIUtil
								.showYesNoCancelDialog(
										GenePattern.getDialogParent(),
										"GenePattern",
										"Do you want to save the changes to the pipeline?",
										new String[] { "Save", "Don't Save",
												"Cancel" });

						if (result == JOptionPane.YES_OPTION) {
							save();
						} else if (result == JOptionPane.NO_OPTION) {
							MessageManager
									.notifyListeners(new ChangeViewMessageRequest(
											this,
											ChangeViewMessageRequest.SHOW_VIEW_PIPELINE_REQUEST,
											analysisService));
						}
					} else {
						MessageManager
						.notifyListeners(new ChangeViewMessageRequest(
								this,
								ChangeViewMessageRequest.SHOW_VIEW_PIPELINE_REQUEST,
								analysisService));
					}
				}
			}

		};
		saveButton.addActionListener(btnListener);
		runButton.addActionListener(btnListener);
		viewButton.addActionListener(btnListener);
		bottomBtnPanel.add(saveButton);
		bottomBtnPanel.add(runButton);
		bottomBtnPanel.add(viewButton);
		bottomBtnPanel.add(helpButton);
		add(bottomBtnPanel, BorderLayout.SOUTH);
	}

	protected void updateInputFileValues() {
		for (int i = 0; i < taskDisplayList.size(); i++) {
			TaskPanel td = (TaskPanel) taskDisplayList.get(i);
			model.setTaskDescription(i, td.getTaskDescription());
			for (int j = 0; j < model.getParameterCount(i); j++) {
				if (model.isInputFile(i, j)) {
					ParameterDisplay pd = td.parameters[j];
					int inheritedTaskIndex = pd.getInheritedTaskIndex();
					if (inheritedTaskIndex != -1) {
						String inheritedFileName = pd.getInheritedFileName(); // can
						// be
						// null
						model.setInheritedFile(i, j, inheritedTaskIndex,
								inheritedFileName);
					} else if (pd.isPromptWhenRun()) {
						model.setPromptWhenRun(i, j);
					} else {
						model.setValue(i, j, pd.getValue());
					}
				}
			}
		}
	}

	protected void updateValues(int taskIndex) {
		TaskPanel td = (TaskPanel) taskDisplayList.get(taskIndex);
		model.setTaskDescription(taskIndex, td.getTaskDescription());
		for (int j = 0; j < model.getParameterCount(taskIndex); j++) {
			ParameterDisplay pd = td.parameters[j];
			int inheritedTaskIndex = pd.getInheritedTaskIndex();
			if (inheritedTaskIndex != -1) {
				String inheritedFileName = pd.getInheritedFileName();
				model.setInheritedFile(taskIndex, j, inheritedTaskIndex,
						inheritedFileName);

			} else if (pd.isPromptWhenRun()) {
				model.setPromptWhenRun(taskIndex, j);
			} else {
				model.setValue(taskIndex, j, pd.getValue());
			}
		}
	}

	protected void save() {
		pipelineChanged = false;
		save(true);
		try {
			MessageManager.notifyListeners(new TaskInstallMessage(this, model
					.getLSID()));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		remove(headerPanel);
		headerPanel = new HeaderPanel(model, buttonPanel);
		add(headerPanel, BorderLayout.NORTH); // update lsid in drop down
		invalidate();
		validate();
		this.analysisService = AnalysisServiceManager.getInstance()
				.getAnalysisService(model.getLSID());
		enableButtons();
	}

	protected void save(boolean saveAll) {
		StringBuffer errors = null;
		if (saveAll) {
			errors = headerPanel.save();
		} else {
			errors = new StringBuffer();
		}
		List localInputFiles = new ArrayList();
		List existingFileNames = new ArrayList();
		for (int i = 0; i < taskDisplayList.size(); i++) {
			TaskPanel td = (TaskPanel) taskDisplayList.get(i);
			model.setTaskDescription(i, td.getTaskDescription());
			for (int j = 0; j < model.getParameterCount(i); j++) {
				ParameterDisplay pd = td.parameters[j];
				int inheritedTaskIndex = pd.getInheritedTaskIndex();
				if (inheritedTaskIndex != -1) {
					String inheritedFileName = pd.getInheritedFileName();
					if (saveAll && inheritedFileName == null) {
						errors.append("Missing value for "
								+ (i + 1)
								+ ". "
								+ model.getTaskName(i)
								+ " "
								+ AnalysisServiceDisplay.getDisplayString(model
										.getParameterName(i, j)) + "\n");
					}
					model.setInheritedFile(i, j, inheritedTaskIndex,
							inheritedFileName);
				} else if (pd.isPromptWhenRun()) {
					model.setPromptWhenRun(i, j);
				} else {
					String value = pd.getValue();
					if (model.isRequired(i, j)) {
						if (saveAll && value.trim().equals("")) {
							errors.append("Missing value for "
									+ (i + 1)
									+ ". "
									+ model.getTaskName(i)
									+ " "
									+ AnalysisServiceDisplay
											.getDisplayString(model
													.getParameterName(i, j))
									+ "\n");
						}
					}

					if (model.isInputFile(i, j)) {
						File file = new File(value);
						if (file.exists()) {
							localInputFiles.add(file);
						} else if (value
								.startsWith("<GenePatternURL>getFile.jsp?task=<LSID>&file=")) {
							String fileName = value.substring(
									"<GenePatternURL>getFile.jsp?task=<LSID>&file="
											.length(), value.length());
							existingFileNames.add(fileName);
						} else if(value.startsWith("job #")) {
							existingFileNames.add(value);
						}
					}

					model.setValue(i, j, value);
				}
			}
		}

		if (!saveAll) {
			return;
		}

		if (errors.length() > 0) {
			GenePattern.showErrorDialog(errors.toString());
			return;
		}
		TaskInfo ti = model.toTaskInfo();
		try {
			List taskFiles = new ArrayList();
			taskFiles.addAll(localInputFiles);
			taskFiles.addAll(model.getLocalDocFiles());

			existingFileNames.addAll(model.getServerDocFiles());

			String lsid = new TaskIntegratorProxy(AnalysisServiceManager
					.getInstance().getServer(), AnalysisServiceManager
					.getInstance().getUsername(), false).modifyTask(
					GPConstants.ACCESS_PUBLIC, ti.getName(), ti
							.getDescription(), ti.getParameterInfoArray(),
					(HashMap) ti.getTaskInfoAttributes(), (File[]) taskFiles
							.toArray(new File[0]), (String[]) existingFileNames
							.toArray(new String[0]));

			model.setLSID(lsid);
		} catch (WebServiceException e1) {
			e1.printStackTrace();
			if (!GenePattern.disconnectedFromServer(e1, AnalysisServiceManager
					.getInstance().getServer())) {
				GenePattern
						.showErrorDialog("An error occurred while saving the pipeline.");
			}
		}
	}

	void reset() {
		taskDisplayList.clear();
		tasksInPipelineComboBox.removeAllItems();
		tasksLayout = new FormLayout("pref", "");
		tasksPanel = new JPanel(tasksLayout);
		tasksPanel.setBackground(getBackground());
		scrollPane.setViewportView(tasksPanel);
	}

	public Iterator getInputFileParameters() {
		List list = new ArrayList();
		for (int i = 0; i < model.getTaskCount(); i++) {
			for (int j = 0, numParams = model.getParameterCount(i); j < numParams; j++) {
				if (model.isInputFile(i, j)) {
					list.add((i + 1)
							+ ". "
							+ model.getTaskName(i)
							+ " "
							+ AnalysisServiceDisplay.getDisplayString(model
									.getParameterName(i, j)));
				}
			}
		}
		return list.iterator();
	}

	public Iterator getInputFileTypes() {
		List list = new ArrayList();
		for (int i = 0; i < model.getTaskCount(); i++) {
			for (int j = 0, numParams = model.getParameterCount(i); j < numParams; j++) {
				if (model.isInputFile(i, j)) {
					list.add(model.getParameterInputTypes(i, j));
				}
			}
		}
		return list.iterator();
	}

	public void sendTo(String sendToString, Sendable sendable) {
		int taskIndex = Integer.parseInt(sendToString.substring(0, sendToString
				.indexOf("."))) - 1;
		String taskNameAndParamName = sendToString.substring(sendToString
				.indexOf(" ") + 1, sendToString.length());
		String parameterName = taskNameAndParamName.substring(
				taskNameAndParamName.indexOf(" ") + 1, taskNameAndParamName
						.length());
		for (int i = 0, numParams = model.getParameterCount(taskIndex); i < numParams; i++) {
			if (AnalysisServiceDisplay.getDisplayString(
					model.getParameterName(taskIndex, i)).equals(parameterName)) {
				TaskPanel tp = (TaskPanel) this.taskDisplayList.get(taskIndex);
				tp.parameters[i].setValue(sendable);
				break;
			}
		}
	}

	public boolean display(AnalysisService svc, PipelineModel pipelineModel) {
		this.analysisService = svc;
		pipelineChanged = false;
		if (svc == null) {
			model = new PipelineEditorModel();
			String username = AnalysisServiceManager.getInstance()
					.getUsername();
			model.setAuthor(username);
			model.setOwner(username);
		
		} else {
			model = new PipelineEditorModel(svc, pipelineModel);
			if(model.getMissingJobSubmissions().size() > 0) {
				return false;
			}
		}
		if (headerPanel != null) {
			remove(headerPanel);
		}
		headerPanel = new HeaderPanel(model, buttonPanel);
		add(headerPanel, BorderLayout.NORTH);

		enableButtons();
		model.addPipelineListener(this);
		

		// show edit link when task has local authority and either belongs
		// to
		// current user or is public
		layoutTasks();
		return true;

	}

	static class HeaderPanel extends JPanel {

		private JTextField nameField;

		private JTextField descriptionField;

		private JTextField authorField;

		private JTextField ownerField;

		private JComboBox privacyComboBox;

		private JTextField versionField;

		private PipelineEditorModel model;

		public StringBuffer save() {
			StringBuffer errors = new StringBuffer();
			String name = nameField.getText().trim();
			if (name.equals("")) {
				errors.append("Missing value for name\n");
			}
			model.setPipelineName(name);

			String author = authorField.getText().trim();
			if (author.equals("")) {
				errors.append("Missing value for author\n");
			}
			model.setAuthor(author);
			String owner = ownerField.getText().trim();
			if (owner.equals("")) {
				errors.append("Missing value for owner\n");
			}
			model.setOwner(owner);
			String privacy = (String) privacyComboBox.getSelectedItem();
			if (privacy.equals("Public")) {
				model.setPrivacy(GPConstants.ACCESS_PUBLIC);
			} else {
				model.setPrivacy(GPConstants.ACCESS_PRIVATE);
			}
			model.setVersionComment(versionField.getText());
			model.setPipelineDescription(descriptionField.getText());

			return errors;
		}

		public HeaderPanel(final PipelineEditorModel model, JPanel buttonPanel) {
			this.model = model;
			// setBorder(BorderFactory.createLineBorder());
			setLayout(new BorderLayout());

			String name = model.getPipelineName();
			if (name != null && name.endsWith(".pipeline")) {
				name = name.substring(0, name.length() - ".pipeline".length());
			}
			JLabel nameLabel = new JLabel("Name:");
			nameField = new JTextField(name, 40);

			JLabel descriptionLabel = new JLabel("Description:");
			descriptionField = new JTextField(model.getPipelineDescription(),
					40);
			JComboBox versionComboBox = null;
			if (name != null) {
				versionComboBox = new VersionComboBox(model.getLSID(),
						ChangeViewMessageRequest.SHOW_EDIT_PIPELINE_REQUEST);
			}
			CellConstraints cc = new CellConstraints();
			JPanel temp = new JPanel(new FormLayout(
					"right:pref:none, 3dlu, pref, pref",
					"pref, 3dlu, pref, pref"));

			temp.add(nameLabel, cc.xy(1, 1));
			temp.add(nameField, cc.xy(3, 1));

			if (versionComboBox != null) {
				temp.add(versionComboBox, cc.xy(4, 1));
			}

			temp.add(descriptionLabel, cc.xy(1, 3));
			temp.add(descriptionField, cc.xy(3, 3));

			StringBuffer rowSpec = new StringBuffer();
			for (int i = 0; i < 6; i++) {
				if (i > 0) {
					rowSpec.append(", ");
				}
				rowSpec.append("pref, ");
				rowSpec.append("3dlu");
			}
			JPanel detailsPanel = new JPanel(new FormLayout(
					"right:pref:none, 3dlu, left:pref", rowSpec.toString()));
			detailsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
			JLabel authorLabel = new JLabel("Author:");
			authorField = new JTextField(model.getAuthor(), 40);
			detailsPanel.add(authorLabel, cc.xy(1, 1));
			detailsPanel.add(authorField, cc.xy(3, 1));

			JLabel ownerLabel = new JLabel("Owner:");
			ownerField = new JTextField(model.getOwner(), 40);
			detailsPanel.add(ownerLabel, cc.xy(1, 3));
			detailsPanel.add(ownerField, cc.xy(3, 3));

			JLabel privacyLabel = new JLabel("Privacy:");
			privacyComboBox = new JComboBox(
					new String[] { "Public", "Private" });
			if (model.getPrivacy() == GPConstants.ACCESS_PRIVATE) {
				privacyComboBox.setSelectedIndex(1);
			}
			detailsPanel.add(privacyLabel, cc.xy(1, 5));
			detailsPanel.add(privacyComboBox, cc.xy(3, 5));

			JLabel versionLabel = new JLabel("Version comment:");
			versionField = new JTextField(model.getVersionComment(), 40);
			detailsPanel.add(versionLabel, cc.xy(1, 7));
			detailsPanel.add(versionField, cc.xy(3, 7));

			JLabel documentationLabel = new JLabel("Documentation:");
			final JComboBox existingDocComboBox = new JComboBox();
			if (name != null) {
				List docFiles = model.getServerDocFiles();
				for (int i = 0; i < docFiles.size(); i++) {
					existingDocComboBox.addItem(docFiles.get(i));
				}
			}

			JButton deleteDocBtn = new JButton("Delete");
			deleteDocBtn.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					if (existingDocComboBox.getSelectedItem() == null) {
						return;
					}
					if (GUIUtil
							.showConfirmDialog("Are you sure you want to delete "
									+ existingDocComboBox.getSelectedItem()
									+ "?")) {
						Object obj = existingDocComboBox.getSelectedItem();
						if (obj instanceof LocalFileWrapper) {
							model
									.removeLocalDocFile(((LocalFileWrapper) obj).file);
						} else {
							model.removeServerDocFile((String) obj);
						}
						existingDocComboBox.removeItemAt(existingDocComboBox
								.getSelectedIndex());
					}
				}
			});

			JButton addDocBtn = new JButton("Add...");
			addDocBtn.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					File f = GUIUtil.showOpenDialog();
					if (f != null) {
						model.addLocalDocFile(f);
						existingDocComboBox.addItem(new LocalFileWrapper(f));
					}
				}
			});

			detailsPanel.add(documentationLabel, cc.xy(1, 9));
			JPanel docPanel = new JPanel(new FormLayout(
					"pref, 3dlu, pref, 3dlu, pref", "pref"));
			docPanel.add(existingDocComboBox, cc.xy(1, 1));
			docPanel.add(deleteDocBtn, cc.xy(3, 1));
			docPanel.add(addDocBtn, cc.xy(5, 1));
			detailsPanel.add(docPanel, cc.xy(3, 9));

			if (name != null) {
				JLabel lsidLabel = new JLabel("LSID:");
				JLabel lsidField = new JLabel(model.getLSID());
				detailsPanel.add(lsidLabel, cc.xy(1, 11));
				detailsPanel.add(lsidField, cc.xy(3, 11));
			}
			TogglePanel detailsToggle = new TogglePanel("Details", detailsPanel);

			JPanel bottom = new JPanel(new BorderLayout());
			bottom.add(detailsToggle, BorderLayout.NORTH);
			bottom.add(buttonPanel, BorderLayout.SOUTH);
			add(temp, BorderLayout.CENTER);
			add(bottom, BorderLayout.SOUTH);
		}
	}

	private static class LocalFileWrapper {
		File file;

		LocalFileWrapper(File f) {
			this.file = f;
		}

		public String toString() {
			return file.getName();
		}
	}

	private void layoutTasks() {
		reset();
		for (int i = 0; i < model.getTaskCount(); i++) {
			layoutTask(i);
		}

		addItemsToTaskComboBox();
		invalidate();
		validate();
		tasksPanel.invalidate();
		tasksPanel.validate();

	}

	private void addItemsToTaskComboBox() {
		tasksInPipelineComboBox.removeAllItems();
		for (int i = 0; i < model.getTaskCount(); i++) {
			tasksInPipelineComboBox.addItem((i + 1) + ". "
					+ model.getTaskName(i));
		}
	}

	private void layoutTask(int taskIndex) {
		TaskPanel taskPanel = new TaskPanel(taskIndex);
		CellConstraints cc = new CellConstraints();
		int rowIndex = taskIndex + 1;
		if (rowIndex > tasksLayout.getRowCount()) {
			tasksLayout.appendRow(new RowSpec("pref"));
		} else {
			tasksLayout.insertRow(rowIndex, new RowSpec("pref"));
		}
		tasksPanel.add(taskPanel, cc.xy(1, 1 + taskIndex));
		taskDisplayList.add(taskIndex, taskPanel);

	}

	void addTask(int index, AnalysisService svc) {
		updateInputFileValues();
		model.add(index, svc.getTaskInfo());
	}

	private void showAddTask(int jobSubmissionIndex, boolean addAfter) {

		if (addAfter)
			System.out.print("Add task after " + jobSubmissionIndex);
		else
			System.out.print("Add task before " + jobSubmissionIndex);
		String title;
		int insertionIndex;

		if (addAfter) {
			if (model.getTaskCount() == 0) {
				title = "Add Task";
			} else {
				title = "Add Task After " + (jobSubmissionIndex + 1) + ". "
						+ model.getTaskName(jobSubmissionIndex);
			}
			insertionIndex = jobSubmissionIndex + 1;
		} else {
			title = "Add Task Before " + (jobSubmissionIndex + 1) + ". "
					+ model.getTaskName(jobSubmissionIndex);
			insertionIndex = jobSubmissionIndex;
		}

		new TaskChooser(GenePattern.getDialogParent(), title, this,
				insertionIndex);
	}

	private void taskInserted(int addedRow) {
		layoutTask(addedRow);
		for (int i = addedRow + 1; i < model.getTaskCount(); i++) {
			TaskPanel task = (TaskPanel) taskDisplayList.get(i);
			task.setTaskIndex(i);
		}

		addItemsToTaskComboBox();
		TaskPanel addedTask = (TaskPanel) taskDisplayList.get(addedRow);
		scrollTo(addedTask);
	}

	private void scrollTo(TaskPanel task) {
		// Rectangle rect = task.getBounds(); //getVisibleRect();
		Point p = task.getLocation();
		System.out.println(p);
		JViewport jvp = scrollPane.getViewport();
		Rectangle rectSpn = jvp.getViewRect();
		jvp.setViewPosition(p);
		/*
		 * if (!rectSpn.contains(rect)) { System.out.println("scrolling to "+
		 * rect.y); jvp.scrollRectToVisible(rect); //Point p = new Point(0,
		 * rect.y); // }
		 */
	}

	private void taskDeleted(int deletedRow) {
		TaskPanel deletedTask = (TaskPanel) taskDisplayList.remove(deletedRow);
		deletedTask.setVisible(false);
		tasksPanel.remove(deletedTask);
		tasksLayout.removeRow(deletedRow + 1);
		for (int i = deletedRow; i < model.getTaskCount(); i++) {
			TaskPanel task = (TaskPanel) taskDisplayList.get(i);
			task.setTaskIndex(i);
		}
		addItemsToTaskComboBox();

	}

	public void pipelineChanged(PipelineEvent e) {
		pipelineChanged = true;
		if (e.getType() == PipelineEvent.REPLACE) {
			TaskPanel deletedTask = (TaskPanel) taskDisplayList.remove(e
					.getFirstRow());
			deletedTask.setVisible(false);
			tasksPanel.remove(deletedTask);
			tasksLayout.removeRow(e.getFirstRow() + 1);
			taskInserted(e.getFirstRow());
		} else if (e.getType() == PipelineEvent.DELETE) {
			taskDeleted(e.getFirstRow());
		} else if (e.getType() == PipelineEvent.INSERT) {
			taskInserted(e.getFirstRow());
		} else if (e.getType() == PipelineEvent.MOVE) {
			int from = e.getFirstRow();
			int to = e.getLastRow();
			TaskPanel movedTask = (TaskPanel) taskDisplayList.remove(from);
			tasksPanel.remove(movedTask);
			tasksLayout.removeRow(from + 1);

			layoutTask(to);

			for (int i = 0; i < model.getTaskCount(); i++) {
				TaskPanel task = (TaskPanel) taskDisplayList.get(i);
				task.setTaskIndex(i);
			}
			addItemsToTaskComboBox();
			scrollTo(movedTask);
		} else {
			System.err.println("Unknown pipeline event");
		}
		enableButtons();
		tasksPanel.invalidate();
		tasksPanel.validate();

	}

	static class ParameterDisplay {
		/** a text field or combo box */
		private JComponent inputField;

		private JCheckBox promptWhenRun;

		private JCheckBox useOutputFromPreviousTask;

		private JComboBox inheritedTaskIndexComboBox;

		private JComboBox inheritedFileNameComboBox;

		private JButton browseBtn;

		private int taskIndex;

		private final int parameterIndex;

		PipelineEditorModel model;

		public ParameterDisplay(PipelineEditorModel model, int taskIndex,
				int parameterIndex) {
			this.model = model;
			this.taskIndex = taskIndex;
			this.parameterIndex = parameterIndex;
		}

		String getInheritedFileName() {
			return (String) inheritedFileNameComboBox.getSelectedItem();
		}

		int getInheritedTaskIndex() {
			if (useOutputFromPreviousTask == null
					|| !useOutputFromPreviousTask.isVisible()
					|| !useOutputFromPreviousTask.isSelected()) {
				return -1;
			}
			String item = (String) inheritedTaskIndexComboBox.getSelectedItem();
			if (item.equals(CHOOSE_TASK)) {
				return PipelineEditorModel.CHOOSE_TASK_INDEX;
			}
			return Integer.parseInt(item.substring(0, item.indexOf("."))) - 1;
		}

		boolean isPromptWhenRun() {
			return promptWhenRun.isVisible() && promptWhenRun.isSelected();
		}

		JCheckBox createUseOutputFromPreviousTaskCheckBox() {
			useOutputFromPreviousTask = new JCheckBox(
					"Use output from previous task");
			useOutputFromPreviousTask.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					/*
					 * if(useOutputFromPreviousTask.isSelected()) { String item =
					 * (String) inheritedTaskIndexComboBox.getSelectedItem();
					 * if(item!=null && !item.equals(CHOOSE_TASK)) { int
					 * inheritedIndex = Integer.parseInt(item.substring(0,
					 * item.indexOf("."))); model.setInheritedFile(taskIndex,
					 * parameterIndex, inheritedIndex, (String)
					 * inheritedFileNameComboBox.getSelectedItem()); } }
					 */
					select();
				}
			});
			return useOutputFromPreviousTask;
		}

		JCheckBox createPromptWhenRunCheckBox() {
			promptWhenRun = new JCheckBox();
			promptWhenRun.setBackground(Color.white);

			promptWhenRun.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (promptWhenRun.isSelected()) {
						model.setPromptWhenRun(taskIndex, parameterIndex);
					}
					if (!promptWhenRun.isSelected()) {
						inputField.setVisible(true);
						if (useOutputFromPreviousTask != null) {
							browseBtn.setVisible(true);
							useOutputFromPreviousTask.setVisible(true);
						}
					} else {
						inputField.setVisible(false);
						if (useOutputFromPreviousTask != null) {
							useOutputFromPreviousTask.setSelected(false);
							useOutputFromPreviousTask.setVisible(false);
							browseBtn.setVisible(false);
							inheritedFileNameComboBox.setVisible(false);
							inheritedTaskIndexComboBox.setVisible(false);
						}
					}

				}

			});
			return promptWhenRun;
		}

		private void select() {
			browseBtn.setVisible(!useOutputFromPreviousTask.isSelected());
			inputField.setVisible(!useOutputFromPreviousTask.isSelected());
			inheritedTaskIndexComboBox.setVisible(useOutputFromPreviousTask
					.isSelected());
			inheritedFileNameComboBox.setVisible(useOutputFromPreviousTask
					.isSelected()
					&& inheritedFileNameComboBox.getItemCount() > 0);
			promptWhenRun.setSelected(false);
		}

		void setValue(Sendable s) {
			if (inputField instanceof JTextField) {
				((ObjectTextField) inputField).setObject(s);
				useOutputFromPreviousTask.setVisible(true);
				useOutputFromPreviousTask.setSelected(false);
				select();
			}
		}

		String getValue() {
			if (inputField instanceof JTextField) {
				return ((JTextComponent) inputField).getText().trim();
			}
			ParameterChoice pc = (ParameterChoice) ((JComboBox) inputField)
					.getSelectedItem();
			return pc.getValue();
		}

		/**
		 * 
		 * @param taskIndex
		 *            the task index for this parameter
		 * @param model
		 */

		private void setTaskIndex(int taskIndex) {
			this.taskIndex = taskIndex;
			if (inheritedTaskIndexComboBox != null) {
				_setTaskIndex(taskIndex);
			}
		}

		private void _setTaskIndex(int taskIndex) {
			inheritedTaskIndexComboBox.removeAllItems();

			inheritedTaskIndexComboBox.addItem(CHOOSE_TASK);
			for (int k = 0; k < taskIndex; k++) {
				inheritedTaskIndexComboBox.addItem((k + 1) + ". "
						+ model.getTaskName(k));
			}

			int inheritedTaskIndex = model.getInheritedTaskIndex(taskIndex,
					parameterIndex);
			browseBtn.setVisible(inheritedTaskIndex == -1);
			inputField.setVisible(inheritedTaskIndex == -1);
			inheritedTaskIndexComboBox.setVisible(inheritedTaskIndex != -1);
			inheritedFileNameComboBox.setVisible(inheritedTaskIndex != -1);
			useOutputFromPreviousTask.setSelected(inheritedTaskIndex != -1);
			inheritedFileNameComboBox.removeAllItems();

			if (inheritedTaskIndex != -1
					&& inheritedTaskIndex != PipelineEditorModel.CHOOSE_TASK_INDEX) {
				inheritedTaskIndexComboBox
						.setSelectedItem((inheritedTaskIndex + 1) + ". "
								+ model.getTaskName(inheritedTaskIndex));
				updateOutputFileChoices();
				inheritedFileNameComboBox.setSelectedItem(model
						.getInheritedFile(taskIndex, parameterIndex));

			} else {
				((JTextComponent) inputField).setText(model.getValue(taskIndex,
						parameterIndex));
			}

		}

		void updateOutputFileChoices() {
			String selectedTaskIndex = (String) inheritedTaskIndexComboBox
					.getSelectedItem();
			int inheritedTaskIndex = Integer.parseInt(selectedTaskIndex
					.substring(0, selectedTaskIndex.indexOf("."))) - 1;

			inheritedFileNameComboBox.setVisible(true);
			inheritedFileNameComboBox.removeAllItems();

			List outputFileTypes = model.getOutputFileTypes(inheritedTaskIndex);

			for (int i = 0; i < outputFileTypes.size(); i++) {
				inheritedFileNameComboBox.addItem(outputFileTypes.get(i));
			}
			inheritedFileNameComboBox.addItem("1st output");
			inheritedFileNameComboBox.addItem("2nd output");
			inheritedFileNameComboBox.addItem("3rd output");
			inheritedFileNameComboBox.addItem("4th output");
			inheritedFileNameComboBox.addItem("stdout");
			inheritedFileNameComboBox.addItem("stderr");
		}

		public JPanel createInputField() {
			CellConstraints cc = new CellConstraints();
			String sendToString = (taskIndex + 1)
					+ ". "
					+ model.getTaskName(taskIndex)
					+ "."
					+ AnalysisServiceDisplay.getDisplayString(model
							.getParameterName(taskIndex, parameterIndex));
			// inputFileParameters.add(sendToString);
			// inputFileTypes.add(model.getParameterInputTypes(taskIndex,
			// i));

			final JTextField inputComponent = new ObjectTextField(20);
			this.inputField = inputComponent;
			JPanel inputPanel = new JPanel();
			inputPanel.setOpaque(false);
			inputPanel.setBackground(Color.white);
			FormLayout inputPanelLayout = new FormLayout(
					"left:pref:none, left:pref:none, left:pref:none, left:pref:none, left:default:none",
					"pref"); // input field, browse, task drop down,
			// output file drop down, checkbox
			inputPanel.setLayout(inputPanelLayout);

			browseBtn = new JButton("Browse...");
			browseBtn.setOpaque(false);
			browseBtn.setBackground(Color.white);
			browseBtn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					File f = GUIUtil.showOpenDialog();
					if (f != null) {
						inputComponent.setText(f.getPath());
					}
				}
			});

			createUseOutputFromPreviousTaskCheckBox();
			useOutputFromPreviousTask.setOpaque(false);
			useOutputFromPreviousTask.setBackground(Color.white);
			inputPanel.add(inputComponent, cc.xy(1, 1));
			inputPanel.add(browseBtn, cc.xy(2, 1));
			inputPanel.add(useOutputFromPreviousTask, cc.xy(5, 1));

			inheritedTaskIndexComboBox = new JComboBox();

			inheritedTaskIndexComboBox.setOpaque(false);
			inheritedFileNameComboBox = new JComboBox();

			inheritedFileNameComboBox.setOpaque(false);
			inputPanel.add(inheritedTaskIndexComboBox, cc.xy(3, 1));
			inputPanel.add(inheritedFileNameComboBox, cc.xy(4, 1));

			setTaskIndex(taskIndex);

			inheritedTaskIndexComboBox.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) { // fired when
					// setSelectedItem
					// is invoked
					// if(e.getStateChange()!=ItemEvent.SELECTED) {
					// return;
					// }
					String item = (String) inheritedTaskIndexComboBox
							.getSelectedItem();
					// System.out.println(item);
					if (item == null) {
						return;
					}
					if (item.equals(CHOOSE_TASK)) {
						if (inheritedTaskIndexComboBox.getItemCount() == 1) {
							return;
						}
						inheritedFileNameComboBox.removeAllItems();
						inheritedFileNameComboBox.setVisible(false);
					} else {
						updateOutputFileChoices();
					}
				}

			});
			inheritedTaskIndexComboBox.setBackground(Color.white);
			inheritedFileNameComboBox.setBackground(Color.white);
			return inputPanel;
		}

		public JComponent createChoiceInput(ParameterChoice[] choices,
				String value) {
			JComboBox comboBox = new JComboBox(choices);
			comboBox.setBackground(Color.white);
			for (int j = 0; j < comboBox.getItemCount(); j++) {
				if (((ParameterChoice) comboBox.getItemAt(j))
						.equalsCmdLineOrUIValue(value)) {
					comboBox.setSelectedIndex(j);
					break;
				}
			}
			this.inputField = comboBox;
			return comboBox;
		}
	}

	class TaskPanel extends JPanel {
		private ParameterDisplay[] parameters;

		private FormLayout layout;

		GroupPanel togglePanel;

		int taskIndex;

		public String toString() {
			return (1 + taskIndex) + ". " + model.getTaskName(taskIndex);
		}

		public TaskPanel(int _taskIndex) {
			this.taskIndex = _taskIndex;
			togglePanel = new GroupPanel((taskIndex + 1) + ". "
					+ model.getTaskName(taskIndex), new JTextField(model
					.getTaskDescription(taskIndex), 80));
			togglePanel.setBackground(getBackground());
			final JPopupMenu popupMenu = new JPopupMenu();

			final JMenuItem addTaskAfterItem = new JMenuItem("Add Task After");
			popupMenu.add(addTaskAfterItem);
			final JMenuItem addBeforeItem = new JMenuItem("Add Task Before");
			popupMenu.add(addBeforeItem);
			final JMenuItem moveUpItem = new JMenuItem("Move Up");
			popupMenu.add(moveUpItem);
			final JMenuItem moveDownItem = new JMenuItem("Move Down");
			popupMenu.add(moveDownItem);
			final JMenuItem deleteItem = new JMenuItem("Delete");
			popupMenu.add(deleteItem);

			ActionListener listener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					updateInputFileValues();
					if (e.getSource() == addTaskAfterItem) {
						showAddTask(taskIndex, true);
					} else if (e.getSource() == addBeforeItem) {
						showAddTask(taskIndex, false);
					} else if (e.getSource() == moveUpItem) {
						model.move(taskIndex, taskIndex - 1);
					} else if (e.getSource() == moveDownItem) {
						model.move(taskIndex, taskIndex + 1);
					} else if (e.getSource() == deleteItem) {
						model.remove(taskIndex);
					}
				}
			};

			addTaskAfterItem.addActionListener(listener);
			addBeforeItem.addActionListener(listener);
			moveUpItem.addActionListener(listener);
			moveDownItem.addActionListener(listener);
			deleteItem.addActionListener(listener);

			togglePanel.getMajorLabel().addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {

					if (e.isPopupTrigger()
							|| e.getModifiers() == MouseEvent.BUTTON3_MASK) {
						moveDownItem.setEnabled((taskIndex + 1) != model
								.getTaskCount());
						moveUpItem.setEnabled(taskIndex > 0);

						// addBeforeItem.setEnabled(taskIndex != 0);
						// addTaskAfterItem.setEnabled(taskIndex != model
						// .getTaskCount() - 1
						// || model.getTaskCount() == 1);
						tasksInPipelineComboBox.setSelectedIndex(taskIndex);
						popupMenu.show(e.getComponent(), e.getX(), e.getY());
					}
				}
			});
			togglePanel.setExpanded(true);
			CellConstraints cc = new CellConstraints();
			setBackground(Color.white);
			layout = new FormLayout(
					"left:pref, 3dlu, right:pref, 3dlu, default:grow", "");
			setLayout(layout);
			addTaskParameters();

			layout.appendRow(new RowSpec("1dlu"));
			layout.appendRow(new RowSpec("pref"));
			add(new JSeparator(), cc.xyw(1, layout.getRowCount(), layout
					.getColumnCount()));
			layout.appendRow(new RowSpec("5dlu"));
		}

		public void setTaskIndex(int taskIndex) {
			this.taskIndex = taskIndex;
			for (int i = 0; i < parameters.length; i++) {
				parameters[i].setTaskIndex(taskIndex);
			}
			togglePanel.getMajorLabel().setText(
					(taskIndex + 1) + ". " + model.getTaskName(taskIndex));
		}

		private void addTaskParameters() {
			CellConstraints cc = new CellConstraints();
			layout.appendRow(new RowSpec("pref"));
			add(togglePanel, cc.xyw(1, 1, layout.getColumnCount()));

			try {
				final String lsidString = model.getTaskLSID(taskIndex);
				final LSID lsid = new LSID(lsidString);
				List versions = (List) AnalysisServiceManager.getInstance()
						.getLSIDToVersionsMap().get(lsid.toStringNoVersion());
				JComboBox versionChooserComboBox = new JComboBox(versions
						.toArray());
				versionChooserComboBox.setSelectedItem(lsid.getVersion());
				versionChooserComboBox.setBackground(Color.white);
				togglePanel.addToggleComponent(versionChooserComboBox);
				layout.appendRow(new RowSpec("pref"));
				add(versionChooserComboBox, cc.xy(1, layout.getRowCount()));

				versionChooserComboBox.addItemListener(new ItemListener() {

					public void itemStateChanged(ItemEvent e) {
						if (e.getStateChange() != ItemEvent.SELECTED) {
							return;
						}
						String version = (String) e.getItem();
						String newLSID = lsid.toStringNoVersion() + ":"
								+ version;
						updateInputFileValues();
						updateValues(taskIndex);
						model.replace(taskIndex, AnalysisServiceManager
								.getInstance().getAnalysisService(newLSID)
								.getTaskInfo());
					}

				});
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			if (model.getParameterCount(taskIndex) > 0) {
				JLabel promptWhenRunLabel = new JLabel("Prompt when run");
				promptWhenRunLabel.setFont(promptWhenRunLabel.getFont()
						.deriveFont(
								promptWhenRunLabel.getFont().getSize2D() - 2));
				togglePanel.addToggleComponent(promptWhenRunLabel);
				layout.appendRow(new RowSpec("pref"));
				add(promptWhenRunLabel, cc.xyw(PROMPT_WHEN_RUN_COLUMN, layout
						.getRowCount(), layout.getColumnCount()));
			}
			parameters = new ParameterDisplay[model
					.getParameterCount(taskIndex)];
			for (int i = 0; i < model.getParameterCount(taskIndex); i++) {
				parameters[i] = new ParameterDisplay(model, taskIndex, i);
				String paramName = model.getParameterName(taskIndex, i);

				JLabel label = new JLabel(AnalysisServiceDisplay
						.getDisplayString(paramName)
						+ ":");
				togglePanel.addToggleComponent(label);
				layout.appendRow(new RowSpec("pref"));

				final JCheckBox promptWhenRunCheckBox = parameters[i]
						.createPromptWhenRunCheckBox();

				promptWhenRunCheckBox.setSelected(model.isPromptWhenRun(
						taskIndex, i));

				togglePanel.addToggleComponent(promptWhenRunCheckBox);

				add(promptWhenRunCheckBox, cc.xy(PROMPT_WHEN_RUN_COLUMN, layout
						.getRowCount(), CellConstraints.LEFT,
						CellConstraints.CENTER));

				add(label, cc.xy(INPUT_LABEL_COLUMN, layout.getRowCount(),
						CellConstraints.RIGHT, CellConstraints.CENTER));

				if (model.isChoiceList(taskIndex, i)) {
					JComponent choiceInput = parameters[i].createChoiceInput(
							model.getChoices(taskIndex, i), model.getValue(
									taskIndex, i));
					add(choiceInput, cc.xy(INPUT_FIELD_COLUMN, layout
							.getRowCount(), CellConstraints.LEFT,
							CellConstraints.BOTTOM));
					togglePanel.addToggleComponent(choiceInput);

				} else if (model.isInputFile(taskIndex, i)) {
					JPanel inputPanel = parameters[i].createInputField();
					togglePanel.addToggleComponent(inputPanel);
					add(inputPanel, cc.xy(INPUT_FIELD_COLUMN, layout
							.getRowCount(), CellConstraints.LEFT,
							CellConstraints.BOTTOM));
				} else {
					JTextField inputComponent = new JTextField(20);
					if (!model.isPromptWhenRun(taskIndex, i)) {
						inputComponent.setText(model.getValue(taskIndex, i));
					}
					parameters[i].inputField = inputComponent;
					add(inputComponent, cc.xy(INPUT_FIELD_COLUMN, layout
							.getRowCount(), CellConstraints.LEFT,
							CellConstraints.BOTTOM));
					togglePanel.addToggleComponent(inputComponent);

				}
				layout.appendRow(new RowSpec("1dlu"));
			}

			int endParameterRow = layout.getRowCount();
			// int[] group = new int[endParameterRow - startParameterRow + 1];
			// for (int i = startParameterRow, index = 0; i <= endParameterRow;
			// i++, index++) {
			// group[index] = i;
			// }
			int[][] rowGroups = layout.getRowGroups();
			int[][] newRowGroups = new int[rowGroups.length + 1][];
			for (int i = 0; i < rowGroups.length; i++) {
				newRowGroups[i] = rowGroups[i];
			}
			// newRowGroups[newRowGroups.length - 1] = group;
			// layout.setRowGroups(newRowGroups);
		}

		public String getTaskDescription() {
			return ((JTextComponent) togglePanel.getMinorComponent()).getText()
					.trim();
		}

		public void setExpanded(boolean b) {
			togglePanel.setExpanded(b);
		}

		public boolean isPromptWhenRun(int parameterIndex) {
			return parameters[parameterIndex].isPromptWhenRun();
		}

		public String getInheritedFileName(int parameterIndex) {
			return parameters[parameterIndex].getInheritedFileName();
		}

		public int getInheritedTaskIndex(int parameterIndex) {
			return parameters[parameterIndex].getInheritedTaskIndex();
		}

		public String getValue(int parameterIndex) {
			return parameters[parameterIndex].getValue();
		}
	}

}
