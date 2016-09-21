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
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.xml.rpc.ServiceException;

import org.bridgedb.bio.Organism;
import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.pathvisio.wpclient.FailedConnectionException;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.models.BrowseResult;
import org.pathvisio.wpclient.models.BrowseTableModel;
import org.pathvisio.wpclient.utils.FileUtils;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 *	This class creates the content in the Browse Panel *
 * 	@author SravanthiSinha
 * 	@author mkutmon
 */
public class BrowsePanel extends JPanel {
	private final WikiPathwaysClientPlugin plugin;

	private JComboBox organismOpt;
	private JTable resultTable;
	private JScrollPane resultspane;	
	private JComboBox collOpt;
	private JComboBox curationOpt;

	private HashMap<String, String> curationTags;
	private HashMap<String, String> collectionTags;
//	public static HashMap<String, String> tagImages;

	private JPanel thisPanel;
	private JLabel lblNumFound;

	public BrowsePanel(final WikiPathwaysClientPlugin plugin) throws RemoteException, FailedConnectionException {
		this.plugin = plugin;
		this.thisPanel = this;
		
		curationTags = new HashMap<String, String>();
		collectionTags = new HashMap<String, String>();
//		tagImages = new HashMap<String, String>();
		List<String> organisms = retrieveOrgansims();
		setUpCurationTags();
			
		this.setLayout(new BorderLayout());
		
		// Browse Option Combo boxes
		organismOpt = new JComboBox(organisms.toArray());
		organismOpt.setSelectedItem(Organism.HomoSapiens.latinName());
	
		collOpt = new JComboBox(getCollectionTags().toArray());
		collOpt.setSelectedItem("All pathways");
		
		curationOpt = new JComboBox(getCurationTags().toArray());
		curationOpt.setSelectedItem("All tags");
			
		DefaultFormBuilder idOptBuilder = new DefaultFormBuilder(new FormLayout("right:pref, 3dlu,right:pref"));
		idOptBuilder.append(organismOpt);
			
		DefaultFormBuilder colOptBuilder = new DefaultFormBuilder(new FormLayout("right:pref, 3dlu,right:pref"));
		colOptBuilder.append(collOpt);
			
		DefaultFormBuilder curationOptBuilder = new DefaultFormBuilder(	new FormLayout("right:pref, 3dlu,right:pref"));
		curationOptBuilder.append(curationOpt);
	
		// Browse annotation labels
		JPanel opts = new JPanel();
		opts.setLayout(new CardLayout());
		JPanel idOpt = idOptBuilder.getPanel();
		opts.add(idOpt, "Species");
		JLabel speciesLabel = new JLabel("Species:");
	
		JPanel opts2 = new JPanel();
		opts2.setLayout(new CardLayout());
		JPanel collOptBuilder = colOptBuilder.getPanel();
		opts2.add(collOptBuilder, "Collections");
		JLabel collectionLabel = new JLabel("Collection:");
	
		JPanel opts4 = new JPanel();
		opts4.setLayout(new CardLayout());
		JPanel curOpt = curationOptBuilder.getPanel();
		opts4.add(curOpt, "Curation");
		JLabel curationTagLabel = new JLabel("Curation Tag:");
			
		// NORTH PANEL = SETTINGS
		JPanel browseOptBox = new JPanel();
		FormLayout layout = new FormLayout("left:pref,6dlu,left:pref,6dlu,left:pref,6dlu,left:pref,6dlu,left:pref,6dlu","p,20dlu");
		CellConstraints cc = new CellConstraints();
	
		browseOptBox.setLayout(layout);
		browseOptBox.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),"Browse options"));
		browseOptBox.add(speciesLabel, cc.xy(1, 1));
		browseOptBox.add(opts, cc.xy(1, 2));
		browseOptBox.add(collectionLabel, cc.xy(3, 1));
		browseOptBox.add(opts2, cc.xy(3, 2));
	
		browseOptBox.add(curationTagLabel, cc.xy(5, 1));
		browseOptBox.add(opts4, cc.xy(5, 2));
		JButton browseButton = new JButton(browseAction);
	
		browseOptBox.add(browseButton, cc.xy(7, 2));
		add(browseOptBox, BorderLayout.NORTH);
	
		// CENTER PANEL = RESULT TABLE
		resultTable = new JTable();
		resultspane = new JScrollPane(resultTable);
		add(resultspane, BorderLayout.CENTER);
			
		// SOUTH PANEL = STATUS 
		lblNumFound = new JLabel();
		add(lblNumFound, BorderLayout.SOUTH);
			
		resultTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					JTable target = (JTable) e.getSource();
					int row = target.getSelectedRow();

					BrowseTableModel model = (BrowseTableModel) target.getModel();

					File tmpDir = new File(plugin.getTmpDir(), FileUtils.getTimeStamp());
					tmpDir.mkdirs();
	
					try  {
						row = target.convertRowIndexToModel(row);
						plugin.openPathwayWithProgress(model.getValueAt(row, 0).toString(), 0, tmpDir);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(BrowsePanel.this,ex.getMessage(), "Error",JOptionPane.ERROR_MESSAGE);
						Logger.log.error("Error", ex);
					}
				}
			}
		});

	}
	
	Action browseAction = new AbstractAction("Browse") {
		public void actionPerformed(ActionEvent e) {
			try {
				resultspane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Pathways"));
				browse();
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(BrowsePanel.this,ex.getMessage(), "Error",JOptionPane.ERROR_MESSAGE);
				Logger.log.error("Error browsing WikiPathways", ex);
			}
		}
	};
	
	/**
	 * returns list of organisms for combo box
	 * retrieves data from wikipathways
	 */
	private List<String> retrieveOrgansims() throws RemoteException, FailedConnectionException {
		List<String> list = new ArrayList<String>();

		// first element in list is "All species"
		list.add("All species");
		
		// retrieve list of organisms from WikiPathways
		List<String> organsims = plugin.getWpQueries().listOrganisms(null);
		Collections.sort(organsims);
		list.addAll(1, organsims);
		
		return list;
	}
	
	private List<String> getCollectionTags() {
		List<String> list = new ArrayList<String>();
		list.add("All pathways");
		list.add("Curated pathways");
		list.add("Featured pathways");
		list.add("Reactome pathways");
		list.add("WormBase pathways");
		list.add("CIRM pathways");
		list.add("Wikipedia pathways");
		list.add("Open Access pathways");
		list.add("GenMAPP pathways");
		return list;
	}
	
	private List<String> getCurationTags() {
		List<String> list = new ArrayList<String>();
		list.add("All tags");
		list.add("Missing annotations");
		list.add("Unconnected lines");
		list.add("Under construction");
		list.add("Stub");
		list.add("Needs work");
		return list;
	}
	
	/**
	 * prepares curation tag and collection
	 * combobox
	 */
	private void setUpCurationTags() {
		// Collection combo box
		collectionTags.put("All pathways", "Curation:All");
		collectionTags.put("Curated pathways", "Curation:AnalysisCollection");
		collectionTags.put("Featured pathways", "Curation:FeaturedPathway");
		collectionTags.put("Reactome pathways", "Curation:Reactome_Approved");
		collectionTags.put("WormBase pathways", "Curation:WormBase_Approved");
		collectionTags.put("CIRM pathways", "Curation:CIRM_Related");
		collectionTags.put("Wikipedia pathways", "Curation:Wikipedia");
		collectionTags.put("Open Access pathways","Curation:OpenAccess");
		collectionTags.put("GenMAPP pathways", "Curation:GenMAPP_Approved");

		// Curation Tag Combobox
		curationTags.put("All tags", "No Curation");
		curationTags.put("Missing annotations", "Curation:MissingXRef");
		curationTags.put("Unconnected lines", "Curation:NoInteractions");
		curationTags.put("Under construction", "Curation:UnderConstruction");
		curationTags.put("Stub", "Curation:Stub");
		curationTags.put("Needs work", "Curation:NeedsWork");

		// Curation Tags mapping for image resources
//		tagImages.put("Curation:MissingXRef", "MissingXRef");
//		tagImages.put("Curation:Stub", "Stub");
//		tagImages.put("Curation:NeedsWork", "NeedsWork");
//		tagImages.put("Curation:AnalysisCollection", "Curated");
//		tagImages.put("Curation:MissingDescription", "MissingDescription");
//		tagImages.put("Curation:NoInteractions", "Unconnected");
//		tagImages.put("Curation:NeedsReference", "NeedsRef");
	}
	
	/**
	 * 
	 * @throws RemoteException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws ServiceException 
	 * @throws MalformedURLException 
	 */
	protected void browse() throws RemoteException, InterruptedException,ExecutionException, MalformedURLException, ServiceException 
	{
		lblNumFound.setText("");
		
		final ProgressKeeper pk = new ProgressKeeper();
		final ProgressDialog d = new ProgressDialog(JOptionPane.getFrameForComponent(this), "Browse", pk, true, true);
		try{
			SwingWorker<List<BrowseResult>, Void> sw = new SwingWorker<List<BrowseResult>, Void>() {
	
				protected List<BrowseResult> doInBackground() throws Exception {
					String organism = organismOpt.getSelectedItem().toString();
					String curationTag = curationOpt.getSelectedItem().toString();
					String collection = collOpt.getSelectedItem().toString();
					
					String collValue = collectionTags.get(collection);
					String curTag = curationTags.get(curationTag);
		
					
					Set<WSPathwayInfo> pathways= new HashSet<WSPathwayInfo>();
					
					if (organism.equalsIgnoreCase("ALL SPECIES")) {
						if(collection.equalsIgnoreCase("All pathways") && curationTag.equalsIgnoreCase("All tags")) {
							pathways = plugin.getWpQueries().browseAll(pk);
						} else if (collection.equalsIgnoreCase("All pathways") && !curationTag.equalsIgnoreCase("All tags")) {
							pathways = plugin.getWpQueries().browseByCurationTag(curTag, pk);
						} else if (!collection.equalsIgnoreCase("All pathways") && curationTag.equalsIgnoreCase("All tags")) {
							pathways = plugin.getWpQueries().browseByCurationTag(collValue, pk);
						} else {
							Set<WSPathwayInfo> set1 = plugin.getWpQueries().browseByCurationTag(curTag, pk);
							Set<WSPathwayInfo> set2 = plugin.getWpQueries().browseByCurationTag(collection, pk);
							
							for(WSPathwayInfo info : set1) {
								if(set2.contains(info)) {
									pathways.add(info);
								}
							}
						}
					} else {
						Organism org = Organism.fromLatinName(organism);
						if(collection.equalsIgnoreCase("All pathways") && curationTag.equalsIgnoreCase("All tags")) {
							System.out.println("browse by organism");
							pathways = plugin.getWpQueries().browseByOrganism(org, pk);
						} else if (collection.equalsIgnoreCase("All pathways") && !curationTag.equalsIgnoreCase("All tags")) {
							pathways = plugin.getWpQueries().browseByOrganismAndCurationTag(org, curTag, pk);
						} else if (!collection.equalsIgnoreCase("All pathways") && curationTag.equalsIgnoreCase("All tags")) {
							pathways = plugin.getWpQueries().browseByOrganismAndCurationTag(org, collValue, pk);
						} else {
							Set<WSPathwayInfo> set1 = plugin.getWpQueries().browseByOrganismAndCurationTag(org, curTag, pk);
							Set<WSPathwayInfo> set2 = plugin.getWpQueries().browseByOrganismAndCurationTag(org, collValue, pk);
							
							for(WSPathwayInfo info : set1) {
								if(set2.contains(info)) {
									pathways.add(info);
								}
							}
						}
					}
//					pk.report("Retrieve curation tags from WikiPathways");
					List<BrowseResult> results = new ArrayList<BrowseResult>();
					for(WSPathwayInfo info : pathways) {
						BrowseResult res = new BrowseResult(info);
//						Set<WSCurationTag> tags = plugin.getWpQueries().getCurationTags(info.getId(), null);
//						res.getTags().addAll(tags);
						results.add(res);
					}
					pk.report(results.size() + " pathways found.");
					return results;
				}
				
				protected void done() {
					if (!pk.isCancelled()) {
						try {
							JOptionPane.showMessageDialog(thisPanel.getParent(), get().size() + " results found");
							pk.finished();
						} catch (HeadlessException e) {
							e.printStackTrace();
						} catch (InterruptedException e) {
							
							e.printStackTrace();
						} catch (ExecutionException e) {
							
							e.printStackTrace();
						}
					} 
				}
			};
	
			sw.execute();
			d.setVisible(true);
			pk.setTaskName("Preparing Result Set");
			
			// prepare result table
			resultTable.setModel(new BrowseTableModel(sw.get()));
			resultTable.setDefaultRenderer(JPanel.class, new TableCellRenderer() {
	
				@Override
				public Component getTableCellRendererComponent(JTable table,Object value, boolean isSelected, boolean arg3, int arg4,
						int arg5) {
					
					JPanel p = (JPanel) value;
					return p;
				}
			});
			
			resultTable.setRowSorter(new TableRowSorter(resultTable.getModel()));
			lblNumFound.setText(sw.get().size() + " pathways found.");
			lblNumFound.repaint();
		} finally {
			pk.finished();
		}
	}
}
