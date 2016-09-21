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
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.xml.rpc.ServiceException;

import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSIndexField;
import org.pathvisio.wikipathways.webservice.WSSearchResult;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.utils.FileUtils;
import org.pathvisio.wpclient.validators.Validator;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 *	This class creates the content in the Dialog of the
 *	ReferenceSearch TabbedPane of Search
 * 	@author Sravanthi Sinha
 * 	@author mkutmon
 */
public class LiteratureSearchPanel extends JPanel {
	
	private WikiPathwaysClientPlugin plugin;
		
	private JTable resultTable;		
	private JScrollPane resultspane;
	private JTextField pubXref;
	private JLabel tipLabel;
	private JLabel lblNumFound;

	public LiteratureSearchPanel(final WikiPathwaysClientPlugin plugin) {
		this.plugin = plugin;
		setLayout(new BorderLayout());
		pubXref = new JTextField();
		pubXref.addActionListener(getSearchLiteratureAction());

		tipLabel = new JLabel("Tip: use Pubmed id or Literature Title");
		tipLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
		
		JLabel tipLabel2 = new JLabel("(e.g.: '18651794' , 'WikiPathways: pathway editing for the people.')");
		tipLabel2.setFont(new Font("SansSerif", Font.ITALIC, 11));
		
		JPanel searchBox = new JPanel();
		FormLayout layoutf = new FormLayout("p,3dlu,120px,2dlu,30px,pref:grow,3dlu",
				"pref, pref, 4dlu, pref, 4dlu, pref");
		CellConstraints cc = new CellConstraints();

		searchBox.setLayout(layoutf);
		searchBox.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));

		JPanel searchOptBox = new JPanel();
		FormLayout layout = new FormLayout(
				"3dlu,p,3dlu,2dlu,30px,fill:pref:grow,2dlu",
				"pref, 2dlu,pref, 2dlu, pref, 4dlu, pref");

		searchOptBox.setLayout(layout);
		searchOptBox.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
				"Search options"));
		
		searchOptBox.add(new JLabel("Publication Title/ID"), cc.xy(2, 1));
		searchOptBox.add(pubXref, cc.xyw(4, 1, 3));
		searchOptBox.add(tipLabel,cc.xyw(2, 3,5));
		searchOptBox.add(tipLabel2,cc.xyw(2, 5,5));

		searchBox.add(searchOptBox, cc.xyw(1, 1, 6));
		
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
						LiteratureResultTableModel	model = (LiteratureResultTableModel) target.getModel();
						File tmpDir = new File(plugin.getTmpDir(),FileUtils.getTimeStamp());
						tmpDir.mkdirs();
						plugin.openPathwayWithProgress(model.getValueAt(row, 0).toString(), 0, tmpDir);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(LiteratureSearchPanel.this,ex.getMessage(), "Error",JOptionPane.ERROR_MESSAGE);
						Logger.log.error("Error", ex);
					}
				}
			}
		});
	}

	private Action getSearchLiteratureAction() {
		Action searchLiteratureAction = new AbstractAction("searchlit") {
			public void actionPerformed(ActionEvent e) {
				try {
					resultspane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Pathways"));
					searchByLiterature();
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(LiteratureSearchPanel.this, "Could not connect to WikiPathways to retrieve result.", "Error", JOptionPane.ERROR_MESSAGE);
					Logger.log.error("Error searching WikiPathways", ex);
				}
			}
		};
		return searchLiteratureAction;
	}
	
	private void searchByLiterature() throws RemoteException,InterruptedException, ExecutionException, MalformedURLException, ServiceException {
		lblNumFound.setText("");
		final String query = pubXref.getText();

		if (!query.isEmpty()) {
			if(Validator.CheckNonAlpha(query)) {
			
				final ProgressKeeper pk = new ProgressKeeper();
				final ProgressDialog d = new ProgressDialog(JOptionPane.getFrameForComponent(this), "", pk, true, true);
	
				SwingWorker<WSSearchResult[], Void> sw = new SwingWorker<WSSearchResult[], Void>() {
					
					WSSearchResult[] results;
					
					protected WSSearchResult[] doInBackground() throws Exception {
						pk.setTaskName("Searching");
						
						try {
							results = plugin.getWpQueries().findByLiteratureReference(query, pk);
						} finally {
							pk.finished();
						}
						
						return results;
					}
					
					protected void done() {
						if(!pk.isCancelled()) {
							if(results.length == 0) {
								 JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),"0 results found");
							}
						} else if(pk.isCancelled()) {
							pk.finished();
						}
					}
				};
	
				sw.execute();
				d.setVisible(true);
	
				resultTable.setModel(new LiteratureResultTableModel(sw.get()));
				lblNumFound.setText(sw.get().length + " pathways found.");
			} else {
				JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(), "Please Enter a Valid Query","Error", JOptionPane.ERROR_MESSAGE);
			}
		} else {
			JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(), "Please Enter a Search Query","Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private class LiteratureResultTableModel extends AbstractTableModel {
		private WSSearchResult[] results;
		private String[] columnNames = new String[] { "ID", "Name", "Species", "Literature Title" };

		public LiteratureResultTableModel(WSSearchResult[] results) {
			this.results = results;
		}

		public int getColumnCount() {
			return 4;
		}

		public int getRowCount() {
			return results.length;
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			WSSearchResult r = results[rowIndex];
			switch (columnIndex) {
				case 0:
					return r.getId();
				case 1:
					return r.getName();
				case 2:
					return r.getSpecies();
				case 3:
					WSIndexField[] fields = r.getFields();
					for (int i = 0; i < fields.length; i++) {
						if(fields[i].getName().toString().equals("literature.title"))
							return fields[i].getValues(0).toString();					
					}	
			}
			return "";
		}

		public String getColumnName(int column) {
			return columnNames[column];
		}
	}
}
