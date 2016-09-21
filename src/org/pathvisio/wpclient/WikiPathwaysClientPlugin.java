// PathVisio WP Client
// Plugin that provides a WikiPathways client for PathVisio.
// Copyright 2013 developed for Google Summer of Code
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

package org.pathvisio.wpclient;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.xml.rpc.ServiceException;

import org.bridgedb.DataSource;
import org.bridgedb.Xref;
import org.pathvisio.core.ApplicationEvent;
import org.pathvisio.core.Engine;
import org.pathvisio.core.Engine.ApplicationEventListener;
import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.preferences.GlobalPreference;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.core.view.GeneProduct;
import org.pathvisio.core.view.Graphics;
import org.pathvisio.core.view.VPathway;
import org.pathvisio.core.view.VPathwayElement;
import org.pathvisio.core.view.VPathwayEvent;
import org.pathvisio.core.view.VPathwayListener;
import org.pathvisio.desktop.PreferencesDlg;
import org.pathvisio.desktop.PvDesktop;
import org.pathvisio.desktop.plugin.Plugin;
import org.pathvisio.gui.PathwayElementMenuListener.PathwayElementMenuHook;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSSearchResult;
import org.pathvisio.wpclient.actions.BrowseAction;
import org.pathvisio.wpclient.actions.OpenPathwayFromXrefAction;
import org.pathvisio.wpclient.actions.SearchAction;
import org.pathvisio.wpclient.actions.UpdateAction;
import org.pathvisio.wpclient.actions.UploadAction;
import org.pathvisio.wpclient.panels.PathwayPanel;
import org.pathvisio.wpclient.preferences.URLPreference;
import org.pathvisio.wpclient.utils.FileUtils;
import org.wikipathways.client.WikiPathwaysClient;

/**
 * Plugin that provides a WikiPathways client for PathVisio. Enables users to
 * open pathways directly from DataNodes annotated to a pathway, using the
 * right-click menu.
 * 
 * This plugin also includes a dialog to search, advanced search browse and load
 * pathways from WikiPathways (like in the Cytoscape GPML plugin).
 * 
 * @author Thomas Kelder, Sravanthi Sinha, mkutmon
 */
public class WikiPathwaysClientPlugin implements Plugin, ApplicationEventListener, VPathwayListener {
	
	private PvDesktop desktop;
	private File tmpDir = new File(GlobalPreference.getPluginDir(), "wpclient-cache");
	private JMenu wikipathwaysMenu;
	private JMenuItem createMenu,updateMenu;
	
	public static String revisionno = "";
	public static String pathwayid = "";
	
	private WikiPathwaysClientPlugin plugin;
	
	public static final String ARG_PROPERTY_OPEN_WPID = "open.wpid";

	// handles 
	private IWPQueries wpQueries;
	
	public WikiPathwaysClientPlugin(IWPQueries wpQueries) {
		this.wpQueries = wpQueries;
		plugin = this;
	}

