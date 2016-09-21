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

/**
 * Class: LoginPanel
 * Description: A simple class to get username and password.
 * @author Sravanthi Sinha
 * @author mkutmon
 **/

import java.awt.GridLayout;
import java.net.MalformedURLException;
import java.rmi.RemoteException;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.xml.rpc.ServiceException;

import org.pathvisio.core.debug.Logger;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.validators.Validator;

public class LoginPanel extends JPanel {
	public static String username = "";
	public static String password = "";
	public static boolean loggedin;

	private JTextField UserField = new JTextField(15);
	private JPasswordField PassField = new JPasswordField(15);
	private JCheckBox itsKeepBox = new JCheckBox("Save details:", false);
	private WikiPathwaysClientPlugin plugin;

	public LoginPanel(WikiPathwaysClientPlugin plugin) {
		this.plugin = plugin;
		setLayout(new GridLayout(3, 2));
		add(new JLabel("Username:"));
		add(UserField);
		add(new JLabel("Password:"));
		add(PassField);
		add(itsKeepBox);
		loggedin = false;
	}

	public boolean login() throws RemoteException, MalformedURLException, ServiceException {
		try {
			username = UserField.getText();
			password = new String(PassField.getPassword());
			if (Validator.CheckNonAlpha(username)) {
				plugin.getWpQueries().login(username, password);
				loggedin = true;
				if (!itsKeepBox.isSelected()) {
					username = "";
					password = "";
				}
			} else {
				JOptionPane.showMessageDialog(plugin.getDesktop().getFrame(),
						"Please Enter Valid UserName", "ERROR",
						JOptionPane.ERROR_MESSAGE);
				username = "";
				password = "";
				Logger.log.error("Wrong username. \n");
				return false;
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
							plugin.getDesktop().getFrame(),
							"Login was unsuccessful. Please check your username and password.\n Do you have webservice write permissions? If not, please contact the develoeprs:\n wikipathways-devel@googlegroups.com",
							"WikiPathways Login Error",
							JOptionPane.ERROR_MESSAGE);
			Logger.log.error("Login was unsuccessfull\n" + ex.getStackTrace() + "\n");
			username = "";
			password = "";
			return false;
		}
		return true;
	}
}
