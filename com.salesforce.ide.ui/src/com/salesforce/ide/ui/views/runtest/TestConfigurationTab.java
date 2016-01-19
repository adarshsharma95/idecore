/*******************************************************************************
 * Copyright (c) 2016 Salesforce.com, inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Salesforce.com, inc. - initial API and implementation
 ******************************************************************************/

package com.salesforce.ide.ui.views.runtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.salesforce.ide.core.internal.utils.QualifiedNames;
import com.salesforce.ide.core.internal.utils.ResourceProperties;
import com.salesforce.ide.core.internal.utils.Utils;
import com.salesforce.ide.core.remote.tooling.RunTests.SuiteManager;
import com.salesforce.ide.core.remote.tooling.RunTests.SuiteManager.MySuite;
import com.salesforce.ide.core.remote.tooling.RunTests.SuitesHolder;
import com.salesforce.ide.core.remote.tooling.RunTests.TestsHolder;
import com.salesforce.ide.core.remote.tooling.RunTests.TestsHolder.Test;

/**
 * Apex test launch configuration tab to select tests or suites
 * 
 * @author jwidjaja
 *
 */
public class TestConfigurationTab extends RunTestsTab {
	
	private ProjectConfigurationTab projectTab;
	
	@VisibleForTesting
	public Map<IProject, TestsHolder> allTests;
	@VisibleForTesting
	public Map<IProject, SuiteManager> suiteManagers;
	
	@VisibleForTesting
	public Text classText;
	@VisibleForTesting
	public Button classButton;
	
	@VisibleForTesting
	public Text testMethodText;
	@VisibleForTesting
	public Button testMethodButton;
	
	@VisibleForTesting
	public Button suiteStatus;
	@VisibleForTesting
	public Table suiteTable;
	
	@VisibleForTesting
	public boolean resetTestSelection = false;
	
	public TestConfigurationTab() {
		super();
		allTests = Maps.newHashMap();
		suiteManagers = Maps.newHashMap();
	}
	
	@VisibleForTesting
	public Map<IProject, TestsHolder> getTestHolder() {
		return allTests;
	}
	
	@VisibleForTesting
	public Map<IProject, SuiteManager> getSuiteManagers() {
		return this.suiteManagers;
	}
	
	/**
	 * Need to be able to get some stuff from ProjectTab,
	 * so this is probably better than doing
	 * getLaunchConfigurationDialog().getTabs() and instanceof.
	 * @param projectTab
	 */
	@VisibleForTesting
	public void saveSiblingTab(RunTestsTab projectTab) {
		this.projectTab = (ProjectConfigurationTab) projectTab;
	}
	
	@VisibleForTesting
	public ProjectConfigurationTab getSiblingTab() {
		return this.projectTab;
	}
	
	/**
	 * Used to inform TestConfigurationTab to reset the test selection
	 * when the tab is initialized/saved
	 */
	@VisibleForTesting
	public void resetTestSelection() {
		this.resetTestSelection = true;
	}
	
	@VisibleForTesting
	@Override
	public void createControl(Composite parent) {
		if (Utils.isEmpty(parent)) return;
    	
        Composite comp = createComposite(parent, SWT.NONE);
        setControl(comp);
        colorGray = comp.getDisplay().getSystemColor(SWT.COLOR_GRAY);
		colorBlack = comp.getDisplay().getSystemColor(SWT.COLOR_BLACK);

        GridLayout grid = new GridLayout();
        comp.setLayout(grid);
        
        // This tab allows test class/method/suites selection
        createSingleTestSelector(comp);
        createSuiteSelector(comp);
	}
	