	@Override
	public void init(PvDesktop desktop) {
		try {
			this.desktop = desktop;
			tmpDir.mkdirs();
			Logger.log.info("Initializing WikiPathways Client plugin");

			// intialization
			initPreferences();
			registerActions();

			new WikipathwaysPluginManagerAction(desktop);

			// register a listener to notify when a pathway is opened
			desktop.getSwingEngine().getEngine().addApplicationEventListener(this);
			
			String str = System.getProperty(ARG_PROPERTY_OPEN_WPID);
			if (str != null) {
				openPathwayWithProgress(str, 0, tmpDir);
			}
		} catch (Exception e) {
			Logger.log.error("Error while initializing WikiPathways client", e);
			JOptionPane.showMessageDialog(desktop.getSwingEngine().getApplicationPanel(), 
					"Could not initialize WikiPathways client plugin.", "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Initializing Preferences.
	 * URLPreference - specify webservice URL
	 */
	private void initPreferences() {
		PreferencesDlg dlg = desktop.getPreferencesDlg();
		
		dlg.addPanel("WikiPathways Plugin", dlg.builder().stringField(URLPreference.CONNECTION_URL, "WP webservice URL").build()); 
	}

	
	/**
	 * Preparing the Submenu For WikiPathways Menu
	 */

	private class WikipathwaysPluginManagerAction {

		public WikipathwaysPluginManagerAction(PvDesktop desktop) {
			// preparing menus and submenus
			wikipathwaysMenu = new JMenu("WikiPathways");
			JMenuItem searchMenu = new JMenuItem("Search");
			JMenuItem browseMenu = new JMenuItem("Browse");

			// preparing actions for menus and submenus
			SearchAction searchAction = new SearchAction(plugin);
			BrowseAction browseAction = new BrowseAction(plugin);

			searchMenu.addActionListener(searchAction);
			browseMenu.addActionListener(browseAction);

			createMenu = new JMenuItem("Upload New");
			updateMenu = new JMenuItem("Update");

			UploadAction createAction = new UploadAction(plugin);
			UpdateAction updateAction = new UpdateAction(plugin);

			createMenu.addActionListener(createAction);
			updateMenu.addActionListener(updateAction);

			wikipathwaysMenu.add(searchMenu);
			wikipathwaysMenu.add(browseMenu);
			wikipathwaysMenu.add(createMenu);
			wikipathwaysMenu.add(updateMenu);
			
			desktop.registerSubMenu("Plugins", wikipathwaysMenu);
			updateState();
		}
	}

	/**
	 * Checks if a pathway is open or not. If there is no open pathway, the
	 * create and update menus are disabled/enabled.
	 */
	public void updateState() {
		boolean status = (desktop.getSwingEngine().getEngine().hasVPathway());
		createMenu.setEnabled(status);
		updateMenu.setEnabled(status);
	}

	/**
	 * Register actions to provide option to open a pathway From Xref on right
	 * click
	 */
	private void registerActions() {
		desktop.addPathwayElementMenuHook(new PathwayElementMenuHook() {
			public void pathwayElementMenuHook(VPathwayElement e, JPopupMenu menu) {
				if (!(e instanceof Graphics)) {
					return;
				}

				PathwayElement pe = ((Graphics) e).getPathwayElement();

				if (pe.getXref() == null) {
					return;
				}

				DataSource ds = pe.getXref().getDataSource();
				if (ds == null) {
					return;
				}
				OpenPathwayFromXrefAction action = new OpenPathwayFromXrefAction(WikiPathwaysClientPlugin.this, pe);
				menu.add(action);
			}
		});
	}

	public void openPathwayWithProgress(final String id, final int rev, final File tmpDir)
			throws InterruptedException, ExecutionException {
		final ProgressKeeper pk = new ProgressKeeper();
		final ProgressDialog d = new ProgressDialog(JOptionPane.getFrameForComponent(desktop.getSwingEngine().getApplicationPanel()), "", pk, false, true);

		SwingWorker<Boolean, Void> sw = new SwingWorker<Boolean, Void>() {
			protected Boolean doInBackground() throws Exception {
				pk.setTaskName("Opening pathway");
				try {
					openPathway(id, rev, tmpDir);
				} catch (Exception e) {
					Logger.log.error("The Pathway is not found", e);
					JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(), "The Pathway is not found", "ERROR", JOptionPane.ERROR_MESSAGE);
				} finally {
					pk.finished();
				}
				return true;
			}

			protected void done() {
				if (pk.isCancelled()) {
					pk.finished();
				}
			}
		};

		sw.execute();
		d.setVisible(true);
		sw.get();
	}

	public void openPathwayWithProgress(final String id, final int rev, final File tmpDir,
			final Xref[] xrefs) throws InterruptedException, ExecutionException {
		final ProgressKeeper pk = new ProgressKeeper();
		final ProgressDialog d = new ProgressDialog(
				JOptionPane.getFrameForComponent(desktop.getSwingEngine().getApplicationPanel()), "", pk, false, true);

		SwingWorker<Boolean, Void> sw = new SwingWorker<Boolean, Void>() {
			protected Boolean doInBackground() throws Exception {
				pk.setTaskName("Opening pathway");
				try {
					openPathway(id, rev, tmpDir, xrefs);
				} catch (Exception e) {
					Logger.log.error("The Pathway is not found", e);
					JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
							"The Pathway is not found", "ERROR",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					pk.finished();
				}
				return true;
			}

			protected void done() {
				if (pk.isCancelled()) {
					pk.finished();
				}
			}
		};

		sw.execute();
		d.setVisible(true);
		sw.get();
	}

	/**
	 * Load Pathway into PathVisio on selection of pathway from list provided by
	 * any Search/ Browse Dialog.
	 * @throws FailedConnectionException 
	 */
	protected void openPathway(String id, int rev, File tmpDir) throws RemoteException, ConverterException, FailedConnectionException {
		WSPathway wsp = getWpQueries().getPathway(id, rev, null);
		Pathway p = WikiPathwaysClient.toPathway(wsp);
		File tmp = new File(tmpDir, wsp.getId() + ".r" + wsp.getRevision() + ".gpml");
		p.writeToXml(tmp, true);
		revisionno = wsp.getRevision();
		pathwayid = wsp.getId();
		Engine engine = desktop.getSwingEngine().getEngine();
		engine.setWrapper(desktop.getSwingEngine().createWrapper());
		engine.openPathway(tmp);
	}

	/**
	 * Load Pathway into PathVisio on selection of pathway from list provided by
	 * any Search/ Browse Dialog.
	 * @throws FailedConnectionException 
	 */
	protected void openPathway(String id, int rev, File tmpDir, Xref[] xrefs) throws RemoteException, ConverterException, FailedConnectionException {
		WSPathway wsp = getWpQueries().getPathway(id, rev, null);
		Pathway p = WikiPathwaysClient.toPathway(wsp);
		File tmp = new File(tmpDir, wsp.getId() + ".r" + wsp.getRevision() + ".gpml");
		p.writeToXml(tmp, true);
		revisionno = wsp.getRevision();
		pathwayid = wsp.getId();
		Engine engine = desktop.getSwingEngine().getEngine();
		engine.setWrapper(desktop.getSwingEngine().createWrapper());
		engine.openPathway(tmp);

		highlightResults(xrefs);
	}
	
	/**
	 * HighLight the DataNodes With particular Xref	 
	 */
	private void highlightResults(Xref[] xrefs) {
		Rectangle2D interestingRect = null;
		Engine engine = desktop.getSwingEngine().getEngine();
		VPathway vpy = engine.getActiveVPathway();
		for (VPathwayElement velt : vpy.getDrawingObjects()) {
			if (velt instanceof GeneProduct) {
				GeneProduct gp = (GeneProduct) velt;
				for (Xref xref : xrefs) {
					if (xref.equals(gp.getPathwayElement().getXref())) {
						gp.highlight(Color.YELLOW);
						if (interestingRect == null) {
							interestingRect = gp.getVBounds();
						}
						break;
					}
				}
			}
		}
		if (interestingRect != null) {
			vpy.getWrapper().scrollTo(interestingRect.getBounds());
		}
	}

	@Override
	public void done() {
		desktop.unregisterSubMenu("Plugins", wikipathwaysMenu);
		if(tmpDir.exists()) {
			FileUtils.deleteDirectory(tmpDir);
		}
	}
	
	public void openPathwayXrefWithProgress(final Xref x, final int rev, final File tmpDir)
			throws InterruptedException, ExecutionException {

		final ProgressKeeper pk = new ProgressKeeper();
		final ProgressDialog d = new ProgressDialog(
				JOptionPane.getFrameForComponent(desktop.getSwingEngine()
						.getApplicationPanel()), "", pk, false, true);

		SwingWorker<Boolean, Void> sw = new SwingWorker<Boolean, Void>() {
			protected Boolean doInBackground() throws Exception {
				pk.setTaskName("Finding Pathways");
				try {
					openPathwayXref(x, rev, tmpDir);
				} catch (Exception e) {
					Logger.log.error("The Pathway is not found", e);
					JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
							"The Pathway is not found", "ERROR",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					pk.finished();
				}
				return true;
			}

		};

		sw.execute();
		d.setVisible(true);
		sw.get();
	}

	protected void openPathwayXref(Xref x, int rev, File tmpDir) throws MalformedURLException, ServiceException, FailedConnectionException, ConverterException {

		WSSearchResult[] wsp;
		try {
			wsp = getWpQueries().findByXref(new Xref[]{x}, null);

			Xref[] xref = { x };
			PathwayPanel p = new PathwayPanel(WikiPathwaysClientPlugin.this, wsp, tmpDir, xref);
			JDialog d = new JDialog(desktop.getFrame(), "Pathways Containing "+x, false);

			d.getContentPane().add(p);
			d.pack();
			d.setVisible(true);
			d.setResizable(false);
			d.setLocationRelativeTo(desktop.getSwingEngine().getFrame());
			d.setVisible(true);

		} catch (RemoteException e) {
			
			e.printStackTrace();
		}
	}

	@Override
	public void applicationEvent(ApplicationEvent e) {
		if(e.getType().equals(ApplicationEvent.Type.VPATHWAY_NEW)) {
			revisionno = "";
			pathwayid = "";
		} else if (e.getType().equals(ApplicationEvent.Type.VPATHWAY_OPENED)) {
			if(desktop.getSwingEngine().getEngine().getActivePathway().getSourceFile() != null) {
				// pathway has not been loaded from webservice
				if(!desktop.getSwingEngine().getEngine().getActivePathway().getSourceFile().getAbsolutePath().contains(GlobalPreference.getPluginDir().getAbsolutePath())) {
					revisionno = "";
					pathwayid = "";
				}
			}
		}
		updateState();
	}

	@Override
	public void vPathwayEvent(VPathwayEvent e) {
		updateState();
	}
	
	//////////////////////////////////////
	// SETTERS & GETTERS
	//////////////////////////////////////

	public IWPQueries getWpQueries() {
		return wpQueries;
	}

	public File getTmpDir() {
		return tmpDir;
	}

	public PvDesktop getDesktop() {
		return desktop;
	}
}
