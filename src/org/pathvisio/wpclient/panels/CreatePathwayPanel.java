// PathVisio WP Client
// Plugin that provides a WikiPathways client for PathVisio.
// Copyright 2013-2016 developed for Google Summer of Code
//
// Licensed under the Apache License, Version 2.0 (the "License"); 
// you may not use this file except in compliance with the License. 
// You may obtain a copy of the License at 
// 
// http://www.apache.org/licenses/LICENSE-2.0 
//  
// Unless required by applicable law or agreed to in writing, software 
// distributed under the License is distributed on an "AS IS" BASIS, 
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
// See the License for the specific language governing permissions and 
// limitations under the License.
//
package org.pathvisio.wpclient.panels;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.utils.FileUtils;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

public class CreatePathwayPanel extends JPanel implements ActionListener {
	private LoginPanel p;
	private JDialog dialog, descriptionDialog;
	
	private JTextArea description = new JTextArea(2, 2);
	private WikiPathwaysClientPlugin plugin;

	public CreatePathwayPanel(WikiPathwaysClientPlugin plugin) {
		this.plugin = plugin;
		if (LoginPanel.username.equals("") || LoginPanel.password.equals("")) {
			showLoginPanel();
		}
		if (!(LoginPanel.username.equals("") && LoginPanel.password.equals("")) ){
			showDescriptionPanel();
		}
	}

	private void showDescriptionPanel() {
		FormLayout layout = new FormLayout(
				"7dlu,150px,fill:pref,150px",
				"pref, 2dlu, pref");
		CellConstraints cc = new CellConstraints();
		DescriptionPanel dp = new DescriptionPanel();
		descriptionDialog = new JDialog(plugin.getDesktop().getFrame(), "WikiPathways", false);
		JButton submit = new JButton("create");

		submit.setActionCommand("Create");
		submit.addActionListener(this);
		descriptionDialog.setLayout(layout);
	
		descriptionDialog.add(dp, cc.xyw(2, 1,3));

		JPanel p = new JPanel();

		p.add(submit);
		descriptionDialog.add(p, cc.xy(3,3));

		descriptionDialog.pack();
		descriptionDialog.setVisible(true);
		descriptionDialog.setResizable(false);
		descriptionDialog.setLocationRelativeTo(plugin.getDesktop().getFrame());
		descriptionDialog.setVisible(true);
	}

	private class DescriptionPanel extends JPanel {
		public DescriptionPanel() {
			super();
			setLayout(new GridLayout(2, 2));
			add(new JLabel("Description for Pathway:"));
			add(new JScrollPane(description));
		}
	}

	private void showLoginPanel() {
		p = new LoginPanel(plugin);
		dialog = new JDialog(plugin.getDesktop().getFrame(), "WikiPathways Login", false);
		JButton submit = new JButton("Login");

		submit.setActionCommand("Login");
		submit.addActionListener(this);
		dialog.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		c.gridwidth = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 1.0;
		dialog.add(p, c);

		c.weighty = 0.0;
		c.weightx = 0.5;
		c.fill = GridBagConstraints.NONE;
		c.gridwidth = GridBagConstraints.HORIZONTAL;
		JPanel p = new JPanel();

		p.add(submit);
		dialog.add(p, c);

		dialog.pack();
		dialog.setVisible(true);
	
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(plugin.getDesktop().getSwingEngine().getFrame());
		dialog.setVisible(true);
	}

	public void createPathway() {
		final ProgressKeeper pk = new ProgressKeeper();
		final ProgressDialog d = new ProgressDialog(
				plugin.getDesktop().getFrame(), "", pk, true, true);

		SwingWorker<WSPathwayInfo, Void> sw = new SwingWorker<WSPathwayInfo, Void>() {
			WSPathwayInfo info;
			protected WSPathwayInfo doInBackground() throws Exception {
				try {
					pk.setTaskName("Uploading pathway.");
					Pathway pathway = plugin.getDesktop().getSwingEngine().getEngine().getActivePathway();
					info = plugin.getWpQueries().uploadPathway(pathway);
					pk.setTaskName("Adding curation tag.");
					plugin.getWpQueries().updateCurationTag( "Curation:UnderConstruction", info.getId(), "", Integer.parseInt(info.getRevision()));
					JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
							"The Pathway " + info.getId() + " has been uploaded.\n\n Curation Tag \"Under Construction\" has been added.\nPlease update the curation tags if needed.");
								WikiPathwaysClientPlugin.revisionno = info.getRevision();
								WikiPathwaysClientPlugin.pathwayid = info.getId();
		
				} catch (Exception e) {
					JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
							"Error while creating new pathway as you donot have the write access.\n Please submit a request in the following link: \n http://plugins.pathvisio.org/wp-client/request-webservice-access/", "Error",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					pk.finished();
				}
				return info;
			}
			
			protected void done() {
				if(info != null) {
					// open latest revision
					File tmpDir = new File(plugin.getTmpDir(), FileUtils.getTimeStamp());
					tmpDir.mkdirs();

					try {
						pk.setTaskName("Open latest revision of pathway.");
						plugin.openPathwayWithProgress(info.getId(), 0, tmpDir);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
								"Could not load new revision.", "Error",
								JOptionPane.ERROR_MESSAGE);
						Logger.log.error("Error", ex);
					}
				}
			}
		};
		
		sw.execute();
		d.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		if ("Login".equals(e.getActionCommand())) {
			final ProgressKeeper pk = new ProgressKeeper();
			final ProgressDialog d = new ProgressDialog(plugin.getDesktop().getFrame(), "", pk, true, true);
			
			SwingWorker<Void, Void> sw = new SwingWorker<Void, Void>() {
				
				Boolean value = false;
				
				protected Void doInBackground() throws Exception {
					pk.setTaskName("Checking login details.");
					dialog.dispose();
					try {
						if(p.login()) {
							value = true;
						}
					} catch (Exception e1) {}
					return null; 
				}
				
				protected void done() {
					if(!pk.isCancelled()) {
						if(value) {
							showDescriptionPanel();
							pk.finished();
						}
					}
				}
			};
			sw.execute();
			d.setVisible(true);
		} else if ("Create".equals(e.getActionCommand())) {		
			createPathway();
			descriptionDialog.dispose();
		}
	}
}