	/**
     * Create 'run single test' group which contains test class and test method.
     * Through this, user may do one of the following:
     *   [*] Run all Apex test methods in all Apex test classes in a Force.com project
     *   [*] Run all Apex test methods in one Apex test class in a Force.com project
     *   [*] Run one Apex test method in one Apex test class in a Force.com project
     *   
     * @param parent
     *   The Composite widget to hold all the labels, input fields, buttons, etc.
     */
	@VisibleForTesting
	public void createSingleTestSelector(Composite parent) {
		Group group = createGroup(parent, SWT.NONE);
		group.setText(Messages.Tab_TestsGroupTitle);
		group.setLayout(new GridLayout(3, false));
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
        // Test class group (label, text, button)
		makeDefaultLabel(group, Messages.Tab_TestClassGroupTitle);
        classText = makeDefaultText(group, Messages.Tab_AllClasses, colorGray);
        classButton = makeDefaultButton(group, Messages.Tab_SearchButtonText, 
        		shouldEnableBasedOnText(projectTab.getProjectName()));
        classButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	handleClassButtonSelected();
                validatePage();
                updateLaunchConfigurationDialog();
            }
        });

        // Test method group (label, text, button)
        makeDefaultLabel(group, Messages.Tab_TestMethodGroupTitle);
        testMethodText = makeDefaultText(group, Messages.Tab_AllMethods, colorGray);
        testMethodButton = makeDefaultButton(group, Messages.Tab_SearchButtonText, 
        		shouldEnableBasedOnText(getTestClassName()));
        testMethodButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	handleTestMethodButtonSelected();
                validatePage();
                updateLaunchConfigurationDialog();
            }
        });
	}
	
	/**
	 * Create group for test suites
	 * @param parent
	 */
	@VisibleForTesting
	public void createSuiteSelector(Composite parent) {
		Group group = createGroup(parent, SWT.NONE);
		group.setText(Messages.Tab_SuiteGroupTitle);
		group.setLayout(new GridLayout(1, true));
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		suiteStatus = makeDefaultCheckbox(group, Messages.Tab_UseSuites, true, false);
		suiteStatus.addSelectionListener(new SelectionAdapter() {
    		@Override
            public void widgetSelected(SelectionEvent e) {
    			Button btn = (Button) e.getSource();
    			enableSuiteTable(btn.getSelection());
    			validatePage();
                updateLaunchConfigurationDialog();
            }
    	});
		
		suiteTable = makeDefaultMultiCheckTable(group, new String[] { Messages.Tab_SuiteColumnName });
		suiteTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (e.getSource() instanceof Table) {
					// Get all table items and ...
					TableItem[] items = ((Table) e.getSource()).getItems();
					for (TableItem item : items) {
						boolean selected = item.getChecked();
						// ... update the suite object with selection
						MySuite theSuite = (MySuite) item.getData();
						if (Utils.isNotEmpty(theSuite)) {
							theSuite.setSelected(selected);
						}
					}
					
					validatePage();
	                updateLaunchConfigurationDialog();
				}
			}
		});
	}
	
	/**
	 * Enable/disable the suite table, which
	 * will disable/enable the single class/method selectors
	 * @param enable
	 */
	@VisibleForTesting
	public void enableSuiteTable(boolean enable) {
		suiteStatus.setSelection(enable);
		suiteTable.setEnabled(enable);
		for (TableItem ti : suiteTable.getItems()) {
			ti.setGrayed(enable);
		}
		
		// Always do the opposite for the text only fields
		classText.setEnabled(!enable);
		testMethodText.setEnabled(!enable);
		if (enable) {
			// If user wants to use suites, always disable the
			// test search buttons
			classButton.setEnabled(!enable);
			testMethodButton.setEnabled(!enable);
		} else {
			// If user does not want to use suites, enable the
			// test search buttons accordingly
			classButton.setEnabled(shouldEnableBasedOnText(projectTab.getProjectName()));
			testMethodButton.setEnabled(shouldEnableBasedOnText(getTestClassName()));
		}
	}
	
	/**
	 * Renew list of test suites from server
	 */
	@VisibleForTesting
	public List<MySuite> fetchSuites(IProject project) {
    	if (Utils.isEmpty(project)) Collections.emptyList();
    	
    	List<MySuite> suites = Lists.newArrayList();
    	SuiteManager mgr = createSuiteMgr(project);
    	if (Utils.isNotEmpty(mgr)) {
    		suiteManagers.put(project, mgr);
    		suites = mgr.fetchSuites();
        	generateSuiteTable(suites);
    	}
    	
    	return suites;
    }
	
	@VisibleForTesting
	public SuiteManager createSuiteMgr(IProject project) {
		if (Utils.isEmpty(project)) return null;
		
		return new SuiteManager(project);
	}
	
	/**
	 * List of suites in the config are previously selected suites
	 * and the source of truth. Reconcile that list with known 
	 * suites from server, and build the suite table.
	 * @param suitesInConfig
	 */
	@VisibleForTesting
	public void reconcileSuites(Set<String> suitesInConfig) {
		IProject project = projectTab.getProjectFromName();
		if (Utils.isEmpty(project)) return;
		
		if (!suiteManagers.containsKey(project)) {
			fetchSuites(project);
		}
		
		SuiteManager mgr = suiteManagers.get(project);
		// Get known suites
		List<MySuite> fetchedSuites = mgr.getFetchedSuites();
		for (MySuite fetchedSuite: fetchedSuites) {
			// Select the suite if it exists in config. Otherwise, deselect
			boolean selected = Utils.isNotEmpty(suitesInConfig) ? 
					suitesInConfig.contains(fetchedSuite.getSuiteId()) : false;
			fetchedSuite.setSelected(selected);
		}
		
		generateSuiteTable(fetchedSuites);
	}
	
	/**
	 * Generate table of suites
	 * @param suites
	 */
	@VisibleForTesting
	public void generateSuiteTable(List<MySuite> suites) {
		if (Utils.isEmpty(suites)) return;
		
		suiteTable.removeAll();
    	suiteTable.clearAll();
    	
    	for (MySuite suite : suites) {
    		TableItem item = new TableItem(suiteTable, SWT.NONE);
        	item.setText(suite.getSuiteName());
        	item.setGrayed(false);
        	item.setChecked(suite.isSelected());
        	// Save the suite to be used in a selection listener
        	item.setData(suite);
    	}
    	
    	for (TableColumn col : suiteTable.getColumns()) {
			col.pack();
		}
	}
	
	/**
     * Find test classes in the project and update other widgets
     * when test class is chosen.
     */
	@VisibleForTesting
	public void handleClassButtonSelected() {
    	String selectedTestClass = chooseTestClass();
    	
    	if (StringUtils.isBlank(selectedTestClass) || Utils.isEmpty(classText)) {
    		return;
    	}
    	
    	// Reset test method text if user changed test class
    	if (!selectedTestClass.equals(getTestClassName())) {
    		setTextProperties(testMethodText, Messages.Tab_AllMethods, colorGray);
    	}
    	// Display newest selected class name
    	classText = setTextProperties(classText, selectedTestClass, colorBlack);
    	// Allow test method selection after test class is known, unless user selected 'all classes'
    	testMethodButton.setEnabled(shouldEnableBasedOnText(getTestClassName()));
    }
	
	/**
     * Display a list of test classes in the project and return
     * the selected one.
     * @return Name of test class
     */
	@VisibleForTesting
	public String chooseTestClass() {
    	// Display the test classes in dialog
    	ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), new LabelProvider() {
            @Override
            public String getText(Object element) {
                if (Utils.isNotEmpty(element) && element instanceof Test) {
                	return ((Test) element).getClassName();
                }
                return "";
            }
        });
    	
    	dialog.setTitle(Messages.Tab_ClassDialogTitle);
        dialog.setMessage(Messages.Tab_ClassDialogInstruction);
        IProject selectedProject = projectTab.getProjectFromName();
        TestsHolder rt = allTests.get(selectedProject);
        // We already got the test classes earlier so just display them
        if (rt != null && rt.getTests() != null && !rt.getTests().isEmpty()) {
        	dialog.setElements(rt.getTests().toArray());
        }
        
        if (dialog.open() == Window.OK) {
            return ((Test) dialog.getFirstResult()).getClassName();
        }
    	
    	return null;
    }
	
	/**
     * Find test methods in the test class and update the
     * appropriate widgets when test method is chosen.
     */
	@VisibleForTesting
	public void handleTestMethodButtonSelected() {
    	String selectedTestMethod = chooseTestMethod();
    	
    	if (StringUtils.isEmpty(selectedTestMethod) || Utils.isEmpty(testMethodText)) {
    		return;
    	}
    	
    	// Display newest selected method name
    	testMethodText = setTextProperties(testMethodText, selectedTestMethod, colorBlack);
    }
	
	/**
     * Display a list of test methods in the test class and return
     * the selected one.
     * @return Name of test method
     */
	@VisibleForTesting
	public String chooseTestMethod() {
    	// We already got test methods earlier so just display the ones
    	// for previously specified test class
    	List<String> testMethodNames = Lists.newArrayList();
    	IProject selectedProject = projectTab.getProjectFromName();
        TestsHolder rt = allTests.get(selectedProject);
    	for (Test test : rt.getTests()) {
    		if (test.getClassName().equals(getTestClassName())) {
    			testMethodNames = test.getTestMethods();
    		}
    	}
    	
    	// Display the test methods in dialog
    	ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), new LabelProvider() {
            @Override
            public String getText(Object element) {
                if (Utils.isEmpty(element)) {
                    return "";
                }
                
                return element.toString();
            }
        });
    	
    	dialog.setTitle(Messages.Tab_MethodDialogTitle);
    	dialog.setMessage(Messages.Tab_MethodDialogInstruction);
    	if (testMethodNames != null && !testMethodNames.isEmpty()) {
    		dialog.setElements(testMethodNames.toArray());
    	}
    	
    	if (dialog.open() == Window.OK) {
    		return dialog.getFirstResult().toString();
    	}
    	
    	return null;
    }
	
	/**
     * Reset messages and validate test/suite selection.
     * @return True if all is okay. False otherwise.
     */
	@VisibleForTesting
	public boolean validatePage() {
		// Reset the messages first to a clean slate
        setErrorMessage(null);
        setMessage(null);
        
        return projectTab.validateProjectSelection() && validateSuiteSelection();
	}
	
	/**
	 * Validate suite table
	 */
	@VisibleForTesting
	public boolean validateSuiteSelection() {
        // If user wants to use suites, make sure at least one is checked
        if (Utils.isNotEmpty(suiteStatus) && suiteStatus.isEnabled() && suiteStatus.getSelection()) {
        	boolean foundOneChecked = false;
        	for (TableItem ti : suiteTable.getItems()) {
        		if (Utils.isNotEmpty(ti) && ti.getChecked()) {
        			foundOneChecked = true;
        			break;
        		}
        	}
        	
        	if (!foundOneChecked) {
        		setErrorMessage(Messages.Tab_ChooseAtLeastOneSuiteErrorMessage);
        		return false;
        	}
        }
        
        return true;
	}
	
	@VisibleForTesting
	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		validatePage();
		return getErrorMessage() == null;
	}
	
	/**
     * Retrieve test classes and methods for a specific project. This
     * should only be called when opening the config for the first time
     * or when user changes the project.
     * @param project
     * @return RunTests POJO
     */
	@VisibleForTesting
	public TestsHolder buildTestsForProject(IProject project) {
    	TestsHolder rt = new TestsHolder();
    	List<Test> testClasses = new ArrayList<Test>();
    	
    	Map<IResource, List<String>> allTests = sourceLookup.findTestClassesInProject(project);
    	for (IResource resource : allTests.keySet()) {
    		List<String> testMethods = allTests.get(resource);
    		if (Utils.isEmpty(testMethods)) {
    			continue;
    		}
    		
    		// If there is more than one test method in the test class, add the 'all methods' option
    		if (testMethods.size() > 1) {
    			testMethods.add(0, Messages.Tab_AllMethods);
    		}
    		
    		String resourceId = ResourceProperties.getProperty(resource, QualifiedNames.QN_ID);
    		
    		Test testClass = new TestsHolder.Test();
    		testClass.setClassId(resourceId);
    		testClass.setClassName(resource.getName());
    		testClass.setTestMethods(testMethods);
    		
    		testClasses.add(testClass);
    	}
    	
    	// If there is more than one test class in the project, add the 'all classes' option
    	if (testClasses != null && testClasses.size() > 1) {
    		Test allClasses = new TestsHolder.Test();
    		allClasses.setClassId(Messages.Tab_AllClasses);
    		allClasses.setClassName(Messages.Tab_AllClasses);
    		List<String> allMethods = new ArrayList<String>();
    		allMethods.add(Messages.Tab_AllMethods);
    		allClasses.setTestMethods(allMethods);
    		
    		testClasses.add(0, allClasses);
    	}
    	
    	rt.setTests(testClasses);
    	return rt;
    }
	
	/**
	 * Create the JSON object of tests to run.
	 * 
	 * @param selectedProject
	 * @return TestsHolder
	 */
	@VisibleForTesting
	public TestsHolder buildTestsForConfig(IProject selectedProject) {
    	if (Utils.isEmpty(selectedProject)) return null;
    	/*
    	 * Clone the original RunTests because the following logic
    	 * will filter out unwanted test classes/methods. We need to maintain the
    	 * original so we don't to re-build when user changes test class/method.
    	 */
    	TestsHolder th = allTests.containsKey(selectedProject) ? allTests.get(selectedProject).clone() : null;
    	if (Utils.isEmpty(th)) return null;
    	
    	boolean oneTestClass = (classText != null && getTestClassName() != null && !getTestClassName().equals(Messages.Tab_AllClasses));
    	boolean oneTestMethod = (testMethodText != null && getTestMethodName() != null && !getTestMethodName().equals(Messages.Tab_AllMethods));

		// Iterate through the test classes
		for (Iterator<Test> tcItr = th.getTests().iterator(); tcItr.hasNext();) {
			Test curTest = tcItr.next();
			/*
			 * Remove this Test object if:
			 * - User wants all test classes and this test class says 'all'
			 * - User wants one test class and this is not the one user wants
			 */
			if ((!oneTestClass && curTest.getClassName().equals(Messages.Tab_AllClasses)) || 
					(oneTestClass && !curTest.getClassName().equals(getTestClassName()))) {
				tcItr.remove();
				continue;
			}
			// Iterate through the test methods
			for (Iterator<String> tmItr = curTest.getTestMethods().iterator(); tmItr.hasNext();) {
				String curMethod = tmItr.next();
				/*
				 * Remove this test method if:
				 * - User wants all test methods and this test method says 'all'
				 * - User wants one test method and this is not the one user wants
				 */
				if ((!oneTestMethod && curMethod.equals(Messages.Tab_AllMethods)) || 
						(oneTestMethod && !curMethod.equals(getTestMethodName()))) {
					tmItr.remove();
				}
			}
		}
		
    	return th;
    }
    
    /**
     * Get comma separated list of suite IDs
     * @param selectedProject
     * @return Suite IDs
     */
    @VisibleForTesting
	public String getCommaSeparatedSuiteIds(IProject selectedProject) {
    	if (Utils.isEmpty(selectedProject)) return "";
    	
    	SuiteManager suiteMgr = suiteManagers.get(selectedProject);
    	List<String> selectedSuites = suiteMgr.getSelectedSuiteIds();
    	String suiteids = Joiner.on(",").join(selectedSuites);
    	
    	return suiteids;
    }
    
    /**
     * Create the JSON object of suites to run
     * @param selectedProject
     * @return SuitesHolder
     */
    @VisibleForTesting
	public SuitesHolder buildSuitesForConfig(String suiteIds) {
    	SuitesHolder sh = new SuitesHolder();
    	sh.setSuiteids(suiteIds);
    	
    	return sh;
    }
    
    /**
     * Decide which test run to use.
     * 
     * Some rules regarding Apex test modes:
     *   [*] If user selects test suites, SFDC only supports that in asynchronous run
     *   [*] If user selects one test class/method, it is always faster to run synchronously
     *   [*] If user selects more than one test class, SFDC only supports that in asynchronous run
     *   [*] If user has a debugging session, SFDC only supports that in synchronous run
     * Therefore, we can always make the best decision for user.
     * 
     * @param th
     * @param shouldUseSuites
     * @return True if async. False if sync.
     */
    @VisibleForTesting
	public boolean isAsyncTestRun(TestsHolder th, boolean shouldUseSuites) {
    	if (shouldUseSuites) return true;
    	
    	if (Utils.isNotEmpty(th) && th.getTests().size() > 1) return true;
    	
    	return false;
    }
    
    @VisibleForTesting
	public String getTestClassName() {
    	return (Utils.isEmpty(classText) ? "": classText.getText());
    }
    
    @VisibleForTesting
	public String getTestMethodName() {
    	return (Utils.isEmpty(testMethodText) ? "" : testMethodText.getText());
    }
	
    @VisibleForTesting
	public boolean shouldUseSuites() {
		return Utils.isNotEmpty(suiteStatus) && suiteStatus.getSelection();
	}
    
    @VisibleForTesting
	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			// Build the POJO for that project
	        IProject selectedProject = projectTab.getProjectFromName();
	        // TODO: Does this work with newly added test class in an existing config?
	        if (Utils.isNotEmpty(selectedProject) && !allTests.containsKey(selectedProject)) {
	        	TestsHolder selectedTests = buildTestsForProject(selectedProject);
	        	allTests.put(selectedProject, selectedTests);
	        }

	        // Set test class
	        String testClassName = this.resetTestSelection ? Messages.Tab_AllClasses : 
	        	configuration.getAttribute(RunTestsConstants.ATTR_TEST_CLASS, Messages.Tab_AllClasses);
	        setTextProperties(classText, testClassName, colorBlack);
	        classButton.setEnabled(shouldEnableBasedOnText(projectTab.getProjectName()));
	        
	        // Set test method
	        String testMethodName = this.resetTestSelection ? Messages.Tab_AllMethods : 
	        	configuration.getAttribute(RunTestsConstants.ATTR_TEST_METHOD, Messages.Tab_AllMethods);
	        setTextProperties(testMethodText, testMethodName, colorBlack);
	        testMethodButton.setEnabled(shouldEnableBasedOnText(getTestClassName()));
	        
	        this.resetTestSelection = false;
	        
	        // Set suite table
	        boolean shouldUseSuites = configuration.getAttribute(RunTestsConstants.ATTR_USE_SUITES, false);
	        enableSuiteTable(shouldUseSuites);
	        String prevSelectedSuiteIds = configuration.getAttribute(RunTestsConstants.ATTR_SUITE_IDS, "");
	        reconcileSuites(Sets.newHashSet(Arrays.asList(prevSelectedSuiteIds.split(","))));
		} catch (CoreException e) {}
	}

	@VisibleForTesting
	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		// Save selected test class name, and test method name
		if (this.resetTestSelection) {
			configuration.setAttribute(RunTestsConstants.ATTR_TEST_CLASS,  Messages.Tab_AllClasses);
			configuration.setAttribute(RunTestsConstants.ATTR_TEST_METHOD, Messages.Tab_AllMethods);
			this.resetTestSelection = false;
		} else {
			configuration.setAttribute(RunTestsConstants.ATTR_TEST_CLASS, getTestClassName());
	        configuration.setAttribute(RunTestsConstants.ATTR_TEST_METHOD, getTestMethodName());
		}
		
		// Save suites
		boolean shouldUseSuites = shouldUseSuites();
		configuration.setAttribute(RunTestsConstants.ATTR_USE_SUITES, shouldUseSuites);
		String currentSelectedSuiteIds = getCommaSeparatedSuiteIds(projectTab.getProjectFromName());
		configuration.setAttribute(RunTestsConstants.ATTR_SUITE_IDS, currentSelectedSuiteIds);
		SuitesHolder currentSelectedSuites = buildSuitesForConfig(currentSelectedSuiteIds);
		String allSuitesInJson = SuitesHolder.serialize(currentSelectedSuites);
		configuration.setAttribute(RunTestsConstants.ATTR_SUITES, allSuitesInJson);
		
        // Save tests
        TestsHolder currentSelectedTests = buildTestsForConfig(projectTab.getProjectFromName());
        String allTestsInJson = TestsHolder.serialize(currentSelectedTests);
        configuration.setAttribute(RunTestsConstants.ATTR_TESTS_ARRAY, allTestsInJson);
        
        // Save test mode
        boolean isAsync = isAsyncTestRun(currentSelectedTests, shouldUseSuites);
        configuration.setAttribute(RunTestsConstants.ATTR_TEST_MODE, isAsync);
	}

	@VisibleForTesting
	@Override
	public String getName() {
		return Messages.Tab_TestsTabTitle;
	}
	
	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}
}
