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

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.xml.rpc.ServiceException;

import org.bridgedb.DataSource;
import org.bridgedb.Xref;
import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.gui.DataSourceModel;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSSearchResult;
import org.pathvisio.wpclient.FailedConnectionException;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.impl.WSResult;
import org.pathvisio.wpclient.models.XrefResultTableModel;
import org.pathvisio.wpclient.utils.FileUtils;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 * This class creates the content in the Dialog of the SearchByIdentifier
 * TabbedPane of Search
 * 
 * @author Sravanthi Sinha
 * @author mkutmon
 */
public class XrefSearchPanel extends JPanel {
	WikiPathwaysClientPlugin plugin;
	public static Xref[] xrefs;
	private List<Xref> pxXref = new ArrayList<Xref>();
	private JTable resultTable;
	private JTextArea txtId;
	private JComboBox cbSyscode;
	private JScrollPane resultspane;

	private JLabel tipLabel;
	private JLabel lblNumFound;

	public XrefSearchPanel(final WikiPathwaysClientPlugin plugin) {
		this.plugin = plugin;

		setLayout(new BorderLayout());

		Action searchXrefAction = new AbstractAction("Search") {
			public void actionPerformed(ActionEvent e) {
				try {
					resultspane.setBorder(BorderFactory.createTitledBorder(
							BorderFactory.createEtchedBorder(), "Pathways"));
					
					searchByXref();
				} catch (Exception ex) {
					JOptionPane
							.showMessageDialog(XrefSearchPanel.this,
									ex.getMessage(), "Error",
									JOptionPane.ERROR_MESSAGE);
					Logger.log.error("Error searching WikiPathways", ex);
				}
			}

		};

		JPanel searchBox = new JPanel();
		FormLayout layoutf = new FormLayout(
				"p,3dlu,120px,2dlu,30px,fill:pref:grow,3dlu",
				"pref, pref, 4dlu, pref, 4dlu, pref");
		CellConstraints cc = new CellConstraints();

		searchBox.setLayout(layoutf);
		searchBox.setBorder(BorderFactory
				.createTitledBorder(BorderFactory.createEtchedBorder()));

		JPanel searchOptBox = new JPanel();
		FormLayout layout = new FormLayout(
				"p,3dlu,120px,2dlu,30px,fill:pref:grow,3dlu,pref:grow,3dlu",
				"pref, pref, 4dlu, pref, 4dlu, pref");

		searchOptBox.setLayout(layout);
		searchOptBox.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Search options"));

		txtId = new JTextArea(2, 2);

		cbSyscode = new JComboBox(new DataSourceModel());
		cbSyscode.setSelectedIndex(0);

		JPanel searchReferenceBox = new JPanel();
		FormLayout layout2 = new FormLayout("p,3dlu,fill:pref:grow,1dlu,pref",
				"pref,3dlu,pref,pref,pref");

		searchReferenceBox.setLayout(layout2);

		searchReferenceBox.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Search By Reference"));

		searchReferenceBox.add(new JLabel("ID"), cc.xy(1, 1));
		searchReferenceBox.add(new JScrollPane(txtId), cc.xyw(3, 1, 2));

		JButton searchButton = new JButton(searchXrefAction);
		searchReferenceBox.add(searchButton, cc.xy(5, 1));

		tipLabel = new JLabel(
				"Enter Gene List (each in a new line)  eg- L:1234");
		tipLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

		searchReferenceBox.add(tipLabel, cc.xyw(1, 4, 5));
		searchBox.add(searchReferenceBox, cc.xyw(1, 4, 6));

		add(searchBox, BorderLayout.NORTH);

		// Center contains table model for results
		resultTable = new JTable();
		resultspane = new JScrollPane(resultTable);

		add(resultspane, BorderLayout.CENTER);
		lblNumFound = new JLabel();
		add (lblNumFound, BorderLayout.SOUTH);
		resultTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					JTable target = (JTable) e.getSource();
					int row = target.getSelectedRow();

					try {
						XrefResultTableModel model = (XrefResultTableModel ) target.getModel();
						File tmpDir = new File(plugin.getTmpDir(),FileUtils.getTimeStamp());
						tmpDir.mkdirs();

						plugin.openPathwayWithProgress(model.getValueAt(row, 0).toString(), 0, tmpDir, xrefs);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(XrefSearchPanel.this,
								ex.getMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
						Logger.log.error("Error", ex);
					}
				}
			}
		});
	}

	private void searchByXref() throws RemoteException, InterruptedException, ExecutionException, MalformedURLException, ServiceException {
		lblNumFound.setText("");
		pxXref.clear();
		if (!txtId.getText().isEmpty()) {
			System.out.println(txtId.getText());
			final ProgressKeeper pk = new ProgressKeeper();
			final ProgressDialog d = new ProgressDialog(JOptionPane.getFrameForComponent(this), "", pk, true, true);
	
			SwingWorker<WSResult[], Void> sw = new SwingWorker<WSResult[], Void>() {
				WSResult[] results;
				protected WSResult[] doInBackground() throws Exception {
					pk.setTaskName("Starting Search");
	
					try {
						String[] xrefids = txtId.getText().split("\n");
						
						if (xrefids.length < 6) {
							int count = 0;
							for (String x : xrefids) {
								String p[] = x.split(":");
								if (p.length == 2) {
									DataSource ds = DataSource.getExistingBySystemCode(p[0]);
									pxXref.add(new Xref(p[1], ds));
								} else {
									JOptionPane.showMessageDialog(XrefSearchPanel.this, "Enter Valid Xrefs ", "Error", JOptionPane.ERROR_MESSAGE);
									pk.finished();
									return results;
								}
								count++;
							}
	
							xrefs = new Xref[count];
							pxXref.toArray(xrefs);
	
							pk.setTaskName("Searching ");
							WSSearchResult[] p = plugin.getWpQueries().findByXref(xrefs, pk);
							pk.setTaskName("Sorting result");
							results = sort(p);
						} else {
							JOptionPane.showMessageDialog(XrefSearchPanel.this, " Can have maximum 5 Xrefs ", "Error", JOptionPane.ERROR_MESSAGE);
							pk.finished();
							return results;
						}
					} finally {
						pk.finished();
					}
					return results;
				}

				private WSResult[] sort(WSSearchResult [] results) throws RemoteException, FailedConnectionException {
					Map<String, WSResult> result = new HashMap<String, WSResult>();
					for(WSSearchResult res : results) {
						WSResult wsResult = new WSResult();
						if(!result.containsKey(res.getId())) {
							wsResult.setWsSearchResult(res);
							int count = 0;
							for (Xref x : pxXref) {
								String [] li = plugin.getWpQueries().getXrefList(res.getId(), x.getDataSource(), pk);
								for(String s : li) {
									if(s.equals(x.getId())) {
										count++;
									}
								}
							}
							wsResult.setCount(count);
							result.put(res.getId(), wsResult);
						}
					}
					List<WSResult> list = new ArrayList<WSResult>();
					list.addAll(result.values());
					Collections.sort(list, Collections.reverseOrder());
					WSResult [] finalresults = new WSResult[list.size()];
					finalresults = list.toArray(finalresults);
						
					return finalresults;
				}

				protected void done() {
					if (!pk.isCancelled()) {
						if (results.length == 0) {
							JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(), "0 results found");
						}
					} else if (pk.isCancelled()) {
						pk.finished();
					}
				}
			};
			
			sw.execute();
			d.setVisible(true);
			
			resultTable.setModel(new XrefResultTableModel(sw.get()));
			lblNumFound.setText(sw.get().length + " pathways found.");
		} else {
			JOptionPane.showMessageDialog(XrefSearchPanel.this,
					"Please Enter ID", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
